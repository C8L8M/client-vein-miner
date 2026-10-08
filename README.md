# Client-Side Vein Miner

**Version 0.1** ｜ Minecraft **1.21.11** ｜ Fabric ｜ **client-side** mod

> [!CAUTION]
> ## ⚠️ Use at your own risk — you are responsible for any server ban
>
> **Using this mod can get you kicked, rolled back, wiped or permanently banned, and the author
> and contributors accept no responsibility or liability for that or for any other consequence of
> using it.**
>
> - This mod **automates block breaking on your client**: it keeps mining block after block on
>   your behalf. In one mode (*Creative Ignore Distance → All*, when the server runs Servux) it
>   hands a whole area edit to the server. Even though every block is broken through the **real
>   vanilla path** (real animation, drops, durability, server validation), most servers consider
>   automation, macros, "chain mining" mods and client-side helpers to be **cheating**.
> - **Anti-cheat plugins may flag it.** The burst of block-breaking packets a chain produces can
>   look like an automated client, and some servers ban for the packet pattern alone, regardless
>   of how vanilla the individual break is.
> - **Consequences are yours, not ours**: kicks, mutes, item wipes, world rollbacks, rank or
>   whitelist removal, permanent account or IP bans, lost progress or in-game purchases — on any
>   server, including ones where you are staff or a paying player, and including single-player
>   worlds or SMPs with their own rules.
> - **Check the rules of every server before you join it with this mod installed.** If the rules
>   are unclear, or client-side automation / chain mining is not explicitly allowed, **do not use
>   this mod there**. Being client-side and server-invisible is not permission — "the server did
>   not detect it" is not the same as "the server allows it".
> - The mod is provided **as is, without warranty of any kind**, and the author and contributors
>   are **not liable** for any ban, punishment, item loss, world damage, rollback or other damage
>   arising from downloading, installing or using it (see [License](#license), LGPL-3.0).
>
> **You installed it, you pressed the hotkey, the account is yours. Use it in single player first,
> and only where automation is allowed.**

Hold a hotkey while pointing at a block and the mod outlines the whole connected group of same-type blocks in white, with a block counter next to the crosshair. Mine any one of them and the rest are mined one by one through the **real vanilla mining path** — real break animation, drops, tool durability, enchantments and server checks. Reachable targets are green, out of reach ones red, refreshed in real time as you move.

- mod id: `client_vein_miner`
- repository: <https://github.com/C8L8M/client-vein-miner>
- 中文文档: [README.zh_CN.md](README.zh_CN.md)

---

## 1. Usage (default keys)

| Step | Action | What happens |
| --- | --- | --- |
| 1 | Hold **`` ` ``** (activate hotkey) while pointing at a block | The whole group of same-type blocks gets a **see-through white group outline**, and the HUD shows `Chainable: N blocks` next to the crosshair |
| 2 | Mine one of those blocks | The chain starts: the remaining blocks are coloured **green / red** by reachability, HUD shows `Remaining: N blocks (reachable M)` |
| 3 | Move around | Green / red **refreshes live**; newly reachable targets join the mining queue |
| 4 | End | Everything mined **or** press **X** (force stop); optionally "stop on key release" |

- **Open the config GUI**: `` ` `` + **C** (or through ModMenu).
- The activate and force-stop hotkeys both **allow extra keys held at the same time**, so you can keep walking while holding them.
- Turning off the main toggle or the chain mining toggle mid-chain stops the chain immediately.

## 2. What gets chained

- Flood fill over the **3×3×3 neighbourhood including diagonals** (26 neighbours, not just the 6 faces).
- By default **same block type** is enough (orientation is ignored); with "Match Exact State" only blocks with an identical block state are chained (e.g. one log orientation).
- "Ignore Tile Entities" is on by default: chests, furnaces and friends are skipped so their contents are not lost.
- "Max Blocks" defaults to **128** (1–1024); ids in "Block Blacklist" are never chained.

## 3. Mining is simulated, never instant

- Every block goes through the **vanilla interaction path** (`attackBlock` / `updateBlockBreakingProgress`): break animation, drops, durability, efficiency, and the server still validates everything. The mod never deletes blocks behind the server's back.
- **Mining Progress Threshold** decides when the mod sends its own stop packet: `100` = wait for the full break, exactly vanilla timing; `70` is the lowest the server accepts and is roughly 30% faster.
- **Mining Interval**: ticks to wait after finishing one block before starting the next (20 ticks = 1 s, 0 = no delay).
- **Creative + interval 0** breaks every reachable target in the same tick, through the vanilla instant-break rule (in survival, efficiency V + haste II does the same thing and is equally legitimate).
- **Auto Stop Seconds** (default 5): a chain with no progress for that long stops by itself, e.g. when every remaining target is out of reach. **The countdown does not run while the hotkey is held**; it starts once you let go, is drawn on the line **below** the counter (`Stops in 3s`, turning red at ≤3 s) and only shows up when there are **no reachable blocks left**.
- "Stop On Key Release" is **off** by default: normally a chain only ends by finishing or by the force stop key.
- **Pausing in single player (Esc menu) freezes the whole state machine**: no packets, no preview refresh, no countdown. Unpausing resumes exactly where it left off.

## 4. Reachability and colours

- **Reach Distance** defaults to 0 = vanilla block interaction range; it can be raised up to 32 blocks.
- **Require Line of Sight** is **off** by default.
- **Reachable Color** `#00FF00` and **Unreachable Color** `#FF0000` are plain **RGB** colours; the fill opacity comes from **Highlight Opacity** (default 20%). With outline rendering off you get pure fills, MiniHUD shape-renderer style.
- **Creative Ignore Distance** has three states (default **Single Player**):
  - **Off** — always use the vanilla reach distance;
  - **Single Player** — in creative mode, lift the reach check entirely in a world this client hosts;
  - **All** — the same, plus: on a server running **Servux** the whole group is handed over as **one area edit**, which is the only distance-free route on a dedicated server. That path drops nothing, updates no neighbours and can only edit loaded chunks. Without Servux it silently falls back to normal chain mining.

> Byte-level details of the Servux channel: [docs/servux-task-packet.md](docs/servux-task-packet.md).

## 5. Rendering

- **White preview outline**: the group's **outer silhouette** only (coplanar seams de-duplicated; a single block shows a full cube wireframe), drawn **see-through**, width = "Line Width" (default 2.0).
- **Red / green targets** have two modes that follow the hotkey:
  - **hotkey held** — per-face see-through rendering (only outward faces, visible through walls);
  - **hotkey released** — plain per-block boxes with depth testing (hidden behind other blocks).
- The HUD counter sits next to the crosshair (+14 px) and can be turned off with "Show Count": `Chainable: N blocks` while previewing, `Remaining: N blocks (reachable M)` while chaining.

## 6. Configuration

The GUI is titled **Chain Mining Configs - version** and has three tabs; ModMenu also recognises the mod in its list.

### General

| Option | Default | Notes |
| --- | --- | --- |
| Main Toggle | on | Master switch for the whole mod |
| Chain Mining | on | The feature itself; every setting on the Chain Mining tab needs it |
| Open Config GUI | `` ` ``+C | Hotkey |

### Misc

Empty for now, reserved for future features.

### Chain Mining

| Option | Default | Range | Notes |
| --- | --- | --- | --- |
| Chain Mining | on | — | Also shown on the General tab |
| Max Blocks | 128 | 1–1024 | Maximum blocks in one chain |
| Match Exact State | off | — | Only chain blocks with an identical block state |
| Ignore Tile Entities | on | — | Skip chests, furnaces, … |
| Mining Progress Threshold | 100 | 70–100 | Send the stop packet at this percentage; 100 = vanilla timing |
| Mining Interval | 2 | 0–40 | Ticks to wait between blocks |
| Auto Stop Seconds | 5 | 0–600 | Stop after this long without progress; 0 = never |
| Reach Distance | 0.0 | 0–32 | 0 = vanilla block interaction range |
| Creative Ignore Distance | Single Player | Off / Single Player / All | See section 4 |
| Require Line of Sight | off | — | Demand a clear line of sight |
| Stop On Key Release | off | — | Abort the chain when the hotkey is released |
| Render Outline | off | — | Draw wireframe edges on top of the fills |
| Show Count | on | — | Block counter next to the crosshair |
| Line Width | 2.0 | 1–8 | Width of the preview outline and target borders |
| Highlight Opacity | 20 | 0–100 | Fill opacity of the red/green targets; the preview outline stays opaque |
| Preview Color | `#FFFFFF` | — | Plain RGB |
| Reachable Color | `#00FF00` | — | Plain RGB |
| Unreachable Color | `#FF0000` | — | Plain RGB |
| Block Blacklist | empty | — | Block ids that are never chained, e.g. `minecraft:stone` |
| Activate Hotkey | `` ` `` | — | Hold to preview / start a chain |
| Force Stop Key | X | — | Abort the chain immediately |

> Changing a default only affects **new** configs; an existing `config/client_vein_miner.json` has to be edited or deleted to pick up new defaults.

## 7. Installation and building

**Install**: Minecraft 1.21.11 + Fabric Loader ≥ 0.19.5 + Fabric API, plus **malilib** ([sakura-ryoko's `LTS/1.21.11` branch](https://github.com/sakura-ryoko/malilib), 0.27.20 or newer). Drop `client_vein_miner-<version>.jar` and those into `mods/`. ModMenu is optional.

**Build**: Gradle 9.7.0 + Loom 1.17.21, **JDK 21** required:

```bash
./gradlew build
```

The jar ends up in `build/libs/client_vein_miner-<version>.jar`. The malilib dependency comes from `https://masa.dy.fi/maven/sakura-ryoko`, which `build.gradle` already configures.

## 8. Technical notes

- **Minecraft 1.21.11** + **Fabric** (Loader 0.19.5, Fabric API 0.141.6+1.21.11), **Java 21**.
- Config and hotkeys are built on **malilib** (sakura-ryoko's `LTS/1.21.11` branch, 0.27.20): a proper hotkey category, a searchable config GUI and ModMenu integration.
- Ships **en_us / zh_cn** translations (57 keys each, identical key sets).
- Five client mixins: the block-break callback, vanilla manual-mining suppression, the reach check, the client world tick manager accessor and the break cooldown accessor.

## License

Released under the **GNU Lesser General Public License v3.0 (LGPL-3.0-only)**, © 2026 C8L8M.

- Full terms: [LICENSE](LICENSE) (the LGPLv3 text)
- The GPLv3 text that LGPLv3 incorporates by reference: [licenses/GPL-3.0.txt](licenses/GPL-3.0.txt)

In short: use it, modify it, redistribute it (modpacks, paid or not, are fine) and link it into
larger works; but a modified version of this mod must keep its source under the same license and
keep the copyright notices. Every source file carries an SPDX header.
