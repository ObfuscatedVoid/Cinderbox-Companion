package com.sdvsync.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdvsync.R
import com.sdvsync.mods.models.ModProfile
import com.sdvsync.ui.components.AppButton
import com.sdvsync.ui.components.AppCard
import com.sdvsync.ui.components.AppDialog
import com.sdvsync.ui.components.AppIconButton
import com.sdvsync.ui.components.AppIcons
import com.sdvsync.ui.components.AppOutlinedButton
import com.sdvsync.ui.components.AppTopBar
import com.sdvsync.ui.components.ButtonVariant
import com.sdvsync.ui.components.EmptyState
import com.sdvsync.ui.components.LoadingIndicator
import com.sdvsync.ui.components.ModCard
import com.sdvsync.ui.components.UpdateBanner
import com.sdvsync.ui.viewmodels.ModFilter
import com.sdvsync.ui.viewmodels.ModManagerViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModManagerScreen(
    onBrowseClick: () -> Unit = {},
    onModClick: (String) -> Unit = {},
    viewModel: ModManagerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // File picker for zip import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromUri(uri)
        }
    }

    // Show import result toast
    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearImportMessage()
        }
    }

    // Show profile message toast
    LaunchedEffect(state.profileMessage) {
        state.profileMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearProfileMessage()
        }
    }

    // Profile dialogs state
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var showApplyProfileConfirm by remember { mutableStateOf<ModProfile?>(null) }
    var showDeleteProfileConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.mods_title),
                actions = {
                    AppIconButton(
                        imageVector = AppIcons.Refresh,
                        onClick = { viewModel.checkForUpdates() },
                        contentDescription = stringResource(R.string.mods_check_update)
                    )
                    AppIconButton(
                        imageVector = AppIcons.Search,
                        onClick = onBrowseClick,
                        contentDescription = stringResource(R.string.mods_browse_title)
                    )
                    AppIconButton(
                        imageVector = AppIcons.Folder,
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        contentDescription = stringResource(R.string.mods_import)
                    )
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.loadInstalledMods(isUserRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading && state.installedMods.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        LoadingIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                state.error != null && state.installedMods.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            AppButton(onClick = { viewModel.loadInstalledMods() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }

                state.installedMods.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            EmptyState(
                                title = stringResource(R.string.mods_empty_title),
                                subtitle = stringResource(R.string.mods_empty_subtitle)
                            )
                            Spacer(Modifier.height(24.dp))
                            AppButton(
                                onClick = onBrowseClick,
                                variant = ButtonVariant.Gold
                            ) {
                                Text(stringResource(R.string.mods_browse_button))
                            }
                            Spacer(Modifier.height(12.dp))
                            AppOutlinedButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                                }
                            ) {
                                Text(stringResource(R.string.mods_import_button))
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.error?.let { error ->
                            item(key = "error") {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        error,
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        // Profiles section
                        if (state.profiles.isNotEmpty() || state.installedMods.isNotEmpty()) {
                            item {
                                ProfileSection(
                                    profiles = state.profiles,
                                    activeProfileName = state.activeProfileName,
                                    isApplying = state.isApplyingProfile,
                                    onSaveCurrent = { showSaveProfileDialog = true },
                                    onApply = { showApplyProfileConfirm = it },
                                    onDelete = { showDeleteProfileConfirm = it }
                                )
                            }
                        }

                        // Search bar
                        item {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.mods_search_installed_hint)) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Filter chips
                        item {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val filterChipColors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                FilterChip(
                                    selected = state.filter == ModFilter.ALL,
                                    onClick = { viewModel.setFilter(ModFilter.ALL) },
                                    label = { Text(stringResource(R.string.mods_filter_all)) },
                                    colors = filterChipColors
                                )
                                FilterChip(
                                    selected = state.filter == ModFilter.ENABLED,
                                    onClick = { viewModel.setFilter(ModFilter.ENABLED) },
                                    label = { Text(stringResource(R.string.mods_filter_enabled)) },
                                    colors = filterChipColors
                                )
                                FilterChip(
                                    selected = state.filter == ModFilter.DISABLED,
                                    onClick = { viewModel.setFilter(ModFilter.DISABLED) },
                                    label = { Text(stringResource(R.string.mods_filter_disabled)) },
                                    colors = filterChipColors
                                )
                                if (state.updates.isNotEmpty()) {
                                    FilterChip(
                                        selected = state.filter == ModFilter.HAS_UPDATE,
                                        onClick = { viewModel.setFilter(ModFilter.HAS_UPDATE) },
                                        label = { Text(stringResource(R.string.mods_filter_has_update)) },
                                        colors = filterChipColors
                                    )
                                }
                            }
                        }

                        // Update checking indicator
                        if (state.isCheckingUpdates) {
                            item {
                                AppCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            stringResource(R.string.mods_checking_updates),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Update banner
                        val updateCount = state.updates.size
                        if (updateCount > 0 && state.filter != ModFilter.HAS_UPDATE) {
                            item {
                                UpdateBanner(
                                    updateCount = updateCount,
                                    onClick = { viewModel.setFilter(ModFilter.HAS_UPDATE) }
                                )
                            }
                        }

                        if (state.displayedMods.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.mods_no_matching),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        itemsIndexed(state.displayedMods, key = { _, mod -> mod.manifest.uniqueID }) { index, mod ->
                            ModCard(
                                mod = mod,
                                hasUpdate = state.updates.containsKey(mod.manifest.uniqueID),
                                onToggle = { enabled ->
                                    viewModel.toggleMod(
                                        mod.folderName,
                                        enabled
                                    )
                                },
                                onClick = { onModClick(mod.manifest.uniqueID) }
                            )
                        }

                        // Browse mods button at bottom
                        item {
                            Spacer(Modifier.height(8.dp))
                            AppButton(
                                onClick = onBrowseClick,
                                variant = ButtonVariant.Gold,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.mods_browse_button))
                            }
                        }
                    }
                }
            }
        }
    }

    // Save profile dialog
    if (showSaveProfileDialog) {
        SaveProfileDialog(
            onDismiss = { showSaveProfileDialog = false },
            onSave = { name ->
                showSaveProfileDialog = false
                viewModel.saveCurrentAsProfile(name)
            }
        )
    }

    // Apply profile confirmation
    showApplyProfileConfirm?.let { profile ->
        AppDialog(
            onDismissRequest = { showApplyProfileConfirm = null },
            title = stringResource(R.string.profiles_apply_confirm_title),
            text = stringResource(R.string.profiles_apply_confirm_message, profile.name),
            confirmButton = {
                AppButton(onClick = {
                    showApplyProfileConfirm = null
                    viewModel.applyProfile(profile)
                }) {
                    Text(stringResource(R.string.sync_confirm_continue))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { showApplyProfileConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Delete profile confirmation
    showDeleteProfileConfirm?.let { name ->
        AppDialog(
            onDismissRequest = { showDeleteProfileConfirm = null },
            title = stringResource(R.string.profiles_delete_title),
            text = stringResource(R.string.profiles_delete_message, name),
            confirmButton = {
                AppButton(
                    onClick = {
                        showDeleteProfileConfirm = null
                        viewModel.deleteProfile(name)
                    },
                    variant = ButtonVariant.Danger
                ) {
                    Text(stringResource(R.string.profiles_delete_button))
                }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { showDeleteProfileConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(
    profiles: List<ModProfile>,
    activeProfileName: String?,
    isApplying: Boolean,
    onSaveCurrent: () -> Unit,
    onApply: (ModProfile) -> Unit,
    onDelete: (String) -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profiles_title),
                    style = MaterialTheme.typography.titleSmall
                )
                AppOutlinedButton(onClick = onSaveCurrent) {
                    Text(stringResource(R.string.profiles_save_current))
                }
            }

            if (activeProfileName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.profiles_active, activeProfileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (profiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.name == activeProfileName,
                            onClick = {
                                if (profile.name != activeProfileName) {
                                    onApply(profile)
                                }
                            },
                            label = { Text(profile.name) },
                            enabled = !isApplying,
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (profile.name != activeProfileName) {
                                        onApply(profile)
                                    }
                                },
                                onLongClick = { onDelete(profile.name) }
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_save_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.profiles_save_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.profiles_save_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.profiles_save_button))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
    )
}
