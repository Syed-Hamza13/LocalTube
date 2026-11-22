package dev.hamza.localtube.util

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.hamza.localtube.LocalTubeApp
import dev.hamza.localtube.di.ServiceLocator

/**
 * Lets `viewModelFactory { initializer { ... } }` reach the hand-written graph without a DI
 * framework: the Application is always in CreationExtras for view models scoped to a ViewModelStoreOwner.
 */
val CreationExtras.appContext: Context
    get() = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
        ?: error("No Application in CreationExtras")

val CreationExtras.services: ServiceLocator
    get() = (appContext.applicationContext as LocalTubeApp).services
