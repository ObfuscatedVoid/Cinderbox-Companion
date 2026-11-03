package com.sdvsync.download

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * R43-C: fail-closed admission primitives. This is NOT a live-game unlock.
 * Android process-list absence, a foreground-service mutex, and user confirmation
 * do not prove that Cinderbox cannot launch concurrently with a directory rename.
 */
internal object R43CUpdateSafety {
    enum class GameState { STOPPED, RUNNING, UNKNOWN }
    data class LaunchObservation(
        val state: GameState,
        /** Must represent a verified exclusion of NEW game launches, not just a mutex in Companion. */
        val launchExcluded: Boolean,
        val authority: String
    )
    fun interface GameWitness { fun observe(): LaunchObservation }

    private val unavailableWitness = GameWitness {
        LaunchObservation(GameState.UNKNOWN, false, "no-qualified-cinderbox-launch-fence")
    }

    /** No supported, device-qualified launch fence exists yet. Deliberately denies. */
    fun requireProductionQuiescence() = requireQuiescence(unavailableWitness)

    /** Pure fixture seam. Never wire an injected fixture witness into production. */
    fun requireQuiescence(witness: GameWitness) {
        val observation = runCatching { witness.observe() }.getOrElse {
            throw IllegalStateException("Game activity could not be verified; update blocked", it)
        }
        check(observation.state == GameState.STOPPED && observation.launchExcluded &&
            observation.authority.isNotBlank()) {
            "Game activity or launch exclusion is unverified; update blocked"
        }
    }

    enum class CapacityCode {
        READY, UNKNOWN_STAGE_BYTES, UNKNOWN_CAPACITY, INSUFFICIENT_SPACE,
        INVALID_LAYOUT, LINKED_ENTRY, UNSAFE_FILESTORE, INVALID_BYTE_COUNT
    }
    data class Capacity(
        val code: CapacityCode,
        val availableBytes: Long?,
        val requiredBytes: Long?,
        val detail: String
    ) { val eligible: Boolean get() = code == CapacityCode.READY }

    private const val MIN_SHARED_RESERVE = 256L * 1024L * 1024L
    private const val MIN_PRIVATE_RESERVE = 16L * 1024L * 1024L

    /**
     * Pure arithmetic for unit tests and platform probes. A staged tree already
     * exists at publish: its bytes MUST NOT be counted as an additional copy.
     * The original is renamed, not duplicated. The extra temporary allowance
     * protects ledger/journal/fsync and unexpected metadata during promotion.
     */
    fun assessCapacity(
        freeSharedBytes: Long?, freePrivateBytes: Long?,
        expectedNewStageBytes: Long?, stageAlreadyComplete: Boolean,
        sameFilesystem: Boolean, reserveBytes: Long = MIN_SHARED_RESERVE,
        metadataAllowanceBytes: Long = 16L * 1024L * 1024L
    ): Capacity {
        if (!sameFilesystem) return Capacity(CapacityCode.UNSAFE_FILESTORE, freeSharedBytes, null,
            "Stage and installed game are not proven on one atomic-rename filesystem")
        if (reserveBytes < 0 || metadataAllowanceBytes < 0 ||
            (expectedNewStageBytes != null && expectedNewStageBytes <= 0))
            return Capacity(CapacityCode.INVALID_BYTE_COUNT, freeSharedBytes, null,
                "Invalid stage or reserve byte count")
        if (expectedNewStageBytes == null)
            return Capacity(CapacityCode.UNKNOWN_STAGE_BYTES, freeSharedBytes, null,
                "Verified Steam stage size is unknown; cannot budget an update")
        if (freeSharedBytes == null || freeSharedBytes < 0 ||
            freePrivateBytes == null || freePrivateBytes < MIN_PRIVATE_RESERVE)
            return Capacity(CapacityCode.UNKNOWN_CAPACITY, freeSharedBytes, null,
                "Shared or private journal capacity is unavailable")
        val needed = try {
            Math.addExact(Math.addExact(reserveBytes, metadataAllowanceBytes),
                if (stageAlreadyComplete) 0L else expectedNewStageBytes)
        } catch (_: ArithmeticException) {
            return Capacity(CapacityCode.INVALID_BYTE_COUNT, freeSharedBytes, null,
                "Storage budget overflow")
        }
        return if (freeSharedBytes >= needed) Capacity(CapacityCode.READY,
            freeSharedBytes, needed, "Capacity and safety reserve currently sufficient")
        else Capacity(CapacityCode.INSUFFICIENT_SPACE, freeSharedBytes, needed,
            "Storage below the update safety reserve")
    }

    /**
     * Observe a fully staged, token-owned sibling without following symlinks.
     * This is a point-in-time observation, NOT a proof that free space, quota,
     * permissions or the stage will stay unchanged after the call.
     */
    fun inspectPreparedStage(stage: File, installed: File, privateRoot: File): Capacity {
        if (!stage.isDirectory || !installed.isDirectory ||
            stage.parentFile?.canonicalPath != installed.parentFile?.canonicalPath ||
            Files.isSymbolicLink(stage.toPath()) || Files.isSymbolicLink(installed.toPath()))
            return Capacity(CapacityCode.INVALID_LAYOUT, null, null, "Unexpected stage layout")
        val sharedStore = runCatching { Files.getFileStore(installed.parentFile.toPath()) }.getOrNull()
        val stageStore = runCatching { Files.getFileStore(stage.toPath()) }.getOrNull()
        if (sharedStore == null || stageStore == null ||
            sharedStore.name() != stageStore.name() || sharedStore.type() != stageStore.type())
            return Capacity(CapacityCode.UNSAFE_FILESTORE, null, null,
                "Filesystem identity cannot be established")
        val total = try { measureStage(stage.toPath()) }
        catch (_: Exception) {
            return Capacity(CapacityCode.LINKED_ENTRY, null, null,
                "Linked, unreadable or unstable staged content")
        }
        return assessCapacity(installed.parentFile.usableSpace.takeIf { it > 0L },
            privateRoot.usableSpace.takeIf { it > 0L }, total, true, true)
    }

    private fun measureStage(root: Path): Long {
        var count = 0
        var bytes = 0L
        Files.walk(root).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                check(!Files.isSymbolicLink(path)) { "Untrusted symbolic link" }
                count++
                check(count <= 200_000) { "Staged tree exceeds inspection limit" }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    bytes = Math.addExact(bytes, Files.size(path))
                else check(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    "Stage contains an unsupported filesystem entry"
                }
            }
        }
        check(bytes > 0L) { "Empty stage" }
        return bytes
    }

    /** A production failure never requests automatic deletion to reclaim space. */
    fun requirePreparedPublicationSpace(stage: File, installed: File, privateRoot: File) {
        val result = inspectPreparedStage(stage, installed, privateRoot)
        check(result.eligible) { "Protected update blocked: ${result.detail}" }
    }

    /** An unmeasured Steam transfer must not start against an occupied game. */
    fun requireEarlyDownloadBudget(verifiedExpectedBytes: Long?) {
        val result = assessCapacity(null, null, verifiedExpectedBytes, false, true)
        check(result.eligible) { "Protected update blocked: ${result.detail}" }
    }
}
