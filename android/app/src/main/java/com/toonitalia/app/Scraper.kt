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

        val h2Elements = doc.select("h2")
        for (h2 in h2Elements) {
            val title = h2.text()
            if (title.contains("Ultimi Aggiornamenti") ||
                title.contains("Anime") ||
                title.contains("Serie TV") ||
                title.contains("Film Animazione")
            ) {
                val items = mutableListOf<ContentItem>()
                var sibling = h2.nextElementSibling()
                while (sibling != null && sibling.tagName() != "h2") {
                    for (a in sibling.select("a[href]")) {
                        val href = a.attr("href")
                        val itemTitle = a.text()
                        val img = a.selectFirst("img")
                        val imageUrl = img?.attr("src") ?: ""

                        if (itemTitle.isNotBlank() && href.startsWith(BASE_URL) && href != BASE_URL) {
                            items.add(ContentItem(
                                title = itemTitle,
                                url = href,
                                image = imageUrl
                            ))
                        }
                    }
                    sibling = sibling.nextElementSibling()
                }
                if (items.isNotEmpty()) {
                    sections.add(CategorySection(title = title, items = items))
                }
            }
        }
        return sections
    }

    fun scrapeContentList(categorySlug: String): List<ContentItem> {
        val doc = getDoc("$BASE_URL/$categorySlug/")
        val items = mutableListOf<ContentItem>()
        val seen = mutableSetOf<String>()

        for (a in doc.select("article a[href], .post a[href], h2 a[href], h3 a[href]")) {
            val href = a.attr("href")
            val title = a.text()
            val img = a.selectFirst("img")
            val imageUrl = img?.attr("src") ?: ""

            if (title.isNotBlank() && href.startsWith(BASE_URL) &&
                href != "$BASE_URL/$categorySlug/" && !seen.contains(href)
            ) {
                seen.add(href)
                items.add(ContentItem(
                    title = title,
                    url = href,
                    image = imageUrl,
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

        val content = doc.selectFirst("article") ?: doc.selectFirst("div.entry-content") ?: doc
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
            val pattern = Regex("$field:\\s*(.+)")
            pattern.find(text)?.let { result[key] = it.groupValues[1].trim() }
        }

        val img = content.selectFirst("img")
        val imageUrl = img?.attr("src") ?: ""

        val tramaMatch = Regex("Trama:\\s*(.+?)(?=Scegli Stagione)", RegexOption.DOT_MATCHES_ALL)
            .find(text)
        val synopsis = tramaMatch?.groupValues?.get(1)?.trim() ?: ""

        var seasonIndex = 0
        for (a in doc.select("a[href]")) {
            val href = a.attr("href")
            val aText = a.text()

            if (aText.isNotBlank() && (href.contains("uqload.is") || href.contains("chuckle-tube.com"))) {
                val epMatch = Regex("(\\d+)\\s*[–-]\\s*(.+)").find(aText)
                val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val epTitle = epMatch?.groupValues?.get(2)?.trim() ?: aText

                episodes.add(Episode(
                    title = aText,
                    url = href,
                    season = seasonIndex,
                    number = epNum
                ))
            }
        }

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
