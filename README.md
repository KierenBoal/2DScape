# 2DScape

A plugin that I've wanted to make for many years, but haven't really found the motivation to re-learn Java, and now that I am incorperating AI into my personal projects, I can now bring goofy ideas like this to life; so have fun!

Feel free to fork, copy/paste this, make it into a shader, it's yours to develop; use it as a base, or just as a concept. If you feel so inclined, toss credit my way

## Requirements

- Java 11
- RuneLite-compatible Gradle environment
- A RuneLite development client login flow that supports Jagex Accounts (ask ChatGPT how to do this, it's suprisingly easy to get into plugin development now; a link to a real document is below though too.)

## Running Locally

From the plugin root, start the RuneLite development client:

```powershell
.\gradlew run
```

On Windows, the included helper script does the same thing:

```powershell
.\run.bat
```

If you use a Jagex Account, follow RuneLite's development client login guide:

https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

## Building

```powershell
.\gradlew build
```

On Windows, the included helper script runs the build task:

```powershell
.\build.bat
```

## Configuration

The plugin adds a `2DScape` plugin entry with controls for:

- animation frame snapping
- projectile, graphics object, and object billboard frame snapping
- target entity types: NPCs, players, projectiles, graphics objects, objects, and ground items
- 2D billboard rendering
- billboard radius, entity limit, render quality, color bands, and light boost
- an API capability audit at `docs/render-target-audit.md`

## In-Game Test Checklist

Only test manually in-game. Do not use automation or scripted input.

1. Start the development client with `.\gradlew run`.
2. Enable the plugin in RuneLite.
3. Confirm nearby NPC animations render with visibly reduced animation frames.
4. Toggle `Apply to NPCs` off and confirm NPC animations return to normal.
5. Toggle `Apply to players` and confirm player animation snapping follows the
   setting.
6. Toggle `Apply to objects` and confirm visible world objects switch to billboard
   rendering when the effect is enabled.
7. Toggle `Apply to graphics objects` near area effects or spell graphics and
   confirm they switch to billboard rendering.
8. Toggle the object, projectile, and graphics object frame snapping options and
   confirm billboard updates visibly step between fewer animation frames.
9. Enable billboard rendering and confirm close entities are hidden and redrawn
   as 2D-style billboards.
10. Adjust billboard radius and max entity count to confirm the effect is limited
    to the configured range and count.
11. Disable the plugin and confirm entity rendering returns to RuneLite defaults.

## Notes

- This plugin is experimental and intended for local development.
- Rotation snapping is exposed in config, but RuneLite does not currently expose
  a public setter for live actor world rotation.
- Dynamic object frame snapping is limited to the billboard path. RuneLite does
  not expose a public setter for live object animation frames.
- Inventory and bank icons, attached spot animations, and minimap object/icon
  rendering are documented in `docs/render-target-audit.md` because RuneLite's
  public API does not expose an equivalent billboard override path for them.
- The plugin should not inject input, automate gameplay, or modify outgoing game
  actions.
- This plugin was heavily vibe coded; there was some manual tweaking/tuning, but the vast majority was created with Codex using Chat GPT 5.4 medium thinking, and on a few particularly trick situations Chat GPT 5.5 medium; I corrected many logic bugs and suggested many optimisations that made this somewhat viable to run despite being a horribly performance 2D CPU renderer
