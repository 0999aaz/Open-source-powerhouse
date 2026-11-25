package com.example.ghtrending.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM repos WHERE id = :repoId LIMIT 1")
    fun observeRepo(repoId: Long): Flow<RepoEntity?>

    @Query("SELECT * FROM repos WHERE id = :repoId LIMIT 1")
    suspend fun getRepo(repoId: Long): RepoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(repos: List<RepoEntity>)

    @Query(
        """
        UPDATE repos
        SET full_name = :fullName,
            description = COALESCE(:description, description),
            language = COALESCE(:language, language),
            stars = :stars
        WHERE id = :id
        """
    )
    suspend fun updateTrendingFields(
        id: Long,
        fullName: String,
        description: String?,
        language: String?,
        stars: Int,
    )

    @Transaction
    suspend fun upsertFromTrending(repos: List<RepoEntity>) {
        insertIgnore(repos)
        repos.forEach { repo ->
            updateTrendingFields(
                id = repo.id,
                fullName = repo.fullName,
                description = repo.description,
                language = repo.language,
                stars = repo.stars,
            )
        }
    }

    @Query(
        """
        UPDATE repos
        SET full_name = :fullName,
            description = :description,
            language = :language,
            stars = :stars,
            forks = :forks,
            created_at = :createdAt,
            pushed_at = :pushedAt,
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateDetailFields(
        id: Long,
        fullName: String,
        description: String?,
        language: String?,
        stars: Int,
        forks: Int,
        createdAt: String?,
        pushedAt: String?,
        updatedAt: String?,
    ): Int

    @Transaction
    suspend fun upsertDetail(entity: RepoEntity) {
        val updated = updateDetailFields(
            id = entity.id,
            fullName = entity.fullName,
            description = entity.description,
            language = entity.language,
            stars = entity.stars,
            forks = entity.forks,
            createdAt = entity.createdAt,
            pushedAt = entity.pushedAt,
            updatedAt = entity.updatedAt,
        )
        if (updated == 0) {
            insertIgnore(listOf(entity))
        }
    }

    @Query(
        """
        UPDATE repos
        SET readme_html = :readmeHtml,
            readme_cached_at = :cachedAt
        WHERE id = :repoId
        """
    )
    suspend fun updateReadme(repoId: Long, readmeHtml: String, cachedAt: Long)
}

@Dao
interface SnapshotDao {
    @Query(
        """
        SELECT r.id AS id,
               r.full_name AS full_name,
               r.description AS description,
               r.language AS language,
               r.stars AS stars,
               s.period_stars AS period_stars,
               s.rank AS rank,
               CASE WHEN b.repo_id IS NOT NULL THEN 1 ELSE 0 END AS is_bookmarked
        FROM snapshots s
        INNER JOIN repos r ON r.id = s.repo_id
        LEFT JOIN bookmarks b ON b.repo_id = r.id
        WHERE s.period = :period AND s.language_key = :languageKey
        ORDER BY s.rank ASC, r.id ASC
        """
    )
    fun pagingSource(period: String, languageKey: String): PagingSource<Int, TrendingRepoRow>

    @Query("SELECT COUNT(*) FROM snapshots WHERE period = :period AND language_key = :languageKey")
    suspend fun countForBoard(period: String, languageKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<SnapshotEntity>)

    @Query("DELETE FROM snapshots WHERE period = :period AND language_key = :languageKey")
    suspend fun clearBoard(period: String, languageKey: String)
}

@Dao
interface RemoteKeyDao {
    @Query("SELECT * FROM remote_keys WHERE board_key = :boardKey LIMIT 1")
    suspend fun getKey(boardKey: String): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE board_key = :boardKey")
    suspend fun deleteKey(boardKey: String)
}

@Dao
interface BookmarkDao {
    @Query(
        """
        SELECT r.id AS id,
               r.full_name AS full_name,
               r.description AS description,
               r.language AS language,
               r.stars AS stars,
               r.forks AS forks,
               b.bookmarked_at AS bookmarked_at
        FROM bookmarks b
        INNER JOIN repos r ON r.id = b.repo_id
        ORDER BY b.bookmarked_at DESC
        """
    )
    fun observeBookmarkedRepos(): Flow<List<BookmarkedRepoRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE repo_id = :repoId)")
    fun observeIsBookmarked(repoId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE repo_id = :repoId)")
    suspend fun isBookmarked(repoId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE repo_id = :repoId")
    suspend fun deleteByRepoId(repoId: Long)
}

@Dao
interface LanguageDao {
    @Query("SELECT * FROM languages ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<LanguageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<LanguageEntity>)

    @Query("DELETE FROM languages")
    suspend fun clearAll()
}
