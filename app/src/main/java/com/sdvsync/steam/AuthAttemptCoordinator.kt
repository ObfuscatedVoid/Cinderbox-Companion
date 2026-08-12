package com.sdvsync.steam

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesAuthSteamclient.EAuthSessionGuardType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

internal enum class AuthAttemptKind {
    RESUME_SESSION,
    CREDENTIALS,
    QR
}

internal data class AuthAttemptToken(val id: Long, val kind: AuthAttemptKind, val job: Job)

internal class AuthAttemptCoordinator {
    private val lock = Any()
    private var nextId = 0L
    private var active: AuthAttemptToken? = null

    fun tryBegin(kind: AuthAttemptKind, job: Job): AuthAttemptToken? = synchronized(lock) {
        if (active != null || !job.isActive) {
            return@synchronized null
        }
        AuthAttemptToken(++nextId, kind, job).also { active = it }
    }

    fun activeAttempt(): AuthAttemptToken? = synchronized(lock) { active?.takeIf { it.job.isActive } }

    fun isActive(attemptId: Long): Boolean = synchronized(lock) {
        active?.let { it.id == attemptId && it.job.isActive } == true
    }

    fun runIfActive(attemptId: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (active?.let { it.id == attemptId && it.job.isActive } != true) {
            return@synchronized false
        }
        action()
        true
    }

    suspend fun runIfActiveOn(attemptId: Long, dispatcher: CoroutineDispatcher, action: () -> Unit): Boolean =
        withContext(dispatcher) {
            runIfActive(attemptId, action)
        }

    fun finish(attempt: AuthAttemptToken, beforeFinish: () -> Unit = {}): Boolean = synchronized(lock) {
        if (active != attempt) {
            return@synchronized false
        }
        beforeFinish()
        active = null
        true
    }

    fun cancelActive(kind: AuthAttemptKind? = null, canCancel: () -> Boolean = { true }): AuthAttemptToken? {
        val cancelled = synchronized(lock) {
            val attempt = active ?: return@synchronized null
            if ((kind != null && attempt.kind != kind) || !canCancel()) {
                return@synchronized null
            }
            active = null
            attempt
        }
        cancelled?.job?.cancel(CancellationException("Authentication attempt cancelled"))
        return cancelled
    }
}

internal fun pollingIntervalMillis(intervalSeconds: Float): Long {
    if (!intervalSeconds.isFinite() || intervalSeconds <= 0f) {
        return 1000L
    }
    return (intervalSeconds * 1000f).roundToLong().coerceAtLeast(1L)
}

internal fun shouldIgnoreTransportDisconnect(callbackIsUserInitiated: Boolean, appDisconnect: Boolean): Boolean =
    callbackIsUserInitiated && !appDisconnect

internal fun EAuthSessionGuardType.isCodeConfirmation(): Boolean =
    this == EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode ||
        this == EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode

private data class PendingValue<T>(val attemptId: Long, val future: CompletableFuture<T>)

private class PendingAuthInput<T>(private val duplicateMessage: String) {
    private val pendingValue = AtomicReference<PendingValue<T>?>(null)

    fun await(attemptId: Long): CompletableFuture<T> {
        val pending = PendingValue(attemptId, CompletableFuture<T>())
        check(pendingValue.compareAndSet(null, pending)) { duplicateMessage }
        return pending.future
    }

    fun submit(attemptId: Long, value: T, beforeComplete: () -> Unit): Boolean {
        val pending = pendingValue.get() ?: return false
        if (pending.attemptId != attemptId || !pendingValue.compareAndSet(pending, null)) {
            return false
        }

        beforeComplete()
        return pending.future.complete(value)
    }

    fun cancel(attemptId: Long): Boolean {
        while (true) {
            val pending = pendingValue.get() ?: return false
            if (pending.attemptId != attemptId) {
                return false
            }
            if (pendingValue.compareAndSet(pending, null)) {
                return pending.future.cancel(false)
            }
        }
    }
}

internal class PendingTwoFactorChallenge {
    private val pendingInput = PendingAuthInput<String>("A Steam Guard code challenge is already pending")

    fun awaitCode(attemptId: Long): CompletableFuture<String> = pendingInput.await(attemptId)

    fun submit(attemptId: Long, code: String, beforeComplete: () -> Unit = {}): Boolean {
        val normalizedCode = code.trim()
        return normalizedCode.isNotEmpty() && pendingInput.submit(attemptId, normalizedCode, beforeComplete)
    }

    fun cancel(attemptId: Long): Boolean = pendingInput.cancel(attemptId)
}

internal class PendingDeviceConfirmationChoice {
    private val pendingInput = PendingAuthInput<Boolean>("A device confirmation choice is already pending")

    fun awaitChoice(attemptId: Long): CompletableFuture<Boolean> = pendingInput.await(attemptId)

    fun submit(attemptId: Long, useMobileApproval: Boolean, beforeComplete: () -> Unit = {}): Boolean =
        pendingInput.submit(attemptId, useMobileApproval, beforeComplete)

    fun cancel(attemptId: Long): Boolean = pendingInput.cancel(attemptId)
}
