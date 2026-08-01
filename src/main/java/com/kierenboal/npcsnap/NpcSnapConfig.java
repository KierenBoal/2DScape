package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("npc-snap")
public interface NpcSnapConfig extends Config
{
	@ConfigSection(name = "Visual Snapping", description = "Make movement and turning look more like an older game.", position = 0)
	String snappingSection = "snappingSection";

	@ConfigSection(name = "Render Targets", description = "Choose which things use the sprite look.", position = 1)
	String targetsSection = "targetsSection";

	@ConfigSection(name = "Rendering config", description = "Choose how the sprite look is drawn.", position = 2)
	String renderingSection = "renderingSection";

	@ConfigSection(name = "Sprite config", description = "Change the edges, shadows, and highlights on sprites.", position = 3)
	String spriteSection = "spriteSection";

	@ConfigSection(name = "Sprite interaction outline", description = "Configure hover and interaction outlines for sprites.", position = 4)
	String spriteInteractionOutlineSection = "spriteInteractionOutlineSection";

	@ConfigSection(name = "Retro", description = "Add extra old-school visual effects to the game and interface.", position = 5)
	String retroSection = "retroSection";

	@ConfigSection(name = "Debug", description = "Extra information for finding problems with the plugin.", position = 6)
	String debugSection = "debugSection";

	@ConfigItem(keyName = "enableAnimationFrameSnapping", name = "Limit frame rate", description = "Make animations update in visible steps instead of every game frame.", section = snappingSection, position = 0)
	default boolean enableAnimationFrameSnapping()
	{
		return true;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(keyName = "animationFrameCount", name = "Frame rate", description = "How many animation updates to show each second. Lower numbers look choppier.", section = snappingSection, position = 1)
	default int animationFrameCount()
	{
		return 4;
	}

	@ConfigItem(keyName = "enableRotationSnapping", name = "Limit rotation angles", description = "Make sprites turn through a small number of set directions.", section = snappingSection, position = 2)
	default boolean enableRotationSnapping()
	{
		return true;
	}

	@Range(min = 1, max = 32)
	@ConfigItem(keyName = "numberOfPitchRotationAngles", name = "Number of pitch rotation angles", description = "How many up-and-down directions a sprite can face.", section = snappingSection, position = 3)
	default int numberOfPitchRotationAngles()
	{
		return 1;
	}

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "numberOfRotationAngles", name = "Number of yaw rotation angles", description = "How many left-and-right directions a sprite can face.", section = snappingSection, position = 4)
	default int numberOfYawRotationAngles()
	{
		return 4;
	}

	@ConfigItem(keyName = "applyToNpcs", name = "Apply to NPCs", description = "Use sprites for NPCs.", section = targetsSection, position = 0)
	default boolean applyToNpcs()
	{
		return true;
	}

	@ConfigItem(keyName = "applyToPlayers", name = "Apply to players", description = "Use sprites for other players and your character.", section = targetsSection, position = 1)
	default boolean applyToPlayers()
	{
		return true;
	}

	@ConfigItem(keyName = "applyToGroundItems", name = "Apply to ground items", description = "Use sprites for items lying on the ground.", section = targetsSection, position = 2)
	default boolean applyToGroundItems()
	{
		return true;
	}

	@ConfigItem(keyName = "applyToGraphicsObjects", name = "Apply to effects", description = "Use sprites for spell hits, splashes, and other visual effects.", section = targetsSection, position = 3)
	default boolean applyToGraphicsObjects()
	{
		return true;
	}

	@ConfigItem(keyName = "applyToProjectiles", name = "Apply to projectiles", description = "Use sprites for arrows, spells, and other things flying through the air.", section = targetsSection, position = 4)
	default boolean applyToProjectiles()
	{
		return true;
	}

	@ConfigItem(keyName = "applyToObjects", name = "Apply to world objects", description = "Use sprites for scenery and animated objects in the world.", section = targetsSection, position = 5)
	default boolean applyToObjects()
	{
		return false;
	}

	@ConfigItem(keyName = "applyToBoats", name = "Apply to boats", description = "Use sprites for your boat and other players' boats while sailing.", section = targetsSection, position = 6, hidden = true)
	default boolean applyToBoats()
	{
		return false;
	}

	@ConfigItem(keyName = "enableShiftRightClickExportPng", name = "Enable shift right-click export PNG", description = "Hold Shift while opening a menu to export a target's billboard angles as transparent PNG files.", section = targetsSection, position = 7)
	default boolean enableShiftRightClickExportPng()
	{
		return false;
	}

