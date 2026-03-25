package com.example.raindroplauncher.ui.launcher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.raindroplauncher.data.UserPreferences
import com.example.raindroplauncher.data.UserPreferencesRepository
import com.example.raindroplauncher.ui.settings.SettingsScreen
import com.example.raindroplauncher.ui.settings.SettingsViewModel
import com.example.raindroplauncher.ui.settings.SettingsViewModelFactory
import kotlinx.coroutines.launch

private const val APPWIDGET_HOST_ID = 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLauncherScreen(
    userPreferences: UserPreferences,
    viewModel: LauncherViewModel = viewModel()
) {
    var showAppDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember { AppWidgetHost(context, APPWIDGET_HOST_ID) }

    DisposableEffect(Unit) {
        appWidgetHost.startListening()
        onDispose {
            appWidgetHost.stopListening()
        }
    }

    val widgetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (appWidgetId != -1) {
                viewModel.addWidget(appWidgetId, 0)
            }
        } else if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
            val data = result.data
            val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
    }

    if (showSettings) {
        val settingsViewModel: SettingsViewModel = viewModel(
            factory = SettingsViewModelFactory(
                context.applicationContext as Application,
                UserPreferencesRepository(context)
            )
        )
        SettingsScreen(
            viewModel = settingsViewModel,
            launcherViewModel = viewModel,
            onBack = { showSettings = false }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = {
                            val appWidgetId = appWidgetHost.allocateAppWidgetId()
                            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            widgetPickerLauncher.launch(pickIntent)
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Widget")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SmallFloatingActionButton(
                        onClick = { showSettings = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LargeFloatingActionButton(
                        onClick = { showAppDrawer = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = "App Drawer")
                    }
                }
            },
            contentWindowInsets = WindowInsets.navigationBars
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                userPreferences.backgroundUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                HomeScreenContent(userPreferences, viewModel, appWidgetHost, appWidgetManager)

                if (showAppDrawer) {
                    ModalBottomSheet(
                        onDismissRequest = { showAppDrawer = false },
                        sheetState = sheetState
                    ) {
                        AppDrawerContent(
                            viewModel = viewModel,
                            onAppClick = { packageName ->
                                viewModel.launchApp(packageName)
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showAppDrawer = false
                                }
                            },
                            onUninstallClick = { packageName ->
                                viewModel.uninstallApp(packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreenContent(
    userPreferences: UserPreferences,
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager
) {
    if (userPreferences.isSinglePageLayout) {
        HomePagerPage(pageNumber = 0, userPreferences = userPreferences, viewModel = viewModel, appWidgetHost = appWidgetHost, appWidgetManager = appWidgetManager)
    } else {
        val pagerState = rememberPagerState(pageCount = { 3 })
        HorizontalPager(state = pagerState) { page ->
            HomePagerPage(pageNumber = page, userPreferences = userPreferences, viewModel = viewModel, appWidgetHost = appWidgetHost, appWidgetManager = appWidgetManager)
        }
    }
}

@Composable
fun HomePagerPage(
    pageNumber: Int,
    userPreferences: UserPreferences,
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager
) {
    val splitScreenPairs by viewModel.splitScreenPairs.collectAsState()
    val widgets by viewModel.widgets.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Good day, ${userPreferences.name}",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            color = if (userPreferences.backgroundUri != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Ready for some ${userPreferences.usageType.lowercase()}?",
            style = MaterialTheme.typography.titleMedium,
            color = if (userPreferences.backgroundUri != null) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Widgets Section
        widgets.filter { it.pageIndex == pageNumber }.forEach { widget ->
            WidgetView(widget.appWidgetId, appWidgetHost, appWidgetManager)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Split Screen Pairs Section
        if (splitScreenPairs.isNotEmpty()) {
            Text(
                "Quick Pairs",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start),
                color = if (userPreferences.backgroundUri != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                splitScreenPairs.forEach { pair ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.launchSplitScreen(pair) },
                        label = { Text(pair.label) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Your Quick Access Categories:",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    userPreferences.importantAppCategories.take(3).forEach { category ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun WidgetView(appWidgetId: Int, host: AppWidgetHost, manager: AppWidgetManager) {
    val context = LocalContext.current
    val appWidgetInfo = remember(appWidgetId) { manager.getAppWidgetInfo(appWidgetId) }

    if (appWidgetInfo != null) {
        AndroidView(
            factory = {
                host.createView(context, appWidgetId, appWidgetInfo).apply {
                    setAppWidget(appWidgetId, appWidgetInfo)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )
    }
}

@Composable
fun AppDrawerContent(
    viewModel: LauncherViewModel,
    onAppClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit
) {
    val searchText by viewModel.searchQuery.collectAsState()
    val apps by viewModel.filteredApps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = MaterialTheme.shapes.medium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps) { app ->
                AppItem(
                    app = app,
                    onClick = { onAppClick(app.packageName) },
                    onLongClick = { onUninstallClick(app.packageName) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        app.icon?.let { icon ->
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
