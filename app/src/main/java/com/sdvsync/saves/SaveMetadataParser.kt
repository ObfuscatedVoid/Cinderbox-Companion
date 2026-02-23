package com.sdvsync.saves

import android.util.Xml
import com.sdvsync.util.GzipUtil
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File

data class SaveMetadata(
    val farmerName: String,
    val farmName: String,
    val dayOfMonth: Int,
    val season: Int,
    val year: Int,
    val gameVersion: String,
    val millisecondsPlayed: Long,
    val uniqueId: String,
) {
    val daysPlayed: Int get() = (year - 1) * 112 + season * 28 + dayOfMonth

    val seasonName: String
        get() = when (season) {
            0 -> "Spring"
            1 -> "Summer"
            2 -> "Fall"
            3 -> "Winter"
            else -> "Unknown"
        }

    val displayDate: String get() = "$seasonName $dayOfMonth, Year $year"
}

class SaveMetadataParser {

    /**
     * Parse SaveGameInfo XML from bytes.
     * SaveGameInfo is small (~2KB) and safe to fully parse.
     */
    fun parseFromBytes(data: ByteArray): SaveMetadata? {
        return try {
            // Stardew 1.6+ saves may be gzip-compressed — decompress before parsing XML
            val xmlData = GzipUtil.decompressIfGzip(data)
            parse(ByteArrayInputStream(xmlData))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse SaveGameInfo XML from a file.
     */
    fun parseFromFile(file: File): SaveMetadata? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val rawBytes = file.readBytes()
            val xmlData = GzipUtil.decompressIfGzip(rawBytes)
            parse(ByteArrayInputStream(xmlData))
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(input: java.io.InputStream): SaveMetadata {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")

        var farmerName = ""
        var farmName = ""
        var dayOfMonth = 1
        var season = 0
        var year = 1
        var gameVersion = ""
        var millisecondsPlayed = 0L
        var uniqueId = ""
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "name" -> if (farmerName.isEmpty()) farmerName = text
                            "farmName" -> farmName = text
                            "dayOfMonthForSaveGame" -> dayOfMonth = text.toIntOrNull() ?: 1
                            "seasonForSaveGame" -> season = text.toIntOrNull() ?: 0
                            "yearForSaveGame" -> year = text.toIntOrNull() ?: 1
                            "gameVersion" -> gameVersion = text
                            "millisecondsPlayed" -> millisecondsPlayed = text.toLongOrNull() ?: 0L
                            "uniqueIDForThisGame" -> uniqueId = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    currentTag = ""
                }
            }
            parser.next()
        }

        return SaveMetadata(
            farmerName = farmerName,
            farmName = farmName,
            dayOfMonth = dayOfMonth,
            season = season,
            year = year,
            gameVersion = gameVersion,
            millisecondsPlayed = millisecondsPlayed,
            uniqueId = uniqueId,
        )
    }

    /**
     * Extract the unique ID from a save folder name.
     * Format: "FarmerName_123456789" -> "123456789"
     */
    fun extractUniqueId(folderName: String): String? {
        val lastUnderscore = folderName.lastIndexOf('_')
        if (lastUnderscore < 0) return null
        val id = folderName.substring(lastUnderscore + 1)
        return if (id.all { it.isDigit() }) id else null
    }
}
