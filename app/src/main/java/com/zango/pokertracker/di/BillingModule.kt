package com.zango.pokertracker.di

import android.content.Context
import android.content.SharedPreferences
import com.zango.pokertracker.billing.BillingManager
import com.zango.pokertracker.billing.RemoveAdsBilling
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Billing's own preferences: the last entitlement Play confirmed, nothing else. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingPreferences

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    /** Screens see the purchase through the interface; the ads layer uses the manager itself. */
    @Binds
    abstract fun bindRemoveAdsBilling(manager: BillingManager): RemoveAdsBilling

    companion object {
        @Provides
        @Singleton
        @BillingPreferences
        fun provideBillingPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("billing", Context.MODE_PRIVATE)
    }
}
