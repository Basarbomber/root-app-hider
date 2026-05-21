package com.example.ui

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.model.AppItem
import com.example.ui.theme.*
import com.example.viewmodel.HiderViewModel
import com.example.viewmodel.TermLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: HiderViewModel,
    modifier: Modifier = Modifier
) {
    val installedApps by viewModel.installedApps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val rootVerified by viewModel.rootVerified.collectAsState()
    val shellLogs by viewModel.shellLogs.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Installed (Visibles), 1 = Stealth Hidden
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = StealthBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Security Shield",
                            tint = if (rootVerified) StealthGreen else StealthDanger,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "ROOT STEALTH MODULE",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color.White
                            )
                            Text(
                                text = if (rootVerified) "SU PRIVILEGES ACTIVE" else "SAFE SIMULATION MODE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (rootVerified) StealthGreen else StealthMatrixText.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.fetchPackages() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = StealthGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StealthSurfaceDark,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(StealthBlack)
        ) {
            // Main Status Shield Alert
            StatusCard(rootVerified = rootVerified)

            // Dynamic Tab Navigation
            TabHeaderGrid(
                activeTab = activeTab,
                installedCount = installedApps.size,
                hiddenCount = hiddenApps.size,
                onTabSelect = { activeTab = it }
            )

            // Search Bar Component
            SearchBarField(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                focusManager = focusManager
            )

            // Dynamic App Lists & Console Output Divider
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = StealthGreen, strokeWidth = 3.dp)
                    }
                } else {
                    val currentList = if (activeTab == 0) installedApps else hiddenApps
                    if (currentList.isEmpty()) {
                        EmptyListFeedback(activeTab = activeTab, isSearching = searchQuery.isNotEmpty())
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(currentList, key = { it.packageName }) { app ->
                                AppListItem(
                                    app = app,
                                    onToggleStealth = { viewModel.toggleAppStealth(app) }
                                )
                            }
                        }
                    }
                }
            }

            // Real-time Terminal Log Output Stream
            TerminalConsoleLogs(
                logs = shellLogs,
                onClear = { viewModel.clearLogs() }
            )
        }
    }
}

