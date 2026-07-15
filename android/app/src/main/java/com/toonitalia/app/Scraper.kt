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

        val cols = doc.select(".col")
        for (col in cols) {
            val h2 = col.selectFirst("h2")
            val title = h2?.text()?.replace(Regex("^[\\u{1F525}\\u{26A1}\\u{1F3AC}\\u{1F4FA}]\\s*"), "")?.trim() ?: continue
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

        for (a in doc.select(".catlist-box ul li a[href]")) {
            val href = a.attr("href")
            val title = a.text().trim()

            if (title.isNotBlank() && href.startsWith(BASE_URL) && !seen.contains(href)) {
                seen.add(href)
                items.add(ContentItem(
                    title = title,
                    url = href,
                    categorySlug = categorySlug
                ))
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

        val img = content.selectFirst("img")
        val imageUrl = img?.attr("src") ?: ""

        // Parse metadata from paragraphs
        val allText = content.text()
        val fields = mapOf(
            "Titolo originale" to "originalTitle",
            "Paese di origine" to "country",
            "Data di pubblicazione" to "year",
            "Stato Opera" to "status",
            "N. Episodi" to "totalEpisodes",
            "Aggiornamento episodi" to "availableEpisodes"
        )
        for ((field, key) in fields) {
            val pattern = Regex("$field\\s*:\\s*(.+)")
            pattern.find(allText)?.let { result[key] = it.groupValues[1].trim() }
        }

        val tramaMatch = Regex("Trama\\s*:\\s*(.+?)(?=Company|Episodi|Stagione)", RegexOption.DOT_MATCHES_ALL)
            .find(allText)
        val synopsis = tramaMatch?.groupValues?.get(1)?.trim() ?: ""

        // Parse episodes from <p> tags
        // Format: "28 – 1929 – Title – VOE – VIDHIDE"
        var currentSeason = 0
        for (el in content.children()) {
            // Track season headers
            if (el.tagName() == "h3" || el.tagName() == "h2") {
                val seasonMatch = Regex("(\\d+)\\s*[°º]\\s*Stagione").find(el.text())
                if (seasonMatch != null) {
                    currentSeason = seasonMatch.groupValues[1].toIntOrNull() ?: currentSeason
                    continue
                }
            }

            if (el.tagName() != "p") continue
            val pText = el.text()
            val links = el.select("a[href]")
            if (links.isEmpty()) continue

            // Check if this paragraph has streaming links
            val hasStreamingLink = links.any {
                val href = it.attr("href")
                href.contains("chuckle-tube.com") || href.contains("vidhideplus.com") ||
                href.contains("uqload.is") || href.contains("voe.sx")
            }
            if (!hasStreamingLink) continue

            // Get the first streaming URL
            val streamingUrl = links.first().attr("href")
            if (streamingUrl.isBlank()) continue

            // Parse episode info from text
            // Format: "28 – 1929 – Topolino – Steamboat Willie – VOE – VIDHIDE"
            // Remove the link texts to get the title part
            val cleanText = links.fold(pText) { acc, link -> acc.replace(link.text(), "").trim() }
                .replace(Regex("\\s*–\\s*$"), "")
                .replace(Regex("\\s*-$"), "")
                .trim()

            val epMatch = Regex("^(\\d+)\\s*–\\s*(\\d{4})\\s*–\\s*(.+?)\\s*$").find(cleanText)
            val epMatch2 = Regex("^(\\d+)\\s*–\\s*(.+?)\\s*$").find(cleanText)

            val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: epMatch2?.groupValues?.get(1)?.toIntOrNull()
                ?: (episodes.size + 1)
            val epTitle = epMatch?.groupValues?.get(3)?.trim()
                ?: epMatch2?.groupValues?.get(2)?.trim()
                ?: "Episodio $epNum"

            episodes.add(Episode(
                title = "Ep. $epNum - $epTitle",
                url = streamingUrl,
                season = currentSeason,
                number = epNum
            ))
        }

        val seasons = content.select("[id^=S], a[name^=S]").map {
            it.id().ifBlank { it.attr("name") }
        }.filter { it.isNotBlank() }.distinct()

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
