package com.gmp.offline

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import com.gmp.offline.push.NotificationChannels
import com.gmp.offline.sync.NetworkConnectivityObserver
import com.gmp.offline.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GmpApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var connectivityObserver: NetworkConnectivityObserver

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var imageLoader: ImageLoader

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(imageLoader)
        NotificationChannels.create(this)
        observeConnectivityForSync()
    }

    private fun observeConnectivityForSync() {
        applicationScope.launch {
            connectivityObserver.observe()
                .drop(1)
                .filter { isOnline -> isOnline }
                .collect { syncScheduler.triggerImmediateSync() }
        }
    }
}
