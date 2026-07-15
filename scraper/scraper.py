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

    for link in soup.select("article a, .post a, h2 a, h3 a"):
        href = link.get("href", "")
        title = link.get_text(strip=True)
        img = link.find("img")
        image_url = img["src"] if img and img.get("src") else ""

        if href and title and BASE_URL in href and href != url:
            items.append({
                "title": title,
                "url": href,
                "image": image_url,
                "category": category_slug,
            })

    seen = set()
    unique = []
    for item in items:
        if item["url"] not in seen:
            seen.add(item["url"])
            unique.append(item)
    return unique


def scrape_homepage():
    soup = get_soup(BASE_URL)
    sections = {}
    current_section = None

    for h2 in soup.find_all("h2"):
        text = h2.get_text(strip=True)
        if any(k in text for k in ["Ultimi Aggiornamenti", "Anime", "Serie TV", "Film Animazione"]):
            current_section = text
            sections[current_section] = []

        if current_section:
            parent = h2.find_next_sibling()
            if parent:
                for a in parent.find_all("a", href=True):
                    href = a["href"]
                    title = a.get_text(strip=True)
                    img = a.find("img")
                    image_url = img["src"] if img and img.get("src") else ""
                    if title and BASE_URL in href:
                        sections[current_section].append({
                            "title": title,
                            "url": href,
                            "image": image_url,
                        })

    return sections


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

    trama_match = re.search(r"Trama:\s*(.+?)(?=\n\n|Scegli Stagione)", text, re.DOTALL)
    if trama_match:
        result["synopsis"] = trama_match.group(1).strip()

    for a in soup.find_all("a", href=True):
        href = a["href"]
        title = a.get_text(strip=True)
        if title and ("uqload.is" in href or "chuckle-tube.com" in href):
            result["episodes"].append({
                "title": title,
                "url": href,
            })

    season_headers = soup.find_all(id=re.compile(r"^S\d+"))
    for header in season_headers:
        season_name = header.get_text(strip=True) or header.get("id", "")
        result["seasons"].append(season_name)

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
