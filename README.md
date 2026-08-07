# KukeEmotes

A **client-only** emote mod for Minecraft **1.21.8 / NeoForge**, ported from
[**McHorse's Emoticons**](https://github.com/mchorse/emoticons).

67 skeletal emotes (dances, gestures, memes), their music, and the props that go with them are the
original work of **McHorse** and the Emoticons contributors, taken from the upstream repository
under the terms of the GNU General Public License v3.0.

## Relationship to upstream

| | Upstream Emoticons | KukeEmotes |
|---|---|---|
| Minecraft | 1.12.2 / 1.8.9, Forge | 1.21.8, NeoForge |
| Dependencies | McLib (+ optional Metamorph / Blockbuster) | none |
| Sides | client + server halves | **client only** |
| Rendering | CPU skinning → fixed-function VBOs | CPU skinning → `VertexConsumer` |
| Player model | replaced full-time (toggleable) | replaced **only while emoting** |
| Models shipped | simple + high-poly + 3D | simple (blocky) only |
| Multiplayer sync | own Forge packet channel | KukeUI plugin-message bridge ↔ KukeCore plugin |

Not ported: the Metamorph morph, the Blockbuster action-recording integration, and the server-side
capability/command layer.

## Licence

GPL-3.0-only, inherited from upstream. See `LICENSE.txt`.

Original project: <https://github.com/mchorse/emoticons> by **McHorse** (Ivan Zhukov) and
contributors. Assets under `src/main/resources/assets/kukeemotes/` (`*.bobj` models and animations,
sounds, textures) are taken from that repository unmodified except for the resource-domain rename.

## Building

```
gradlew build          # requires JDK 21
```

The jar drops in `build/libs/kukeemotes-<version>.jar` and goes in the client's `mods/` folder.

## Sync

If [KukeUI](https://github.com/kukemc) is installed the mod reflectively resolves
`kuke.kukeui.client.net.UiPayloadBridge` and talks namespace `emotes` over `kukeui:main`; the
KukeCore Paper plugin broadcasts emote state to nearby players and owns the interrupt rules.
Neither is a build dependency — if the bridge is absent, everything no-ops and emotes stay local.
