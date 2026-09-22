package com.sdvsync.autosync

import org.junit.Assert.*
import org.junit.Test

class SaveChangesTest {
    @Test
    fun uploadsOnlyChangedAndNewSavesRegardlessOfDaysPlayed() {
        val oldest = saveFingerprint(mapOf("main" to "year ten".toByteArray()))
        val before = mapOf("OldFarmer_1" to oldest, "NewFarmer_2" to saveFingerprint(mapOf("main" to byteArrayOf(1))))
        val after =
            before + mapOf("NewFarmer_2" to saveFingerprint(mapOf("main" to byteArrayOf(2))), "NewFarm_3" to oldest)
        assertEquals(setOf("NewFarmer_2", "NewFarm_3"), changedSaveNames(before, after))
        assertTrue(changedSaveNames(before, before).isEmpty())
    }

    @Test
    fun failedBaselineNeverGuessesWhichSaveToUpload() {
        val after = mapOf("Farm_1" to saveFingerprint(mapOf("main" to byteArrayOf(1))))
        assertTrue(changedSaveNames(null, after).isEmpty())
        assertTrue(changedSaveNames(after, emptyMap()).isEmpty())
    }

    @Test
    fun fileOrderDoesNotMatterButAddedOrRemovedFilesDo() {
        val a = saveFingerprint(mapOf("main" to byteArrayOf(1), "info" to byteArrayOf(2)))
        val b = saveFingerprint(mapOf("info" to byteArrayOf(2), "main" to byteArrayOf(1)))
        assertEquals(a, b)
        assertNotEquals(a, saveFingerprint(mapOf("main" to byteArrayOf(1))))
    }
}
