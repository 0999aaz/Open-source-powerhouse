package com.example.ghtrending.data.repo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.example.ghtrending.data.local.AppDatabase
import com.example.ghtrending.data.local.BookmarkEntity
import com.example.ghtrending.data.local.BookmarkedRepoRow
import com.example.ghtrending.data.local.LanguageEntity
import com.example.ghtrending.data.local.RepoEntity
import com.example.ghtrending.data.local.TrendingRepoRow
import com.example.ghtrending.data.remote.ApiFailure
import com.example.ghtrending.data.remote.GhTrendingApi
import com.example.ghtrending.data.remote.toApiFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class RepoDetailResult(
    val repo: RepoEntity?,
    val isOffline: Boolean,
    val error: ApiFailure?,
)

data class ReadmeResult(
    val html: String?,
    val isOffline: Boolean,
    val errorMessage: String?,
)

@Singleton
class TrendingRepository @Inject constructor(
    private val api: GhTrendingApi,
    private val database: AppDatabase,
    private val json: Json,
) {

    @OptIn(ExperimentalPagingApi::class)
    fun pagedTrending(
        period: String,
        language: String?,
        onSyncStatusChanged: (isOffline: Boolean, rateLimited: Boolean) -> Unit,
    ): Flow<PagingData<TrendingRepoRow>> {
        val languageKey = language?.lowercase().orEmpty()
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false,
            ),
            remoteMediator = TrendingRemoteMediator(
                period = period,
                language = language,
                api = api,
                database = database,
                json = json,
                onSyncStatusChanged = onSyncStatusChanged,
            ),
            pagingSourceFactory = {
                database.snapshotDao().pagingSource(period, languageKey)
            },
        ).flow
    }

    fun observeLanguages(): Flow<List<LanguageEntity>> =
        database.languageDao().observeAll()

    suspend fun refreshLanguages() {
        runCatching {
            val response = api.getLanguages()
            val entities = response.items.mapIndexed { index, item ->
                LanguageEntity(
                    code = item.code,
                    label = item.label,
                    sortOrder = index,
                )
            }
            database.withTransaction {
                database.languageDao().clearAll()
                database.languageDao().replaceAll(entities)
            }
        }
    }

    fun observeRepo(repoId: Long): Flow<RepoEntity?> =
        database.repoDao().observeRepo(repoId)

    suspend fun fetchRepoDetail(repoId: Long): RepoDetailResult {
        val cached = database.repoDao().getRepo(repoId)
        return try {
            val dto = api.getRepoDetail(repoId)
            val entity = RepoEntity(
                id = dto.id,
                fullName = dto.fullName,
                description = dto.description,
                language = dto.language,
                stars = dto.stars,
                forks = dto.forks,
                createdAt = dto.createdAt,
                pushedAt = dto.pushedAt,
                updatedAt = dto.updatedAt,
                readmeHtml = cached?.readmeHtml,
                readmeCachedAt = cached?.readmeCachedAt,
            )
            database.repoDao().upsertDetail(entity)
            RepoDetailResult(
                repo = database.repoDao().getRepo(repoId) ?: entity,
                isOffline = false,
                error = null,
            )
        } catch (t: Throwable) {
            val failure = t.toApiFailure(json)
            RepoDetailResult(
                repo = cached,
                isOffline = cached != null,
                error = failure,
            )
        }
    }

    suspend fun fetchRepoReadme(repoId: Long): ReadmeResult {
        val cached = database.repoDao().getRepo(repoId)
        return try {
            val dto = api.getRepoReadme(repoId)
            database.repoDao().updateReadme(
                repoId = repoId,
                readmeHtml = dto.html,
                cachedAt = System.currentTimeMillis(),
            )
            ReadmeResult(
                html = dto.html,
                isOffline = false,
                errorMessage = null,
            )
        } catch (t: Throwable) {
            val failure = t.toApiFailure(json)
            val cachedHtml = cached?.readmeHtml
            if (!cachedHtml.isNullOrBlank()) {
                ReadmeResult(
                    html = cachedHtml,
                    isOffline = true,
                    errorMessage = null,
                )
            } else {
                ReadmeResult(
                    html = null,
                    isOffline = false,
                    errorMessage = when (failure) {
                        is ApiFailure.RateLimited -> failure.message
                        else -> "README 暂不可用，请稍后重试"
                    },
                )
            }
        }
    }

    fun observeBookmarks(): Flow<List<BookmarkedRepoRow>> =
        database.bookmarkDao().observeBookmarkedRepos()

    fun observeIsBookmarked(repoId: Long): Flow<Boolean> =
        database.bookmarkDao().observeIsBookmarked(repoId)

    suspend fun toggleBookmark(repoId: Long) {
        val currentlyBookmarked = database.bookmarkDao().isBookmarked(repoId)
        if (currentlyBookmarked) {
            database.bookmarkDao().deleteByRepoId(repoId)
        } else {
            database.bookmarkDao().insert(
                BookmarkEntity(
                    repoId = repoId,
                    bookmarkedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun removeBookmark(repoId: Long) {
        database.bookmarkDao().deleteByRepoId(repoId)
    }
}
