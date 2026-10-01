#!/usr/bin/env python3
"""Converts trail-data/*.js (window.TRAILS[...] = {...}) into the bundled app assets:

  app/src/main/assets/trails/<slug>.json  - one per trail, static route data
  app/src/main/assets/trails/index.json   - display order (array of slugs)
  app/src/main/assets/seed-progress.json  - the owner's existing logged stages,
                                             imported into Room on first launch

The source files are JS object literals, not JSON (unquoted keys, // comments,
trailing commas, HTML entities in prose). This is a hand-rolled tokenizer/parser
for that specific subset rather than a regex "quote the keys" hack, because notes
like "Knockholt Pound: taxi/Uber" contain colons inside string values that a
naive regex would misread as object keys.

Re-run this after editing trail-data/*.js by hand. Do not hand-edit the
generated assets.
"""
import html
import json
import re
import sys
import unicodedata
from datetime import datetime
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

REPO_ROOT = Path(__file__).resolve().parent.parent
TRAIL_DATA_DIR = REPO_ROOT / "trail-data"
ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets"
TRAILS_ASSETS_DIR = ASSETS_DIR / "trails"


# ---------------------------------------------------------------------------
# Tokenizer + recursive-descent parser for the JS object-literal subset used
# in trail-data/*.js: objects, arrays, double-quoted strings, numbers, and
# // line comments. No template literals, no single-quoted strings, no
# multi-line strings - the source files don't use them.
# ---------------------------------------------------------------------------

TOKEN_RE = re.compile(
    r"""
      (?P<WS>\s+)
    | (?P<COMMENT>//[^\n]*)
    | (?P<STRING>"(?:\\.|[^"\\])*")
    | (?P<NUMBER>-?\d+(?:\.\d+)?)
    | (?P<IDENT>[A-Za-z_]\w*)
    | (?P<LBRACE>\{)
    | (?P<RBRACE>\})
    | (?P<LBRACKET>\[)
    | (?P<RBRACKET>\])
    | (?P<COLON>:)
    | (?P<COMMA>,)
    | (?P<SEMI>;)
    """,
    re.VERBOSE,
)

STRING_ESCAPES = {
    '"': '"', "\\": "\\", "/": "/", "n": "\n", "t": "\t", "r": "\r",
}


def tokenize(text):
    tokens = []
    pos = 0
    while pos < len(text):
        m = TOKEN_RE.match(text, pos)
        if not m:
            raise ValueError(f"Unexpected character {text[pos]!r} at offset {pos}")
        kind = m.lastgroup
        value = m.group()
        pos = m.end()
        if kind in ("WS", "COMMENT"):
            continue
        tokens.append((kind, value))
    tokens.append(("EOF", ""))
    return tokens


