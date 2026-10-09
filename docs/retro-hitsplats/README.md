# Retro hitsplat comparison workflow

This developer tool renders the **same Java2D vector code used in-game** into PNG
comparison sheets. The wiki images are local references, not plugin assets. The
renderer does not download anything, and running the preview does not launch or
control RuneScape.

## Save the references once

1. Open the [RSC hitsplats reference](https://classic.runescape.wiki/w/File:Hitsplats.png)
   to get the RuneScape Classic vibe: simple geometric stars, blue for blocks and
   red for damage. Save the actual `Hitsplats.png` image locally if you want it
   included at the top of the comparison sheet.
2. Open the [OSRS hitsplat page](https://oldschool.runescape.wiki/w/Hitsplat).
   Let its images load, then press **Ctrl+S** in your browser and choose
   **Webpage, Complete**. Save both the HTML and its accompanying assets folder.
3. A typical save looks like this:

   ```text
   tmp_wiki_page/
     Hitsplat - OSRS Wiki.html
     Hitsplat - OSRS Wiki_files/
       Damage_hitsplat.png
       Damage_hitsplat_(tinted).png
       Damage_hitsplat_(max_hit).png
       Poison_hitsplat.png
       ...
   ```

4. Point Codex, or your agent harness of choice, at the saved folder and this
   README. Have it read the HTML to identify the correct image and variant for
   each `HitsplatID`. Work from those local PNGs rather than repeatedly hitting
   the wiki. Refresh the saved page only when researching new or changed content.

## Run the comparison

From the plugin repository root in PowerShell:

```powershell
.\gradlew.bat renderRetroHitsplatPreview --console=plain
```

The default reference directory is `tmp_wiki_page`. To use a different save,
include an RSC image, and keep successive attempts separately:

```powershell
.\gradlew.bat renderRetroHitsplatPreview `
  '-PhitsplatWikiDirectory=C:\references\tmp_wiki_page' `
  '-PrscHitsplatReference=C:\references\Hitsplats.png' `
  -PhitsplatPreviewName=attempt-03 --console=plain
```

`hitsplatWikiDirectory` accepts the saved HTML, its containing folder, or the
`Hitsplat - OSRS Wiki_files` folder directly. `rscHitsplatReference` is optional.
Use a preview name containing only letters, numbers, underscores, or hyphens.
On macOS/Linux, use `./gradlew` and your shell's quoting/line continuation.

Outputs are ignored build artifacts under `build/reports/retro-hitsplats/`:

- `<attempt>.png`: complete comparison, native size and enlarged views, normal,
  tinted and max-hit variants, plus multi-digit and zero examples.
- `<attempt>-undocumented.png`: 32 synthetic unknown IDs in a compact grid, with
  their chosen RGB values and shapes. Read left to right, then down.

The preview has an explicit `HitsplatID`-to-wiki-filename table in
`RetroHitsplatPreview.java`; it does **not** guess filenames or use reflection to
enumerate constants. The agent should check every named constant in the API
against that table and the renderer's type switch. Add new constants and their
local wiki filenames when the API and wiki document new content. If a PNG is
missing, fix the saved assets or the mapping; do not silently substitute an
unrelated reference. Synthetic unknown rows deliberately have no wiki image.

## Inspect, tweak, repeat — then keep accepted styles stable

- Show each attempt inline or open its PNG. Send progress updates describing the
  specific change, with wiki and vector versions visible together.
- Compare palette, silhouette, number readability, zero and wide values, tinted
  borders, and max-hit trim at native size as well as enlarged size. Keep the
  flat **vector** RSC aesthetic; do not trace a pixel map or replace it with a
  detailed native sprite.
- Adjust only the style being introduced or an existing style that has
  **meaningfully changed in-game because the developers changed it**. Do not
  revisit accepted colours, geometry, or trim merely for aesthetic preference.
- Accepted styles are stable. The routine maintenance triggers are a genuinely
  changed native hitsplat or a completely new hitsplat ID not yet implemented.
  Preserve ordinary RSC damage and block stars.
- Render another named attempt, inspect it, and repeat until the new or changed
  style is as close as practical while preserving the RSC vibe. Pause and
  incorporate user feedback when requested.
- Run `./gradlew test` (`.\gradlew.bat test` on Windows), update the current release
  changelog within its 80-character-per-entry limit, and have the user validate
  accessible examples in-game. Only the user operates RuneScape. Development
  login guidance: [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Undocumented hitsplats are deliberately conspicuous

Unknown IDs render as **circles, squares, or triangles** with black outlines and
the usual white number and black shadow. Larger numbers increase the shape's
size; triangles have a wider base to fit the text. It never falls back to an ordinary
damage/block star just because its amount is positive or zero.

The ID seeds a deterministic pseudorandom pastel colour. Two RGB channels are
128 (half) or 255 (full); the third is strictly between them, 129–254. Channels
are shuffled deterministically. This follows the full/half examples, keeps every
channel at least 128, and guarantees the channels are not all equal. The colour
depends only on the ID, not the damage amount, frame, actor, or random runtime
state; it remains the same after restarting. Shape selection is seeded too.

Roll again whenever a colour or shape matches the previous ID's selection.
Generation uses fixed blocks of 32 IDs to keep first-time work bounded and
independent of lookup order. The last ID also avoids the next block's seeded
first ID, so the no-repeat rule holds across block boundaries. The same ID keeps
its descriptor every time it appears; the no-repeat rule applies to neighbouring
numeric IDs, not successive damage events from the same ID.

IDs **1001 through 1032** are synthetic preview/test cases, not claims
about real game content. Their conspicuous shapes make unhandled types easy to
notice and report. When a real new ID appears, record its ID/context, consult an
updated locally saved wiki page, and add its proper vector demake through this
workflow. Existing named types remain unchanged.

Retro hitsplats persist for **at least two game ticks (1.2 seconds)** from their
recorded event. A longer native expiry is preserved. Hiding an actor does not
restart the timer, and disabling retro overheads or clearing an actor still
clears its tracked hitsplats.
