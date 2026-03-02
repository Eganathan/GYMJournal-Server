#!/usr/bin/env python3
"""
Scrape full exercise data from hevyapp.com/exercises.

Outputs: data/hevy_exercises_full.json

Each record:
  name, primaryMuscle, secondaryMuscles, equipment, difficulty,
  instructions (list), tips (list), imageUrls (list), videoUrls (list), tags (list)
"""

import json
import re
import time
import os
import sys
from urllib.parse import urlparse
from bs4 import BeautifulSoup

try:
    import requests
except ImportError:
    sys.exit("requests not installed — run: pip3 install requests")

BASE      = "https://www.hevyapp.com"
LIST_BASE = f"{BASE}/exercises/"
HEADERS   = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}
DELAY = 1.2   # seconds between requests — be polite

# ---------------------------------------------------------------------------
# Step 1: collect all exercise detail URLs from listing pages
# ---------------------------------------------------------------------------

def collect_exercise_urls() -> list[str]:
    urls: list[str] = []
    page = 1
    while True:
        paged = LIST_BASE if page == 1 else f"{LIST_BASE}page/{page}/"
        print(f"  listing page {page}: {paged}", flush=True)
        r = requests.get(paged, headers=HEADERS, timeout=20)
        if r.status_code == 404:
            print(f"    → 404, done at page {page - 1}")
            break
        r.raise_for_status()
        soup = BeautifulSoup(r.text, "html.parser")

        found = []
        # Hevy listing: exercise links are inside <article> tags or .exercise-card links
        # They follow the pattern /exercises/<slug>/
        for a in soup.find_all("a", href=True):
            href: str = a["href"]
            if re.match(r"https://www\.hevyapp\.com/exercises/[^/]+/$", href):
                if href not in urls and href not in found:
                    found.append(href)

        if not found:
            print(f"    → no exercise links found, done at page {page - 1}")
            break

        urls.extend(found)
        print(f"    found {len(found)} exercises (total so far: {len(urls)})")
        page += 1
        time.sleep(DELAY)

    return urls


# ---------------------------------------------------------------------------
# Step 2: parse a single exercise detail page
# ---------------------------------------------------------------------------

def text_of(tag) -> str:
    return tag.get_text(" ", strip=True) if tag else ""


