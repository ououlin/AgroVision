package com.agrovision.app.di

import android.content.Context
import androidx.room.Room
import com.agrovision.app.data.local.dao.FeedbackDao
import com.agrovision.app.data.local.dao.HistoryDao
import com.agrovision.app.data.local.dao.PlotDao
import com.agrovision.app.data.local.dao.TreatmentDao
import com.agrovision.app.data.local.database.AppDatabase
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "agrovision.db"
        )
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(
        database: AppDatabase
    ): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideFeedbackDao(
        database: AppDatabase
    ): FeedbackDao {
        return database.feedbackDao()
    }

    @Provides
    @Singleton
    fun providePlotDao(
        database: AppDatabase
    ): PlotDao {
        return database.plotDao()
    }

    @Provides
    @Singleton
    fun provideTreatmentDao(
        database: AppDatabase
    ): TreatmentDao {
        return database.treatmentDao()
    }
}
