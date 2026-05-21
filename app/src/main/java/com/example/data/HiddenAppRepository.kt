package com.example.data

import kotlinx.coroutines.flow.Flow

class HiddenAppRepository(private val hiddenAppDao: HiddenAppDao) {
    val allHiddenApps: Flow<List<HiddenAppEntity>> = hiddenAppDao.getAllHiddenApps()

    suspend fun insert(app: HiddenAppEntity) {
        hiddenAppDao.insertHiddenApp(app)
    }

    suspend fun delete(packageName: String) {
        hiddenAppDao.deleteHiddenApp(packageName)
    }

    suspend fun getHiddenApp(packageName: String): HiddenAppEntity? {
        return hiddenAppDao.getHiddenApp(packageName)
    }
}