@Composable
fun StatusCard(rootVerified: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StealthSurfaceDark
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (rootVerified) StealthGreen.copy(alpha = 0.3f) else StealthDivider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (rootVerified) StealthGreen.copy(alpha = 0.15f) else StealthDanger.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (rootVerified) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = "Status Icon",
                    tint = if (rootVerified) StealthGreen else StealthDanger,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rootVerified) "System vollstandig manipulierbar" else "Safe-Modus aktiv (Kein Root)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (rootVerified) 
                        "Diese App verbirgt Systempakete und Drittanbieter-Apps vollstandig, sodass selbst der Cache und Google Play diese nicht mehr sehen konnen." 
                        else "Simulierter Modus. Das Hinzufügen von Apps sperrt und verbirgt diese im lokalen datengeschützten Stealth-Safe.",
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun TabHeaderGrid(
    activeTab: Int,
    installedCount: Int,
    hiddenCount: Int,
    onTabSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(StealthSurfaceDark)
            .border(1.dp, StealthDivider, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tab 0: Visible Installed Apps
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                .background(if (activeTab == 0) StealthGreen.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onTabSelect(0) }
                .testTag("tab_installed"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Installed apps",
                        tint = if (activeTab == 0) StealthGreen else Color.LightGray.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Apps ($installedCount)",
                        color = if (activeTab == 0) StealthGreen else Color.White,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (activeTab == 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(40.dp)
                            .height(2.dp)
                            .background(StealthGreen)
                    )
                }
            }
        }

        // Divider Line
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(StealthDivider)
        )

        // Tab 1: Invisible Hidden Apps
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp))
                .background(if (activeTab == 1) StealthGreen.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onTabSelect(1) }
                .testTag("tab_hidden"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Hidden apps",
                        tint = if (activeTab == 1) StealthGreen else Color.LightGray.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Versteckt ($hiddenCount)",
                        color = if (activeTab == 1) StealthGreen else Color.White,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (activeTab == 1) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(40.dp)
                            .height(2.dp)
                            .background(StealthGreen)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(52.dp)
            .border(1.dp, StealthDivider, RoundedCornerShape(10.dp))
            .testTag("app_search_field"),
        placeholder = {
            Text(
                text = "Nach Name oder Paket filtern...",
                color = Color.Gray,
                fontSize = 13.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search icon",
                tint = StealthGreen.copy(alpha = 0.7f)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = Color.LightGray
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = StealthSurfaceDark,
            unfocusedContainerColor = StealthSurfaceDark,
            disabledContainerColor = StealthSurfaceDark,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = StealthGreen,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
fun AppListItem(
    app: AppItem,
    onToggleStealth: () -> Unit
) {
    var showSelfDialog by remember { mutableStateOf(false) }

    val iconPainter: Painter = remember(app.packageName) {
        try {
            BitmapPainter(app.icon.toBitmap().asImageBitmap())
        } catch (e: Exception) {
            BitmapPainter(android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StealthDivider, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = StealthSurfaceDark
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App Icon
            Image(
                painter = iconPainter,
                contentDescription = "${app.appName} Icon",
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StealthHighlight),
                contentScale = ContentScale.Crop
            )

            // Package Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    color = StealthMatrixText.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "v${app.versionName} • " + if (app.isSystemDisabled || app.isHiddenInDb) "STATUS: UNPASSBAR" else "STATUS: SICHTBAR",
                    color = if (app.isSystemDisabled || app.isHiddenInDb) StealthGreen else Color.LightGray.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Action Button
            Button(
                onClick = { onToggleStealth() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (app.isSystemDisabled || app.isHiddenInDb) StealthHighlight else StealthGreen,
                    contentColor = if (app.isSystemDisabled || app.isHiddenInDb) StealthGreen else StealthBlack
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("steal_toggle_${app.packageName}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (app.isSystemDisabled || app.isHiddenInDb) Icons.Default.PlayArrow else Icons.Default.Lock,
                        contentDescription = "Action Icon",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (app.isSystemDisabled || app.isHiddenInDb) "Anzeigen" else "Sperren",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyListFeedback(activeTab: Int, isSearching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Default.Warning else if (activeTab == 0) Icons.Default.Home else Icons.Default.Check,
                contentDescription = "No apps",
                tint = StealthGreen.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = if (isSearching) "Keine Apps gefunden" else if (activeTab == 0) "Keine Apps vorhanden" else "Keine versteckten Apps",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (isSearching) "Ändere deine Suchanfrage und versuche es erneut." 
                       else if (activeTab == 0) "Installierte Anwendungen konnten nicht ausgelesen werden." 
                       else "Wähle eine App aus der Liste aus und klicke auf 'Sperren', um sie absolut unsichtbar zu schalten.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.widthIn(max = 280.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TerminalConsoleLogs(
    logs: List<TermLog>,
    onClear: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(StealthSurfaceDark)
            .border(1.dp, StealthDivider, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        // Console Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Console",
                    tint = StealthGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "LOG ENGINE OUTPUT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StealthGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "+${logs.size}",
                        color = StealthGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (logs.isNotEmpty()) {
                    Text(
                        text = "LEEREN",
                        modifier = Modifier
                            .clickable { onClear() }
                            .padding(4.dp),
                        color = StealthDanger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = "Expand Console",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Active Expand Console Panel
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(StealthBlack)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "LOG_STREAM: Bereit für Signale...",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    items(logs) { log ->
                        TerminalLogRecord(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLogRecord(log: TermLog) {
    val timeStr = remember(log.timestamp) {
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        formatter.format(Date(log.timestamp))
    }

    val typeColor = when (log.type) {
        TermLog.LogType.SUCCESS -> StealthGreen
        TermLog.LogType.ERROR -> StealthDanger
        TermLog.LogType.ROOT_CMD -> StealthMatrixText
        TermLog.LogType.INFO -> Color.Cyan
    }

    val prefixSymbol = when (log.type) {
        TermLog.LogType.SUCCESS -> "[SECURE_OK]"
        TermLog.LogType.ERROR -> "[ROOT_ERR]"
        TermLog.LogType.ROOT_CMD -> "[SYS_ROOT]"
        TermLog.LogType.INFO -> "[SYS_INFO]"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StealthDivider.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .background(StealthSurfaceDark.copy(alpha = 0.6f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$prefixSymbol -> ${log.command}",
                color = typeColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = timeStr,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
        
        if (log.stdout.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.stdout,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }

        if (log.stderr.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ERR: ${log.stderr}",
                color = StealthDanger.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}
