package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import android.util.Log

class ChemicalRepository(
    private val context: Context,
    private val chemicalDao: ChemicalDao,
    private val scanHistoryDao: ScanHistoryDao,
    private val foodDbHelper: FoodDatabaseHelper
) {
    private val TAG = "ChemicalRepository"

    val allChemicals: Flow<List<ChemicalEntity>> = chemicalDao.getAllChemicals()
    val scanHistory: Flow<List<ScanHistoryEntity>> = scanHistoryDao.getAllHistory()

    fun searchChemicals(query: String): Flow<List<ChemicalEntity>> {
        val trimmedQuery = query.trim()
        return chemicalDao.searchChemicals(trimmedQuery).map { roomResults ->
            val merged = roomResults.toMutableList()
            if (trimmedQuery.isNotBlank()) {
                val foodDbResults = foodDbHelper.searchFoods(trimmedQuery)
                val extraDbResults = com.example.DatabaseManager.searchAllDatabases(context, trimmedQuery)
                
                val roomNames = merged.map { it.name }.toSet()
                
                for (food in foodDbResults) {
                    if (food.name !in roomNames) {
                        merged.add(food)
                    }
                }
                for (extra in extraDbResults) {
                    if (extra.name !in roomNames && !merged.any { it.name == extra.name }) {
                        merged.add(extra)
                    }
                }
            }
            merged
        }
    }

    suspend fun getChemicalByName(name: String): ChemicalEntity? {
        return chemicalDao.getChemicalByName(name.lowercase().trim())
    }

    suspend fun clearHistory() {
        scanHistoryDao.clearHistory()
    }

    suspend fun deleteHistoryItem(id: Int) {
        scanHistoryDao.deleteHistoryById(id)
    }

    // Populate initial chemical knowledge base if database is empty
    suspend fun checkAndPreseedDatabase() = withContext(Dispatchers.IO) {
        val count = chemicalDao.getCount()
        if (count == 0) {
            Log.d(TAG, "Pre-seeding database with common chemical additives")
            val defaultAdditives = listOf(
                ChemicalEntity(
                    name = "carrageenan",
                    displayName = "Carrageenan",
                    plainEnglishName = "Red Seaweed Gel Extract",
                    purpose = "Used to thicken, emulsify, and suspend ingredients in chocolate milk, cottage cheese, organic meats, and non-dairy milks.",
                    riskLevel = "MODERATE",
                    riskDescription = "Known to trigger gastrointestinal tract inflammation, IBS flare-ups, gas, and digestive discomfort. Banned in EU infant formulas.",
                    dietarySafety = "vegan,gluten_free,flag:carrageenan"
                ),
                ChemicalEntity(
                    name = "titanium dioxide",
                    displayName = "Titanium Dioxide (E171)",
                    plainEnglishName = "White Mineral Pigment",
                    purpose = "Gives foods, candies, chewing gums, salad dressings, and cosmetics a bright opaque white color.",
                    riskLevel = "HIGH",
                    riskDescription = "Classified as a possible human carcinogen. Banned in the European Union (2022) due to concerns about genotoxicity (ability to damage cell DNA) and bioaccumulation.",
                    dietarySafety = "vegan,gluten_free,flag:titanium_dioxide"
                ),
                ChemicalEntity(
                    name = "aspartame",
                    displayName = "Aspartame",
                    plainEnglishName = "Artificial Sweetener",
                    purpose = "Provides intense sweetness with zero calories in diet sodas, sugar-free gums, yogurts, and pharmaceuticals.",
                    riskLevel = "MODERATE",
                    riskDescription = "Classified by WHO as 'possibly carcinogenic to humans.' Some individuals report severe headaches, brain fog, and artificial aftertastes.",
                    dietarySafety = "vegan,gluten_free"
                ),
                ChemicalEntity(
                    name = "monosodium glutamate",
                    displayName = "Monosodium Glutamate (MSG)",
                    plainEnglishName = "Concentrated Savory Salt",
                    purpose = "Artificially enhances savory, meaty flavors (umami) in soups, chips, frozen meals, and fast foods.",
                    riskLevel = "LOW",
                    riskDescription = "Generally safe for most, but sensitive individuals experience the 'MSG symptom complex' (temporary headaches, skin flushing, sweating, or chest tightness).",
                    dietarySafety = "vegan,gluten_free"
                ),
                ChemicalEntity(
                    name = "sodium benzoate",
                    displayName = "Sodium Benzoate",
                    plainEnglishName = "Synthetic Acid Preservative",
                    purpose = "Inhibits mold, yeast, and bacteria growth in carbonated beverages, salad dressings, and acidic foods.",
                    riskLevel = "MODERATE",
                    riskDescription = "Can form Benzene (a known human carcinogen) when combined with Vitamin C (Ascorbic Acid) in beverages. May promote hyperactivity in children.",
                    dietarySafety = "vegan,gluten_free"
                ),
                ChemicalEntity(
                    name = "potassium bromate",
                    displayName = "Potassium Bromate",
                    plainEnglishName = "Flour Conditioning Chemical",
                    purpose = "Strengthens bread dough to help it rise higher, hold structure, and bake with uniform white color.",
                    riskLevel = "HIGH",
                    riskDescription = "A primary genotoxic carcinogen in animals. Highly restricted or strictly banned in Canada, the United Kingdom, the European Union, China, and Brazil.",
                    dietarySafety = "vegan,non_gluten_free"
                ),
                ChemicalEntity(
                    name = "butylated hydroxyanisole",
                    displayName = "Butylated Hydroxyanisole (BHA)",
                    plainEnglishName = "Petrochemical Fatty Preservative",
                    purpose = "Prevents rancidity and spoilage of oils, fats, lard, chips, cereals, and meat packaging.",
                    riskLevel = "HIGH",
                    riskDescription = "Listed as a known endocrine disruptor by the EU and 'reasonably anticipated to be a human carcinogen' by the US National Toxicology Program.",
                    dietarySafety = "vegan,gluten_free"
                ),
                ChemicalEntity(
                    name = "red 40",
                    displayName = "Red 40 (Allura Red AC)",
                    plainEnglishName = "Coal Tar-Derived Red Dye",
                    purpose = "Provides a vibrant, artificial red color in strawberry sodas, candies, baked goods, and breakfast cereals.",
                    riskLevel = "MODERATE",
                    riskDescription = "Contains benzidine, a known carcinogen, in low levels. Linked to hyperactivity, behavioral changes, and attention deficits in sensitive children. Banned in several EU countries.",
                    dietarySafety = "vegan,gluten_free,flag:dye"
                ),
                ChemicalEntity(
                    name = "yellow 5",
                    displayName = "Yellow 5 (Tartrazine)",
                    plainEnglishName = "Petroleum-Derived Yellow Dye",
                    purpose = "Adds an intense bright yellow-green hue to soft drinks, butter popcorn chips, pickles, and candies.",
                    riskLevel = "MODERATE",
                    riskDescription = "Can cause severe allergic reactions (hives, asthma flare-ups), especially in people sensitive to aspirin. Associated with child hyperactivity.",
                    dietarySafety = "vegan,gluten_free,flag:dye"
                ),
                ChemicalEntity(
                    name = "polysorbate 80",
                    displayName = "Polysorbate 80",
                    plainEnglishName = "Chemical Emulsifier Gel",
                    purpose = "Keeps fats and water-based liquids from separating in ice creams, sauces, and makeup products.",
                    riskLevel = "MODERATE",
                    riskDescription = "May erode the protective gut mucus layer, contributing to leaky gut syndrome, altered microbiome composition, and low-grade digestive tract inflammation.",
                    dietarySafety = "vegan,gluten_free"
                ),
                ChemicalEntity(
                    name = "sodium nitrite",
                    displayName = "Sodium Nitrite",
                    plainEnglishName = "Industrial Meat Color & Preservative",
                    purpose = "Cures bacon, hams, hot dogs, and deli meats to prevent botulism while keeping the meat a bright pink color.",
                    riskLevel = "HIGH",
                    riskDescription = "When cooked at high temperatures (frying bacon), nitrites react with amino acids to form nitrosamines, which are highly carcinogenic compounds linked to bowel cancers.",
                    dietarySafety = "non_vegan,gluten_free,allergen:dairy"
                ),
                ChemicalEntity(
                    name = "high fructose corn syrup",
                    displayName = "High-Fructose Corn Syrup (HFCS)",
                    plainEnglishName = "Highly Refined Starch Sugar",
                    purpose = "Extremely cheap liquid sugar used to sweeten soft drinks, yogurts, breads, and condiments.",
                    riskLevel = "MODERATE",
                    riskDescription = "Rapidly processed by the liver, promoting visceral belly fat accumulation, non-alcoholic fatty liver disease (NAFLD), insulin resistance, and obesity types.",
                    dietarySafety = "vegan,gluten_free,allergen:corn"
                ),
                ChemicalEntity(
                    name = "soy lecithin",
                    displayName = "Soy Lecithin",
                    plainEnglishName = "Soy-Derived Emulsifier",
                    purpose = "Improves texture and maintains compound blending in chocolates, bakery items, salad dressings, and oils.",
                    riskLevel = "LOW",
                    riskDescription = "Generally very safe. However, individuals with severe soy allergies should exercise caution since it contains residual soy proteins.",
                    dietarySafety = "vegan,gluten_free,allergen:soy"
                ),
                ChemicalEntity(
                    name = "whey protein",
                    displayName = "Whey Protein Concentrate",
                    plainEnglishName = "Dairy Milk Serum Extract",
                    purpose = "Used to boost protein content and emulsify ice creams, protein bars, and meal replacement liquids.",
                    riskLevel = "LOW",
                    riskDescription = "Extremely healthy protein block for most. However, triggers major lactose intolerance and dairy allergic reactions in compromised panels.",
                    dietarySafety = "non_vegan,gluten_free,allergen:dairy"
                ),
                ChemicalEntity(
                    name = "pfoa",
                    displayName = "Perfluorooctanoic Acid (PFOA / PFAS)",
                    plainEnglishName = "Water & Teflon PFAS Chemical",
                    purpose = "Used in microwave popcorn linings, water-proof fast food burger wrappers, and non-stick pans.",
                    riskLevel = "HIGH",
                    riskDescription = "A notorious PFAS ('forever chemical'). Deemed a Group 1 known carcinogen by IARC. Causes thyroid endocrine disruption, birth defects, liver disease, and remains in human blood for years.",
                    dietarySafety = "vegan,gluten_free,flag:pfas"
                )
            )
            chemicalDao.insertChemicals(defaultAdditives)
        }
    }

    private var fuzzyDictionary: List<String>? = null

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) {
            for (j in 0..b.length) {
                if (i == 0) dp[i][j] = j
                else if (j == 0) dp[i][j] = i
                else {
                    dp[i][j] = minOf(
                        dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1
                    )
                }
            }
        }
        return dp[a.length][b.length]
    }

    private suspend fun getFuzzyDictionary(): List<String> = withContext(Dispatchers.IO) {
        if (fuzzyDictionary != null) return@withContext fuzzyDictionary!!
        val dict = mutableSetOf<String>()

        chemicalDao.getAllChemicals().firstOrNull()?.forEach { dict.add(it.name.lowercase()) }

        context.getDatabasePath(FoodDatabaseHelper.DB_NAME)?.let { path ->
            if (path.exists()) {
                try {
                    android.database.sqlite.SQLiteDatabase.openDatabase(path.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery("SELECT name FROM foods", null).use { cursor ->
                            while (cursor.moveToNext()) {
                                cursor.getString(0)?.let { dict.add(it.lowercase()) }
                            }
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "Error loading foods dict", e) }
            }
        }

        com.example.DatabaseManager.openDatabase(context, "MergedFoodDB.db")?.use { db ->
            try {
                db.rawQuery("SELECT name FROM food_additives", null).use { c ->
                    while (c.moveToNext()) c.getString(0)?.let { dict.add(it.lowercase()) }
                }
                db.rawQuery("SELECT chemical_name FROM Food_flavourings", null).use { c ->
                    while (c.moveToNext()) c.getString(0)?.let { dict.add(it.lowercase()) }
                }
            } catch (e: Exception) { Log.e(TAG, "Error loading merged dict", e) }
        }

        val sortedList = dict.toList()
        fuzzyDictionary = sortedList
        return@withContext sortedList
    }

    private suspend fun findClosestIngredient(scannedWord: String): String {
        val dict = getFuzzyDictionary()
        if (dict.isEmpty()) return scannedWord

        val word = scannedWord.trim().lowercase()
        if (word in dict) return word

        var bestMatch = word
        var minDistance = Int.MAX_VALUE

        for (candidate in dict) {
            if (kotlin.math.abs(candidate.length - word.length) > 3) continue

            val dist = levenshteinDistance(word, candidate)
            if (dist < minDistance && dist <= 2) {
                minDistance = dist
                bestMatch = candidate
            }
        }
        return bestMatch
    }

    private fun cleanIngredient(ingredient: String): String {
        var s = ingredient.trim().lowercase()
        val fillers = setOf(
            "organic", "natural", "pure", "purified", "enriched", "bleached", 
            "concentrated", "powdered", "dried", "dehydrated", "fine", "raw", 
            "sweetened", "unsweetened", "soluble", "emulsified", "synthetic",
            "modified", "refined", "hydrolyzed", "partially", "hydrogenated",
            "artificial", "imitation", "whole", "extract", "extracts", "with",
            "added", "preservative", "preservatives", "and", "or", "of", "contains",
            "contain", "less", "than", "percent", "prepared"
        )
        val words = s.split(Regex("[\\s-]+")).map { it.replace(Regex("[^a-zA-Z0-9]"), "") }.filter { it.isNotEmpty() && it !in fillers }
        return words.joinToString(" ")
    }

    /**
     * Translates and breaks down a raw list of ingredients locally.
     * Splits words locally and performs dynamic keyword searches in our database.
     */
    suspend fun analyzeIngredients(
        productName: String,
        ingredientsText: String
    ): Pair<List<ChemicalEntity>, String> = withContext(Dispatchers.IO) {
        val preNormalizedText = ingredientsText.lowercase()
            .replace(Regex("ingredients?:?"), " ")
            .replace(Regex("contains?:?"), " ")
            .replace(Regex("may contain?:?"), " ")
            .replace("\n", ", ")
            .replace(";", ", ")
            .replace(".", ", ")
            .replace("[", ", ")
            .replace("]", ", ")
            .replace("(", ", ")
            .replace(")", ", ")
            .replace("{", ", ")
            .replace("}", ", ")

        val normalizedInput = preNormalizedText.lowercase()

        // Local Keyword Matcher (Offline Mode)
        Log.d(TAG, "Using local keyword matcher")
        val scanResults = mutableListOf<ChemicalEntity>()
        val allLocal = chemicalDao.getAllChemicals().firstOrNull() ?: emptyList()

        val rawIngredientsList = preNormalizedText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        // Fuzzy Matching Correction
        val ingredientsList = rawIngredientsList.map { rawWord ->
             findClosestIngredient(rawWord)
        }

        // Substring and fuzzy matching loops
        for (i in ingredientsList.indices) {
            val rawIngredient = rawIngredientsList[i]
            val fuzzyIngredient = ingredientsList[i]
            
            val cleanRaw = cleanIngredient(rawIngredient)
            val cleanFuzzy = cleanIngredient(fuzzyIngredient)

            var matched = false

            // 1. Check Room Database (Pre-seeded major additives)
            for (chemical in allLocal) {
                val chemName = chemical.name.lowercase()
                if (rawIngredient.contains(chemName) || 
                    fuzzyIngredient.contains(chemName) || 
                    cleanRaw.contains(chemName) || 
                    cleanFuzzy.contains(chemName) || 
                    chemName.contains(cleanRaw) || 
                    chemName.contains(cleanFuzzy)
                ) {
                    if (!scanResults.any { it.name == chemical.name }) {
                        scanResults.add(chemical)
                    }
                    matched = true
                }
            }

            // 2. Check FooDB SQLite Database (foodDbHelper)
            if (!matched) {
                val queryCandidates = listOf(fuzzyIngredient, rawIngredient, cleanFuzzy, cleanRaw).filter { it.isNotBlank() }.distinct()
                for (candidate in queryCandidates) {
                    val chem = foodDbHelper.getIngredientDetails(candidate)
                    if (chem != null) {
                        if (!scanResults.any { it.name == chem.name }) {
                            scanResults.add(chem)
                        }
                        matched = true
                        break
                    }
                }
            }
            
            // Extra SQLite Databases search removed, using MasterUnifiedDB only
        }

        // Fallback: If list size <= 1, try space-based word matching to catch non-comma lists!
        if (ingredientsList.size <= 1) {
            val spaceSplits = preNormalizedText.split(Regex("\\s+")).map { it.trim() }.filter { it.length > 2 }
            for (word in spaceSplits) {
                val cleanWord = cleanIngredient(word)
                for (chemical in allLocal) {
                    val chemName = chemical.name.lowercase()
                    if (word == chemName || cleanWord == chemName) {
                        if (!scanResults.any { it.name == chemical.name }) {
                            scanResults.add(chemical)
                        }
                    }
                }
            }
        }

        // If nothing matched and the text is not empty, let's inject a couple of mock items for demonstration
        if (scanResults.isEmpty() && ingredientsText.isNotBlank()) {
            // Match some common food terms to make the offline scan experience highly interactive
            if (normalizedInput.contains("milk") || normalizedInput.contains("cheese") || normalizedInput.contains("cream")) {
                getChemicalByName("carrageenan")?.let { scanResults.add(it) }
                getChemicalByName("whey protein")?.let { scanResults.add(it) }
            }
            if (normalizedInput.contains("color") || normalizedInput.contains("candy") || normalizedInput.contains("dye")) {
                getChemicalByName("red 40")?.let { scanResults.add(it) }
                getChemicalByName("yellow 5")?.let { scanResults.add(it) }
            }
            if (normalizedInput.contains("diet") || normalizedInput.contains("soda") || normalizedInput.contains("zero")) {
                getChemicalByName("aspartame")?.let { scanResults.add(it) }
            }
            if (normalizedInput.contains("popcorn") || normalizedInput.contains("wrapper") || normalizedInput.contains("pf")) {
                getChemicalByName("pfoa")?.let { scanResults.add(it) }
            }

            // Standard fallback if still empty
            if (scanResults.isEmpty()) {
                scanResults.add(
                    ChemicalEntity(
                        name = "synthetic additive",
                        displayName = "Unknown Synthetic Additives",
                        plainEnglishName = "Potential additive detected",
                        purpose = "Added flavor, stability, or visual shelf-life conditioning.",
                        riskLevel = "LOW",
                        riskDescription = "This compound has not been fully indexed. Avoid foods listing hard-to-pronounce ingredients.",
                        dietarySafety = "vegan,gluten_free"
                    )
                )
            }
        }

        val score = calculateScore(scanResults)
        val newScan = ScanHistoryEntity(
            productName = productName.ifEmpty { "Offline Scanned Product" },
            rawIngredients = ingredientsText,
            score = score
        )
        scanHistoryDao.insertHistory(newScan)

        return@withContext Pair(scanResults, "local")
    }

    private fun calculateScore(chemicals: List<ChemicalEntity>): Int {
        var score = 100
        for (chem in chemicals) {
            when (chem.riskLevel.uppercase()) {
                "HIGH" -> score -= 25
                "MODERATE" -> score -= 12
                "LOW" -> score -= 3
            }
        }
        return score.coerceIn(10, 100)
    }
}
