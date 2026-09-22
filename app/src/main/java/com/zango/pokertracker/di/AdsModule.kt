package com.zango.pokertracker.di

import android.content.Context
import android.content.SharedPreferences
import com.zango.pokertracker.ads.AdPrivacy
import com.zango.pokertracker.ads.ConsentManager
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** The ads layer's own preferences, kept apart from anything the app itself stores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AdsPreferences

@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    @Provides
    @Singleton
    fun provideConsentInformation(@ApplicationContext context: Context): ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /** Settings reaches the consent choice through this, never through the SDK directly. */
    @Provides
    fun provideAdPrivacy(manager: ConsentManager): AdPrivacy = manager

    @Provides
    @Singleton
    @AdsPreferences
    fun provideAdsPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("ads", Context.MODE_PRIVATE)
}
