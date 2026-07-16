import requests
from bs4 import BeautifulSoup
import json
import re
import time
from urllib.parse import urljoin

BASE_URL = "https://toonitalia.xyz"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Accept-Language": "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7",
}

session = requests.Session()
session.headers.update(HEADERS)


def get_soup(url):
    resp = session.get(url, timeout=30)
    resp.raise_for_status()
    return BeautifulSoup(resp.text, "lxml")


def scrape_categories():
    categories = {
        "anime-ita": "Anime Ita",
        "contatti": "Anime Sub-Ita",
        "film-animazione": "Film Animazione",
        "serie-tv": "Serie Tv",
    }
    return categories


def scrape_content_list(category_slug):
    url = f"{BASE_URL}/{category_slug}/"
    soup = get_soup(url)
    items = []
    seen = set()

    # Category pages list every title in .catlist-box > ul > li > a[href].
    for link in soup.select(".catlist-box ul li a[href]"):
        href = link.get("href", "")
        title = link.get_text(strip=True)
        if title and BASE_URL in href and href not in seen:
            seen.add(href)
            items.append({
                "title": title,
                "url": href,
                "image": "",
                "category": category_slug,
            })
    return items


def scrape_homepage():
    soup = get_soup(BASE_URL)
    sections = {}
    current_section = None

    # The homepage is laid out as a series of .col blocks, each with an <h2>
    # section title and a list of .item cards (.card-link > img + .title span).
    for col in soup.select(".col"):
        h2 = col.find("h2")
        if not h2:
            continue
        text = h2.get_text(strip=True)
        # strip a leading emoji / decorative char if present
        text = re.sub(r"^[\u0000-\u001F\u200B-\u200D\uFEFF\u20E3\u2600-\u27BF\U0001F000-\U0001FFFF]\s*", "", text).strip()
        if not text:
            continue
        current_section = text
        sections[current_section] = []

        for item in col.select(".item"):
            link = item.find("a", href=True)
            if not link:
                continue
            href = link["href"]
            img = item.find("img")
            image_url = img["src"] if img and img.get("src") else ""
            title_el = item.find(class_="title")
            title = title_el.get_text(strip=True) if title_el else link.get_text(strip=True)
            if title and BASE_URL in href and href != BASE_URL:
                sections[current_section].append({
                    "title": title,
                    "url": href,
                    "image": image_url,
                })

    # Fallback: if the layout changed, build sections from the category pages.
    if not sections:
        for slug, name in scrape_categories().items():
            try:
                items = scrape_content_list(slug)[:30]
                if items:
                    sections[name] = items
            except Exception:
                pass

    return sections


HOSTS = [
    "chuckle-tube.com", "vidhideplus.com", "uqload.is", "uqload.com",
    "uqload.bz", "uqload.to", "voe.sx", "ryderjet.com", "luluvdo.com",
    "streamtape", "strcloud.link", "scloud.lol", "supervideo", "dood",
    "mixdrop", "filelions", "streamwish", "vidmoly", "embedsb",
]


def _is_streaming_link(href):
    return any(h in href for h in HOSTS)


