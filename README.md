# 2DScape

Experimental RuneLite plugin for RuneScape Classic-inspired graphics using
render-time animation frame snapping and 2D-style billboard rendering.

## Requirements

- Java 11
- RuneLite-compatible Gradle environment
- A RuneLite development client login flow that supports Jagex Accounts

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
- target entity types: NPCs, players, projectiles, and ground items
- 2D billboard rendering
- billboard radius, entity limit, render quality, color bands, and light boost

## In-Game Test Checklist

Only test manually in-game. Do not use automation or scripted input.

1. Start the development client with `.\gradlew run`.
2. Enable the plugin in RuneLite.
3. Confirm nearby NPC animations render with visibly reduced animation frames.
4. Toggle `Apply to NPCs` off and confirm NPC animations return to normal.
5. Toggle `Apply to players` and confirm player animation snapping follows the
   setting.
6. Enable billboard rendering and confirm close entities are hidden and redrawn
   as 2D-style billboards.
7. Adjust billboard radius and max entity count to confirm the effect is limited
   to the configured range and count.
8. Disable the plugin and confirm entity rendering returns to RuneLite defaults.

## Notes

- This plugin is experimental and intended for local development.
- Rotation snapping is exposed in config, but RuneLite does not currently expose
  a public setter for live actor world rotation.
- The plugin should not inject input, automate gameplay, or modify outgoing game
  actions.
