package com.sdvsync.saves

import android.content.Context
import android.util.Xml
import com.sdvsync.R
import com.sdvsync.logging.AppLogger
import com.sdvsync.util.GzipUtil
import java.io.ByteArrayInputStream
import java.io.File
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

class SaveValidator(private val context: Context) {

    companion object {
        private const val TAG = "SaveValidator"
    }

    /**
     * Validate a save folder on the local filesystem.
     */
    fun validateSaveFolder(saveDir: File): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!saveDir.exists() || !saveDir.isDirectory) {
            return ValidationResult(false, listOf(context.getString(R.string.health_dir_missing)))
        }

        val folderName = saveDir.name
        val mainSaveFile = saveDir.resolve(folderName)
        val saveGameInfo = saveDir.resolve("SaveGameInfo")

        // Check main save file
        if (!mainSaveFile.exists()) {
            errors.add(context.getString(R.string.health_main_missing, folderName))
        } else if (mainSaveFile.length() < 1024) {
            errors.add(context.getString(R.string.health_main_too_small, mainSaveFile.length()))
        } else if (mainSaveFile.length() > 200 * 1024 * 1024) {
            warnings.add(context.getString(R.string.health_main_too_large, mainSaveFile.length() / 1024 / 1024))
        }

        // Check SaveGameInfo
        if (!saveGameInfo.exists()) {
            errors.add(context.getString(R.string.health_info_missing))
        } else if (saveGameInfo.length() < 100) {
            errors.add(context.getString(R.string.health_info_too_small, saveGameInfo.length()))
        }

        // Check for in-progress saves
        val tempFiles = saveDir.listFiles()?.filter {
            it.name.contains("_STARDEWVALLEYSAVETMP")
        } ?: emptyList()
        if (tempFiles.isNotEmpty()) {
            errors.add(context.getString(R.string.health_temp_files))
        }

        // Validate XML structure of main save
        if (mainSaveFile.exists() && mainSaveFile.length() > 0) {
            if (!validateXmlEnding(mainSaveFile, "</SaveGame>")) {
                errors.add(context.getString(R.string.health_main_xml_end))
            }
        }

        // Validate XML structure of SaveGameInfo
        if (saveGameInfo.exists() && saveGameInfo.length() > 0) {
            if (!validateXmlEnding(saveGameInfo, "</Farmer>")) {
                errors.add(context.getString(R.string.health_info_xml_end))
            }
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Validate downloaded save data (in-memory).
     */
    fun validateSaveData(mainSaveData: ByteArray?, saveGameInfoData: ByteArray?): ValidationResult {
        val errors = mutableListOf<String>()

        if (mainSaveData == null || mainSaveData.isEmpty()) {
            errors.add(context.getString(R.string.health_main_empty))
        } else {
            // Stardew 1.6+ saves may be gzip-compressed — decompress before checking XML
            AppLogger.d(TAG, "Validating main save: size=${mainSaveData.size}, isGzip=${GzipUtil.isGzip(mainSaveData)}")
            val xmlData = GzipUtil.decompressIfGzip(mainSaveData)
            AppLogger.d(TAG, "Main save after decompression: size=${xmlData.size}")
            if (xmlData.size < 1024) {
                errors.add(context.getString(R.string.health_main_data_small, xmlData.size))
            }
            val tail = String(xmlData.takeLast(100).toByteArray())
            AppLogger.d(TAG, "Main save tail (last 100 chars): '$tail'")
            if (!tail.contains("</SaveGame>")) {
                errors.add(context.getString(R.string.health_main_tag_missing))
            }
        }

        if (saveGameInfoData == null || saveGameInfoData.isEmpty()) {
            errors.add(context.getString(R.string.health_info_empty))
        } else {
            AppLogger.d(
                TAG,
                "Validating SaveGameInfo: size=${saveGameInfoData.size}, isGzip=${GzipUtil.isGzip(saveGameInfoData)}"
            )
            val xmlData = GzipUtil.decompressIfGzip(saveGameInfoData)
            AppLogger.d(TAG, "SaveGameInfo after decompression: size=${xmlData.size}")
            val tail = String(xmlData.takeLast(100).toByteArray())
            AppLogger.d(TAG, "SaveGameInfo tail (last 100 chars): '$tail'")
            if (!tail.contains("</Farmer>")) {
                errors.add(context.getString(R.string.health_info_tag_missing))
            }
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    /**
     * Deep structural validation of save data.
     * Runs shallow validation first, then streams XML to verify required elements.
     */
    fun deepValidateSaveData(mainSaveData: ByteArray?, saveGameInfoData: ByteArray?): ValidationResult {
        // Run shallow validation first
        val shallow = validateSaveData(mainSaveData, saveGameInfoData)
        if (!shallow.valid) return shallow

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Validate main save structure
        if (mainSaveData != null) {
            val xmlData = GzipUtil.decompressIfGzip(mainSaveData)

            if (xmlData.size < 100 * 1024) {
                warnings.add(context.getString(R.string.health_main_small_warning, xmlData.size / 1024))
            }

            try {
                val requiredChildren = setOf("player", "locations", "currentSeason", "dayOfMonth", "year")
                val foundChildren = mutableSetOf<String>()

                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(xmlData), "UTF-8")

                var depth = 0
                var insideSaveGame = false

                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            depth++
                            if (depth == 1 && parser.name == "SaveGame") {
                                insideSaveGame = true
                            }
                            if (insideSaveGame && depth == 2) {
                                foundChildren.add(parser.name)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (depth == 1) insideSaveGame = false
                            depth--
                        }
                    }
                    parser.next()
                }

                val missing = requiredChildren - foundChildren
                if (missing.isNotEmpty()) {
                    errors.add(context.getString(R.string.health_required_missing, missing.joinToString(", ")))
                }
            } catch (e: XmlPullParserException) {
                errors.add(context.getString(R.string.health_xml_parse_line, e.lineNumber, e.message ?: context.getString(R.string.error_unknown)))
            } catch (e: Exception) {
                errors.add(context.getString(R.string.health_main_parse_failed, e.message ?: context.getString(R.string.error_unknown)))
            }
        }

        // Validate SaveGameInfo structure
        if (saveGameInfoData != null) {
            try {
                val xmlData = GzipUtil.decompressIfGzip(saveGameInfoData)
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(xmlData), "UTF-8")

                var foundName = false
                var foundFarmName = false
                var season = -1
                var day = -1
                var year = -1
                var currentTag = ""

                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> currentTag = parser.name
                        XmlPullParser.TEXT -> {
                            val text = parser.text?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "name" -> if (!foundName) foundName = true
                                    "farmName" -> foundFarmName = true
                                    "seasonForSaveGame" -> season = text.toIntOrNull() ?: -1
                                    "dayOfMonthForSaveGame" -> day = text.toIntOrNull() ?: -1
                                    "yearForSaveGame" -> year = text.toIntOrNull() ?: -1
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> currentTag = ""
                    }
                    parser.next()
                }

                if (!foundName) errors.add(context.getString(R.string.health_farmer_missing))
                if (!foundFarmName) errors.add(context.getString(R.string.health_farm_missing))
                if (season !in 0..3) errors.add(context.getString(R.string.health_season_invalid, season))
                if (day !in 1..28) errors.add(context.getString(R.string.health_day_invalid, day))
                if (year < 1) errors.add(context.getString(R.string.health_year_invalid, year))
            } catch (e: XmlPullParserException) {
                errors.add(context.getString(R.string.health_info_xml_line, e.lineNumber, e.message ?: context.getString(R.string.error_unknown)))
            } catch (e: Exception) {
                errors.add(context.getString(R.string.health_info_parse_failed, e.message ?: context.getString(R.string.error_unknown)))
            }
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun validateXmlEnding(file: File, expectedTag: String): Boolean = try {
        val rawBytes = file.readBytes()
        val xmlBytes = GzipUtil.decompressIfGzip(rawBytes)
        val tailSize = minOf(xmlBytes.size, 200)
        val tail = String(xmlBytes, xmlBytes.size - tailSize, tailSize)
        val found = tail.contains(expectedTag)
        if (!found) {
            AppLogger.w(TAG, "validateXmlEnding: '$expectedTag' not found in ${file.name} tail: '$tail'")
        }
        found
    } catch (e: Exception) {
        AppLogger.e(TAG, "validateXmlEnding failed for ${file.name}", e)
        false
    }
}