def decode_string(raw):
    inner = raw[1:-1]
    out = []
    i = 0
    while i < len(inner):
        c = inner[i]
        if c == "\\" and i + 1 < len(inner):
            nxt = inner[i + 1]
            if nxt == "u" and i + 5 < len(inner):
                out.append(chr(int(inner[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(STRING_ESCAPES.get(nxt, nxt))
            i += 2
            continue
        out.append(c)
        i += 1
    return html.unescape("".join(out))


class Parser:
    def __init__(self, tokens):
        self.tokens = tokens
        self.i = 0

    def peek(self):
        return self.tokens[self.i]

    def next(self):
        tok = self.tokens[self.i]
        self.i += 1
        return tok

    def expect(self, kind):
        tok = self.next()
        if tok[0] != kind:
            raise ValueError(f"Expected {kind}, got {tok} at token {self.i}")
        return tok

    def parse_value(self):
        kind, value = self.peek()
        if kind == "LBRACE":
            return self.parse_object()
        if kind == "LBRACKET":
            return self.parse_array()
        if kind == "STRING":
            self.next()
            return decode_string(value)
        if kind == "NUMBER":
            self.next()
            return float(value) if "." in value else int(value)
        if kind == "IDENT" and value in ("true", "false"):
            self.next()
            return value == "true"
        if kind == "IDENT" and value == "null":
            self.next()
            return None
        raise ValueError(f"Unexpected token {kind} {value!r} at token {self.i}")

    def parse_object(self):
        self.expect("LBRACE")
        obj = {}
        while self.peek()[0] != "RBRACE":
            key_kind, key_value = self.next()
            if key_kind not in ("STRING", "IDENT"):
                raise ValueError(f"Expected object key, got {key_kind} {key_value!r}")
            key = decode_string(key_value) if key_kind == "STRING" else key_value
            self.expect("COLON")
            obj[key] = self.parse_value()
            if self.peek()[0] == "COMMA":
                self.next()
        self.expect("RBRACE")
        return obj

    def parse_array(self):
        self.expect("LBRACKET")
        arr = []
        while self.peek()[0] != "RBRACKET":
            arr.append(self.parse_value())
            if self.peek()[0] == "COMMA":
                self.next()
        self.expect("RBRACKET")
        return arr


ASSIGNMENT_RE = re.compile(
    r'window\.TRAILS\["([\w-]+)"\]\s*=\s*', re.MULTILINE
)


def parse_trail_file(path):
    text = path.read_text(encoding="utf-8")
    m = ASSIGNMENT_RE.search(text)
    if not m:
        raise ValueError(f"{path}: couldn't find window.TRAILS[\"slug\"] = assignment")
    slug = m.group(1)
    body = text[m.end():]
    if body.rstrip().endswith(";"):
        body = body.rstrip()[:-1]
    tokens = tokenize(body)
    parser = Parser(tokens)
    data = parser.parse_value()
    if parser.peek()[0] != "EOF":
        raise ValueError(f"{path}: trailing content after the object literal")
    return slug, data


# ---------------------------------------------------------------------------
# Conversion to the app's JSON schema
# ---------------------------------------------------------------------------

def convert_landmark(raw):
    name = raw[0]
    miles = float(raw[1])
    note = raw[2] if len(raw) > 2 else None
    return {"name": name, "milesFromStart": miles, "note": note, "lat": None, "lon": None}


def convert_trail(slug, data, landmark_names):
    stages = data.get("stages", [])
    default_stages = []
    for stage in stages:
        from_name = stage["from"]
        to_name = stage["to"]
        if from_name not in landmark_names:
            raise ValueError(f"{slug}: stage 'from' landmark not found: {from_name!r}")
        if to_name not in landmark_names:
            raise ValueError(f"{slug}: stage 'to' landmark not found: {to_name!r}")
        default_stages.append({
            "fromLandmark": from_name,
            "toLandmark": to_name,
            "note": stage.get("note"),
        })

    return {
        "id": slug,
        "name": data["name"],
        "route": data["route"],
        "startLabel": data["startLabel"],
        "endLabel": data["endLabel"],
        "colour": data["colour"],
        "subtitle": data["subtitle"],
        "footer": data["footer"],
        "landmarks": [convert_landmark(l) for l in data["landmarks"]],
        "defaultStages": default_stages,
        "gpxAsset": None,
    }


def parse_source_date(date_str):
    # Source format: "17 Jun 2026" -> ISO "2026-06-17"
    return datetime.strptime(date_str, "%d %b %Y").date().isoformat()


def extract_seed_logs(slug, data):
    logs = []
    for stage_index, stage in enumerate(data.get("stages", [])):
        if "date" not in stage:
            continue
        logs.append({
            "trailId": slug,
            "stageIndex": stage_index,
            "dateWalked": parse_source_date(stage["date"]),
            "steps": stage.get("steps"),
            "notes": None,
            "actualMiles": None,
        })
    return logs


def sort_key(name):
    n = name[4:] if name.startswith("The ") else name
    n = unicodedata.normalize("NFKD", n)
    n = "".join(c for c in n if not unicodedata.combining(c))
    return n.lower()


def main():
    TRAILS_ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    source_files = sorted(TRAIL_DATA_DIR.glob("*.js"))
    if not source_files:
        print(f"No .js files found in {TRAIL_DATA_DIR}", file=sys.stderr)
        sys.exit(1)

    trails = []
    all_seed_logs = []

    for path in source_files:
        slug, data = parse_trail_file(path)
        if slug != path.stem:
            print(f"warning: {path.name} key {slug!r} != filename stem {path.stem!r}", file=sys.stderr)

        landmark_names = [l[0] for l in data["landmarks"]]
        if len(landmark_names) != len(set(landmark_names)):
            seen = set()
            dupes = [n for n in landmark_names if n in seen or seen.add(n)]
            raise ValueError(f"{slug}: duplicate landmark name(s): {dupes}")

        trail = convert_trail(slug, data, set(landmark_names))
        trails.append(trail)
        all_seed_logs.extend(extract_seed_logs(slug, data))

        out_path = TRAILS_ASSETS_DIR / f"{slug}.json"
        out_path.write_text(json.dumps(trail, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

        stage_count = len(trail["defaultStages"])
        total_miles = trail["landmarks"][-1]["milesFromStart"] if trail["landmarks"] else 0.0
        print(f"  {slug}: {stage_count} stages, {total_miles:g} miles -> {out_path.relative_to(REPO_ROOT)}")

    index = [t["id"] for t in sorted(trails, key=lambda t: sort_key(t["name"]))]
    index_path = TRAILS_ASSETS_DIR / "index.json"
    index_path.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    print(f"\nWrote {index_path.relative_to(REPO_ROOT)} ({len(index)} trails, display order):")
    for slug in index:
        name = next(t["name"] for t in trails if t["id"] == slug)
        print(f"  - {name}")

    seed_path = ASSETS_DIR / "seed-progress.json"
    seed_path.write_text(json.dumps({"stageLogs": all_seed_logs}, indent=2) + "\n", encoding="utf-8")
    print(f"\nWrote {seed_path.relative_to(REPO_ROOT)} ({len(all_seed_logs)} logged stages)")


if __name__ == "__main__":
    main()