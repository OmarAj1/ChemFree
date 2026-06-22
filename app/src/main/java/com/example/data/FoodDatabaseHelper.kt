package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import android.util.Log

class FoodDatabaseHelper(private val context: Context) {

    companion object {
        const val DB_NAME = "MasterUnifiedDB.db"
        const val DB_VERSION = 4
        private const val TAG = "FoodDatabaseHelper"

        @Volatile
        private var _cachedDb: SQLiteDatabase? = null

        fun getDatabase(context: Context): SQLiteDatabase {
            var db = _cachedDb
            if (db != null && db.isOpen) {
                return db
            }
            synchronized(this) {
                db = _cachedDb
                if (db != null && db.isOpen) {
                    return db!!
                }
                
                checkAndCopyDatabase(context)
                val dbPath = context.getDatabasePath(DB_NAME)
                db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
                _cachedDb = db
                return db!!
            }
        }

        private fun isDatabaseValid(context: Context, dbPath: File): Boolean {
            if (!dbPath.exists()) return false
            try {
                SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { checkDb ->
                    checkDb.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='UnifiedIngredients'", null).use { cursor ->
                        if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                return false
            }
            return false
        }

        private fun checkAndCopyDatabase(context: Context) {
            val dbPath = context.getDatabasePath(DB_NAME)
            val prefs = context.getSharedPreferences("database_prefs", Context.MODE_PRIVATE)
            val copiedVersion = prefs.getInt("db_version", 0)

            if (!dbPath.exists() || copiedVersion < DB_VERSION || !isDatabaseValid(context, dbPath)) {
                Log.d(TAG, "Copying database. Old valid: ${isDatabaseValid(context, dbPath)}, version: $copiedVersion")
                if (dbPath.exists()) {
                    dbPath.delete()
                    File(dbPath.path + "-journal").delete()
                    File(dbPath.path + "-shm").delete()
                    File(dbPath.path + "-wal").delete()
                }
                dbPath.parentFile?.mkdirs()
                try {
                    context.assets.open("databases/$DB_NAME").use { inputStream ->
                        FileOutputStream(dbPath).use { outputStream ->
                            inputStream.copyTo(outputStream)
                            outputStream.flush()
                        }
                    }
                    prefs.edit().putInt("db_version", DB_VERSION).apply()
                    Log.d(TAG, "Successfully copied database from assets.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy database: ${e.message}", e)
                }
            }
        }
    }

    val readableDatabase: SQLiteDatabase
        get() = getDatabase(context)

    fun getIngredientDetails(foodName: String): Result<ChemicalEntity?> {
        val queryWord = foodName.trim().lowercase()
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val db = readableDatabase
                db.rawQuery(
                    "SELECT name, description, category, dietary_safety, purpose, health_risks, risk_level, dietary_info, plain_english_name FROM UnifiedIngredients WHERE name LIKE ? OR ? LIKE '%' || name || '%' ORDER BY length(name) DESC LIMIT 1",
                    arrayOf("%$queryWord%", queryWord)
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return Result.success(ChemicalEntity(
                            name = cursor.getString(0) ?: foodName,
                            displayName = (cursor.getString(8) ?: cursor.getString(0) ?: foodName).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            plainEnglishName = cursor.getString(8) ?: "Food Component",
                            purpose = cursor.getString(4) ?: cursor.getString(1) ?: "Analyzed nutrient profile or food component.",
                            riskLevel = cursor.getString(6) ?: "LOW",
                            riskDescription = cursor.getString(5) ?: "Validated in local nutrition database.",
                            dietarySafety = cursor.getString(3) ?: cursor.getString(7) ?: ""
                        ))
                    }
                    return Result.success(null)
                }
            } catch (e: android.database.sqlite.SQLiteDatabaseLockedException) {
                Log.w(TAG, "Database locked, retrying attempt $attempt")
                lastException = e
                Thread.sleep(100L * attempt)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying database for $foodName: ${e.message}")
                return Result.failure(e)
            }
        }
        return Result.failure(lastException ?: Exception("Database locked after 3 retries"))
    }

    fun searchFoods(query: String): List<ChemicalEntity> {
        val db = readableDatabase
        val results = mutableListOf<ChemicalEntity>()
        try {
            db.rawQuery("SELECT name, description, category, dietary_safety, purpose, health_risks, risk_level, dietary_info, plain_english_name FROM UnifiedIngredients WHERE name LIKE ? LIMIT 50", arrayOf("%$query%")).use { cursor ->
                while (cursor.moveToNext()) {
                    val chem = ChemicalEntity(
                        name = cursor.getString(0) ?: query,
                        displayName = (cursor.getString(8) ?: cursor.getString(0) ?: query).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        plainEnglishName = cursor.getString(8) ?: "Food Component",
                        purpose = cursor.getString(4) ?: cursor.getString(1) ?: "Analyzed nutrient profile or food component.",
                        riskLevel = cursor.getString(6) ?: "LOW",
                        riskDescription = cursor.getString(5) ?: "Validated in local nutrition database.",
                        dietarySafety = cursor.getString(3) ?: cursor.getString(7) ?: ""
                    )
                    results.add(chem)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching database for $query: ${e.message}")
        }
        return results
    }
}
