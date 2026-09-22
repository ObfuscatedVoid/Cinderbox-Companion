package com.sdvsync.download

import java.math.BigInteger

internal fun isNewerRelease(installed: String?, latest: String?): Boolean {
    val current = parseVersion(installed) ?: return false
    val candidate = parseVersion(latest) ?: return false
    for (index in 0 until maxOf(current.components.size, candidate.components.size)) {
        val comparison = candidate.components.getOrElse(index) { BigInteger.ZERO }
            .compareTo(current.components.getOrElse(index) { BigInteger.ZERO })
        if (comparison != 0) return comparison > 0
    }
    val currentPre = current.prerelease
    val candidatePre = candidate.prerelease
    if (currentPre.isEmpty() || candidatePre.isEmpty()) return currentPre.isNotEmpty()
    for (index in 0 until minOf(currentPre.size, candidatePre.size)) {
        val currentPart = currentPre[index]
        val candidatePart = candidatePre[index]
        val currentNumeric = currentPart.all(Char::isDigit)
        val candidateNumeric = candidatePart.all(Char::isDigit)
        val comparison = when {
            currentNumeric && candidateNumeric -> BigInteger(candidatePart).compareTo(BigInteger(currentPart))
            currentNumeric -> 1
            candidateNumeric -> -1
            else -> candidatePart.compareTo(currentPart)
        }
        if (comparison != 0) return comparison > 0
    }
    return candidatePre.size > currentPre.size
}

private val versionPattern = Regex(
    "^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
)

private data class ReleaseVersion(val components: List<BigInteger>, val prerelease: List<String>)

private fun parseVersion(version: String?): ReleaseVersion? {
    if (version == null || version.length > 200) return null
    val match = versionPattern.matchEntire(version) ?: return null
    return ReleaseVersion(
        match.groupValues[1].split('.').map(::BigInteger),
        match.groupValues[2].takeIf(String::isNotEmpty)?.split('.').orEmpty()
    )
}
