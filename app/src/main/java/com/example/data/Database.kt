package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Index
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "chemicals",
    indices = [
        Index(value = ["name"]),
        Index(value = ["displayName"])
    ]
)
data class ChemicalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // normalized lowercase
    val displayName: String,
    val plainEnglishName: String,
    val purpose: String,
    val riskLevel: String, // "HIGH", "MODERATE", "LOW"
    val riskDescription: String,
    val dietarySafety: String, // comma separated list like "vegan,gluten_free" or specialty tags
    val isPfas: Boolean = false
)

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productName: String,
    val rawIngredients: String,
    val score: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
@JvmSuppressWildcards
interface ChemicalDao {
    @Query("SELECT * FROM chemicals ORDER BY displayName ASC")
    fun getAllChemicals(): Flow<List<ChemicalEntity>>

    @Query("SELECT * FROM chemicals WHERE name = :normalizedName LIMIT 1")
    suspend fun getChemicalByName(normalizedName: String): ChemicalEntity?

    @Query("SELECT * FROM chemicals WHERE name LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%'")
    fun searchChemicals(query: String): Flow<List<ChemicalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChemical(chemical: ChemicalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChemicals(chemicals: List<ChemicalEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM chemicals")
    suspend fun getCount(): Int
}

@Dao
@JvmSuppressWildcards
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScanHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ScanHistoryEntity): Long

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int): Int

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory(): Int
}

@Database(entities = [ChemicalEntity::class, ScanHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chemicalDao(): ChemicalDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purely_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
