package com.gmp.offline.di

import android.content.Context
import androidx.room.Room
import com.gmp.offline.data.local.GmpDatabase
import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.dao.PendingOperationDao
import com.gmp.offline.data.local.dao.StaffDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GmpDatabase =
        Room.databaseBuilder(context, GmpDatabase::class.java, "gmp-offline.db")
            // Fase 4: todavía no hay Migrations definidas (versión 1, recién
            // nacida). Si cambia el esquema en fases futuras, agregar una
            // Migration explícita en vez de esto.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideJobDao(db: GmpDatabase): JobDao = db.jobDao()

    @Provides
    fun provideJobWorkerDao(db: GmpDatabase): JobWorkerDao = db.jobWorkerDao()

    @Provides
    fun provideMaterialDao(db: GmpDatabase): MaterialDao = db.materialDao()

    @Provides
    fun provideJobMaterialDao(db: GmpDatabase): JobMaterialDao = db.jobMaterialDao()

    @Provides
    fun provideJobPhotoDao(db: GmpDatabase): JobPhotoDao = db.jobPhotoDao()

    @Provides
    fun provideStaffDao(db: GmpDatabase): StaffDao = db.staffDao()

    @Provides
    fun providePendingOperationDao(db: GmpDatabase): PendingOperationDao = db.pendingOperationDao()
}
