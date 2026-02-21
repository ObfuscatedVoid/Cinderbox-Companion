package com.sdvsync.saves

import java.io.File

data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

class SaveValidator {

    /**
     * Validate a save folder on the local filesystem.
     */
    fun validateSaveFolder(saveDir: File): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!saveDir.exists() || !saveDir.isDirectory) {
            return ValidationResult(false, listOf("Save directory does not exist"))
        }

        val folderName = saveDir.name
        val mainSaveFile = saveDir.resolve(folderName)
        val saveGameInfo = saveDir.resolve("SaveGameInfo")

        // Check main save file
        if (!mainSaveFile.exists()) {
            errors.add("Main save file missing: $folderName")
        } else if (mainSaveFile.length() < 1024) {
            errors.add("Main save file too small (${mainSaveFile.length()} bytes)")
        } else if (mainSaveFile.length() > 200 * 1024 * 1024) {
            warnings.add("Main save file unusually large (${mainSaveFile.length() / 1024 / 1024}MB)")
        }

        // Check SaveGameInfo
        if (!saveGameInfo.exists()) {
            errors.add("SaveGameInfo file missing")
        } else if (saveGameInfo.length() < 100) {
            errors.add("SaveGameInfo too small (${saveGameInfo.length()} bytes)")
        }

        // Check for in-progress saves
        val tempFiles = saveDir.listFiles()?.filter {
            it.name.contains("_STARDEWVALLEYSAVETMP")
        } ?: emptyList()
        if (tempFiles.isNotEmpty()) {
            errors.add("Save appears to be in progress (temp files found)")
        }

        // Validate XML structure of main save
        if (mainSaveFile.exists() && mainSaveFile.length() > 0) {
            if (!validateXmlEnding(mainSaveFile, "</SaveGame>")) {
                errors.add("Main save file has invalid XML (missing closing tag)")
            }
        }

        // Validate XML structure of SaveGameInfo
        if (saveGameInfo.exists() && saveGameInfo.length() > 0) {
            if (!validateXmlEnding(saveGameInfo, "</Farmer>")) {
                errors.add("SaveGameInfo has invalid XML (missing closing tag)")
            }
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Validate downloaded save data (in-memory).
     */
    fun validateSaveData(
        mainSaveData: ByteArray?,
        saveGameInfoData: ByteArray?,
    ): ValidationResult {
        val errors = mutableListOf<String>()

        if (mainSaveData == null || mainSaveData.isEmpty()) {
            errors.add("Main save data is empty")
        } else {
            if (mainSaveData.size < 1024) {
                errors.add("Main save data too small (${mainSaveData.size} bytes)")
            }
            val tail = String(mainSaveData.takeLast(100).toByteArray())
            if (!tail.contains("</SaveGame>")) {
                errors.add("Main save data missing closing </SaveGame> tag")
            }
        }

        if (saveGameInfoData == null || saveGameInfoData.isEmpty()) {
            errors.add("SaveGameInfo data is empty")
        } else {
            val tail = String(saveGameInfoData.takeLast(100).toByteArray())
            if (!tail.contains("</Farmer>")) {
                errors.add("SaveGameInfo missing closing </Farmer> tag")
            }
        }

        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    private fun validateXmlEnding(file: File, expectedTag: String): Boolean {
        return try {
            val tailSize = minOf(file.length(), 200L).toInt()
            val tail = ByteArray(tailSize)
            file.inputStream().use { stream ->
                stream.skip(file.length() - tailSize)
                stream.read(tail)
            }
            String(tail).contains(expectedTag)
        } catch (e: Exception) {
            false
        }
    }
}