def scrape_detail(url):
    soup = get_soup(url)
    result = {"url": url, "episodes": [], "seasons": []}

    title_tag = soup.find("h1")
    result["title"] = title_tag.get_text(strip=True) if title_tag else ""

    content = soup.find("article") or soup.find("div", class_="entry-content") or soup
    text = content.get_text("\n", strip=True)

    for field, key in [
        ("Titolo originale", "original_title"),
        ("Paese di origine", "country"),
        ("Data di pubblicazione", "year"),
        ("Stato Opera", "status"),
        ("N. Episodi", "total_episodes"),
        ("Episodi disponibili", "available_episodes"),
    ]:
        m = re.search(rf"{field}:\s*(.+)", text)
        if m:
            result[key] = m.group(1).strip()

    img_tag = content.find("img")
    if img_tag and img_tag.get("src"):
        result["image"] = img_tag["src"]

    trama_match = re.search(r"Trama:\s*(.+?)(?=\n\n|Company|Episodi|Stagione)", text, re.DOTALL)
    if trama_match:
        result["synopsis"] = trama_match.group(1).strip()

    current_season = 0
    episodes = []
    # Episode blocks live inside <p> elements (often nested inside a div), so we
    # walk every <p> descendant, not just direct children.
    for el in content.find_all("p"):
        # Track season headers that appear before this paragraph.
        prev = el.find_previous(["h2", "h3"])
        if prev is not None:
            sm = re.search(r"(\d+)\s*[°º]\s*Stagione", prev.get_text())
            if sm:
                current_season = int(sm.group(1))

        for part_html in re.split(r"<br\s*/?>", str(el), flags=re.IGNORECASE):
            seg = BeautifulSoup(f"<p>{part_html}</p>", "lxml").find("p")
            if not seg:
                continue
            links = seg.find_all("a", href=True)
            if not links:
                continue
            if not any(_is_streaming_link(a["href"]) for a in links):
                continue

            player_links = []
            for a in links:
                href = a["href"]
                if not href:
                    continue
                label = a.get_text(strip=True) or "Player"
                player_links.append({"label": label, "url": href})
            if not player_links:
                continue

            seg_text = seg.get_text()
            clean_text = seg_text
            for a in links:
                clean_text = clean_text.replace(a.get_text(), "").strip()
            clean_text = re.sub(r"\s*[–\-–]\s*$", "", clean_text).strip()
            # Drop a trailing separator that may remain after the last link.
            clean_text = re.sub(r"[–\-–]\s*$", "", clean_text).strip()

            m1 = re.match(r"^(\d+)\s*[–-]\s*(\d{4})\s*[–-]\s*(.+?)\s*$", clean_text)
            m2 = re.match(r"^(\d+)\s*[–-]\s*(.+?)\s*$", clean_text)
            ep_num = int(m1.group(1)) if m1 else (int(m2.group(1)) if m2 else (len(episodes) + 1))
            ep_title = (m1.group(3).strip() if m1 else (m2.group(2).strip() if m2 else f"Episodio {ep_num}"))

            episodes.append({
                "title": f"Ep. {ep_num} - {ep_title}",
                "url": player_links[0]["url"],
                "season": current_season,
                "number": ep_num,
                "players": player_links,
            })

    result["episodes"] = episodes

    seasons = []
    for s in content.select("[id^=S], a[name^=S]"):
        sid = s.get("id") or s.get("name")
        if sid:
            seasons.append(sid)
    result["seasons"] = list(dict.fromkeys(seasons))

    return result


def scrape_all_content():
    all_content = []
    categories = scrape_categories()

    for slug, name in categories.items():
        print(f"Scraping {name}...")
        items = scrape_content_list(slug)
        for item in items:
            print(f"  -> {item['title']}")
            detail = scrape_detail(item["url"])
            detail["category"] = name
            detail["category_slug"] = slug
            detail["thumbnail"] = item.get("image", "")
            all_content.append(detail)
            time.sleep(0.5)

    return all_content


def save_json(data, filename="toonitalia_data.json"):
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"Saved {len(data)} items to {filename}")


if __name__ == "__main__":
    print("=== ToonItalia Scraper ===")
    print("1. Scrape homepage")
    print("2. Scrape all content")
    print("3. Scrape single URL")
    choice = input("Choice (1/2/3): ").strip()

    if choice == "1":
        data = scrape_homepage()
        save_json(data, "toonitalia_homepage.json")
        print(json.dumps(data, indent=2, ensure_ascii=False)[:3000])

    elif choice == "2":
        data = scrape_all_content()
        save_json(data)

    elif choice == "3":
        url = input("URL: ").strip()
        data = scrape_detail(url)
        save_json([data])
        print(json.dumps(data, indent=2, ensure_ascii=False)[:3000])
