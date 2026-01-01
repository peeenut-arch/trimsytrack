package com.trimsytrack.data

import com.trimsytrack.data.dao.StoreDao
import com.trimsytrack.data.entities.StoreEntity
import kotlinx.coroutines.flow.Flow

class StoreRepository(
    private val storeDao: StoreDao,
    private val regionRepository: RegionRepository,
) {
    suspend fun ensureRegionLoaded(regionCode: String) {
        val region = regionRepository.loadRegion(regionCode)

        // Preserve favorites when reloading/updating a region.
        val existingFavorites = storeDao.getByRegion(regionCode)
            .associate { it.id to it.isFavorite }

        val stores = region.stores.map {
            StoreEntity(
                id = it.id,
                name = it.name,
                lat = it.lat,
                lng = it.lng,
                radiusMeters = it.radiusMeters,
                regionCode = region.regionCode,
                city = it.city.ifBlank { region.regionName },
                isActive = false,
                isFavorite = existingFavorites[it.id] ?: false,
            )
        }

        // Refresh to pick up region JSON updates (e.g., new test pins) without requiring a full app data clear.
        storeDao.deleteByRegion(regionCode)
        storeDao.upsertAll(stores)
    }

    suspend fun getStore(id: String): StoreEntity? = storeDao.getById(id)

    suspend fun setActiveStores(storeIds: List<String>) {
        storeDao.deactivateAll()
        if (storeIds.isNotEmpty()) storeDao.activateByIds(storeIds)
    }

    suspend fun getActiveStores(): List<StoreEntity> = storeDao.getActive()

    fun observeAllStores(): Flow<List<StoreEntity>> = storeDao.observeAll()
}