	@ConfigItem(keyName = "enable2dBillboardSprites", name = "Enable 2D billboards", description = "Draw selected things as flat sprites instead of their usual 3D models.", section = renderingSection, position = 0)
	default boolean enable2dBillboardSprites()
	{
		return true;
	}

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "BillboardColorBands", name = "Color bands", description = "How many shades sprites can use. Lower numbers give a more retro look.", section = renderingSection, position = 1)
	default int billboardColorBands()
	{
		return 16;
	}

	@Range(min = 0, max = 512)
	@ConfigItem(keyName = "billboardLightBoostPercent", name = "Light boost", description = "Make sprites brighter or darker. 100% keeps their normal brightness.", section = renderingSection, position = 2)
	default int billboardLightBoostPercent()
	{
		return 180;
	}

	@ConfigItem(keyName = "billboardOcclusionQuality", name = "Occlusion quality", description = "Hide parts of sprites behind walls, hills, trees, and other scenery. Higher settings look smoother but may use more performance.", section = renderingSection, position = 3)
	default BillboardOcclusionQuality billboardOcclusionQuality()
	{
		return BillboardOcclusionQuality.MEDIUM;
	}

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "billboardRadiusTiles", name = "Render distance tiles", description = "How many tiles away sprites can be drawn. Lower values can improve performance.", section = renderingSection, position = 4)
	default int billboardRadiusTiles()
	{
		return 90;
	}

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "billboardMaxEntities", name = "Max render count", description = "The most sprites the plugin can draw at once. Lower values can improve performance in crowded places.", section = renderingSection, position = 5)
	default int billboardMaxEntities()
	{
		return 128;
	}

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "billboardMaxDrawsPerFrame", name = "Max updates per frame", description = "The most sprite pictures the plugin can refresh at once. Lower values can improve performance but make changes appear later.", section = renderingSection, position = 6)
	default int billboardMaxDrawsPerFrame()
	{
		return 16;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "renderBillboardQuality", name = "Render quality", description = "How sharp newly made sprites are. Lower values can improve performance.", section = renderingSection, position = 7)
	default double renderBillboardQuality()
	{
		return 33.0d;
	}

	@ConfigItem(keyName = "renderBillboardsOnAllPlanes", name = "Render billboards on all planes", description = "Also draw sprites on floors above and below you.", section = renderingSection, position = 8)
	default boolean renderBillboardsOnAllPlanes()
	{
		return false;
	}

	@ConfigItem(keyName = "enableBillboardSpriteShadows", name = "Add sprite shadows", description = "Draw a small shadow underneath each sprite.", section = spriteSection, position = 0)
	default boolean enableBillboardSpriteShadows()
	{
		return true;
	}

	@ConfigItem(keyName = "enablePlayerInteractionOutline", name = "Enable Player interaction outline", description = "Show hover and interaction outlines on player sprites.", section = spriteInteractionOutlineSection, position = 0)
	default boolean enablePlayerInteractionOutline()
	{
		return true;
	}

	@ConfigItem(keyName = "playerHoverOutlineColor", name = "Player hover outline color", description = "Choose the outline colour shown when hovering a player sprite.", section = spriteInteractionOutlineSection, position = 1)
	@Alpha
	default Color playerHoverOutlineColor()
	{
		return new Color(0x9000FFFF, true);
	}

	@ConfigItem(keyName = "playerInteractionOutlineColor", name = "Player interaction outline color", description = "Choose the outline colour shown while interacting with a player sprite.", section = spriteInteractionOutlineSection, position = 2)
	@Alpha
	default Color playerInteractionOutlineColor()
	{
		return new Color(0x90FF0000, true);
	}

	@ConfigItem(keyName = "enableNpcInteractionOutline", name = "Enable NPC interaction outline", description = "Show hover and interaction outlines on NPC sprites.", section = spriteInteractionOutlineSection, position = 3)
	default boolean enableNpcInteractionOutline()
	{
		return true;
	}

	@ConfigItem(keyName = "npcHoverOutlineColor", name = "NPC hover outline color", description = "Choose the outline colour shown when hovering an NPC sprite.", section = spriteInteractionOutlineSection, position = 4)
	@Alpha
	default Color npcHoverOutlineColor()
	{
		return new Color(0x90FFFF00, true);
	}

	@ConfigItem(keyName = "npcInteractionOutlineColor", name = "NPC interaction outline color", description = "Choose the outline colour shown while interacting with an NPC sprite.", section = spriteInteractionOutlineSection, position = 5)
	@Alpha
	default Color npcInteractionOutlineColor()
	{
		return new Color(0x90FF0000, true);
	}

	@ConfigItem(keyName = "enableGroundItemInteractionOutline", name = "Enable Ground item interaction outline", description = "Show hover and interaction outlines on ground-item sprites.", section = spriteInteractionOutlineSection, position = 6)
	default boolean enableGroundItemInteractionOutline()
	{
		return true;
	}

	@ConfigItem(keyName = "groundItemHoverOutlineColor", name = "Ground item hover outline color", description = "Choose the outline colour shown when hovering a ground-item sprite.", section = spriteInteractionOutlineSection, position = 7)
	@Alpha
	default Color groundItemHoverOutlineColor()
	{
		return new Color(0x9000FFFF, true);
	}

	@ConfigItem(keyName = "groundItemInteractionOutlineColor", name = "Ground item interaction outline color", description = "Choose the outline colour shown while interacting with a ground-item sprite.", section = spriteInteractionOutlineSection, position = 8)
	@Alpha
	default Color groundItemInteractionOutlineColor()
	{
		return new Color(0x90FF0000, true);
	}

	@ConfigItem(keyName = "enableBillboardSpriteOutline", name = "Enable sprite outline", description = "Draw a coloured line around the outside edge of every sprite.", section = spriteSection, position = 1)
	default boolean enableBillboardSpriteOutline()
	{
		return false;
	}

	@ConfigItem(keyName = "enableBillboardSpriteInline", name = "Enable sprite inline", description = "Draw a coloured line just inside the edge of every sprite.", section = spriteSection, position = 2)
	default boolean enableBillboardSpriteInline()
	{
		return false;
	}

	@ConfigItem(keyName = "billboardSpriteOutlineColor", name = "Sprite outline color", description = "Choose the colour used for the normal outline and inline.", section = spriteSection, position = 3)
	@Alpha
	default Color billboardSpriteOutlineColor()
	{
		return new Color(0x40000000, true);
	}

	@ConfigItem(keyName = "enableBillboardHighlightOutline", name = "Enable highlight outlines", description = "Make sprite outlines lighter using nearby sprite colours.", section = spriteSection, position = 4)
	default boolean enableBillboardHighlightOutline()
	{
		return false;
	}

	@ConfigItem(keyName = "enableBillboardHighlightInline", name = "Enable highlight inline", description = "Make sprite inlines lighter using nearby sprite colours.", section = spriteSection, position = 5)
	default boolean enableBillboardHighlightInline()
	{
		return false;
	}

	@ConfigItem(keyName = "enableBillboardShadowOutline", name = "Enable shadow outline", description = "Make sprite outlines darker using nearby sprite colours.", section = spriteSection, position = 6)
	default boolean enableBillboardShadowOutline()
	{
		return false;
	}

	@ConfigItem(keyName = "enableBillboardShadowInline", name = "Enable shadow inline", description = "Make sprite inlines darker using nearby sprite colours.", section = spriteSection, position = 7)
	default boolean enableBillboardShadowInline()
	{
		return false;
	}

	@ConfigItem(keyName = "enableSkillingBubbles", name = "Enable skilling bubbles", description = "Show a skill icon above your character when you gain non-combat XP.", section = retroSection, position = 0)
	default boolean enableSkillingBubbles()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(keyName = "skillingTimeoutSeconds", name = "Skilling bubble timeout seconds", description = "How long the skill icon stays above your character after you gain XP.", section = retroSection, position = 1)
	default int skillingTimeoutSeconds()
	{
		return 15;
	}

	@ConfigItem(keyName = "enableBillboardCombatSnapping", name = "Enable combat rotation snapping", description = "Make fighting NPC and player sprites face each other in set directions.", section = retroSection, position = 2)
	default boolean enableBillboardCombatSnapping()
	{
		return false;
	}

	@ConfigItem(keyName = "useInventorySpritesForGroundItems", name = "Use inventory sprites for ground items", description = "Use the same small picture as the item in your inventory for ground-item sprites.", section = retroSection, position = 3)
	default boolean useInventorySpritesForGroundItems()
	{
		return false;
	}

	@ConfigItem(keyName = "enableUiTextureBanding", name = "Reduce UI texture quality", description = "Give interface icons and artwork fewer shades for a more retro look.", section = retroSection, position = 4)
	default boolean enableUiTextureBanding()
	{
		return false;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(keyName = "uiSpriteQuality", name = "UI sprite quality", description = "How sharp interface icons and artwork are. Lower values look rougher.", section = retroSection, position = 5)
	default double uiSpriteQuality()
	{
		return 100.0d;
	}

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "uiTextureColorBands", name = "UI texture color bands", description = "How many shades interface icons and artwork can use when UI texture reduction is on.", section = retroSection, position = 6)
	default int uiTextureColorBands()
	{
		return 8;
	}

	@ConfigItem(keyName = "enableGlobalTextureBanding", name = "Reduce game texture quality", description = "Give world textures fewer shades for a more retro look.", section = retroSection, position = 7)
	default boolean enableGlobalTextureBanding()
	{
		return false;
	}

	@Range(min = 1, max = 64)
	@ConfigItem(keyName = "globalTextureSpriteQuality", name = "Texture sprite quality", description = "How sharp world textures are. Lower values make texture pixels larger and rougher.", section = retroSection, position = 8)
	default double globalTextureSpriteQuality()
	{
		return 100.0d;
	}

	@Range(min = 1, max = 64)
	@ConfigItem(keyName = "globalTextureColorBands", name = "Texture color bands", description = "How many shades world textures can use when game texture reduction is on.", section = retroSection, position = 9)
	default int globalTextureColorBands()
	{
		return 16;
	}

	@ConfigItem(keyName = "deterministicAnimationLooping", name = "Deterministic animation looping", description = "Keep the previous animation frame when an animation loops instead of following its loop metadata.", section = retroSection, position = 10)
	default boolean deterministicAnimationLooping()
	{
		return false;
	}

	@ConfigItem(keyName = "useRetroHpBar", name = "Use retro HP bar", description = "Draw compact retro health bars above billboard sprites.", section = retroSection, position = 11)
	default boolean useRetroHpBar()
	{
		return true;
	}

	@ConfigItem(keyName = "useRetroHitsplats", name = "Use retro hitsplats", description = "Draw red retro damage splats and blue miss splats over billboard sprites.", section = retroSection, position = 12)
	default boolean useRetroHitsplats()
	{
		return true;
	}

	@ConfigItem(keyName = "useRetroChatEffects", name = "Use retro chat effects", description = "Use retro overhead chat colours and animated text commands.", section = retroSection, position = 13)
	default boolean useRetroChatEffects()
	{
		return true;
	}

	@ConfigItem(keyName = "debugDrawBillboardOutline", name = "Draw sprite bounds", description = "Draw a red box around each sprite.", section = debugSection, position = 0)
	default boolean debugDrawBillboardOutline()
	{
		return false;
	}

	@ConfigItem(keyName = "logBillboardAnimationData", name = "Log billboard animation data", description = "Write billboard actor animation frames and loop metadata to the developer log for troubleshooting.", section = debugSection, position = 7)
	default boolean logBillboardAnimationData()
	{
		return false;
	}

	@ConfigItem(keyName = "debugDrawBillboardPaintOrder", name = "Show sprite draw order", description = "Show the number that tells you which sprite was drawn first.", section = debugSection, position = 1)
	default boolean debugDrawBillboardPaintOrder()
	{
		return false;
	}

	@ConfigItem(keyName = "debugDrawFrameNumber", name = "Show sprite state info", description = "Show extra numbers about a sprite's animation, direction, and place in the update list.", section = debugSection, position = 2)
	default boolean debugDrawFrameNumber()
	{
		return false;
	}

	@ConfigItem(keyName = "debugShowReadyToRedrawFrames", name = "Show delayed sprite updates", description = "Mark sprites that are ready to update but have to wait until a later frame.", section = debugSection, position = 3)
	default boolean debugShowReadyToRedrawFrames()
	{
		return false;
	}

	@ConfigItem(keyName = "debugShowCacheInvalidations", name = "Show sprite redraws", description = "Mark sprites on the frame when the plugin redraws their picture.", section = debugSection, position = 4)
	default boolean debugShowFrameRedraws()
	{
		return false;
	}

	@ConfigItem(keyName = "debugDrawBillboardOcclusionMask", name = "Show scenery blocking", description = "Show the parts of scenery that hide sprites behind them.", section = debugSection, position = 5)
	default boolean debugDrawBillboardOcclusionMask()
	{
		return false;
	}

	@ConfigItem(keyName = "debugPerformanceMetrics", name = "Show performance numbers", description = "Show how much time the plugin spends drawing sprites.", section = debugSection, position = 6)
	default boolean debugPerformanceMetrics()
	{
		return false;
	}

	@ConfigItem(keyName = "debugLogBillboardOcclusion", name = "Log scenery blocking", description = "Write information about scenery that blocks sprites to the RuneLite log.", section = debugSection, position = 7)
	default boolean debugLogBillboardOcclusion()
	{
		return false;
	}

	@ConfigItem(keyName = "debugLogClassifications", name = "Log target choices", description = "Write why the plugin did or did not use a sprite for something to the RuneLite log.", section = debugSection, position = 8)
	default boolean debugLogClassifications()
	{
		return false;
	}

	@ConfigItem(keyName = "debugLogBillboardColors", name = "Log sprite colours", description = "Write the colours picked for sprites to the RuneLite log.", section = debugSection, position = 9)
	default boolean debugLogBillboardColors()
	{
		return false;
	}
}
