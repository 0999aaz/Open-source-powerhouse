package com.example.ghtrending.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "repos")
data class RepoEntity(
    @PrimaryKey
    val id: Long,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val forks: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: String?,
    @ColumnInfo(name = "pushed_at")
    val pushedAt: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String?,
    @ColumnInfo(name = "readme_html")
    val readmeHtml: String? = null,
    @ColumnInfo(name = "readme_cached_at")
    val readmeCachedAt: Long? = null,
)

@Entity(
    tableName = "snapshots",
    indices = [
        Index(value = ["period", "language_key", "rank"]),
        Index(value = ["repo_id"]),
    ],
)
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "repo_id")
    val repoId: Long,
    val period: String,
    @ColumnInfo(name = "language_key")
    val languageKey: String,
    val rank: Int,
    @ColumnInfo(name = "period_stars")
    val periodStars: Int,
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,
)

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "board_key")
    val boardKey: String,
    @ColumnInfo(name = "next_cursor")
    val nextCursor: String?,
    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "repo_id")
    val repoId: Long,
    @ColumnInfo(name = "bookmarked_at")
    val bookmarkedAt: Long,
)

@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey
    val code: String,
    val label: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

data class TrendingRepoRow(
    val id: Long,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    @ColumnInfo(name = "period_stars")
    val periodStars: Int,
    val rank: Int,
    @ColumnInfo(name = "is_bookmarked")
    val isBookmarked: Boolean,
)

data class BookmarkedRepoRow(
    val id: Long,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val forks: Int,
    @ColumnInfo(name = "bookmarked_at")
    val bookmarkedAt: Long,
)
