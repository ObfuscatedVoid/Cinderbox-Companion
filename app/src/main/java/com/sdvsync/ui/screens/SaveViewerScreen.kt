package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdvsync.R
import com.sdvsync.saves.AnimalEntry
import com.sdvsync.saves.CraftingRecipeEntry
import com.sdvsync.saves.FarmerStats
import com.sdvsync.saves.RelationshipEntry
import com.sdvsync.saves.SaveFileData
import com.sdvsync.ui.animation.StaggeredAnimatedItem
import com.sdvsync.ui.components.ArrowLeftData
import com.sdvsync.ui.components.PixelIconButton
import com.sdvsync.ui.components.PixelLoadingSpinner
import com.sdvsync.ui.components.PixelProgressBar
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.theme.GoldAmber
import com.sdvsync.ui.viewmodels.SaveViewerTab
import com.sdvsync.ui.viewmodels.SaveViewerViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SaveViewerScreen(saveFolderName: String, onBack: () -> Unit, viewModel: SaveViewerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(saveFolderName) {
        viewModel.loadSave(saveFolderName)
    }

    Scaffold(
        topBar = {
            StardewTopAppBar(
                title = state.data?.farmer?.name ?: stringResource(R.string.save_viewer_title),
                navigationIcon = {
                    PixelIconButton(
                        pixelData = ArrowLeftData,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    PixelLoadingSpinner()
                }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            state.data != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = state.selectedTab.ordinal,
                        edgePadding = 16.dp
                    ) {
                        SaveViewerTab.entries.forEach { tab ->
                            Tab(
                                selected = state.selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                text = { Text(tabTitle(tab)) }
                            )
                        }
                    }

                    when (state.selectedTab) {
                        SaveViewerTab.FARMER -> FarmerTab(state.data!!.farmer)
                        SaveViewerTab.RELATIONSHIPS -> RelationshipsTab(state.data!!.relationships)
                        SaveViewerTab.ANIMALS -> AnimalsTab(state.data!!.animals)
                        SaveViewerTab.BUNDLES -> BundlesTab(state.data!!)
                        SaveViewerTab.MUSEUM -> MuseumTab(state.data!!.museumDonated)
                        SaveViewerTab.CRAFTING -> CraftingTab(state.data!!.craftingRecipes, state.data!!.cookingRecipes)
                    }
                }
            }
        }
    }
}

@Composable
private fun tabTitle(tab: SaveViewerTab): String = when (tab) {
    SaveViewerTab.FARMER -> stringResource(R.string.save_viewer_tab_farmer)
    SaveViewerTab.RELATIONSHIPS -> stringResource(R.string.save_viewer_tab_relationships)
    SaveViewerTab.ANIMALS -> stringResource(R.string.save_viewer_tab_animals)
    SaveViewerTab.BUNDLES -> stringResource(R.string.save_viewer_tab_bundles)
    SaveViewerTab.MUSEUM -> stringResource(R.string.save_viewer_tab_museum)
    SaveViewerTab.CRAFTING -> stringResource(R.string.save_viewer_tab_crafting)
}

@Composable
private fun FarmerTab(farmer: FarmerStats) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StardewCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(farmer.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.save_farm_name, farmer.farmName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (farmer.favoriteThing.isNotEmpty()) {
                        Text(
                            stringResource(R.string.save_viewer_favorite_thing, farmer.favoriteThing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            StardewCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.save_viewer_money),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "%,dg".format(farmer.money),
                        style = MaterialTheme.typography.headlineSmall,
                        color = GoldAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.save_viewer_total_earned, "%,d".format(farmer.totalMoneyEarned)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            StardewCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.save_viewer_skills),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(12.dp))
                    SkillRow(stringResource(R.string.save_viewer_skill_farming), farmer.farmingLevel)
                    SkillRow(stringResource(R.string.save_viewer_skill_mining), farmer.miningLevel)
                    SkillRow(stringResource(R.string.save_viewer_skill_combat), farmer.combatLevel)
                    SkillRow(stringResource(R.string.save_viewer_skill_foraging), farmer.foragingLevel)
                    SkillRow(stringResource(R.string.save_viewer_skill_fishing), farmer.fishingLevel)
                }
            }
        }
    }
}

@Composable
private fun SkillRow(name: String, level: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "$level",
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        PixelProgressBar(
            progress = level / 10f,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RelationshipsTab(relationships: List<RelationshipEntry>) {
    if (relationships.isEmpty()) {
        EmptyTabContent(stringResource(R.string.save_viewer_no_relationships))
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(relationships, key = { _, r -> r.npcName }) { index, rel ->
            StaggeredAnimatedItem(index = index) {
                StardewCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rel.npcName, style = MaterialTheme.typography.titleSmall)
                            if (rel.status.isNotEmpty()) {
                                Text(
                                    rel.status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            buildHeartsString(rel.hearts),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun buildHeartsString(hearts: Int): String {
    val maxHearts = 10
    val filled = hearts.coerceIn(0, maxHearts)
    val empty = maxHearts - filled
    return "\u2764".repeat(filled) + "\u2661".repeat(empty)
}

@Composable
private fun AnimalsTab(animals: List<AnimalEntry>) {
    if (animals.isEmpty()) {
        EmptyTabContent(stringResource(R.string.save_viewer_no_animals))
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(animals, key = { i, a -> "${a.name}_$i" }) { index, animal ->
            StaggeredAnimatedItem(index = index) {
                StardewCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(animal.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                animal.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (animal.building.isNotEmpty()) {
                            Text(
                                animal.building,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BundlesTab(data: SaveFileData) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StardewCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.save_viewer_bundles_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(12.dp))
                    if (data.bundles.totalBundles > 0) {
                        Text(
                            stringResource(
                                R.string.save_viewer_bundles_progress,
                                data.bundles.completedBundles,
                                data.bundles.totalBundles
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        PixelProgressBar(
                            progress = data.bundles.completedBundles.toFloat() / data.bundles.totalBundles
                        )
                    } else {
                        Text(
                            stringResource(R.string.save_viewer_bundles_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MuseumTab(donatedItems: Int) {
    val totalMuseumItems = 102

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StardewCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.save_viewer_museum_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.save_viewer_museum_progress, donatedItems, totalMuseumItems),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    PixelProgressBar(
                        progress = donatedItems.toFloat() / totalMuseumItems
                    )
                }
            }
        }
    }
}

@Composable
private fun CraftingTab(craftingRecipes: List<CraftingRecipeEntry>, cookingRecipes: List<CraftingRecipeEntry>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (craftingRecipes.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.save_viewer_crafting_recipes, craftingRecipes.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(craftingRecipes, key = { "craft_${it.name}" }) { recipe ->
                RecipeRow(recipe)
            }
        }

        if (cookingRecipes.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.save_viewer_cooking_recipes, cookingRecipes.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(cookingRecipes, key = { "cook_${it.name}" }) { recipe ->
                RecipeRow(recipe)
            }
        }

        if (craftingRecipes.isEmpty() && cookingRecipes.isEmpty()) {
            item {
                EmptyTabContent(stringResource(R.string.save_viewer_no_recipes))
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: CraftingRecipeEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            recipe.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.save_viewer_times_crafted, recipe.timesCrafted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyTabContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
