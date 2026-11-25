package com.example.ghtrending.data.repo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.ghtrending.data.local.AppDatabase
import com.example.ghtrending.data.local.RemoteKeyEntity
import com.example.ghtrending.data.local.RepoEntity
import com.example.ghtrending.data.local.SnapshotEntity
import com.example.ghtrending.data.local.TrendingRepoRow
import com.example.ghtrending.data.remote.ApiFailure
import com.example.ghtrending.data.remote.GhTrendingApi
import com.example.ghtrending.data.remote.toApiFailure
import kotlinx.serialization.json.Json

@OptIn(ExperimentalPagingApi::class)
class TrendingRemoteMediator(
    private val period: String,
    private val language: String?,
    private val api: GhTrendingApi,
    private val database: AppDatabase,
    private val json: Json,
    private val onSyncStatusChanged: (isOffline: Boolean, rateLimited: Boolean) -> Unit,
) : RemoteMediator<Int, TrendingRepoRow>() {

    private val languageKey: String = language?.lowercase().orEmpty()
    private val boardKey: String = "$period:${languageKey.ifEmpty { "all" }}"

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, TrendingRepoRow>,
    ): MediatorResult {
        val cursor: String? = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val remoteKey = database.remoteKeyDao().getKey(boardKey)
                remoteKey?.nextCursor
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return try {
            val response = api.getTrending(
                period = period,
                language = language,
                cursor = cursor,
                limit = state.config.pageSize.coerceAtMost(50),
            )
            val now = System.currentTimeMillis()
            val repoEntities = response.items.map { item ->
                RepoEntity(
                    id = item.id,
                    fullName = item.fullName,
                    description = item.description,
                    language = item.language,
                    stars = item.stars,
                    forks = 0,
                    createdAt = null,
                    pushedAt = null,
                    updatedAt = null,
                )
            }
            val snapshotEntities = response.items.map { item ->
                SnapshotEntity(
                    repoId = item.id,
                    period = period,
                    languageKey = languageKey,
                    rank = item.rank,
                    periodStars = item.periodStars,
                    capturedAt = now,
                )
            }

            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    database.snapshotDao().clearBoard(period, languageKey)
                }
                database.repoDao().upsertFromTrending(repoEntities)
                database.snapshotDao().insertAll(snapshotEntities)
                database.remoteKeyDao().upsert(
                    RemoteKeyEntity(
                        boardKey = boardKey,
                        nextCursor = response.nextCursor,
                        lastUpdatedAt = now,
                    )
                )
            }
            onSyncStatusChanged(false, false)
            MediatorResult.Success(endOfPaginationReached = response.nextCursor == null)
        } catch (t: Throwable) {
            val failure = t.toApiFailure(json)
            val isRateLimited = failure is ApiFailure.RateLimited
            onSyncStatusChanged(!isRateLimited, isRateLimited)
            MediatorResult.Error(failure)
        }
    }
}
