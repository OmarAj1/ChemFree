package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Loading : ScanUiState
    data class Success(
        val originalText: String,
        val detectedChemicals: List<ChemicalEntity>,
        val isFromApi: Boolean
    ) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ChemicalRepository(db.chemicalDao(), db.scanHistoryDao())

    // UI Tab State: "translator", "history", "profile", "directory"
    private val _currentTab = MutableStateFlow("translator")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Active User Config
    private val sharedPref = application.getSharedPreferences("chem_translator_prefs", Context.MODE_PRIVATE)
    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    // Chemical Knowledge Directory Flow
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val directoryList: StateFlow<List<ChemicalEntity>> = _searchQuery
        .debounce(250)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allChemicals
            } else {
                repository.searchChemicals(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Scan Text Processing State
    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    // History flows
    val scanHistory: StateFlow<List<ScanHistoryEntity>> = repository.scanHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            // Check preseed on first startup
            repository.checkAndPreseedDatabase()
            // Load user profile preferences
            loadSettings()
        }
    }

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadSettings() {
        _userSettings.value = UserSettings(
            isVegan = sharedPref.getBoolean("is_vegan", false),
            isGlutenFree = sharedPref.getBoolean("is_gluten_free", false),
            flagDyes = sharedPref.getBoolean("flag_dyes", false),
            flagTitaniumDioxide = sharedPref.getBoolean("flag_titanium_dioxide", false),
            flagCarrageenan = sharedPref.getBoolean("flag_carrageenan", false),
            flagPfas = sharedPref.getBoolean("flag_pfas", false),
            allergySoy = sharedPref.getBoolean("allergy_soy", false),
            allergyDairy = sharedPref.getBoolean("allergy_dairy", false),
            allergyWheat = sharedPref.getBoolean("allergy_wheat", false),
            allergyNuts = sharedPref.getBoolean("allergy_nuts", false),
            allergyCorn = sharedPref.getBoolean("allergy_corn", false)
        )
    }

    fun updateSettings(settings: UserSettings) {
        _userSettings.value = settings
        sharedPref.edit().apply {
            putBoolean("is_vegan", settings.isVegan)
            putBoolean("is_gluten_free", settings.isGlutenFree)
            putBoolean("flag_dyes", settings.flagDyes)
            putBoolean("flag_titanium_dioxide", settings.flagTitaniumDioxide)
            putBoolean("flag_carrageenan", settings.flagCarrageenan)
            putBoolean("flag_pfas", settings.flagPfas)
            putBoolean("allergy_soy", settings.allergySoy)
            putBoolean("allergy_dairy", settings.allergyDairy)
            putBoolean("allergy_wheat", settings.allergyWheat)
            putBoolean("allergy_nuts", settings.allergyNuts)
            putBoolean("allergy_corn", settings.allergyCorn)
            apply()
        }
    }

    fun clearScanningState() {
        _scanState.value = ScanUiState.Idle
    }

    fun processIngredientsScan(productName: String, text: String) {
        if (text.isBlank()) return
        _scanState.value = ScanUiState.Loading
        viewModelScope.launch {
            try {
                val result = repository.analyzeIngredients(productName, text)
                _scanState.value = ScanUiState.Success(
                    originalText = text,
                    detectedChemicals = result.first,
                    isFromApi = result.second == "api"
                )
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.localizedMessage ?: "Scanning failed.")
            }
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Helper function to return details of what dietary restrictions or allergy triggers a chemical sets off
    fun checkDietaryViolations(chemical: ChemicalEntity, settings: UserSettings): List<String> {
        val violations = mutableListOf<String>()
        val tags = chemical.dietarySafety.lowercase()

        if (settings.isVegan && (tags.contains("non_vegan") || tags.contains("whey") || tags.contains("lact") || tags.contains("dairy"))) {
            violations.add("Vegan Violation")
        }
        if (settings.isGlutenFree && (tags.contains("gluten") || tags.contains("non_gluten_free") || tags.contains("bromate"))) {
            violations.add("Gluten Violation")
        }
        if (settings.flagDyes && (tags.contains("flag:dye") || tags.contains("dye") || tags.contains("red") || tags.contains("yellow"))) {
            violations.add("Synthetic Dye")
        }
        if (settings.flagTitaniumDioxide && (tags.contains("flag:titanium_dioxide") || tags.contains("titanium"))) {
            violations.add("Titanium Dioxide")
        }
        if (settings.flagCarrageenan && (tags.contains("flag:carrageenan") || tags.contains("carrageenan"))) {
            violations.add("Inflammatory Carrageenan")
        }
        if (settings.flagPfas && (tags.contains("flag:pfas") || tags.contains("pfas") || tags.contains("pfoa"))) {
            violations.add("PFAS Forever Chemical")
        }

        // Allergy Checks
        if (settings.allergySoy && (tags.contains("soy") || tags.contains("lecithin"))) {
            violations.add("Soy Allergen Trigger")
        }
        if (settings.allergyDairy && (tags.contains("dairy") || tags.contains("whey") || tags.contains("casein") || tags.contains("non_vegan"))) {
            violations.add("Dairy Allergen Trigger")
        }
        if (settings.allergyWheat && (tags.contains("gluten") || tags.contains("wheat") || tags.contains("non_gluten_free") || tags.contains("bromate"))) {
            violations.add("Wheat Allergen Trigger")
        }
        if (settings.allergyCorn && (tags.contains("corn") || tags.contains("fructose") || tags.contains("starch") || tags.contains("hfcs"))) {
            violations.add("Corn Allergen Trigger")
        }

        return violations
    }
}
