"""Draw a Nungil decision trace: python plot.py <trace.jsonl> <out.png>

One row per mode (find, walk, cloud), time on the x axis, one marker per event coloured by what the user got:
a beep, a buzz, a sentence, or nothing. Pull the trace with
`adb exec-out run-as com.nungil cat files/trace/<date>.jsonl > trace.jsonl`.
"""
import json
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

MODES = ["find", "walk", "cloud"]
COLOURS = {"say": "#1f77b4", "beep": "#ff7f0e", "buzz": "#2ca02c", "none": "#bbbbbb"}


def kind(action: str) -> str:
    if action.startswith("say") or "; say" in action:
        return "say"
    if action.startswith("buzz") or "buzz" in action:
        return "buzz"
    if action.startswith("beep"):
        return "beep"
    return "none"


def main(path: str, out: str) -> None:
    events = [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]
    if not events:
        raise SystemExit("no events in " + path)
    t0 = min(e["at"] for e in events)
    fig, ax = plt.subplots(figsize=(10, 3))
    for e in events:
        if e["mode"] not in MODES:
            continue
        y = MODES.index(e["mode"])
        k = kind(e["action"])
        ax.scatter((e["at"] - t0) / 1000.0, y, s=18 if k != "none" else 6, color=COLOURS[k], zorder=3)
    ax.set_yticks(range(len(MODES)))
    ax.set_yticklabels(MODES)
    ax.set_xlabel("seconds")
    ax.set_title("Perception to action: %d events" % len(events))
    for k, c in COLOURS.items():
        ax.scatter([], [], color=c, label=k)
    ax.legend(loc="upper right", ncol=4, frameon=False)
    ax.grid(axis="x", alpha=0.3)
    fig.tight_layout()
    fig.savefig(out, dpi=150)
    print("wrote", out, "with", len(events), "events")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
