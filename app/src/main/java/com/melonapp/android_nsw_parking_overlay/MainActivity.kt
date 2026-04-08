package com.melonapp.android_nsw_parking_overlay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.melonapp.android_nsw_parking_overlay.data.DataStoreManager
import com.melonapp.android_nsw_parking_overlay.data.api.RetrofitClient
import com.melonapp.android_nsw_parking_overlay.data.database.AppDatabase
import com.melonapp.android_nsw_parking_overlay.data.database.HistoryBackupManager
import com.melonapp.android_nsw_parking_overlay.data.repository.CarParkRepository
import com.melonapp.android_nsw_parking_overlay.overlay.OverlayService
import com.melonapp.android_nsw_parking_overlay.ui.CarParkUiState
import com.melonapp.android_nsw_parking_overlay.ui.CarParkViewModel
import com.melonapp.android_nsw_parking_overlay.ui.CarParkViewModelFactory
import com.melonapp.android_nsw_parking_overlay.ui.HistoryScreen
import com.melonapp.android_nsw_parking_overlay.ui.theme.AndroidnswparkingoverlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = CarParkRepository(
            apiService = RetrofitClient.apiService,
            historyDao = database.carParkHistoryDao()
        )
        val dataStoreManager = DataStoreManager(applicationContext)
        val historyBackupManager = HistoryBackupManager(
            context = applicationContext,
            historyDao = database.carParkHistoryDao()
        )
        val factory = CarParkViewModelFactory(
            repository = repository,
            dataStoreManager = dataStoreManager,
            historyBackupManager = historyBackupManager
        )

        setContent {
            AndroidnswparkingoverlayTheme {
                val viewModel: CarParkViewModel = viewModel(factory = factory)
                AndroidnswparkingoverlayApp(viewModel)
            }
        }
    }
}

