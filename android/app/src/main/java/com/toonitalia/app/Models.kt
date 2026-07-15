package com.toonitalia.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContentItem(
    val title: String = "",
    val url: String = "",
    val image: String = "",
    val category: String = "",
    val categorySlug: String = "",
    val thumbnail: String = "",
    val synopsis: String = "",
    val originalTitle: String = "",
    val country: String = "",
    val year: String = "",
    val status: String = "",
    val totalEpisodes: String = "",
    val availableEpisodes: String = "",
    val episodes: List<Episode> = emptyList(),
    val seasons: List<String> = emptyList()
) : Parcelable

@Parcelize
data class Episode(
    val title: String = "",
    val url: String = "",
    val season: Int = 0,
    val number: Int = 0
) : Parcelable

data class CategorySection(
    val title: String,
    val items: List<ContentItem>
)
