# Getting started with 3790's Fashion (1.0 candidate)

This guide applies to `1.0.0`, not a released 1.0 build. The current published version remains 0.5.0; use the eventual release instructions for the final package. [简体中文](getting-started-zh_cn.md)

## Install and open the wardrobe

Use Minecraft Java 26.2, Java 25, Fabric Loader 0.19.3 and Fabric API 0.158.0+26.2. Put this candidate's main JAR and Fabric API in the instance's `mods/` folder. Full multiplayer features require both client and server installation; singleplayer needs only the client installation. Exit normally and back up your configuration and world before upgrading. Do not load two main Mod versions together.

Players do not need the Visual API compile-only JAR or the optional Elytra Slot add-on. The API JAR is a development input, not a player dependency.

Right-click a vanilla armor stand with an item such as a stick when that action has no vanilla equipment effect. Equipping and removing items take priority; otherwise the wardrobe opens. Hold secondary use (usually Sneak) to bypass the wardrobe. The Mod adds no items or equipment attributes.

## Select, preview and save

- **Cape**: choose Original or a resource. The associated Elytra appearance follows the Cape choice. There is no separate hidden-Cape or Elytra cosmetic selection.
- **Outfit**: choose all parts, a group or an individual part, then Original, No overlay or a resource. No overlay hides only the skin overlay, keeping the base player. A partial resource changes only supported parts.
- **Armor**: choose Helmet, Chestplate, Leggings or Boots, then Original, Hidden or a texture. Hidden removes the complete armor visual but preserves the real item, attributes and durability. Empty equipment slots stay empty.

The 2D samples show resource artwork without the actual skin, dye, trim or enchantment. The 3D preview shows the player and real equipment. The preview button icon shows its current Cape/Elytra mode; its tooltip explains the next action. Cape opens facing the back; Outfit and Armor face the front. You can then drag to rotate. Changing preview mode never saves a selection.

All three tabs share one draft. Switching tabs or parts preserves it without saving. Click **Apply** once to submit every changed field. The window stays open after server confirmation; Apply is disabled when nothing changed. Cancel, E and Escape discard unapplied drafts, but cannot withdraw a sent request or undo a completed Apply. Reopen to check the saved selection.

Read external-conflict messages before continuing. The wardrobe's **Reload** discards all drafts and reads the latest saved choices; it is unavailable while awaiting a request. It does not reload resource files. Use Tab/Shift+Tab to move focus and Enter/Space to activate. PageUp/PageDown reads all pages of long tooltips. Language follows vanilla settings; closing the wardrobe to change language discards unapplied drafts normally.

## Add resources

In singleplayer, paths are relative to the **game instance directory**, not a config folder inside the world. On a Dedicated Server, they are relative to the **server working directory**. Resources live in `config/3790s-fashion/`. Multiplayer clients download server resources automatically. An `<id>` is a directory name and saved identity, not a translated display name.

| Type | Files under the resource root | Basic requirements | Refresh |
| --- | --- | --- | --- |
| Cape | `capes/<id>/cape.png`; optionally `elytra.png`; or only `cape_elytra.png` | 64×32 PNG, ≤65536 bytes each; do not mix the three layouts | Restart the server/singleplayer world normally |
| Outfit | `outfits/<id>/wide.png` and/or `slim.png` | 64×64 PNG, ≤65536 bytes each; arm widths are not converted | `/fashion3790 reload` |
| Armor | `armor/<id>/armor.json` and its declared PNG files | JSON required; 64×32, 8-bit RGBA, non-interlaced, alpha 0/255, ≤16384 bytes each | `/fashion3790 reload` |

Cape examples are available in the installation package's `templates/capes/`; see the [Cape layout reference](cape-cosmetic-asset-layout.md). PNG-only Outfits infer supported parts from non-transparent overlay UV pixels. If both models are supplied, their inferred parts must match. Explicit JSON can declare fully transparent parts. An invalid JSON file does not fall back to PNG-only mode.

This **outfit.json fragment** also needs your own valid WIDE-UV `wide.png`; it is not a complete installable resource:

```json
{"schema_version":1,"parts":["head"],"models":["wide"]}
```

This **armor.json fragment** belongs in `demo_helmet` and also needs your own armor-UV `outer.png`:

```json
{"format_version":1,"id":"demo_helmet","name":"Example helmet","slots":["head"],"textures":{"outer":"outer.png"}}
```

Leggings use inner; the other slots use outer. See the [armor reference](armor-resources.md) for full fields, naming, UV, size and safety limits. This guide supplies no Outfit/Armor artwork or automatic installer.

## Update and troubleshoot

After completing file edits, an administrator runs `/fashion3790 reload` to scan Outfit and Armor together. Do not edit directories during the scan. Current clients update resources and previews without another Apply. Cape requires a restart; there is no automatic file watcher. Ask the server administrator if you lack command permission.

| Situation | Selection and display |
| --- | --- |
| List synchronization or texture loading | Wait for completion; this is not an empty library and needs no repeated Apply |
| Existing resource directory temporarily invalid | Saved references remain with a safe display fallback; repair, then reload/restart as appropriate |
| Cape/Outfit directory confirmed deleted by a trusted scan | Server reconciliation clears its saved references; Outfit affects only parts referencing that ID |
| Armor missing, deleted or no longer supporting a slot | Saved ID remains with Original fallback; restoring a valid resource restores its appearance |
| Entire root unreadable or scan untrusted | Previous trusted resources remain; investigate server errors instead of replacing selections with defaults |

Selections live in the current world's `data/fashion_3790/player_fashion.dat`. The server owns schema 4; do not edit it manually. Downloaded client caches live under the game instance's `3790s-fashion/cache/`, separate from source artwork and saved selections. Check server files and diagnostics first; do not delete a save to fix a texture.

Existing migration rules handle older schemas and identity paths. Downgrading needs a pre-upgrade backup. The fixed Elytra Slot v1 world integration remains supported, with a known extra-BODY wing/cape limitation in vanilla inventory; equipment-driven BODY combination preview is deferred. Active Cape/Elytra try-on and real chest-slot wings remain available. Version 1.0 does not promise compatibility with every third-party Mod.
