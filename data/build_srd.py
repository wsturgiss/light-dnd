#!/usr/bin/env python3
"""Turn the 5e-bits SRD 5.2 JSON into the compact assets the tool reads.

    ./data/build_srd.py            # fetch from GitHub, write tool/src/main/assets/srd/
    ./data/build_srd.py <dir>      # read 5e-SRD-*.json already downloaded to <dir>

Each output file is a JSON array of entries:

    {"n": name, "m": one-line summary, "b": body, ...kind-specific filter fields}

Bodies use a tiny line-based markup that the tool renders (see Markup.kt):

    "# Heading"   a section heading
    "@ text"      a monospaced line (stat rows, columns that must line up)
    "**Bold.**"   inline bold, as the SRD itself writes it

The output is checked in: Light's builder only copies src/main, so the tool
cannot run this at build time.
"""

import json
import pathlib
import re
import sys
import urllib.request

SOURCE = "https://raw.githubusercontent.com/5e-bits/5e-database/main/src/2024/en/5e-SRD-{}.json"
OUT = pathlib.Path(__file__).resolve().parent.parent / "tool/src/main/assets/srd"
ABILITIES = ["strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma"]


def load(name, src_dir):
    if src_dir:
        return json.loads((pathlib.Path(src_dir) / f"{name}.json").read_text())
    with urllib.request.urlopen(SOURCE.format(name)) as r:
        return json.load(r)


def clean(text):
    """SRD text uses markdown line breaks ("  \n") and stray spaces after them."""
    text = text.replace("’", "'")
    text = re.sub(r" *\n *", "\n", text.strip())
    return re.sub(r"\n{3,}", "\n\n", text)


def signed(n):
    return f"+{n}" if n >= 0 else str(n)


def mod(score):
    return (score - 10) // 2


def ordinal(n):
    return {1: "1st", 2: "2nd", 3: "3rd"}.get(n, f"{n}th")


def spells(src):
    out = []
    for s in src:
        level = s["level"]
        school = s["school"]["name"]
        kind = f"{school} cantrip" if level == 0 else f"{ordinal(level)}-level {school.lower()}"
        tags = [t for t, on in (("C", s.get("concentration")), ("R", s.get("ritual"))) if on]
        classes = [c["name"] for c in s.get("classes", [])]
        comps = ", ".join(s.get("components", []))
        if s.get("material"):
            comps += f" ({s['material']})"
        body = [
            f"@ Cast   {s['casting_time']}" + ("  ritual" if s.get("ritual") else ""),
            f"@ Range  {s['range']}",
            f"@ Time   {s['duration']}",
            f"Components: {comps}",
            "",
            clean(s["description"]),
        ]
        if s.get("higher_level"):
            body += ["", "# At higher levels", clean(s["higher_level"])]
        body += ["", f"Classes: {', '.join(classes)}"]
        out.append({
            "n": s["name"],
            "m": kind + (" · " + " ".join(tags) if tags else ""),
            "b": "\n".join(body),
            "l": level,
            "c": classes,
        })
    return out


def monsters(src):
    out = []
    for m in src:
        cr = m["challenge_rating"]
        cr_text = {0.125: "1/8", 0.25: "1/4", 0.5: "1/2"}.get(cr, str(int(cr)) if cr == int(cr) else str(cr))
        ac = m["armor_class"][0]["value"]
        speed = ", ".join(v if k == "walk" else f"{k} {v}" for k, v in m["speed"].items() if isinstance(v, str))
        scores = [m[a] for a in ABILITIES]
        rows = [f"{a[:3].upper()} {s:>2} {signed(mod(s)):>2}" for a, s in zip(ABILITIES, scores)]
        body = [
            f"@ AC {ac}   HP {m['hit_points']} ({m['hit_points_roll']})",
            f"@ Speed {speed}",
            "",
            "@ " + "   ".join(rows[:3]),
            "@ " + "   ".join(rows[3:]),
            "",
        ]
        saves = [f"{p['proficiency']['name'].split(': ')[1]} {signed(p['value'])}"
                 for p in m["proficiencies"] if p["proficiency"]["index"].startswith("saving-throw")]
        skills = [f"{p['proficiency']['name'].split(': ')[1]} {signed(p['value'])}"
                  for p in m["proficiencies"] if p["proficiency"]["index"].startswith("skill")]
        if saves:
            body.append(f"Saves: {', '.join(saves)}")
        if skills:
            body.append(f"Skills: {', '.join(skills)}")
        for label, key in (("Vulnerable", "damage_vulnerabilities"), ("Resists", "damage_resistances"),
                           ("Immune", "damage_immunities")):
            if m.get(key):
                body.append(f"{label}: {', '.join(m[key])}")
        if m.get("condition_immunities"):
            body.append("Condition immune: " + ", ".join(c["name"] for c in m["condition_immunities"]))
        senses = ", ".join(f"{k.replace('_', ' ')} {v}" for k, v in m["senses"].items())
        body.append(f"Senses: {senses}")
        body.append(f"Languages: {m.get('languages') or 'none'}")
        body.append(f"CR {cr_text} ({m['xp']:,} XP)  PB +{m['proficiency_bonus']}")
        for title, key in (("Traits", "special_abilities"), ("Actions", "actions"),
                           ("Bonus actions", "bonus_actions"), ("Reactions", "reactions"),
                           ("Legendary actions", "legendary_actions")):
            items = m.get(key) or []
            if not items:
                continue
            body += ["", f"# {title}"]
            for a in items:
                usage = a.get("usage") or {}
                note = ""
                if usage.get("type") == "per day":
                    note = f" ({usage['times']}/day)"
                elif usage.get("type") == "recharge on roll":
                    note = f" (recharge {usage.get('min_value', 6)}-6)"
                body.append(f"**{a['name']}{note}.** {clean(a['desc'])}")
        size_type = f"{m['size']} {m['type']}"
        out.append({
            "n": m["name"],
            "m": f"CR {cr_text} · {size_type}",
            "b": "\n".join(body),
            "cr": cr,
        })
    return out


