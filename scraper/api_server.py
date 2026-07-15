from flask import Flask, jsonify, request
from scraper import scrape_homepage, scrape_content_list, scrape_detail, scrape_categories
import json
import os
import time

app = Flask(__name__)

CACHE_DIR = "cache"
os.makedirs(CACHE_DIR, exist_ok=True)


def get_cache(key, ttl=3600):
    path = os.path.join(CACHE_DIR, f"{key}.json")
    if os.path.exists(path):
        age = time.time() - os.path.getmtime(path)
        if age < ttl:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
    return None


def set_cache(key, data):
    path = os.path.join(CACHE_DIR, f"{key}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)


@app.route("/api/categories")
def api_categories():
    return jsonify(scrape_categories())


@app.route("/api/homepage")
def api_homepage():
    cache = get_cache("homepage", ttl=1800)
    if cache:
        return jsonify(cache)
    data = scrape_homepage()
    set_cache("homepage", data)
    return jsonify(data)


@app.route("/api/list/<category>")
def api_list(category):
    cache = get_cache(f"list_{category}", ttl=1800)
    if cache:
        return jsonify(cache)
    data = scrape_content_list(category)
    set_cache(f"list_{category}", data)
    return jsonify(data)


@app.route("/api/detail")
def api_detail():
    url = request.args.get("url")
    if not url:
        return jsonify({"error": "url parameter required"}), 400
    cache_key = url.replace("/", "_").replace(":", "")
    cache = get_cache(cache_key, ttl=3600)
    if cache:
        return jsonify(cache)
    data = scrape_detail(url)
    set_cache(cache_key, data)
    return jsonify(data)


@app.route("/api/search")
def api_search():
    query = request.args.get("q", "").lower()
    if not query:
        return jsonify([])
    categories = scrape_categories()
    results = []
    for slug in categories:
        items = scrape_content_list(slug)
        for item in items:
            if query in item["title"].lower():
                results.append(item)
    return jsonify(results)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
