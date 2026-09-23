package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.GalleryPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryPhotoDao {

    @Query("SELECT * FROM gallery_photos ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<GalleryPhotoEntity>>

    @Query("SELECT * FROM gallery_photos WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): GalleryPhotoEntity?

    @Upsert
    suspend fun upsertAll(items: List<GalleryPhotoEntity>)

    @Query("DELETE FROM gallery_photos WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM gallery_photos")
    suspend fun clearAll()
}