@Composable
fun AndroidnswparkingoverlayApp(viewModel: CarParkViewModel) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val uiState by viewModel.uiState.collectAsState()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = destination.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen(viewModel, uiState)
                    AppDestinations.FAVORITES -> FavoritesScreen(viewModel, uiState)
                    AppDestinations.HISTORY -> HistoryScreen(viewModel, uiState)
                    AppDestinations.PROFILE -> ProfileScreen(viewModel, uiState)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: CarParkViewModel, uiState: CarParkUiState) {
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var apiKeyDirty by rememberSaveable { mutableStateOf(false) }
    var apiKeyHasFocus by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val normalizedSearchQuery = searchQuery.trim()
    val filteredFacilities = remember(uiState.facilities, normalizedSearchQuery) {
        uiState.facilities
            .toList()
            .sortedBy { (_, name) -> name.lowercase() }
            .filter { (id, name) ->
                normalizedSearchQuery.isBlank() ||
                    name.contains(normalizedSearchQuery, ignoreCase = true) ||
                    id.contains(normalizedSearchQuery, ignoreCase = true)
            }
    }

    LaunchedEffect(uiState.apiKey, apiKeyDirty, apiKeyHasFocus) {
        if (!apiKeyDirty && !apiKeyHasFocus) {
            apiKeyInput = if (uiState.apiKey.isBlank()) "" else "********"
        }
    }

    val overlayPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            viewModel.setOverlayPermission(OverlayService.isOverlayPermissionGranted(context))
        }

    LaunchedEffect(Unit) {
        viewModel.setOverlayPermission(OverlayService.isOverlayPermissionGranted(context))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Configuration", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            val noTextToolbar = remember {
                object : TextToolbar {
                    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

                    override fun hide() = Unit

                    override fun showMenu(
                        rect: Rect,
                        onCopyRequested: (() -> Unit)?,
                        onPasteRequested: (() -> Unit)?,
                        onCutRequested: (() -> Unit)?,
                        onSelectAllRequested: (() -> Unit)?
                    ) = Unit
                }
            }

            CompositionLocalProvider(LocalTextToolbar provides noTextToolbar) {
                DisableSelection {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyDirty = true
                            apiKeyInput = it
                        },
                        label = { Text("Transport for NSW API Key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                val nowFocused = state.isFocused
                                if (nowFocused && !apiKeyHasFocus && !apiKeyDirty && apiKeyInput == "********") {
                                    apiKeyInput = ""
                                }
                                apiKeyHasFocus = nowFocused
                            },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                val iconRes = if (apiKeyVisible) {
                                    android.R.drawable.ic_menu_view
                                } else {
                                    android.R.drawable.ic_menu_close_clear_cancel
                                }
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = if (apiKeyVisible) "Hide API Key" else "Show API Key"
                                )
                            }
                        }
                    )
                }
            }

            Button(
                onClick = {
                    val valueToSave = if (!apiKeyDirty && apiKeyInput == "********") {
                        uiState.apiKey
                    } else {
                        apiKeyInput
                    }
                    viewModel.setApiKey(valueToSave)
                    apiKeyDirty = false
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Save API Key")
            }
        }

        item {
            Text("Overlay", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = if (uiState.hasOverlayPermission) {
                    "Permission: granted"
                } else {
                    "Permission: not granted"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        } else {
                            viewModel.setOverlayPermission(true)
                        }
                    },
                    enabled = !uiState.hasOverlayPermission
                ) {
                    Text("Grant permission")
                }

                Button(
                    onClick = { OverlayService.start(context) },
                    enabled = uiState.hasOverlayPermission
                ) {
                    Text("Start overlay")
                }

                OutlinedButton(onClick = { OverlayService.stop(context) }) {
                    Text("Stop")
                }
            }
        }

        item {
            Text("Overlay refresh interval", style = MaterialTheme.typography.titleMedium)
            var intervalSecInput by remember(uiState.overlayRefreshIntervalMs) {
                mutableStateOf((uiState.overlayRefreshIntervalMs / 1000L).toString())
            }
            OutlinedTextField(
                value = intervalSecInput,
                onValueChange = { intervalSecInput = it.filter(Char::isDigit) },
                label = { Text("Seconds (5 - 600)") },
                singleLine = true,
                modifier = Modifier.widthIn(max = 220.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = {
                    val sec = intervalSecInput.toLongOrNull() ?: 30L
                    viewModel.setOverlayRefreshIntervalMs(sec.coerceIn(5L, 600L) * 1000L)
                },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("Save interval")
            }
        }

        item {
            Text("Overlay colors & thresholds", style = MaterialTheme.typography.titleMedium)
            var lowInput by remember(uiState.overlayThresholdLow) {
                mutableStateOf(uiState.overlayThresholdLow.toString())
            }
            var midInput by remember(uiState.overlayThresholdMid) {
                mutableStateOf(uiState.overlayThresholdMid.toString())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lowInput,
                    onValueChange = { lowInput = it.filter(Char::isDigit) },
                    label = { Text("Red <= threshold") },
                    singleLine = true,
                    modifier = Modifier.width(160.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = midInput,
                    onValueChange = { midInput = it.filter(Char::isDigit) },
                    label = { Text("Orange <= threshold") },
                    singleLine = true,
                    modifier = Modifier.width(180.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            var redArgb by remember(uiState.overlayColorRedArgb) {
                mutableStateOf(uiState.overlayColorRedArgb)
            }
            var orangeArgb by remember(uiState.overlayColorOrangeArgb) {
                mutableStateOf(uiState.overlayColorOrangeArgb)
            }
            var greenArgb by remember(uiState.overlayColorGreenArgb) {
                mutableStateOf(uiState.overlayColorGreenArgb)
            }

            ColorPickerRow(label = "Red", argb = redArgb, onArgbChange = { redArgb = it })
            ColorPickerRow(label = "Orange", argb = orangeArgb, onArgbChange = { orangeArgb = it })
            ColorPickerRow(label = "Green", argb = greenArgb, onArgbChange = { greenArgb = it })

            Button(
                onClick = {
                    val low = (lowInput.toIntOrNull() ?: 10).coerceAtLeast(0)
                    val mid = (midInput.toIntOrNull() ?: 30).coerceAtLeast(low)
                    viewModel.setOverlayThresholds(low = low, mid = mid)
                    viewModel.setOverlayColors(
                        redArgb = redArgb,
                        orangeArgb = orangeArgb,
                        greenArgb = greenArgb
                    )
                },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("Save overlay styling")
            }
        }

        item {
            Text("Car Park Selection", style = MaterialTheme.typography.headlineSmall)
            Button(
                onClick = { viewModel.fetchFacilities() },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Fetch Facilities")
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search car parks") },
                placeholder = { Text("Type name or ID") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "Clear search"
                            )
                        }
                    }
                }
            )
        }

        items(filteredFacilities) { (id, name) ->
            val isSelected = uiState.selectedCarParks.any { it.id == id }
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = { Text(id) },
                trailingContent = {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { viewModel.toggleCarParkSelection(id, name) }
                    )
                },
                modifier = Modifier.clickable { viewModel.toggleCarParkSelection(id, name) }
            )
        }
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    argb: Int,
    onArgbChange: (Int) -> Unit
) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    val color = remember(argb) { Color(argb) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color = color, shape = MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(onClick = { open = true }) {
                Text("Pick")
            }
        }
    }

    if (open) {
        ColorPickerDialog(
            title = "$label color",
            initialArgb = argb,
            onDismiss = { open = false },
            onConfirm = {
                onArgbChange(it)
                open = false
            }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var a by remember { mutableStateOf((initialArgb ushr 24) and 0xFF) }
    var r by remember { mutableStateOf((initialArgb ushr 16) and 0xFF) }
    var g by remember { mutableStateOf((initialArgb ushr 8) and 0xFF) }
    var b by remember { mutableStateOf(initialArgb and 0xFF) }

    val preview = remember(a, r, g, b) {
        Color((a shl 24) or (r shl 16) or (g shl 8) or b)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(preview, shape = MaterialTheme.shapes.medium)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("A:$a  R:$r  G:$g  B:$b", style = MaterialTheme.typography.bodyMedium)
                }

                ChannelSlider("Alpha", a) { a = it }
                ChannelSlider("Red", r) { r = it }
                ChannelSlider("Green", g) { g = it }
                ChannelSlider("Blue", b) { b = it }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm((a shl 24) or (r shl 16) or (g shl 8) or b) }) {
                Text("Done")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f
        )
    }
}

