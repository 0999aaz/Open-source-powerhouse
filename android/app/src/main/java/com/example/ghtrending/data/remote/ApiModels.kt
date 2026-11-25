package com.example.ghtrending.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LanguageDto(
    val code: String,
    val label: String,
)

@Serializable
data class LanguagesResponseDto(
    val items: List<LanguageDto>,
)

@Serializable
data class TrendingRepoDto(
    val id: Long,
    @SerialName("full_name")
    val fullName: String,
    val description: String? = null,
    val language: String? = null,
    val stars: Int,
    @SerialName("period_stars")
    val periodStars: Int,
    val rank: Int,
)

@Serializable
data class TrendingResponseDto(
    val items: List<TrendingRepoDto>,
    @SerialName("next_cursor")
    val nextCursor: String? = null,
)

@Serializable
data class RepoDetailDto(
    val id: Long,
    @SerialName("full_name")
    val fullName: String,
    val description: String? = null,
    val language: String? = null,
    val stars: Int,
    val forks: Int,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("pushed_at")
    val pushedAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

@Serializable
data class RepoReadmeDto(
    @SerialName("repo_id")
    val repoId: Long,
    val html: String,
)

@Serializable
data class ApiErrorDetailDto(
    val code: String,
    val message: String,
    @SerialName("min_version")
    val minVersion: String? = null,
    @SerialName("download_url")
    val downloadUrl: String? = null,
)

@Serializable
data class ApiErrorEnvelopeDto(
    val error: ApiErrorDetailDto,
)
