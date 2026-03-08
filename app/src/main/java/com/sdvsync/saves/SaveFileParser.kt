package com.sdvsync.saves

import android.util.Xml
import com.sdvsync.logging.AppLogger
import com.sdvsync.util.GzipUtil
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser

class SaveFileParser {

    companion object {
        private const val TAG = "SaveFileParser"
        private val EXPERIENCE_FIELDS = listOf(
            "farmingExperience",
            "miningExperience",
            "combatExperience",
            "foragingExperience",
            "fishingExperience"
        )
    }

    fun parse(rawData: ByteArray): SaveFileData? = try {
        val xmlData = GzipUtil.decompressIfGzip(rawData)
        AppLogger.d(TAG, "Parsing save data: ${xmlData.size} bytes")

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlData), "UTF-8")

        var farmer = FarmerStats("", "", "", 0, 0, 0, 0, 0, 0, 0)
        val relationships = mutableListOf<RelationshipEntry>()
        val animals = mutableListOf<AnimalEntry>()
        val craftingRecipes = mutableListOf<CraftingRecipeEntry>()
        val cookingRecipes = mutableListOf<CraftingRecipeEntry>()
        var completedBundles = 0
        var totalBundles = 0
        var isCommunityCenter = true
        var museumDonated = 0

        // Tag stack for context tracking
        val tagStack = mutableListOf<String>()
        var currentTag = ""

        // Farmer fields
        var name = ""
        var farmName = ""
        var favoriteThing = ""
        var money = 0
        var totalEarned = 0
        var farmingLevel = 0
        var miningLevel = 0
        var combatLevel = 0
        var foragingLevel = 0
        var fishingLevel = 0
        val experiencePoints = mutableListOf<Int>()

        // Relationship parsing state
        var inFriendshipData = false
        var currentNpcKey = ""
        var currentFriendshipPoints = 0
        var currentRelStatus = ""

        // Animal parsing state
        var inFarmAnimal = false
        var animalName = ""
        var animalType = ""
        var animalBuilding = ""
        var animalHappiness = 0
        var animalFriendship = 0

        // Recipe parsing state
        var inCraftingRecipes = false
        var inCookingRecipes = false
        var recipeKey = ""
        var recipeValue = 0

        // Bundle parsing state
        var inBundleData = false

        // Museum parsing
        var inArchaeologyFound = false
        var inMineralsFound = false

        // Player depth tracking
        var playerDepth = -1

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tagStack.add(parser.name)
                    currentTag = parser.name

                    // Track player section (depth-1 child of SaveGame)
                    if (tagStack.size == 2 && parser.name == "player") {
                        playerDepth = tagStack.size
                    }

                    // Friendship data section
                    if (playerDepth > 0 && parser.name == "friendshipData" && tagStack.size == playerDepth + 1) {
                        inFriendshipData = true
                    }

                    // FarmAnimal elements (anywhere in locations)
                    if (parser.name == "FarmAnimal") {
                        inFarmAnimal = true
                        animalName = ""
                        animalType = ""
                        animalBuilding = ""
                        animalHappiness = 0
                        animalFriendship = 0
                    }

                    // Crafting/cooking recipes in player
                    if (playerDepth > 0 && tagStack.size == playerDepth + 1) {
                        when (parser.name) {
                            "craftingRecipes" -> inCraftingRecipes = true
                            "cookingRecipes" -> inCookingRecipes = true
                            "archaeologyFound" -> inArchaeologyFound = true
                            "mineralsFound" -> inMineralsFound = true
                        }
                    }

                    // Bundle data
                    if (tagStack.size == 2 && parser.name == "bundleData") {
                        inBundleData = true
                    }

                    // Track "key" in item entries for friendship
                    if (inFriendshipData && parser.name == "key") {
                        // Next text in <string> will be the NPC name
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isEmpty()) {
                        parser.next()
                        continue
                    }

                    // Player-level fields
                    if (playerDepth > 0 && tagStack.size >= playerDepth) {
                        val parentTag = tagStack.getOrNull(tagStack.size - 2)

                        when {
                            // Direct player fields
                            tagStack.size == playerDepth + 1 -> when (currentTag) {
                                "name" -> if (name.isEmpty()) name = text
                                "farmName" -> farmName = text
                                "favoriteThing" -> favoriteThing = text
                                "money" -> money = text.toIntOrNull() ?: 0
                                "totalMoneyEarned" -> totalEarned = text.toIntOrNull() ?: 0
                                "farmingLevel" -> farmingLevel = text.toIntOrNull() ?: 0
                                "miningLevel" -> miningLevel = text.toIntOrNull() ?: 0
                                "combatLevel" -> combatLevel = text.toIntOrNull() ?: 0
                                "foragingLevel" -> foragingLevel = text.toIntOrNull() ?: 0
                                "fishingLevel" -> fishingLevel = text.toIntOrNull() ?: 0
                            }

                            // Experience points array (player > experiencePoints > int)
                            parentTag == "experiencePoints" && currentTag == "int" -> {
                                text.toIntOrNull()?.let { experiencePoints.add(it) }
                            }

                            // Friendship key/value parsing
                            inFriendshipData -> {
                                if (currentTag == "string" && parentTag == "key") {
                                    currentNpcKey = text
                                }
                                if (parentTag == "value" || tagStack.any { it == "value" }) {
                                    when (currentTag) {
                                        "Points" -> currentFriendshipPoints = text.toIntOrNull() ?: 0
                                        "Status" -> currentRelStatus = text
                                    }
                                }
                            }

                            // Crafting recipes (key=name, value=timesCrafted)
                            (inCraftingRecipes || inCookingRecipes) -> {
                                if (currentTag == "string" && parentTag == "key") {
                                    recipeKey = text
                                }
                                if (currentTag == "int" && parentTag == "value") {
                                    recipeValue = text.toIntOrNull() ?: 0
                                }
                            }

                            // Museum items
                            (inArchaeologyFound || inMineralsFound) -> {
                                // Count unique items by tracking key entries
                            }
                        }
                    }

                    // FarmAnimal fields
                    if (inFarmAnimal) {
                        when (currentTag) {
                            "name" -> if (animalName.isEmpty()) animalName = text
                            "type" -> if (animalType.isEmpty()) animalType = text
                            "buildingTypeILiveIn" -> animalBuilding = text
                            "happiness" -> animalHappiness = text.toIntOrNull() ?: 0
                            "friendshipTowardFarmer" -> animalFriendship = text.toIntOrNull() ?: 0
                        }
                    }

                    // Bundle data counting
                    if (inBundleData && currentTag == "boolean") {
                        totalBundles++
                        if (text == "true") completedBundles++
                    }
                }

                XmlPullParser.END_TAG -> {
                    // Complete relationship entry
                    if (inFriendshipData &&
                        parser.name == "item" &&
                        currentNpcKey.isNotEmpty()
                    ) {
                        relationships.add(
                            RelationshipEntry(currentNpcKey, currentFriendshipPoints, currentRelStatus)
                        )
                        currentNpcKey = ""
                        currentFriendshipPoints = 0
                        currentRelStatus = ""
                    }

                    // Complete FarmAnimal
                    if (inFarmAnimal && parser.name == "FarmAnimal") {
                        if (animalName.isNotEmpty()) {
                            animals.add(
                                AnimalEntry(animalName, animalType, animalBuilding, animalHappiness, animalFriendship)
                            )
                        }
                        inFarmAnimal = false
                    }

                    // Complete recipe entries
                    if ((inCraftingRecipes || inCookingRecipes) && parser.name == "item" && recipeKey.isNotEmpty()) {
                        val entry = CraftingRecipeEntry(recipeKey, recipeValue)
                        if (inCraftingRecipes) craftingRecipes.add(entry) else cookingRecipes.add(entry)
                        recipeKey = ""
                        recipeValue = 0
                    }

                    // Museum item counting (each <item> in archaeologyFound/mineralsFound)
                    if ((inArchaeologyFound || inMineralsFound) && parser.name == "item") {
                        museumDonated++
                    }

                    // Exit sections
                    if (inFriendshipData && parser.name == "friendshipData") inFriendshipData = false
                    if (inCraftingRecipes && parser.name == "craftingRecipes") inCraftingRecipes = false
                    if (inCookingRecipes && parser.name == "cookingRecipes") inCookingRecipes = false
                    if (inArchaeologyFound && parser.name == "archaeologyFound") inArchaeologyFound = false
                    if (inMineralsFound && parser.name == "mineralsFound") inMineralsFound = false
                    if (inBundleData && parser.name == "bundleData") inBundleData = false

                    if (playerDepth > 0 && tagStack.size == playerDepth && parser.name == "player") {
                        playerDepth = -1
                    }

                    if (tagStack.isNotEmpty()) tagStack.removeAt(tagStack.size - 1)
                    currentTag = tagStack.lastOrNull() ?: ""
                }
            }
            parser.next()
        }

        farmer = FarmerStats(
            name = name,
            farmName = farmName,
            favoriteThing = favoriteThing,
            money = money,
            totalMoneyEarned = totalEarned,
            farmingLevel = farmingLevel,
            miningLevel = miningLevel,
            combatLevel = combatLevel,
            foragingLevel = foragingLevel,
            fishingLevel = fishingLevel,
            farmingExperience = experiencePoints.getOrElse(0) { 0 },
            fishingExperience = experiencePoints.getOrElse(1) { 0 },
            foragingExperience = experiencePoints.getOrElse(2) { 0 },
            miningExperience = experiencePoints.getOrElse(3) { 0 },
            combatExperience = experiencePoints.getOrElse(4) { 0 }
        )

        SaveFileData(
            farmer = farmer,
            relationships = relationships.sortedByDescending { it.friendshipPoints },
            animals = animals,
            bundles = BundleProgress(completedBundles, totalBundles, isCommunityCenter),
            museumDonated = museumDonated,
            craftingRecipes = craftingRecipes.sortedBy { it.name },
            cookingRecipes = cookingRecipes.sortedBy { it.name }
        )
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse save file", e)
        null
    }
}
