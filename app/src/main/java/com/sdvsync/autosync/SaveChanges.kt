package com.sdvsync.autosync

import java.security.MessageDigest

internal fun saveFingerprint(files: Map<String, ByteArray>): Map<String, String> = files.mapValues { (_, bytes) ->
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

internal fun changedSaveNames(
    before: Map<String, Map<String, String>>?,
    after: Map<String, Map<String, String>>
): Set<String> = if (before == null) emptySet() else after.keys.filterTo(mutableSetOf()) { after[it] != before[it] }
