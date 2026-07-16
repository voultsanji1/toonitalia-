package com.toonitalia.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.text.RegexOption
import org.jsoup.Jsoup
import java.net.URLEncoder

object Scraper {
    private const val BASE_URL = "https://toonitalia.xyz"

    private fun getDoc(url: String): org.jsoup.nodes.Document {
        val html = NetworkModule.fetch(url)
        return Jsoup.parse(html)
    }

    private var homepageImageCache = mutableMapOf<String, String>()

    private fun buildHomepageCache() {
        if (homepageImageCache.isNotEmpty()) return
        try {
            val doc = getDoc(BASE_URL)
            for (item in doc.select(".item")) {
                val link = item.selectFirst("a[href]") ?: continue
                val href = link.attr("href")
                val img = item.selectFirst("img")
                val imageUrl = img?.attr("src") ?: ""
                if (href.isNotBlank() && imageUrl.isNotBlank()) {
                    homepageImageCache[href.trimEnd('/')] = imageUrl
                }
            }
        } catch (_: Exception) {}
    }

    private fun getImageForUrl(url: String): String {
        return homepageImageCache[url.trimEnd('/')] ?: ""
    }

    private fun cleanEmojiTitle(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace(Regex("[\\u0000-\\u001F\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("[\\u20E3\\u2600-\\u27BF\\u{1F000}-\\u{1FAFF}\\u{2600}-\\u{27BF}]"), "")
            .trim()
    }

    suspend fun scrapeHomepage(): List<CategorySection> {
        return try {
            val doc = getDoc(BASE_URL)
            val sections = mutableListOf<CategorySection>()

            val cols = doc.select(".col")
            for (col in cols) {
                val h2 = col.selectFirst("h2")
                val title = cleanEmojiTitle(h2?.text()) ?: continue
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

            if (sections.isNotEmpty()) {
                // Cache images for search/category lookups
                for (section in sections) {
                    for (item in section.items) {
                        if (item.image.isNotBlank()) homepageImageCache[item.url.trimEnd('/')] = item.image
                    }
                }
                sections
            } else {
                fallbackHomepage()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackHomepage()
        }
    }

    private suspend fun fallbackHomepage(): List<CategorySection> {
        val cats = scrapeCategories()
        val sections = mutableListOf<CategorySection>()
        for ((slug, label) in cats) {
            try {
                val items = scrapeContentList(slug).take(30)
                if (items.isNotEmpty()) {
                    sections.add(CategorySection(title = label, items = items))
                }
            } catch (_: Exception) {
            }
        }
        return sections
    }

    suspend fun scrapeContentList(categorySlug: String): List<ContentItem> {
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

        buildHomepageCache()

        for (item in items) {
            if (item.image.isEmpty()) {
                val cached = getImageForUrl(item.url)
                if (cached.isNotEmpty()) {
                    items[items.indexOf(item)] = item.copy(image = cached)
                }
            }
        }

        val needsFetch = items.filter { it.image.isEmpty() }.take(30)
        if (needsFetch.isNotEmpty()) {
            try {
                val fetched = runCatching {
                    coroutineScope {
                        needsFetch.chunked(6).map { chunk ->
                            chunk.map { item ->
                                async(Dispatchers.IO) {
                                    try {
                                        val detailDoc = getDoc(item.url)
                                        val img = detailDoc.selectFirst("article .entry-content img")
                                            ?: detailDoc.selectFirst("article img")
                                            ?: detailDoc.selectFirst(".entry-content img")
                                        val imgUrl = img?.attr("src") ?: ""
                                        if (imgUrl.isNotBlank()) item to imgUrl else null
                                    } catch (_: Exception) { null }
                                }
                            }.awaitAll().filterNotNull()
                        }.flatten()
                    }
                }.getOrDefault(emptyList())

                for ((item, imgUrl) in fetched) {
                    val idx = items.indexOfFirst { it.url == item.url }
                    if (idx >= 0) {
                        items[idx] = items[idx].copy(image = imgUrl)
                    }
                }
            } catch (_: Exception) {}
        }

        return items
    }

    private fun isStreamingHref(href: String?): Boolean {
        if (href.isNullOrBlank() || !href.contains("://")) return false
        if (href.contains("toonitalia.xyz")) return false
        return true
    }

    private fun hostLabel(href: String): String {
        return try {
            val host = Regex("https?://([^/]+)/").find(href)?.groupValues?.get(1) ?: href
            host.replace("www.", "")
        } catch (_: Exception) { "Player" }
    }

    private fun isAdText(text: String): Boolean {
        return text.contains("http")
    }

    private fun parseEpisodeSegment(segment: String, seasonLabel: String, fallbackNumber: Int): Episode? {
        // segment looks like: "929 – Titolo episodio – PLAYER1 – PLAYER2" or "VOE – VIDHIDE" (film)
        val segDoc = Jsoup.parse("<p>$segment</p>")
        val segEl = segDoc.selectFirst("p") ?: return null
        val links = segEl.select("a[href]").filter { a -> isStreamingHref(a.attr("href")) }
        if (links.isEmpty()) return null

        val playerLinks = links.map { a ->
            val href = a.attr("href")
            val label = a.text().trim().ifBlank { hostLabel(href) }
            PlayerLink(label = label, url = href)
        }

        // Remove link texts from the segment text to isolate the episode title.
        var text = segEl.text()
        for (a in links) text = text.replace(a.text(), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        text = text.replace(Regex("^[^\\d]*?(\\d{2,4})\\s*[–-]\\s*"), "$1 – ").trim()
        text = text.replace(Regex("[–-]\\s*$"), "").trim()

        val numMatch = Regex("^(\\d{2,4})").find(text)
        val epNum = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: fallbackNumber
        val epTitle = if (numMatch != null) {
            text.removePrefix(numMatch.groupValues[1]).replace(Regex("^\\s*[–-]\\s*"), "").trim()
        } else {
            text.replace(Regex("link\\s*streaming\\s*:", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^[\\s–-]+"), "")
                .trim()
        }.ifBlank { seasonLabel.ifBlank { "Streaming" } }

        return Episode(
            title = if (numMatch != null) "Ep. $epNum - $epTitle" else epTitle,
            url = playerLinks.first().url,
            season = 0,
            number = epNum,
            players = playerLinks
        )
    }

    private fun splitEpisodeSegments(raw: String): List<String> {
        // Episodes are separated by " NNN - Title - PLAYER ..." on the same line.
        val parts = raw.split(Regex("(?<=\\s)(?=\\d{2,4}\\s*[–-]\\s)"))
        return parts.map { it.trim() }.filter { it.isNotBlank() }
    }

    fun scrapeDetail(url: String): ContentItem {
        val doc = getDoc(url)
        val episodes = mutableListOf<Episode>()

        val titleEl = doc.selectFirst("h1")
        val title = titleEl?.text()?.trim() ?: ""

        val content = doc.selectFirst("article .entry-content") ?: doc.selectFirst("article") ?: doc

        val img = content.selectFirst("img")
        val imageUrl = img?.attr("src") ?: ""

        // --- Metadata (single <p> with "Field : value Field2 : value2") ---
        var originalTitle = ""
        var country = ""
        var year = ""
        var status = ""
        var totalEpisodes = ""
        var availableEpisodes = ""
        for (p in content.select("p")) {
            val t = p.text()
            if ("Titolo originale" in t || "Paese di origine" in t || "Data di pubblicazione" in t
                || "Stato Opera" in t || "N. Episodi" in t || "Aggiornamento episodi" in t
            ) {
                for ((field, key) in mapOf(
                    "Titolo originale" to "originalTitle",
                    "Titoli alternativi" to "originalTitle",
                    "Paese di origine" to "country",
                    "Data di pubblicazione" to "year",
                    "Stato Opera" to "status",
                    "N. Episodi" to "totalEpisodes",
                    "Aggiornamento episodi" to "availableEpisodes"
                )) {
                    val m = Regex("$field\\s*:\\s*([^\\n]+?)(?=\\s{2,}[A-Z][^:]*:|$)").find(t)
                    if (m != null) {
                        val v = m.groupValues[1].trim().trimEnd('.').trim()
                        when (key) {
                            "originalTitle" -> if (originalTitle.isBlank()) originalTitle = v
                            "country" -> if (country.isBlank()) country = v
                            "year" -> if (year.isBlank()) year = v
                            "status" -> if (status.isBlank()) status = v
                            "totalEpisodes" -> if (totalEpisodes.isBlank()) totalEpisodes = v
                            "availableEpisodes" -> if (availableEpisodes.isBlank()) availableEpisodes = v
                        }
                    }
                }
            }
        }

        // --- Synopsis (between "Trama:" and the next relevant heading) ---
        var synopsis = ""
        var grab = false
        for (el in content.select("> *")) {
            val name = el.tagName()
            if (name == "h3" || name == "h2") {
                val txt = el.text()
                if ("Trama" in txt) { grab = true; continue }
                if (grab) break
            }
            if (grab && name == "p") {
                val t = el.text().trim()
                if (t.isNotBlank() && "PLAYER" !in t && !isAdText(t)) {
                    synopsis = t
                    break
                }
            }
        }

        // --- Episodes: walk direct children, split multi-episode <p> blocks ---
        var currentSeason = ""
        var fallbackCounter = 1
        for (el in content.select("> *")) {
            val name = el.tagName()
            if (name == "h3" || name == "h2") {
                val txt = el.text().trim()
                if (Regex("\\d+\\s*[°º]\\s*Stagione", RegexOption.IGNORE_CASE).containsMatchIn(txt)) {
                    currentSeason = txt
                } else if ("Sigle" in txt || "Scegli Stagione" in txt || "Special" in txt) {
                    currentSeason = txt
                }
                continue
            }
            if (name != "p") continue

            val links = el.select("a[href]").filter { a -> isStreamingHref(a.attr("href")) }
            if (links.isEmpty()) continue

            val raw = el.text()
            val segments = splitEpisodeSegments(raw)
            if (segments.isEmpty()) {
                // Film / single block without episode numbers
                val ep = parseEpisodeSegment(raw, currentSeason.ifBlank { title }, fallbackCounter++)
                if (ep != null) episodes.add(ep)
            } else {
                for (seg in segments) {
                    val ep = parseEpisodeSegment(seg, currentSeason.ifBlank { title }, fallbackCounter++)
                    if (ep != null) episodes.add(ep)
                }
            }
        }

        return ContentItem(
            title = title,
            url = url,
            image = imageUrl,
            synopsis = synopsis,
            originalTitle = originalTitle,
            country = country,
            year = year,
            status = status,
            totalEpisodes = totalEpisodes,
            availableEpisodes = availableEpisodes,
            episodes = episodes,
            seasons = emptyList()
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

    suspend fun search(query: String): List<ContentItem> {
        val results = mutableListOf<ContentItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val doc = getDoc("$BASE_URL/?s=$encoded")
            buildHomepageCache()
            val rawItems = mutableListOf<ContentItem>()
            for (article in doc.select("article")) {
                val h2 = article.selectFirst("h2")
                val link = h2?.selectFirst("a[href]") ?: continue
                val href = link.attr("href")
                val title = link.text().trim()
                if (title.isNotBlank() && href.startsWith(BASE_URL) &&
                    !href.contains("/?s=") && !href.contains("/category/") &&
                    !href.contains("/author/") && !href.contains("/tag/")) {
                    // Search result cards have no inline image: try the homepage cache first.
                    var imageUrl = getImageForUrl(href)
                    if (imageUrl.isBlank()) {
                        val img = article.selectFirst("img")
                        imageUrl = img?.attr("src") ?: ""
                        if (imageUrl.isBlank()) {
                            val schemaScript = article.selectFirst("script[type=application/ld+json]")
                            if (schemaScript != null) {
                                val text = schemaScript.html()
                                val thumbMatch = Regex("\"thumbnailUrl\"\\s*:\\s*\"([^\"]+)\"").find(text)
                                imageUrl = thumbMatch?.groupValues?.get(1) ?: ""
                            }
                        }
                        if (imageUrl.isBlank()) {
                            val metaImg = article.selectFirst("meta[property=og:image]")
                            imageUrl = metaImg?.attr("content") ?: ""
                        }
                    }
                    rawItems.add(ContentItem(title = title, url = href, image = imageUrl))
                }
            }
            results.addAll(rawItems)

            // Fetch detail pages (in parallel) only for items still missing an image.
            val needsFetch = rawItems.filter { it.image.isBlank() }.take(20)
            if (needsFetch.isNotEmpty()) {
                try {
                    val fetched = runCatching {
                        coroutineScope {
                            needsFetch.chunked(5).map { chunk ->
                                chunk.map { item ->
                                    async(Dispatchers.IO) {
                                        try {
                                            val detailDoc = getDoc(item.url)
                                            val img = detailDoc.selectFirst("article .entry-content img")
                                                ?: detailDoc.selectFirst("article img")
                                                ?: detailDoc.selectFirst(".entry-content img")
                                            val imgUrl = img?.attr("src") ?: ""
                                            if (imgUrl.isNotBlank()) item to imgUrl else null
                                        } catch (_: Exception) { null }
                                    }
                                }.awaitAll().filterNotNull()
                            }.flatten()
                        }
                    }.getOrDefault(emptyList())

                    for ((item, imgUrl) in fetched) {
                        val idx = results.indexOfFirst { it.url == item.url }
                        if (idx >= 0) {
                            results[idx] = results[idx].copy(image = imgUrl)
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }
}
