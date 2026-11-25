package com.example.ghtrending.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RepoEntity::class,
        SnapshotEntity::class,
        RemoteKeyEntity::class,
        BookmarkEntity::class,
        LanguageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun languageDao(): LanguageDao
}
