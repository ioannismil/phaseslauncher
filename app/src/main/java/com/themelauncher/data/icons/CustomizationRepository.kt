package com.themelauncher.data.icons

import com.themelauncher.data.db.AppCustomizationEntity
import com.themelauncher.data.db.CustomizationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Per-app custom names and hand-picked icons. */
class CustomizationRepository(private val dao: CustomizationDao) {

    val customizations: Flow<Map<String, AppCustomizationEntity>> =
        dao.observeAll().map { rows -> rows.associateBy { it.appKey } }

    /** Saves a name and/or icon; null fields mean "use the default". Removes the row when nothing is custom. */
    suspend fun set(appKey: String, label: String?, iconPack: String?, iconName: String?) {
        val cleanLabel = label?.trim()?.ifBlank { null }
        if (cleanLabel == null && (iconPack == null || iconName == null)) {
            dao.delete(appKey)
        } else {
            dao.upsert(AppCustomizationEntity(appKey, cleanLabel, iconPack, iconName))
        }
    }
}
