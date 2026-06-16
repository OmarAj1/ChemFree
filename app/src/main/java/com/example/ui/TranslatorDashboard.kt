package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorDashboardScreen(
    viewModel: TranslatorViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()

    var showSubscriptionDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ChemTranslator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                listOf(
                    Triple("translator", Icons.Default.Search, "Scan & Translate"),
                    Triple("directory", Icons.AutoMirrored.Filled.List, "Dictionary"),
                    Triple("history", Icons.Default.Refresh, "Scan History"),
                    Triple("profile", Icons.Default.Settings, "Diet Profile")
                ).forEach { (tabId, icon, label) ->
                    val selected = currentTab == tabId
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tabId) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("nav_item_$tabId")
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "translator" -> ScanAndTranslateTab(
                    viewModel = viewModel,
                    userSettings = userSettings
                )
                "directory" -> ChemicalDirectoryTab(
                    viewModel = viewModel,
                    userSettings = userSettings
                )
                "history" -> HistoryTab(viewModel = viewModel)
                "profile" -> ProfileTab(
                    viewModel = viewModel,
                    userSettings = userSettings
                )
            }
        }
    }
}

@Composable
fun ScanAndTranslateTab(
    viewModel: TranslatorViewModel,
    userSettings: UserSettings
) {
    var productName by remember { mutableStateOf("") }
    var rawIngredientsText by remember { mutableStateOf("") }
    val scanUiState by viewModel.scanState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Sample Simulation Presets representing common additives
    val presets = listOf(
        PresetProduct(
            name = "Classic Strawberry Candy",
            icon = "🍬",
            ingredients = "Sugar, Corn Syrup, Citric Acid, Red 40, Artificial Strawberry Flavor, Yellow 5, Titanium Dioxide"
        ),
        PresetProduct(
            name = "Zero-Sugar Blue Energy Drink",
            icon = "⚡",
            ingredients = "Carbonated Water, Citric Acid, Aspartame, Sodium Benzoate, Caffeine, Soy Lecithin"
        ),
        PresetProduct(
            name = "Organic Chocolate Milk",
            icon = "🥛",
            ingredients = "Organic Whole Milk, Cane Sugar, Organic Cocoa, Carrageenan, Whey Protein"
        ),
        PresetProduct(
            name = "Teatime Bread Roll",
            icon = "🍞",
            ingredients = "Enriched Wheat Flour, Water, Yeast, High Fructose Corn Syrup, Potassium Bromate"
        ),
        PresetProduct(
            name = "PFAS Microwave Popcorn",
            icon = "🍿",
            ingredients = "Whole Grain Popcorn, Palm Oil, Salt, Natural Butter Flavor, PFOA Barrier Lining Packaging"
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Chemical Translator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Scan ingredient labels in plain English and color-code toxic hazards.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Clean Minimalism Scan Trigger Area Hero
        item {
            Card(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    // Simulate point-and-scan camera scan of classical high hazard product
                    productName = "Classic Strawberry Candy"
                    rawIngredientsText = "Sugar, Corn Syrup, Citric Acid, Red 40, Artificial Strawberry Flavor, Yellow 5, Titanium Dioxide"
                    viewModel.processIngredientsScan(productName, rawIngredientsText)
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSystemInDarkTheme()) Color(0xFF1D201A) else Color(0xFFE2E9D8)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_trigger_hero")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Scan Ingredients",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Scan Ingredients",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Point your camera at any food label",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Preset simulators
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📱 Simulated Scans (Try one!)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            Card(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    productName = preset.name
                                    rawIngredientsText = preset.ingredients
                                    viewModel.processIngredientsScan(preset.name, preset.ingredients)
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .width(180.dp)
                                    .testTag("preset_${preset.name.replace(" ", "_")}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(preset.icon, fontSize = 20.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = preset.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Tap to scan",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Ingredient input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔎 Custom Translation Scan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = productName,
                        onValueChange = { productName = it },
                        label = { Text("Product Name (Optional)") },
                        singleLine = true,
                        placeholder = { Text("e.g., Organic Cereal Bag") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = rawIngredientsText,
                        onValueChange = { rawIngredientsText = it },
                        label = { Text("Ingredient List To Translate") },
                        minLines = 3,
                        maxLines = 5,
                        placeholder = { Text("Paste ingredients list from labels (e.g., Water, Carrageenan, Red 40, Titanium Dioxide...)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ingredients_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            viewModel.processIngredientsScan(productName, rawIngredientsText)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("scan_button"),
                        enabled = rawIngredientsText.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze & Translate Ingredients", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Scanning Status and Result block
        item {
            AnimatedContent(
                targetState = scanUiState,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                },
                label = "scan_status_transition"
            ) { state ->
                when (state) {
                    is ScanUiState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active scan. Try entering an ingredient list above!",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                            )
                        }
                    }

                    is ScanUiState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Translating compounds in plain English...",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Text(
                                "Running OCR parser and health safety indices.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }

                    is ScanUiState.Success -> {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Overall summary header card
                            ScanSummaryHeaderCard(
                                productName = productName.ifEmpty { "Analyzed Ingredient Board" },
                                detectedCount = state.detectedChemicals.size,
                                chemicals = state.detectedChemicals,
                                isFromApi = state.isFromApi,
                                onClear = { viewModel.clearScanningState() }
                            )

                            // Result Details list title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🧪 Identified Chemical Additives",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${state.detectedChemicals.size} Additives Detected",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }

                            // Individual translated entity cards
                            state.detectedChemicals.forEach { chemical ->
                                val alerts = viewModel.checkDietaryViolations(chemical, userSettings)
                                ChemicalBreakdownCard(
                                    chemical = chemical,
                                    alerts = alerts,
                                    userSettings = userSettings
                                )
                            }
                        }
                    }

                    is ScanUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Scan Error",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        state.message,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pro Tip card
        item {
            val isDark = isSystemInDarkTheme()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E2419) else Color(0xFFF0F5EA)
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2E3B24) else Color(0xFFDDE5D9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isDark) Color(0xFF2E3C25) else Color(0xFFE2E9D8),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Pro Tip",
                            tint = if (isDark) MinimForestPrimaryDark else MinimForestPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Longevity Tip",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Look for 'Titanium Dioxide-free' alternatives in organic confectionery aisles and check the chemical safety indices regularly.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun ScanSummaryHeaderCard(
    productName: String,
    detectedCount: Int,
    chemicals: List<ChemicalEntity>,
    isFromApi: Boolean,
    onClear: () -> Unit
) {
    // Generate simplified risk score
    val highRiskCount = chemicals.count { it.riskLevel.uppercase() == "HIGH" }
    val modRiskCount = chemicals.count { it.riskLevel.uppercase() == "MODERATE" }
    val lowRiskCount = chemicals.count { it.riskLevel.uppercase() == "LOW" }

    var score = 100 - (highRiskCount * 25) - (modRiskCount * 12) - (lowRiskCount * 3)
    score = score.coerceIn(10, 100)

    val scoreColor = when {
        score >= 80 -> Color(0xFF10B981) // Safe Green
        score >= 50 -> Color(0xFFF59E0B) // Warn Orange
        else -> Color(0xFFEF4444) // Danger Red
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Safety Score breakdown",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }

                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score indicator
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .drawBehind {
                            drawCircle(
                                color = scoreColor.copy(alpha = 0.15f),
                                radius = size.minDimension / 2f
                            )
                            drawCircle(
                                color = scoreColor,
                                radius = size.minDimension / 2f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = score.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "SCORE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Breakdown counts
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• Risk Severity:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BadgeLabel(text = "$highRiskCount High", color = Color(0xFFEF4444))
                        BadgeLabel(text = "$modRiskCount Mod", color = Color(0xFFF59E0B))
                        BadgeLabel(text = "$lowRiskCount Low", color = Color(0xFF10B981))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isFromApi) "✨ Translated by Gemini AI Service" else "☕ Local Database Match (Offline Mode)",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChemicalBreakdownCard(
    chemical: ChemicalEntity,
    alerts: List<String>,
    userSettings: UserSettings
) {
    val isDark = isSystemInDarkTheme()
    val uppercaseRisk = chemical.riskLevel.uppercase()
    
    val (riskColor, riskBgColor, riskIndicatorText) = when (uppercaseRisk) {
        "HIGH" -> Triple(
            Color(0xFFBA1A1A), 
            if (isDark) Color(0xFF5A1010) else Color(0xFFFCEEEB),
            "DETECTED HAZARD"
        )
        "MODERATE" -> Triple(
            Color(0xFFD97706), 
            if (isDark) Color(0xFF4C2A02) else Color(0xFFFEF3C7),
            "MODERATE RISK"
        )
        else -> Triple(
            Color(0xFF386B1D), 
            if (isDark) Color(0xFF1B3B0A) else Color(0xFFECFDF5),
            "SAFE ADDITIVE"
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF31352D) else Color(0xFFDDE5D9)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("breakdown_card_${chemical.id}")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Display name and Risk badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Risk Subtitle Pill
                    Box(
                        modifier = Modifier
                            .background(riskBgColor, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = riskIndicatorText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            color = riskColor,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = chemical.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 28.sp
                    )
                }

                // Alert Logo Icon Box (Top right corner of card)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = riskBgColor,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uppercaseRisk == "HIGH") Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = riskColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Translation (Plain English)
            Text(
                text = "TRANSLATION",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF74796E),
                letterSpacing = 1.5.sp
            )
            Text(
                text = chemical.plainEnglishName,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Section: Purpose
            Text(
                text = "INDUSTRY PURPOSE",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF74796E),
                letterSpacing = 1.5.sp
            )
            Text(
                text = chemical.purpose,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            // Section: Risk Meter
            HorizontalDivider(color = if (isDark) Color(0xFF31352D) else Color(0xFFDDE5D9))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HEALTH RISK INDEX",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF74796E),
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = uppercaseRisk,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = riskColor,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Segmented interactive bar index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Low Risk Segment (Green)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF386B1D).copy(alpha = if (uppercaseRisk == "LOW") 1f else 0.25f))
                )
                // Moderate Risk Segment (Orange)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFFD97706).copy(alpha = if (uppercaseRisk == "MODERATE") 1f else 0.25f))
                )
                // High Risk Segment (Red)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFFBA1A1A).copy(alpha = if (uppercaseRisk == "HIGH") 1f else 0.25f))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = chemical.riskDescription,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Personal Allergy & Diet Flags
            if (alerts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isDark) Color(0xFF3F1616) else Color(0xFFFEF2F2), 
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, if (isDark) Color(0xFF7F1D1D) else Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    alerts.forEach { alert ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "⚠️ Personalized Alert: Matches $alert setting",
                                color = if (isDark) Color(0xFFFECACA) else Color(0xFF991B1B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5), 
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Safe for your personalized dietary profile",
                        color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChemicalDirectoryTab(
    viewModel: TranslatorViewModel,
    userSettings: UserSettings
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val chemicals by viewModel.directoryList.collectAsStateWithLifecycle()

    var expandedChemicalId by remember { mutableStateOf<Int?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "Chemical Additive Dictionary",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Search and view our extensive indexed knowledge base of regulatory food chemicals, coloring pigments, and preservatives.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search by chemical name (e.g. Carrageenan)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("glossary_search_field"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (chemicals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chemicals matched this query.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("chemicals_glossary_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chemicals) { chem ->
                    val isExpanded = expandedChemicalId == chem.id
                    Card(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            expandedChemicalId = if (isExpanded) null else chem.id
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chemical_item_${chem.name}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chem.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = chem.plainEnglishName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                val badgeColor = when (chem.riskLevel.uppercase()) {
                                    "HIGH" -> Color(0xFFEF4444)
                                    "MODERATE" -> Color(0xFFF59E0B)
                                    else -> Color(0xFF10B981)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = chem.riskLevel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = badgeColor
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    Text(
                                        text = "Why Manufacturers Use It:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = chem.purpose,
                                        fontStyle = FontStyle.Italic,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    Text(
                                        text = "Health Concern Breakdown:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = chem.riskDescription,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    // Checks
                                    val viol = viewModel.checkDietaryViolations(chem, userSettings)
                                    if (viol.isNotEmpty()) {
                                        Text(
                                            text = "Matches personal trigger: ${viol.joinToString()}",
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun HistoryTab(viewModel: TranslatorViewModel) {
    val history by viewModel.scanHistory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Scanned Histories",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Keep track of previous scan indexes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearAllHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "History is empty",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Previous product chemical scans appear here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history) { record ->
                    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                    val scanDate = sdf.format(Date(record.timestamp))

                    val scoreColor = when {
                        record.score >= 80 -> Color(0xFF10B981)
                        record.score >= 50 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Tap to reload into scan screen
                                viewModel.processIngredientsScan(record.productName, record.rawIngredients)
                                viewModel.selectTab("translator")
                            }
                            .testTag("history_item_${record.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Score Indicator
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .drawBehind {
                                        drawCircle(color = scoreColor.copy(alpha = 0.12f), radius = size.minDimension / 2f)
                                        drawCircle(
                                            color = scoreColor,
                                            radius = size.minDimension / 2f,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    record.score.toString(),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.productName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = record.rawIngredients,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = scanDate,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteHistoryItem(record.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete record",
                                    tint = Color(0xFFEF4444).copy(alpha = 0.72f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun ProfileTab(
    viewModel: TranslatorViewModel,
    userSettings: UserSettings
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Personal Health Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure custom triggers to automatically flag specific health allergens, synthetic coloring, or toxic compounds.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Tiers monetization removed

        // Diet Toggles Section
        item {
            Text(
                "🥗 Personalized Diets",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    ToggleItem(
                        title = "Strictly Vegan",
                        description = "Flag any chemicals containing whey, gelatin, carmine, or animal extracts.",
                        checked = userSettings.isVegan,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(isVegan = it)) },
                        testTag = "toggle_vegan"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    ToggleItem(
                        title = "Gluten Friendly",
                        description = "Flag compounds containing hidden wheat or dough conditioning gluten.",
                        checked = userSettings.isGlutenFree,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(isGlutenFree = it)) },
                        testTag = "toggle_gluten"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    ToggleItem(
                        title = "Synthetic Dye Warning",
                        description = "Trigger alerts for chemicals linked to hyperactivity (like Red 40, Yellow 5).",
                        checked = userSettings.flagDyes,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(flagDyes = it)) },
                        testTag = "toggle_dyes"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    ToggleItem(
                        title = "Avoid Titanium Dioxide",
                        description = "Flag whitener chemical E171 banned in EU food products.",
                        checked = userSettings.flagTitaniumDioxide,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(flagTitaniumDioxide = it)) },
                        testTag = "toggle_titanium"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    ToggleItem(
                        title = "Carrageenan Sensitive",
                        description = "Identify emulsifiers known to irritate digestive tracks.",
                        checked = userSettings.flagCarrageenan,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(flagCarrageenan = it)) },
                        testTag = "toggle_carrageenan"
                    )
                }
            }
        }

        // Allergies Toggle Section
        item {
            Text(
                "❌ Automated Allergens",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    ToggleItem(
                        title = "Soy Allergies",
                        description = "Flag lecithin and soy-derivative binding agents.",
                        checked = userSettings.allergySoy,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(allergySoy = it)) },
                        testTag = "toggle_allergy_soy"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    ToggleItem(
                        title = "Dairy Allergies",
                        description = "Flag casein and whey lactose proteins.",
                        checked = userSettings.allergyDairy,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(allergyDairy = it)) },
                        testTag = "toggle_allergy_dairy"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    ToggleItem(
                        title = "Wheat Allergies",
                        description = "Flag structural products and yeasts matching wheat profiles.",
                        checked = userSettings.allergyWheat,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(allergyWheat = it)) },
                        testTag = "toggle_allergy_wheat"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    ToggleItem(
                        title = "Corn Allergies",
                        description = "Flag glucose and high-fructose corn syrups.",
                        checked = userSettings.allergyCorn,
                        onUpdate = { viewModel.updateSettings(userSettings.copy(allergyCorn = it)) },
                        testTag = "toggle_allergy_corn"
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun ToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onUpdate: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onUpdate,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

// Extension to scale components as needed
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.drawBehind { /* Dummy modifier to hold scale alignment in custom switches */ }
)

@Composable
fun BadgeLabel(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            color = color
        )
    }
}


data class PresetProduct(
    val name: String,
    val icon: String,
    val ingredients: String
)
