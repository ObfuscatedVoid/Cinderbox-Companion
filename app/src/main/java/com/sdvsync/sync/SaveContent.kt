package com.sdvsync.sync

import com.sdvsync.steam.CloudFile
import java.security.MessageDigest

internal fun saveContentMatches(local: Map<String, ByteArray>, cloud: List<CloudFile>): Boolean = local.isNotEmpty() &&
    local.keys == cloud.map { it.baseName }.toSet() &&
    cloud.all { file ->
        MessageDigest.getInstance("SHA-1").digest(local.getValue(file.baseName)).contentEquals(file.sha)
    }

internal fun saveContentMatches(local: Map<String, ByteArray>, cloud: Map<String, ByteArray>): Boolean =
    local.isNotEmpty() &&
        local.keys == cloud.keys &&
        local.all { (name, bytes) ->
            bytes.contentEquals(cloud.getValue(name))
        }