def parse_detail(url: str, html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")

    # --- name ---
    h1 = soup.find("h1")
    name = text_of(h1)

    # --- meta box (muscle, equipment, difficulty) ---
    # Hevy uses a table or definition list in .entry-content for the meta info
    primary_muscle    = ""
    secondary_muscles: list[str] = []
    equipment         = ""
    difficulty        = ""

    # Look for structured meta in <table> rows or <p> tags containing labels
    # Pattern 1: <table> with rows like "Primary Muscle | Chest"
    for table in soup.find_all("table"):
        for row in table.find_all("tr"):
            cells = row.find_all(["th", "td"])
            if len(cells) >= 2:
                label = cells[0].get_text(strip=True).lower()
                value = cells[1].get_text(strip=True)
                if "primary" in label and "muscle" in label:
                    primary_muscle = value
                elif "secondary" in label:
                    secondary_muscles = [v.strip() for v in re.split(r"[,/]", value) if v.strip()]
                elif "equipment" in label:
                    equipment = value
                elif "difficulty" in label:
                    difficulty = value

    # Pattern 2: <li> or <p> tags with "Primary Muscle:" label text
    if not primary_muscle:
        for tag in soup.find_all(["li", "p", "div", "span"]):
            txt = tag.get_text(strip=True)
            m = re.match(r"(?i)^Primary\s+Muscle\s*[:\-]\s*(.+)$", txt)
            if m:
                primary_muscle = m.group(1).strip()
                break

    if not equipment:
        for tag in soup.find_all(["li", "p", "div", "span"]):
            txt = tag.get_text(strip=True)
            m = re.match(r"(?i)^Equipment\s*[:\-]\s*(.+)$", txt)
            if m:
                equipment = m.group(1).strip()
                break

    if not difficulty:
        for tag in soup.find_all(["li", "p", "div", "span"]):
            txt = tag.get_text(strip=True)
            m = re.match(r"(?i)^Difficulty\s*[:\-]\s*(.+)$", txt)
            if m:
                difficulty = m.group(1).strip()
                break

    # Pattern 3: look for breadcrumbs or category links (muscle group as category)
    if not primary_muscle:
        for a in soup.find_all("a", href=True):
            if "/exercises/category/" in a["href"] or "/muscle-group/" in a["href"]:
                primary_muscle = text_of(a)
                break

    # --- instructions ---
    instructions: list[str] = []
    # Find the first <ol> in .entry-content (How To section)
    entry = soup.find(class_="entry-content") or soup.find("article") or soup
    ol = entry.find("ol")
    if ol:
        for li in ol.find_all("li"):
            step = li.get_text(" ", strip=True)
            if step:
                instructions.append(step)

    # If no <ol>, fall back to numbered paragraphs
    if not instructions:
        for p in entry.find_all("p"):
            txt = p.get_text(strip=True)
            if re.match(r"^\d+[\.\)]", txt):
                instructions.append(re.sub(r"^\d+[\.\)]\s*", "", txt))

    # --- tips ---
    tips: list[str] = []
    # Tips are often under an <h3> heading that contains "tip" or "tips"
    for h in entry.find_all(["h2", "h3", "h4"]):
        heading_text = h.get_text(strip=True).lower()
        if "tip" in heading_text:
            # Collect sibling content until next heading
            sib = h.find_next_sibling()
            while sib and sib.name not in ["h2", "h3", "h4"]:
                if sib.name == "ul":
                    for li in sib.find_all("li"):
                        t = li.get_text(" ", strip=True)
                        if t:
                            tips.append(t)
                elif sib.name == "p":
                    t = sib.get_text(" ", strip=True)
                    if t:
                        tips.append(t)
                sib = sib.find_next_sibling()
            break

    # --- secondary muscles from page body if not found in table ---
    if not secondary_muscles:
        for h in entry.find_all(["h2", "h3", "h4"]):
            heading_text = h.get_text(strip=True).lower()
            if "secondary" in heading_text or "also works" in heading_text or "synergist" in heading_text:
                sib = h.find_next_sibling()
                while sib and sib.name not in ["h2", "h3", "h4"]:
                    if sib.name in ["ul", "ol"]:
                        for li in sib.find_all("li"):
                            t = li.get_text(" ", strip=True)
                            if t:
                                secondary_muscles.append(t)
                    elif sib.name == "p":
                        t = sib.get_text(" ", strip=True)
                        if t:
                            for part in re.split(r"[,/]", t):
                                p2 = part.strip()
                                if p2:
                                    secondary_muscles.append(p2)
                    sib = sib.find_next_sibling()
                break

    # --- image URLs ---
    image_urls: list[str] = []
    for img in entry.find_all("img"):
        src = img.get("src") or img.get("data-src") or ""
        # Only include substantive images (from wp-content or hevyapp CDN), skip icons/logos
        if src and ("wp-content" in src or "hevyapp" in src or "cloudfront" in src):
            if src not in image_urls:
                image_urls.append(src)
    # Limit to first 3 images
    image_urls = image_urls[:3]

    # --- video URLs ---
    video_urls: list[str] = []
    for iframe in soup.find_all("iframe"):
        src = iframe.get("src") or ""
        if "youtube" in src or "youtu.be" in src:
            # Normalise to watch URL
            m = re.search(r"(?:embed/|v=|youtu\.be/)([A-Za-z0-9_\-]{11})", src)
            if m:
                video_urls.append(f"https://www.youtube.com/watch?v={m.group(1)}")

    # --- tags ---
    tags: list[str] = []
    # Use category/tag links on the page
    for a in soup.find_all("a", href=True, rel=lambda r: r and "tag" in r):
        t = text_of(a)
        if t and t not in tags:
            tags.append(t)
    # Also check .tags, .post-tags, etc.
    for tag_container in soup.find_all(class_=re.compile(r"tag", re.I)):
        for a in tag_container.find_all("a"):
            t = text_of(a)
            if t and t not in tags:
                tags.append(t)

    # Deduplicate
    secondary_muscles = list(dict.fromkeys(secondary_muscles))
    tips = list(dict.fromkeys(tips))

    return {
        "name":             name,
        "primaryMuscle":    primary_muscle,
        "secondaryMuscles": secondary_muscles,
        "equipment":        equipment,
        "difficulty":       difficulty,
        "instructions":     instructions,
        "tips":             tips,
        "imageUrls":        image_urls,
        "videoUrls":        video_urls,
        "tags":             tags,
        "sourceUrl":        url,
    }


# ---------------------------------------------------------------------------
# Step 3: fetch and parse each detail page
# ---------------------------------------------------------------------------

def scrape_all(urls: list[str]) -> list[dict]:
    results = []
    for i, url in enumerate(urls, 1):
        print(f"  [{i}/{len(urls)}] {url}", flush=True)
        try:
            r = requests.get(url, headers=HEADERS, timeout=20)
            if r.status_code == 404:
                print(f"    → 404 skipped")
                continue
            r.raise_for_status()
            data = parse_detail(url, r.text)
            if data["name"]:
                results.append(data)
                print(f"    ✓ {data['name']} | muscle={data['primaryMuscle']} | equip={data['equipment']} | steps={len(data['instructions'])} | tips={len(data['tips'])}")
            else:
                print(f"    ⚠ no name found, skipped")
        except Exception as e:
            print(f"    ✗ ERROR: {e}")
        time.sleep(DELAY)
    return results


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.join(out_dir, "hevy_exercises_full.json")

    print("=== Phase 1: collecting exercise listing URLs ===")
    urls = collect_exercise_urls()
    print(f"Total exercises found: {len(urls)}\n")

    if not urls:
        print("No URLs found — aborting.")
        sys.exit(1)

    print("=== Phase 2: scraping exercise detail pages ===")
    exercises = scrape_all(urls)
    print(f"\nSuccessfully scraped: {len(exercises)} exercises")

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(exercises, f, indent=2, ensure_ascii=False)

    print(f"\nSaved → {out_path}")


if __name__ == "__main__":
    main()
