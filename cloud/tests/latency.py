"""Round-trip latency of the deployed look-closer Lambda: python latency.py <function url> <key> [lang]

Posts every JPEG under cloud/tests/fixtures and app/src/androidTest/assets/photos (first frame of each folder),
prints each answer, then p50 and p95 in milliseconds and a Markdown table for docs/hackathon/evaluation.md.
Standard library only, so it runs from any Python 3.
"""
import base64
import json
import pathlib
import statistics
import sys
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]


def photos():
    out = sorted((ROOT / "cloud" / "tests" / "fixtures").glob("*.jpg"))
    for folder in sorted((ROOT / "app" / "src" / "androidTest" / "assets" / "photos").glob("*")):
        if folder.is_dir():
            frames = sorted(folder.glob("*.jpg"))
            if frames:
                out.append(frames[0])
    return out


def ask(url, key, path, lang):
    body = json.dumps({"image": base64.b64encode(path.read_bytes()).decode(), "lang": lang}).encode()
    req = urllib.request.Request(url.rstrip("/") + "/look", data=body, method="POST",
                                 headers={"content-type": "application/json", "x-nungil-key": key})
    t = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            data = json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return None, (time.perf_counter() - t) * 1000, "HTTP %d" % e.code
    return data, (time.perf_counter() - t) * 1000, None


def main(url, key, lang="en"):
    rows = []
    for path in photos():
        data, ms, err = ask(url, key, path, lang)
        name = path.parent.name if path.name.startswith("0") else path.stem
        if err:
            print("%-28s %6.0f ms  %s" % (name, ms, err))
            continue
        rows.append((name, ms, data.get("ms", 0), data["answer"]))
        print("%-28s %6.0f ms (lambda %4d ms)  %s" % (name, ms, data.get("ms", 0), data["answer"]))
    if not rows:
        raise SystemExit("no answers")
    totals = sorted(r[1] for r in rows)
    p50 = statistics.median(totals)
    p95 = totals[min(len(totals) - 1, int(round(0.95 * (len(totals) - 1))))]
    print("\nround trip p50 %.0f ms, p95 %.0f ms over %d photos (%s)\n" % (p50, p95, len(rows), lang))
    print("| Photo | Round trip (ms) | In the Lambda (ms) | Answer |")
    print("|---|---|---|---|")
    for name, ms, lam, answer in rows:
        print("| %s | %.0f | %d | %s |" % (name, ms, lam, answer.replace("|", "/")))


if __name__ == "__main__":
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    main(*sys.argv[1:4])
