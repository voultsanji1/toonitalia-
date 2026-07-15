package com.toonitalia.app

import org.jsoup.Jsoup

object Scraper {
    private const val BASE_URL = "https://toonitalia.xyz"

    private fun getDoc(url: String): org.jsoup.nodes.Document {
        val html = NetworkModule.fetch(url)
        return Jsoup.parse(html)
    }

    fun scrapeHomepage(): List<CategorySection> {
        val doc = getDoc(BASE_URL)
        val sections = mutableListOf<CategorySection>()

        // Homepage uses custom layout: .col contains h2 headers and .item divs
        // Each .item has an <a class="card-link"> with <img> and <span class="title">
        val cols = doc.select(".col")
        for (col in cols) {
            val h2 = col.selectFirst("h2")
            val title = h2?.text()?.replace(Regex("^[🔥⚡🎬📺]\\s*"), "")?.trim() ?: continue
            if (title.isBlank()) continue

            val items = mutableListOf<ContentItem>()
            for (item in col.select(".item")) {
                val link = item.selectFirst("a[href]")
                val href = link?.attr("href") ?: continue
                val img = item.selectFirst("img")
                val imageUrl = img?.attr("src") ?: ""
                val itemTitle = item.selectFirst(".title")?.text()?.trim()
                    ?: link.text().trim()

                if (itemTitle.isNotBlank() && href.startsWith(BASE_URL) && href != BASE_URL) {
                    items.add(ContentItem(
                        title = itemTitle,
                        url = href,
                        image = imageUrl
                    ))
                }
            }
            if (items.isNotEmpty()) {
                sections.add(CategorySection(title = title, items = items))
            }
        }
        return sections
    }

    fun scrapeContentList(categorySlug: String): List<ContentItem> {
        val doc = getDoc("$BASE_URL/$categorySlug/")
        val items = mutableListOf<ContentItem>()
        val seen = mutableSetOf<String>()

        // Category pages use .catlist-box ul li a (text-only links, no images)
        for (a in doc.select(".catlist-box ul li a[href]")) {
            val href = a.attr("href")
            val title = a.text().trim()

            if (title.isNotBlank() && href.startsWith(BASE_URL) &&
                !seen.contains(href)
            ) {
                seen.add(href)
                items.add(ContentItem(
                    title = title,
                    url = href,
                    image = "",
                    categorySlug = categorySlug
                ))
            }
        }

        // Fetch OG images in background for first batch
        if (items.isNotEmpty()) {
            val toFetch = items.take(40)
            for (item in toFetch) {
                try {
                    val detailDoc = getDoc(item.url)
                    val ogImage = detailDoc.selectFirst("meta[property=og:image]")
                    val imageUrl = ogImage?.attr("content") ?: ""
                    if (imageUrl.isNotBlank()) {
                        val idx = items.indexOf(item)
                        if (idx >= 0) {
                            items[idx] = item.copy(image = imageUrl)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return items
    }

    fun scrapeDetail(url: String): ContentItem {
        val doc = getDoc(url)
        val result = mutableMapOf<String, String>()
        val episodes = mutableListOf<Episode>()

        val titleEl = doc.selectFirst("h1")
        result["title"] = titleEl?.text() ?: ""

        val content = doc.selectFirst("article .entry-content") ?: doc.selectFirst("article") ?: doc
        val text = content.text()

        val fields = mapOf(
            "Titolo originale" to "originalTitle",
            "Paese di origine" to "country",
            "Data di pubblicazione" to "year",
            "Stato Opera" to "status",
            "N. Episodi" to "totalEpisodes",
            "Episodi disponibili" to "availableEpisodes"
        )

        for ((field, key) in fields) {
            val pattern = Regex("$field\\s*:\\s*(.+)")
            pattern.find(text)?.let { result[key] = it.groupValues[1].trim() }
        }

        val img = content.selectFirst("img")
        val imageUrl = img?.attr("src") ?: ""

        val tramaMatch = Regex("Trama\\s*:\\s*(.+?)(?=Scegli Stagione|Episodi|\\$)", RegexOption.DOT_MATCHES_ALL)
            .find(text)
        val synopsis = tramaMatch?.groupValues?.get(1)?.trim() ?: ""

        // Parse episodes - format: "01 - Title - PLAYER1 - PLAYER2"
        // Look for links to uqload.is and chuckle-tube.com
        for (p in doc.select("p")) {
            val pText = p.text()
            val links = p.select("a[href]")
            if (links.isEmpty()) continue

            // Check if this paragraph contains streaming links
            val hasStreamingLink = links.any {
                val href = it.attr("href")
                href.contains("uqload.is") || href.contains("chuckle-tube.com")
            }
            if (!hasStreamingLink) continue

            // Extract episode number and title from the paragraph text
            // Format: "01 - Title - PLAYER1 - PLAYER2"
            val cleaned = pText.replace("PLAYER1", "").replace("PLAYER2", "").trim()
            val epMatch = Regex("^(\\d+)\\s*[–\\-]\\s*(.+?)\\s*[–\\-]\\s*$").find(cleaned)
                ?: Regex("^(\\d+)\\s*[–\\-]\\s*(.+)").find(cleaned)

            val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: (episodes.size + 1)
            val epTitle = epMatch?.groupValues?.get(2)?.trim() ?: "Episodio $epNum"

            // Use the first streaming link (uqload preferred, fallback to chuckle-tube)
            val streamingUrl = links.firstOrNull {
                it.attr("href").contains("uqload.is")
            }?.attr("href") ?: links.firstOrNull {
                it.attr("href").contains("chuckle-tube.com")
            }?.attr("href") ?: continue

            episodes.add(Episode(
                title = "Ep. $epNum - $epTitle",
                url = streamingUrl,
                season = 0,
                number = epNum
            ))
        }

        // Also try season tabs
        val seasons = doc.select("[id^=S]").map { it.id() }

        return ContentItem(
            title = result["title"] ?: "",
            url = url,
            image = imageUrl,
            synopsis = synopsis,
            originalTitle = result["originalTitle"] ?: "",
            country = result["country"] ?: "",
            year = result["year"] ?: "",
            status = result["status"] ?: "",
            totalEpisodes = result["totalEpisodes"] ?: "",
            availableEpisodes = result["availableEpisodes"] ?: "",
            episodes = episodes,
            seasons = seasons
        )
    }

    fun scrapeCategories(): Map<String, String> {
        return mapOf(
            "anime-ita" to "Anime Ita",
            "contatti" to "Anime Sub-Ita",
            "film-animazione" to "Film Animazione",
            "serie-tv" to "Serie Tv"
        )
    }

    fun search(query: String): List<ContentItem> {
        val results = mutableListOf<ContentItem>()
        val q = query.lowercase()
        for ((slug, _) in scrapeCategories()) {
            val items = scrapeContentList(slug)
            results.addAll(items.filter { it.title.lowercase().contains(q) })
        }
        return results
    }
}