def conditions(src):
    return [{"n": c["name"], "m": "Condition", "b": clean(c["description"])} for c in src]


def magic_items(src):
    out = []
    for i in src:
        if i.get("variants"):
            continue  # the +1/+2/+3 entries each carry the full text
        desc = clean(i["desc"])
        # The first line restates type and rarity ("Weapon (Rare)"): keep it as a
        # mono line so it reads as a header, not as the opening of the prose.
        first, _, rest = desc.partition("\n")
        meta = [i["equipment_category"]["name"], i["rarity"]["name"]]
        if i.get("attunement"):
            meta.append("attunement")
        out.append({
            "n": i["name"],
            "m": " · ".join(meta),
            "b": f"@ {first}\n\n{rest.strip()}" if rest.strip() else first,
        })
    return out


def equipment(src):
    out = []
    for e in src:
        cats = [c["name"] for c in e["equipment_categories"]]
        cat = next((c for c in cats if c not in ("Weapons", "Melee Weapons", "Ranged Weapons",
                                                  "Simple Weapons", "Martial Weapons", "Armor")), cats[0])
        cost = f"{e['cost']['quantity']} {e['cost']['unit']}" if e.get("cost") else ""
        weight = f"{e['weight']:g} lb" if e.get("weight") else ""
        body = []
        if e.get("damage"):
            d = e["damage"]
            line = f"@ {d['damage_dice']} {d['damage_type']['name'].lower()}"
            if e.get("two_handed_damage"):
                line += f"  ({e['two_handed_damage']['damage_dice']} two-handed)"
            body.append(line)
        if e.get("range") and e["range"].get("long"):
            body.append(f"@ Range {e['range']['normal']}/{e['range']['long']} ft.")
        if e.get("properties"):
            body.append("Properties: " + ", ".join(p["name"] for p in e["properties"]))
        if e.get("mastery"):
            body.append(f"Mastery: {e['mastery']['name']}")
        if e.get("armor_class"):
            a = e["armor_class"]
            ac = f"{a['base']}" + (" + Dex" if a.get("dex_bonus") else "")
            if a.get("max_bonus"):
                ac += f" (max {a['max_bonus']})"
            body.append(f"@ AC {ac}")
            if e.get("str_minimum"):
                body.append(f"Strength {e['str_minimum']} required")
            if e.get("stealth_disadvantage"):
                body.append("Disadvantage on Stealth")
        if e.get("description"):
            body += ["", clean(e["description"])]
        out.append({
            "n": e["name"],
            "m": " · ".join(x for x in (cat, cost, weight) if x),
            "b": "\n".join(body).strip() or cat,
            "w": e.get("weight") or 0,
        })
    return out


def rules(skills, props, mastery, damage):
    out = []
    for s in skills:
        out.append({"n": s["name"], "m": f"Skill · {s['ability_score']['name']}", "b": clean(s["description"])})
    for p in props:
        out.append({"n": p["name"], "m": "Weapon property", "b": clean(" ".join(p.get("desc", [])) if isinstance(p.get("desc"), list) else p.get("description", p.get("desc", "")))})
    for p in mastery:
        out.append({"n": p["name"], "m": "Weapon mastery", "b": clean(p.get("description", ""))})
    for d in damage:
        text = d.get("description") or " ".join(d.get("desc", []))
        out.append({"n": d["name"], "m": "Damage type", "b": clean(text)})
    return out


def main():
    src_dir = sys.argv[1] if len(sys.argv) > 1 else None
    OUT.mkdir(parents=True, exist_ok=True)
    kinds = {
        "spells": spells(load("Spells", src_dir)),
        "monsters": monsters(load("Monsters", src_dir)),
        "conditions": conditions(load("Conditions", src_dir)),
        "magic-items": magic_items(load("Magic-Items", src_dir)),
        "equipment": equipment(load("Equipment", src_dir)),
        "rules": rules(load("Skills", src_dir), load("Weapon-Properties", src_dir),
                       load("Weapon-Mastery-Properties", src_dir), load("Damage-Types", src_dir)),
    }
    for kind, entries in kinds.items():
        entries.sort(key=lambda e: e["n"].lower())
        path = OUT / f"{kind}.json"
        path.write_text(json.dumps(entries, ensure_ascii=False, separators=(",", ":")))
        print(f"{kind:12} {len(entries):4} entries  {path.stat().st_size // 1024:5} KB")


if __name__ == "__main__":
    main()
