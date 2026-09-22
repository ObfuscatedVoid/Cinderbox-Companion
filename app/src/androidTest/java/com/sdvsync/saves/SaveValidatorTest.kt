package com.sdvsync.saves

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sdvsync.util.GzipUtil
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveValidatorTest {
    private val info = "<Farmer><name>Test</name></Farmer>".toByteArray()
    private val main = "<SaveGame><player/>${" ".repeat(1100)}</SaveGame>".toByteArray()

    @Test
    fun completePlainAndCompressedDocumentsValidate() {
        val validator = SaveValidator()
        assertTrue(validator.validateSaveData(main, info).valid)
        assertTrue(validator.validateSaveData(GzipUtil.compress(main), GzipUtil.compress(info)).valid)
    }

    @Test
    fun closingTagsCannotHideMalformedDocumentsOrWrongRoots() {
        val validator = SaveValidator()
        val malformed = "<SaveGame><player>${" ".repeat(1100)}</SaveGame>".toByteArray()
        assertFalse(validator.validateSaveData(malformed, info).valid)
        assertFalse(validator.validateSaveData(main, "<Wrong><!-- </Farmer> --></Wrong>".toByteArray()).valid)
        assertFalse(validator.validateSaveData(main, byteArrayOf(0x1f, 0x8b.toByte(), 1)).valid)
    }

    @Test
    fun externalEntitiesAndDocumentDeclarationsAreRejected() {
        val external = "<!DOCTYPE Farmer [<!ENTITY x SYSTEM 'file:///does-not-exist'>]><Farmer>&x;</Farmer>"
        assertFalse(SaveValidator().validateSaveData(main, external.toByteArray()).valid)
    }
}
