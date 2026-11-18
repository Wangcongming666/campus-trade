#!/usr/bin/env python3
"""
从 Wikimedia Commons 收集小样本测试图（只取自由许可：CC0 / Public domain / CC BY / CC BY-SA）。

两步：
  1) python3 collect_samples.py candidates   # 每类按固定随机种子抽候选、下载 500px 缩略图（Wikimedia 标准缩略图尺寸）、生成拼图供人工核对
  2) 人工核对后在 exclusions.json 里写明剔除的候选（如图片主体不是该类商品）及原因
     python3 collect_samples.py finalize      # 每类取前 N 张未剔除的候选，写 samples.csv
核对只看“图片主体是否属于该分类”，在运行模型之前完成，不依据模型结果挑图。
"""
import csv
import hashlib
import html
import io
import json
import os
import random
import re
import sys
import time

import requests
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
IMG_DIR = os.path.join(HERE, "images")
API = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "campus-trade-thesis-experiment/1.0 (offline zero-shot classification eval; contact: student project)"}
SEED = 20261118
CANDIDATES_PER_CLASS = 20
FINAL_PER_CLASS = 8
OK_LICENSE = re.compile(r"^(cc0|public domain|pd|cc by(-sa)? [0-9.]+)", re.I)


def api(params):
    params = dict(params, format="json", formatversion=2)
    for i in range(5):
        r = requests.get(API, params=params, headers=UA, timeout=60)
        if r.status_code == 429:
            time.sleep(5 * (i + 1))
            continue
        r.raise_for_status()
        return r.json()
    r.raise_for_status()


def members(cat):
    out, cont = [], {}
    while len(out) < 500:
        d = api({"action": "query", "list": "categorymembers", "cmtitle": cat, "cmtype": "file",
                 "cmlimit": 500, **cont})
        out += [m["title"] for m in d["query"]["categorymembers"]]
        if "continue" not in d:
            break
        cont = d["continue"]
    return [t for t in out if re.search(r"\.(jpe?g|png)$", t, re.I)]


def info(titles):
    d = api({"action": "query", "prop": "imageinfo", "titles": "|".join(titles),
             "iiprop": "url|extmetadata|size", "iiurlwidth": 500})
    res = {}
    for p in d["query"]["pages"]:
        if "imageinfo" not in p:
            continue
        ii = p["imageinfo"][0]
        md = ii.get("extmetadata", {})
        lic = md.get("LicenseShortName", {}).get("value", "")
        artist = re.sub(r"<[^>]+>", "", html.unescape(md.get("Artist", {}).get("value", ""))).strip()
        res[p["title"]] = {"title": p["title"], "page": ii["descriptionurl"], "thumb": ii.get("thumburl", ii["url"]),
                           "license": lic, "licenseUrl": md.get("LicenseUrl", {}).get("value", ""),
                           "artist": artist[:120]}
    return res


def fetch(m):
    """下载缩略图；被限流（429）两次或失败则放弃该候选，按顺序取下一张（不影响随机性以外的选择逻辑）。"""
    dest = os.path.join(IMG_DIR, m["file"])
    if os.path.exists(dest):
        return True
    for k in range(2):
        try:
            r = requests.get(m["thumb"], headers=UA, timeout=60)
        except requests.RequestException as e:
            print(f"    下载失败 {m['file']}: {e}", file=sys.stderr, flush=True)
            return False
        if r.status_code == 429:
            print(f"    429 {m['file']}", file=sys.stderr, flush=True)
            time.sleep(5)
            continue
        if r.status_code != 200:
            print(f"    HTTP {r.status_code} {m['file']}", file=sys.stderr, flush=True)
            return False
        try:
            im = Image.open(io.BytesIO(r.content)).convert("RGB")
        except Exception as e:  # noqa
            print(f"    无法解码 {m['file']}: {e}", file=sys.stderr, flush=True)
            return False
        im.save(dest, quality=92)
        print(f"    已下载 {m['file']} {m['license']}", file=sys.stderr, flush=True)
        time.sleep(1.0)
        return True
    return False


def fname(cid, title):
    h = hashlib.sha1(title.encode()).hexdigest()[:10]
    return f"{cid}_{h}.jpg"


def candidates():
    src = json.load(open(os.path.join(HERE, "sources.json"), encoding="utf-8"))
    os.makedirs(IMG_DIR, exist_ok=True)
    rng = random.Random(SEED)
    allc = {}
    for cid, cats in src.items():
        if cid.startswith("_"):
            continue
        pool = []
        for c in cats:
            try:
                ms = members(c)
            except Exception as e:  # noqa
                print(f"  {c} 失败: {e}", file=sys.stderr)
                ms = []
            print(f"  {cid} {c}: {len(ms)} 个文件", file=sys.stderr)
            pool += ms
        pool = sorted(set(pool))
        rng.shuffle(pool)
        picked = []
        for i in range(0, len(pool), 40):
            if len(picked) >= CANDIDATES_PER_CLASS:
                break
            meta = info(pool[i:i + 40])
            for t in pool[i:i + 40]:
                m = meta.get(t)
                if not m or not OK_LICENSE.match(m["license"].strip()) or len(picked) >= CANDIDATES_PER_CLASS:
                    continue
                m["file"] = fname(cid, m["title"])
                if fetch(m):
                    picked.append(m)
        allc[cid] = picked
        sheet(cid, picked)
    json.dump(allc, open(os.path.join(HERE, "candidates.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)


def sheet(cid, picked):
    """4 列拼图，供人工核对。"""
    os.makedirs(os.path.join(HERE, "sheets"), exist_ok=True)
    W = 200
    s = Image.new("RGB", (W * 4, (W + 16) * ((len(picked) + 3) // 4)), "white")
    d = ImageDraw.Draw(s)
    for i, m in enumerate(picked):
        im = Image.open(os.path.join(IMG_DIR, m["file"]))
        im.thumbnail((W, W))
        x, y = (i % 4) * W, (i // 4) * (W + 16)
        s.paste(im, (x, y + 16))
        d.text((x + 2, y + 2), f"#{i} {m['file'][20:30]}", fill="black")
    s.save(os.path.join(HERE, "sheets", f"{cid}.jpg"))


def finalize():
    allc = json.load(open(os.path.join(HERE, "candidates.json"), encoding="utf-8"))
    excl = json.load(open(os.path.join(HERE, "exclusions.json"), encoding="utf-8"))
    prompts = json.load(open(os.path.join(HERE, "..", "..", "scripts", "ai", "category_prompts.json"), encoding="utf-8"))
    rows = []
    for cid, picked in allc.items():
        bad = {e["file"] for e in excl.get(cid, [])}
        ok = [m for m in picked if m["file"] not in bad][:FINAL_PER_CLASS]
        if len(ok) < 5:
            print(f"警告：{cid} 只有 {len(ok)} 张有效样本", file=sys.stderr)
        for m in ok:
            c = prompts["categories"][cid]
            rows.append({"file": m["file"], "categoryId": cid, "categoryName": c["name"],
                         "parentId": c.get("parentId") or cid, "parentName": c.get("parentName") or c["name"],
                         "source": m["page"], "license": m["license"], "licenseUrl": m["licenseUrl"],
                         "author": " ".join(m["artist"].split())})
    with open(os.path.join(HERE, "samples.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print(f"samples.csv：{len(rows)} 张")


if __name__ == "__main__":
    {"candidates": candidates, "finalize": finalize}[sys.argv[1] if len(sys.argv) > 1 else "candidates"]()
