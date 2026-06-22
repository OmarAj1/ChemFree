package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
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
                val roomNames = merged.map { it.name }.toSet()
                
                for (food in foodDbResults) {
                    if (food.name !in roomNames) {
                        merged.add(food)
                    }
                }
            }
            merged
        }.flowOn(Dispatchers.IO)
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

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        var v0 = IntArray(s2.length + 1) { it }
        var v1 = IntArray(s2.length + 1)

        for (i in 0 until s1.length) {
            v1[0] = i + 1
            var minV1 = v1[0]
            for (j in 0 until s2.length) {
                val cost = if (s1[i] == s2[j]) 0 else 1
                v1[j + 1] = minOf(v1[j] + 1, v0[j + 1] + 1, v0[j] + cost)
                if (v1[j + 1] < minV1) minV1 = v1[j + 1]
            }
            if (minV1 > 2) return 3 // Early exit if we already exceed max allowed distance
            val temp = v0
            v0 = v1
            v1 = temp
        }
        return v0[s2.length]
    }

    private suspend fun findClosestIngredient(scannedWord: String): String = withContext(Dispatchers.IO) {
        val word = scannedWord.trim().lowercase()
        if (word.isEmpty()) return@withContext scannedWord

        val firstChar = word.firstOrNull()?.toString() ?: return@withContext word
        val minLen = maxOf(1, word.length - 2)
        val maxLen = word.length + 2

        val candidates = mutableSetOf<String>()
        val args = arrayOf("$firstChar%", minLen.toString(), maxLen.toString())

        // 1. Room DB (purely_db)
        context.getDatabasePath("purely_db")?.let { path ->
            if (path.exists()) {
                try {
                    android.database.sqlite.SQLiteDatabase.openDatabase(path.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery("SELECT name FROM chemicals WHERE name LIKE ? AND length(name) BETWEEN ? AND ?", args).use { cursor ->
                            while (cursor.moveToNext()) {
                                cursor.getString(0)?.let { candidates.add(it.lowercase()) }
                            }
                        }
                    }
                } catch (e: Exception) { Log.e(TAG, "Error loading chemicals dict", e) }
            }
        }

        // 2. MasterUnifiedDB (via FoodDatabaseHelper)
        try {
            val db = foodDbHelper.readableDatabase
            db.rawQuery("SELECT name FROM UnifiedIngredients WHERE name LIKE ? AND length(name) BETWEEN ? AND ?", args).use { c ->
                while (c.moveToNext()) c.getString(0)?.let { candidates.add(it.lowercase()) }
            }
        } catch (e: Exception) { Log.e(TAG, "Error loading master dict", e) }

        if (word in candidates) return@withContext word

        var bestMatch = word
        var minDistance = Int.MAX_VALUE

        for (candidate in candidates) {
            ensureActive()
            val dist = levenshteinDistance(word, candidate)
            if (dist < minDistance && dist <= 2) {
                minDistance = dist
                bestMatch = candidate
            }
        }
        return@withContext bestMatch
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
        val words = s.split(Regex("[\\s-]+")).filter { it.isNotEmpty() && it !in fillers }
        return words.joinToString(" ")
    }

    private fun parseIngredients(text: String): List<String> {
        val list = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (char in text) {
            when (char) {
                '(', '[', '{' -> {
                    depth++
                    current.append(char)
                }
                ')', ']', '}' -> {
                    depth--
                    if (depth < 0) depth = 0
                    current.append(char)
                }
                ',' -> {
                    if (depth == 0) {
                        list.add(current.toString().trim())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotBlank()) {
            list.add(current.toString().trim())
        }
        return list
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

        val normalizedInput = preNormalizedText.lowercase()

        // Local Keyword Matcher (Offline Mode)
        Log.d(TAG, "Using local keyword matcher")
        val scanResults = mutableListOf<ChemicalEntity>()
        val allLocal = chemicalDao.getAllChemicals().firstOrNull() ?: emptyList()

        val rawIngredientsList = parseIngredients(preNormalizedText).filter { it.isNotEmpty() }
        
        // Fuzzy Matching Correction
        val ingredientsList = rawIngredientsList.map { rawWord ->
             ensureActive()
             findClosestIngredient(rawWord)
        }

        // Substring and fuzzy matching loops
        for (i in ingredientsList.indices) {
            ensureActive()
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
                    val chemResult = foodDbHelper.getIngredientDetails(candidate)
                    if (chemResult.isFailure) {
                        throw chemResult.exceptionOrNull() ?: Exception("Database error")
                    }
                    val chem = chemResult.getOrNull()
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
            for (chemical in allLocal) {
                val chemName = chemical.name.lowercase()
                if (preNormalizedText.contains(chemName)) {
                    if (!scanResults.any { it.name == chemical.name }) {
                        scanResults.add(chemical)
                    }
                }
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
