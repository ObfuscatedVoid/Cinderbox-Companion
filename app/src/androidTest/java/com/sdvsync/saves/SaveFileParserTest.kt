package com.sdvsync.saves

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveFileParserTest {
    @Test
    fun bundlesUseDonationCountsAndRequiredItemCountsRegardlessOfXmlOrder() {
        val definitions = """
            <bundleData>
              <item><key><string>Pantry/0</string></key><value><string>Spring//24 1 0 188 1 0/0///Spring</string></value></item>
              <item><key><string>Pantry/1</string></key><value><string>Choice//24 1 0 188 1 0 190 1 0/0/2//Choice</string></value></item>
              <item><key><string>Vault/2</string></key><value><string>Gold//-1 2500 2500/0///Gold</string></value></item>
            </bundleData>
        """.trimIndent()
        val donations = """
            <locations><GameLocation><bundles>
              <item><key><int>0</int></key><value><ArrayOfBoolean><boolean>true</boolean><boolean>false</boolean></ArrayOfBoolean></value></item>
              <item><key><int>1</int></key><value><ArrayOfBoolean><boolean>true</boolean><boolean>true</boolean><boolean>false</boolean></ArrayOfBoolean></value></item>
              <item><key><int>2</int></key><value><ArrayOfBoolean><boolean>true</boolean><boolean>false</boolean><boolean>false</boolean></ArrayOfBoolean></value></item>
            </bundles></GameLocation></locations>
        """.trimIndent()
        for (body in listOf(definitions + donations, donations + definitions)) {
            val parsed = requireNotNull(SaveFileParser().parse("<SaveGame>$body</SaveGame>".toByteArray()))
            assertEquals(3, parsed.bundles.totalBundles)
            assertEquals(2, parsed.bundles.completedBundles)
        }
    }

    @Test
    fun museumCountsDonationsInsteadOfDiscoveredItems() {
        val xml = """
            <SaveGame>
              <player><name>Test</name><archaeologyFound><item/><item/></archaeologyFound><mineralsFound><item/></mineralsFound></player>
              <locations><GameLocation><museumPieces><item><key><Vector2><X>1</X><Y>2</Y></Vector2></key><value><string>555</string></value></item></museumPieces></GameLocation></locations>
            </SaveGame>
        """.trimIndent()
        val parsed = requireNotNull(SaveFileParser().parse(xml.toByteArray()))
        assertEquals(1, parsed.museumDonated)
        assertEquals("Test", parsed.farmer.name)
    }
}
