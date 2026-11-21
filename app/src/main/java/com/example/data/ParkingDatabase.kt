package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "parking_spots")
data class ParkingSpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val floorOrLevel: String,
    val notes: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val savedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val savedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface ParkingSpotDao {
    @Query("SELECT * FROM parking_spots ORDER BY savedAtMillis DESC")
    fun observeAll(): Flow<List<ParkingSpotEntity>>

    @Query("SELECT * FROM parking_spots WHERE id = :id")
    suspend fun getById(id: Long): ParkingSpotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spot: ParkingSpotEntity): Long

    @Update
    suspend fun update(spot: ParkingSpotEntity)

    @Query("DELETE FROM parking_spots WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY savedAtMillis DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: SavedPlaceEntity): Long

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [ParkingSpotEntity::class, SavedPlaceEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ArcCompassDatabase : RoomDatabase() {
    abstract fun parkingSpotDao(): ParkingSpotDao
    abstract fun savedPlaceDao(): SavedPlaceDao

    companion object {
        @Volatile
        private var instance: ArcCompassDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_places` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitudeMeters` REAL,
                        `savedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): ArcCompassDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArcCompassDatabase::class.java,
                    "arccompass_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class ParkingRepository(private val dao: ParkingSpotDao) {
    val allSpots: Flow<List<ParkingSpotEntity>> = dao.observeAll()

    suspend fun saveSpot(spot: ParkingSpotEntity): Long = dao.insert(spot)

    suspend fun updateSpot(spot: ParkingSpotEntity) = dao.update(spot)

    suspend fun deleteSpot(id: Long) = dao.deleteById(id)
}

class SavedPlacesRepository(private val dao: SavedPlaceDao) {
    val allPlaces: Flow<List<SavedPlaceEntity>> = dao.observeAll()

    suspend fun savePlace(place: SavedPlaceEntity): Long = dao.insert(place)

    suspend fun deletePlace(id: Long) = dao.deleteById(id)
}
