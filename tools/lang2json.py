"""Convert upstream Emoticons .lang files to 1.21.8 JSON language files.

Only the keys that survive the port are kept:
  emoticons.emotes.*          -> kukeemotes.emote.*      (emote titles/descriptions)
  emoticons.keys.*            -> key.kukeemotes.*        (keybinds, remapped by hand below)
Metamorph/Blockbuster/command keys are dropped.
"""
import json
import os
import re

SRC = r"D:\kuke-src-git\KukeEmotes\upstream-lang"
DST = r"D:\kuke-src-git\KukeEmotes\src\main\resources\assets\kukeemotes\lang"

# upstream file name -> 1.21.8 language code
FILES = {
    "en_US.lang": "en_us.json",
    "zh_CN.lang": "zh_cn.json",
    "zh_TW.lang": "zh_tw.json",
    "ru_RU.lang": "ru_ru.json",
    "es_ES.lang": "es_es.json",
    "ua_UA.lang": "uk_ua.json",
}

os.makedirs(DST, exist_ok=True)

for src_name, dst_name in FILES.items():
    src = os.path.join(SRC, src_name)
    if not os.path.exists(src):
        continue
    out = {}
    with open(src, encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n").rstrip("\r")
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            # .lang used %n / \n escapes; JSON lang files take literal newlines
            value = value.replace("\\n", "\n")
            m = re.match(r"^emoticons\.emotes\.([a-z0-9_]+)\.(title|desc)$", key)
            if m:
                out["kukeemotes.emote.%s.%s" % (m.group(1), m.group(2))] = value
                continue
            if key == "emoticons.translation.credit":
                out["kukeemotes.translation.credit"] = value
    # keys added by the port (not present upstream in this form)
    if dst_name == "en_us.json":
        out.update({
            "key.categories.kukeemotes": "KukeEmotes",
            "key.kukeemotes.wheel": "Emote wheel",
            "key.kukeemotes.stop": "Stop emote",
            "key.kukeemotes.slot": "Emote slot %s",
            "kukeemotes.ui.title": "Emotes",
            "kukeemotes.ui.locked": "Locked",
            "kukeemotes.ui.page": "%s / %s",
        })
    elif dst_name == "zh_cn.json":
        out.update({
            "key.categories.kukeemotes": "表情动作",
            "key.kukeemotes.wheel": "表情轮盘",
            "key.kukeemotes.stop": "停止表情",
            "key.kukeemotes.slot": "表情快捷位 %s",
            "kukeemotes.ui.title": "表情",
            "kukeemotes.ui.locked": "未解锁",
            "kukeemotes.ui.page": "%s / %s",
        })
    with open(os.path.join(DST, dst_name), "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write("\n")
    print("%s -> %s  (%d keys)" % (src_name, dst_name, len(out)))
