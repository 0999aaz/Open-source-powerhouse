package com.example.ghtrending.di

import android.content.Context
import androidx.room.Room
import com.example.ghtrending.data.local.AppDatabase
import com.example.ghtrending.data.local.BookmarkDao
import com.example.ghtrending.data.local.LanguageDao
import com.example.ghtrending.data.local.RemoteKeyDao
import com.example.ghtrending.data.local.RepoDao
import com.example.ghtrending.data.local.SnapshotDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ghtrending.db",
        ).build()

    @Provides
    fun provideRepoDao(db: AppDatabase): RepoDao = db.repoDao()

    @Provides
    fun provideSnapshotDao(db: AppDatabase): SnapshotDao = db.snapshotDao()

    @Provides
    fun provideRemoteKeyDao(db: AppDatabase): RemoteKeyDao = db.remoteKeyDao()

    @Provides
    fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideLanguageDao(db: AppDatabase): LanguageDao = db.languageDao()
}
