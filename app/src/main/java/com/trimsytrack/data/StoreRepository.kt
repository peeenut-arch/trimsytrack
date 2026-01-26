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
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        val region = regionRepository.loadRegion(regionCode)

        // Preserve per-store flags when reloading/updating a region.
        val existingById = storeDao.getByRegion(uid, regionCode)
            .associateBy { it.id }

        val stores = region.stores.map {
            StoreEntity(
                uid = uid,
                id = it.id,
                name = it.name,
                lat = it.lat,
                lng = it.lng,
                radiusMeters = it.radiusMeters,
                regionCode = region.regionCode,
                city = it.city.ifBlank { region.regionName },
                isActive = existingById[it.id]?.isActive ?: false,
                isFavorite = existingById[it.id]?.isFavorite ?: false,
            )
        }

        // Refresh to pick up region JSON updates (e.g., new test pins) without requiring a full app data clear.
        storeDao.deleteByRegion(uid, regionCode)
        storeDao.upsertAll(stores)
    }

    suspend fun getStore(id: String): StoreEntity? {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return null
        return storeDao.getById(uid, id)
    }

    suspend fun setActiveStores(storeIds: List<String>) {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        storeDao.deactivateAll(uid)
        if (storeIds.isNotEmpty()) storeDao.activateByIds(uid, storeIds)
    }

    /** Marks a single store as active without deactivating other stores. */
    suspend fun activateStore(storeId: String) {
        val id = storeId.trim()
        if (id.isBlank()) return
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        storeDao.activateByIds(uid, listOf(id))
    }

    suspend fun deleteStore(storeId: String) {
        val id = storeId.trim()
        if (id.isBlank()) return
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return
        storeDao.deleteByIds(uid, listOf(id))
    }

    suspend fun getActiveStores(): List<StoreEntity> {
        val uid = settings.uidOrEmpty()
        if (uid.isBlank()) return emptyList()
        return storeDao.getActive(uid)
    }

    fun observeAllStores(): Flow<List<StoreEntity>> {
        return settings.uid
            .flatMapLatest { uid ->
                if (uid.isBlank()) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    storeDao.observeAll(uid)
                }
            }
    }
}
