# 2DScape
![image](https://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/rank/plugin/2dscape)
![image](https://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/installs/plugin/2dscape)

![2DScape in Old School RuneScape](md_imgs/hero.png)

2DScape is a 3D-to-2D downsampler that tries to show what Old School RuneScape might look like with RSC's graphics. It's all rendered as an overlay, with custom occlusion culling so the sprites actually sit in the world.

**Heads up:** You need RuneLite's **GPU** plugin or **117 HD** enabled for 2DScape to render properly. Without a GPU renderer, it can't hide the original 3D models before drawing the 2D ones over them.

There are a LOT of config options to play with, so have a go and find your favourite style.

## Doing bossing/raids?
If you are doing content that requiers click precision, I'd suggest enabling "Improved click accuracy mode", it maps the sprite better to the clickbox, it looks a bit goofy, but it's MUCH better for reducing missclicks.
![Improved click accuracy mode](md_imgs/click_accuracy.jpg)

## Custom rendering features
![2D billboards on NPCs, players, pets, and more](md_imgs/first_set.jpg)

- NPCs, Players, Pets and even thralls become flat 2D sprites
- Projectiles and effects are also rendered in 2D
- Even Ground items (stuff on the floor) is rendered in 2D, just at a slightly higher angle for visibility
- You can also enable world objects being converted to 2D, it's really stupid
- You can export sprites, saving them to your computer

![Occlusion, crowds, combat effects, and Sailing](md_imgs/second_set.jpg)

- Custom z-buffer hides sprites behind scenery including transparency composition with transparent geometry like stained glass windows
- Large crowds render well on average hardware, there are config options to reduce how much occlusion culling and how much billboards can update per frame, so you can configure this for a toaster if performance is ever an issue
- Special attacks work too including their graphics
- All Spells work, so yes, the Ice Barrage blocks show up as 2D.
- Sailing partially works, the fascilities don't perform occlusion culling yet.

## RSC features 
![Retro skilling bubbles](md_imgs/skilling_bubbles.jpg)

Skilling bubbles display whenever you gain non-combat XP, it will fade away after a few seconds. You can even stack them together for multi-skilling!

Me getting a fat multi XP drop from Perdu after the recent quest XP changes.
![Retro skilling bubbles](md_imgs/fat_xp.jpg)

![RSC Chat color codes](md_imgs/chat.jpg)

- Use old chat color codes like @red@, @blu@, @rand@, etc.
- Use old hitsplats

Under the "Retro" section, enable "Use retro HP bar, overhead chat, hitsplats and prayers"
![2D billboards on NPCs, players, pets, and more](md_imgs/retro_chat_config.jpg)

We have compatability with Retro NPC Swapper

![Exported sprite frames](md_imgs/retro.jpg)

Get extra retro wih classic NPC and object models using [Retro NPC Swapper](https://runelite.net/plugin-hub/show/retro-npc-swapper)

## Sprite export feature
![Exported sprite frames](md_imgs/sprite_export.jpg)

Want to make a sprite sheet? Turn on **Enable shift right-click export PNG** in the config, then Shift-right-click a player, NPC, pet, or world object and choose **Export sprite**. World objects can be exported even with their billboard setting disabled. Shift-right-click your equipment tab to export yourself. The plugin saves the angles and animation frames as transparent PNGs, ready to put into a 2D sprite sheet for a game lol that'd be so sick if someone used it to make the art for a game.

Exports are saved in RuneLite's `plugin-data/2dscape` directory. The in-game message shows and copies the export folder path.

**Note**: These sprites are generated in real time by rasterizing the triangles from Jagex's Old School RuneScapes in-game models. They aren't hand drawn; they're calculated on the fly with 2D rasterization and a bunch of 3D math. So if you export sprites, keep in mind whatever licensing Jaggy Baggy requiers.

---

## Creator: Kieren Boal

I want to acknowledge and disclose usage of AI agentic engineering in creating this plugin, I have several years industry experience as a programmer, and still hand write code to this day as part of my work life, so it's been amazing being able to transfer the skills I have in programming into a programming domain (Java, specifically with RuneLites Plugin system, learning what the hell a gradel was and how it lol) I was unfailiar with, from this whole process I learnt a HEAP of new skills and how to use the RuneLite API, alongside creating this plugin that I once tried to hand code back in 2022, but was unable to figure out the rendering in Java, I could do the rendering in C# with a CPU rasterizer that I'd written but translating that to Java just wasn't gonna happen easily (I'd even considered writing it in C#, then  hosting a C# server locally that does the rasterization in nice familiar C#, and RuneLite would pipe the models, world and camera position back to the server, but yeah that was absoloutely not gonna fly for a real plugin and would've been really bad lmao), so I've been thrilled to use OpenAI's Codex to make this plugin a reality and actually finish the project! I am very proud of how this has come out.

### Usage of OpenAIs Codex:

- **GPT-5.4:** Original draft code with 2D-to-3D work.
- **GPT-5.5:** First implementation of blocky occlusion culling.
- **GPT-5.6:** Code tidy-up and SO many optimisations. Sol handled orchestration, high-level planning, and a lot of cleanup; Terra and Luna handled implementation.
- **GPT-6.0 Astra:** Made the occlusion culling truly shine by getting rid of the blockiness and adding composite transparency, so sprites can be seen behind transparent geometry like stained glass.
- **GPT-6.1 Sol:** Workhorse, new retro hitsplats and new improved clickbox accuracy, boat occlusion culling, unified FPS 