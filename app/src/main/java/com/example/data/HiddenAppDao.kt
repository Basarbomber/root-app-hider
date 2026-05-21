package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenAppDao {
    @Query("SELECT * FROM hidden_apps ORDER BY hiddenAt DESC")
    fun getAllHiddenApps(): Flow<List<HiddenAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenApp(app: HiddenAppEntity)

    @Query("DELETE FROM hidden_apps WHERE packageName = :packageName")
    suspend fun deleteHiddenApp(packageName: String)

    @Query("SELECT * FROM hidden_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getHiddenApp(packageName: String): HiddenAppEntity?
}