@Composable
fun FavoritesScreen(viewModel: CarParkViewModel, uiState: CarParkUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Selected Car Parks", style = MaterialTheme.typography.headlineMedium)
        Text("Max 3 allowed for widget", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.refreshSelectedCarParks() }) {
            Text("Refresh Now")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            items(uiState.selectedCarParks) { carPark ->
                ListItem(
                    headlineContent = { Text("${carPark.name} (${carPark.abbr})") },
                    supportingContent = { Text("Available: ${carPark.availableSpots}") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.toggleCarParkSelection(carPark.id, carPark.name) }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_delete),
                                contentDescription = "Remove"
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(viewModel: CarParkViewModel, uiState: CarParkUiState) {
    val context = LocalContext.current
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { viewModel.exportHistory(context.contentResolver, it) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.importHistory(context.contentResolver, it) }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("P Overlay: NSW Commute Park", style = MaterialTheme.typography.headlineMedium)
        Text("Version 0.1")

        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("History backup", style = MaterialTheme.typography.titleMedium)
                Text("Saved records: ${uiState.historyCount}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Exports are written to a folder you choose, so you can keep a backup outside the app before uninstalling.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportLauncher.launch(null) },
                        enabled = !uiState.isHistoryOperationInProgress
                    ) {
                        Text("Export history")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        enabled = !uiState.isHistoryOperationInProgress
                    ) {
                        Text("Import history")
                    }
                }
                if (uiState.isHistoryOperationInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                uiState.historyStatusMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int
) {
    HOME("Setup", R.drawable.ic_home),
    FAVORITES("Selected", R.drawable.ic_favorite),
    HISTORY("History", R.drawable.ic_show_chart),
    PROFILE("About", R.drawable.ic_account_box),
}
