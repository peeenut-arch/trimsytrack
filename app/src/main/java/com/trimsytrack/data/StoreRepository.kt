package com.trimsytrack.data

import com.trimsytrack.data.dao.StoreDao
import com.trimsytrack.data.entities.StoreEntity
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow

class StoreRepository(
    private val storeDao: StoreDao,
    private val regionRepository: RegionRepository,
    private val settings: SettingsStore,
) {
    suspend fun ensureRegionLoaded(regionCode: String) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        val region = regionRepository.loadRegion(regionCode)

        // Preserve favorites when reloading/updating a region.
        val existingFavorites = storeDao.getByRegion(profileId, regionCode)
            .associate { it.id to it.isFavorite }

        val stores = region.stores.map {
            StoreEntity(
                profileId = profileId,
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
        storeDao.deleteByRegion(profileId, regionCode)
        storeDao.upsertAll(stores)
    }

    suspend fun getStore(id: String): StoreEntity? {
        val profileId = settings.profileId.first().ifBlank { "default" }
        return storeDao.getById(profileId, id)
    }

    suspend fun setActiveStores(storeIds: List<String>) {
        val profileId = settings.profileId.first().ifBlank { "default" }
        storeDao.deactivateAll(profileId)
        if (storeIds.isNotEmpty()) storeDao.activateByIds(profileId, storeIds)
    }

    suspend fun getActiveStores(): List<StoreEntity> {
        val profileId = settings.profileId.first().ifBlank { "default" }
        return storeDao.getActive(profileId)
    }

    fun observeAllStores(): Flow<List<StoreEntity>> {
        return settings.profileId
            .map { it.ifBlank { "default" } }
            .flatMapLatest { pid -> storeDao.observeAll(pid) }
    }
}
