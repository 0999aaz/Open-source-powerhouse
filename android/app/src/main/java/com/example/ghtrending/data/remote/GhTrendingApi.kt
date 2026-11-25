package com.example.ghtrending.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GhTrendingApi {

    @GET("v1/languages")
    suspend fun getLanguages(): LanguagesResponseDto

    @GET("v1/trending")
    suspend fun getTrending(
        @Query("period") period: String,
        @Query("language") language: String?,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int = 20,
    ): TrendingResponseDto

    @GET("v1/repos/{id}")
    suspend fun getRepoDetail(
        @Path("id") repoId: Long,
    ): RepoDetailDto

    @GET("v1/repos/{id}/readme")
    suspend fun getRepoReadme(
        @Path("id") repoId: Long,
    ): RepoReadmeDto
}
