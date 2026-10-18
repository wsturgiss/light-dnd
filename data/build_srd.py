#!/usr/bin/env python3
"""Turn the 5e-bits SRD 5.2 JSON into the compact assets the tool reads.

    ./data/build_srd.py            # fetch from GitHub, write tool/src/main/assets/srd/
    ./data/build_srd.py <dir>      # read 5e-SRD-*.json already downloaded to <dir>

Each output file is a JSON array of entries (starting-gear.json aside; see starting_gear):

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


def starting_gear(classes, backgrounds, equipment_src):
    """Each class's and background's starting-equipment options, in book order.

        {"Fighter": [{"i": [[name, count, weight, in_srd], ...], "gp": 4}, ...], "Soldier": [...]}

    An item "of your choice" (a gaming set, an instrument) goes in under its generic name, with a
    fifth element listing what it can be: [[name, weight], ...].
    """
    by_index = {e["index"]: e for e in equipment_src}
    in_category = {}
    for e in equipment_src:
        for c in e["equipment_categories"]:
            in_category.setdefault(c["index"], []).append(e)

    def each(e):
        # the SRD weighs some things by the bundle (20 arrows: 1 lb); the bag weighs each one
        return ((e or {}).get("weight") or 0) / ((e or {}).get("quantity") or 1)

    def choices(choice):
        src = choice["from"]
        if src["option_set_type"] == "equipment_category":
            return [[e["name"], each(e)] for e in in_category.get(src["equipment_category"]["index"], [])]
        return [c for o in src["options"] for c in choices(o["choice"])]

    def item(it):
        if it["option_type"] == "counted_reference":
            e = by_index.get(it["of"]["index"])
            return [e["name"] if e else it["of"]["name"], it["count"], each(e), e is not None]
        choice = it["choice"]
        name = choice.get("desc") or choice["from"]["equipment_category"]["name"].removesuffix("s")
        return [name.replace("’", "'"), 1, 0, False, choices(choice)]

    out = {}
    for who, options in [(c["name"], c["starting_equipment_options"]) for c in classes] + \
                        [(b["name"], b["equipment_options"]) for b in backgrounds]:
        parts_by_option = []
        for opt in options[0]["from"]["options"]:
            parts_by_option.append(list(opt["items"] if opt["option_type"] == "multiple" else [opt]))
        if who in GEAR_FIXES:
            GEAR_FIXES[who](parts_by_option[0])
        out[who] = [{
            "i": [item(p) for p in parts if p["option_type"] != "money"],
            "gp": sum(p["count"] for p in parts if p["option_type"] == "money"),
        } for parts in parts_by_option]
    for choices_ in out.values():
        for c in choices_:
            for it in c["i"]:
                # one holy symbol is filed under the plural, the name of the kind (amulet, emblem, reliquary)
                if it[0] == "Holy Symbols":
                    it[0] = "Holy Symbol"
    return out


def _replace(old_index, new):
    def fix(parts):
        i = next(n for n, p in enumerate(parts) if p.get("of", {}).get("index") == old_index)
        parts[i] = new
    return fix


def _ref(index, count=1):
    return {"option_type": "counted_reference", "count": count, "of": {"index": index, "name": index}}


def _choose(name, category):
    return {"option_type": "choice", "choice": {"desc": name, "from": {
        "option_set_type": "equipment_category", "equipment_category": {"index": category}}}}


# Where 5e-bits' item list for an option disagrees with the option's own text ("desc"), the text
# wins. Each fix edits the first option's parts, in 5e-bits' own shape.
GEAR_FIXES = {
    # "(a) Spear, 2 Daggers, Arcane Focus (crystal), ...": the list has Leather Armor, which sorcerers aren't trained in
    "Sorcerer": _replace("leather-armor", _ref("spear")),
    # "(a) Leather Armor, 2 Daggers, Musical Instrument of your choice, ...": the list leaves the instrument out
    "Bard": lambda parts: parts.insert(2, _choose("Musical Instrument", "musical-instruments")),
}


def species(src, subspecies, traits):
    """Each species with its traits in full, then each lineage/ancestry/legacy as its own entry."""
    by_index = {t["index"]: t for t in traits}

    def body(refs):
        out = []
        for r in refs:
            t = by_index.get(r["index"])
            if not t:
                continue
            at = f" (level {r['level']})" if r.get("level", 1) > 1 else ""
            out += [f"# {t['name']}{at}", clean(t["description"]), ""]
        return "\n".join(out).strip()

    out = []
    for s in src:
        kinds = [x for x in subspecies if x["species"]["index"] == s["index"]]
        b = body(s["traits"])
        if kinds:
            b += "\n\n# Choose one\n" + "\n".join(f"@ {k['name'].split(': ', 1)[-1]}" for k in kinds)
        out.append({"n": s["name"], "m": f"Species · {s.get('size') or 'Medium or Small'} · speed {s['speed']} ft.", "b": b})
    for k in subspecies:
        kind, _, short = k["name"].partition(": ")
        out.append({"n": k["name"], "m": f"{kind} · {k['species']['name']}", "b": body(k["traits"])})
    return out


def feats(src):
    out = []
    for f in src:
        kind = {"origin": "Origin feat", "general": "General feat", "fighting-style": "Fighting style",
                "epic-boon": "Epic boon"}.get(f.get("type"), "Feat")
        body = clean(f["description"])
        if f.get("prerequisites") or f.get("prerequisite_options"):
            pre = f.get("prerequisites") or []
            text = ", ".join(p.get("desc") or p.get("name") or json.dumps(p) for p in pre if isinstance(p, dict))
            if text:
                body = f"Prerequisite: {text}\n\n" + body
        if f.get("repeatable"):
            body += "\n\n" + clean(f["repeatable"]) if isinstance(f["repeatable"], str) else ""
        out.append({"n": f["name"], "m": kind, "b": body})
    return out


def feature_names(features, subclasses):
    """Look up names for class and subclass features: a name two classes share gets its class."""
    from collections import Counter
    # 5e-bits lists subclass features in Features too; count them once, from the subclasses
    counts = Counter(f["name"] for f in features if not f.get("subclass"))
    for sc in subclasses:
        counts.update(x["name"] for x in sc["features"])
    names = {}
    for f in features:
        if not f.get("subclass"):
            names[f["index"]] = f["name"] if counts[f["name"]] == 1 else f"{f['name']} ({f['class']['name']})"
    for sc in subclasses:
        for x in sc["features"]:
            names[(sc["index"], x["name"])] = x["name"] if counts[x["name"]] == 1 else f"{x['name']} ({sc['name']})"
    return names


def class_features(features, subclasses, names):
    out = []
    for f in features:
        if f.get("subclass"):
            continue  # subclass features come from the subclasses, with their levels
        out.append({"n": names[f["index"]], "m": f"{f['class']['name']} {f['level']['name'].rsplit(' ', 1)[-1]}",
                    "b": clean(f.get("description", ""))})
    for sc in subclasses:
        lines = [clean(sc.get("description", "")), "", "# Features"] + \
                [f"@ {x['level']:>2}  {names[(sc['index'], x['name'])]}" for x in sc["features"]]
        out.append({"n": sc["name"], "m": f"{sc['class']['name']} subclass", "b": "\n".join(lines).strip()})
        for x in sc["features"]:
            out.append({"n": names[(sc["index"], x["name"])], "m": f"{sc['name']} {x['level']}",
                        "b": clean(x.get("description", ""))})
    return out


ABBR = {"strength": "STR", "dexterity": "DEX", "constitution": "CON", "intelligence": "INT", "wisdom": "WIS", "charisma": "CHA"}


def creation(classes, levels, features, subclasses, species_src, subspecies, backgrounds, feats_src, languages, names):
    """What the character builder chooses from, as names that match Look up entries."""
    def skill_names(pc):
        return [o["item"]["name"].removeprefix("Skill: ") for o in pc["from"]["options"] if "skill" in o["item"]["index"]]

    out = {"classes": {}, "species": {}, "backgrounds": {}, "feats": {}, "languages": {}}
    for c in classes:
        skill_choice = next(pc for pc in c["proficiency_choices"] if "skill" in json.dumps(pc["from"]))
        other = [pc["desc"].replace("’", "'").replace("â€™", "'") for pc in c["proficiency_choices"] if pc is not skill_choice]
        sc = next((x for x in subclasses if x["class"]["index"] == c["index"]), None)
        rows = []
        for lv in range(1, 21):
            row = next(x for x in levels if x.get("class", {}).get("index") == c["index"]
                       and x["level"] == lv and not x.get("subclass"))
            casting = row.get("spellcasting") or {}
            rows.append({
                "f": [names[f["index"]] for f in row["features"] if f["index"] in names],
                "sf": [names[(sc["index"], x["name"])] for x in sc["features"] if x["level"] == lv] if sc else [],
                "c": casting.get("cantrips_known", 0),
                "p": casting.get("prepared_spells", 0),
            })
        out["classes"][c["name"]] = {
            "hitDie": c["hit_die"],
            "saves": [s["name"] for s in c["saving_throws"]],
            "skills": skill_names(skill_choice),
            "skillCount": skill_choice["choose"],
            "training": [p["name"].removeprefix("Tool: ") for p in c["proficiencies"] if not p["name"].startswith("Saving Throw")],
            "toolChoice": other[0] if other else None,
            "subclass": sc["name"] if sc else None,
            "levels": rows,
        }
    for s in species_src:
        kinds = [k["name"] for k in subspecies if k["species"]["index"] == s["index"]]
        out["species"][s["name"]] = {
            "size": s.get("size") or "Medium or Small",
            "traits": [t["name"] for t in s["traits"]],
            "choice": kinds[0].split(": ")[0] if kinds else None,
            "options": kinds,
        }
    for b in backgrounds:
        feat = b["feat"]
        out["backgrounds"][b["name"]] = {
            "feat": feat["name"],
            "note": feat.get("note") or BACKGROUND_FEAT_NOTES.get(b["name"]),
            "tools": [p["name"].removeprefix("Tool: ") for p in b["proficiencies"] if p["name"].startswith("Tool: ")],
            "toolChoice": (b.get("proficiency_choices") or [{}])[0].get("desc"),
        }
    for f in feats_src:
        out["feats"].setdefault(f.get("type", "general"), []).append(f["name"])
    for l in languages:
        if l["name"] in CLASS_LANGUAGES:
            continue
        out["languages"].setdefault("rare" if l.get("is_rare") else "standard", []).append(l["name"])
    return out


# Languages a class feature teaches (Druid, Rogue), not ones a new character picks.
CLASS_LANGUAGES = {"Druidic", "Thieves' Cant"}

# 5e-bits gives Acolyte's Magic Initiate its list ("Cleric") but not Sage's; SRD 5.2: Sage, Magic Initiate (Wizard).
BACKGROUND_FEAT_NOTES = {"Sage": "Wizard"}



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
    equipment_src = load("Equipment", src_dir)
    classes, subclasses, features = load("Classes", src_dir), load("Subclasses", src_dir), load("Features", src_dir)
    species_src, subspecies_src, traits_src = load("Species", src_dir), load("Subspecies", src_dir), load("Traits", src_dir)
    backgrounds_src, feats_src = load("Backgrounds", src_dir), load("Feats", src_dir)
    names = feature_names(features, subclasses)
    kinds = {
        "spells": spells(load("Spells", src_dir)),
        "monsters": monsters(load("Monsters", src_dir)),
        "conditions": conditions(load("Conditions", src_dir)),
        "magic-items": magic_items(load("Magic-Items", src_dir)),
        "equipment": equipment(equipment_src),
        "species": species(species_src, subspecies_src, traits_src),
        "feats": feats(feats_src),
        "features": class_features(features, subclasses, names),
        "rules": rules(load("Skills", src_dir), load("Weapon-Properties", src_dir),
                       load("Weapon-Mastery-Properties", src_dir), load("Damage-Types", src_dir)),
    }
    for kind, entries in kinds.items():
        entries.sort(key=lambda e: e["n"].lower())
        path = OUT / f"{kind}.json"
        path.write_text(json.dumps(entries, ensure_ascii=False, separators=(",", ":")))
        print(f"{kind:12} {len(entries):4} entries  {path.stat().st_size // 1024:5} KB")
    made = creation(classes, load("Levels", src_dir), features, subclasses, species_src, subspecies_src,
                    backgrounds_src, feats_src, load("Languages", src_dir), names)
    path = OUT / "creation.json"
    path.write_text(json.dumps(made, ensure_ascii=False, separators=(",", ":")))
    print(f"{'creation':12} {len(made['classes']):4} classes  {path.stat().st_size // 1024:5} KB")
    gear = starting_gear(classes, backgrounds_src, equipment_src)
    path = OUT / "starting-gear.json"
    path.write_text(json.dumps(gear, ensure_ascii=False, separators=(",", ":")))
    print(f"{'starting-gear':12} {len(gear):4} sources  {path.stat().st_size // 1024:5} KB")


if __name__ == "__main__":
    main()
