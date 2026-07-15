package com.toonitalia.app

import org.jsoup.Jsoup
import java.net.URLEncoder

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

        // Parse episodes from <p> tags, splitting by <br> (all episodes are in one <p>)
        var currentSeason = 0
        for (el in content.children()) {
            if (el.tagName() == "h3" || el.tagName() == "h2") {
                val seasonMatch = Regex("(\\d+)\\s*[°º]\\s*Stagione").find(el.text())
                if (seasonMatch != null) {
                    currentSeason = seasonMatch.groupValues[1].toIntOrNull() ?: currentSeason
                    continue
                }
            }

            if (el.tagName() != "p") continue

            // Split <p> by <br> to get individual episodes
            val html = el.html()
            val parts = html.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))

            for (partHtml in parts) {
                val segDoc = Jsoup.parse("<p>$partHtml</p>")
                val segEl = segDoc.selectFirst("p") ?: continue
                val segText = segEl.text()
                val links = segEl.select("a[href]")
                if (links.isEmpty()) continue

                val hasStreamingLink = links.any {
                    val href = it.attr("href")
                    href.contains("chuckle-tube.com") || href.contains("vidhideplus.com") ||
                    href.contains("uqload.is") || href.contains("uqload.com") ||
                    href.contains("uqload.bz") || href.contains("uqload.to") ||
                    href.contains("voe.sx") || href.contains("ryderjet.com") ||
                    href.contains("luluvdo.com") || href.contains("streamtape") ||
                    href.contains("strcloud.link") || href.contains("scloud.lol")
                }
                if (!hasStreamingLink) continue

                val playerLinks = mutableListOf<PlayerLink>()
                for (link in links) {
                    val href = link.attr("href")
                    if (href.isBlank()) continue
                    val label = link.text().trim()
                    playerLinks.add(PlayerLink(label = label.ifBlank { "Player" }, url = href))
                }
                if (playerLinks.isEmpty()) continue

                val streamingUrl = playerLinks.first().url

                val cleanText = links.fold(segText) { acc, link -> acc.replace(link.text(), "").trim() }
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
                    number = epNum,
                    players = playerLinks
                ))
            }
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
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val doc = getDoc("$BASE_URL/?s=$encoded")
            for (article in doc.select("article")) {
                val h2 = article.selectFirst("h2")
                val link = h2?.selectFirst("a[href]") ?: continue
                val href = link.attr("href")
                val title = link.text().trim()
                if (title.isNotBlank() && href.startsWith(BASE_URL) &&
                    !href.contains("/?s=") && !href.contains("/category/") && !href.contains("/author/")) {
                    val img = article.selectFirst("img")
                    results.add(ContentItem(
                        title = title,
                        url = href,
                        image = img?.attr("src") ?: ""
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }
}
