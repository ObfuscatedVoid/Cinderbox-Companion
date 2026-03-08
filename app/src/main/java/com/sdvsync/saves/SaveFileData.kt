package com.sdvsync.saves

data class SaveFileData(
    val farmer: FarmerStats,
    val relationships: List<RelationshipEntry>,
    val animals: List<AnimalEntry>,
    val bundles: BundleProgress,
    val museumDonated: Int,
    val craftingRecipes: List<CraftingRecipeEntry>,
    val cookingRecipes: List<CraftingRecipeEntry>
)

data class FarmerStats(
    val name: String,
    val farmName: String,
    val favoriteThing: String,
    val money: Int,
    val totalMoneyEarned: Int,
    val farmingLevel: Int,
    val miningLevel: Int,
    val combatLevel: Int,
    val foragingLevel: Int,
    val fishingLevel: Int,
    val farmingExperience: Int = 0,
    val miningExperience: Int = 0,
    val combatExperience: Int = 0,
    val foragingExperience: Int = 0,
    val fishingExperience: Int = 0
)

data class RelationshipEntry(val npcName: String, val friendshipPoints: Int, val status: String) {
    val hearts: Int get() = friendshipPoints / 250
}

data class AnimalEntry(
    val name: String,
    val type: String,
    val building: String,
    val happiness: Int,
    val friendshipPoints: Int
)

data class BundleProgress(val completedBundles: Int, val totalBundles: Int, val isCommunityCenter: Boolean)

data class CraftingRecipeEntry(val name: String, val timesCrafted: Int)
