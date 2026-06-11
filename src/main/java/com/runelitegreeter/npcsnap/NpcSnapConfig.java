package com.runelitegreeter.npcsnap;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("npc-snap")
public interface NpcSnapConfig extends Config
{
	@ConfigSection(
		name = "Visual Snapping",
		description = "Animation and rotation snapping settings",
		position = 0
	)
	String snappingSection = "snappingSection";

	@ConfigSection(
		name = "Render Targets",
		description = "Entity types affected by snapping and billboards",
		position = 1
	)
	String targetsSection = "targetsSection";

	@ConfigSection(
		name = "Billboard Config",
		description = "2D billboard rendering settings",
		position = 2
	)
	String billboardSection = "billboardSection";

	@ConfigSection(
		name = "Textures",
		description = "Global in-game texture quality settings",
		position = 3
	)
	String texturesSection = "texturesSection";
	
	@ConfigSection(
		name = "Debug",
		description = "Billboard diagnostic rendering options",
		position = 4
	)
	String debugSection = "debugSection";

	@ConfigItem(
		keyName = "enableAnimationFrameSnapping",
		name = "Enable animation frame snapping",
		description = "Enable stepped NPC animation frame rendering",
		section = snappingSection
	)
	default boolean enableAnimationFrameSnapping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableProjectileFrameSnapping",
		name = "Enable projectile frame snapping",
		description = "Enable stepped projectile billboard animation frames, such as arrows, spells in flight, and similar world projectiles",
		section = snappingSection
	)
	default boolean enableProjectileFrameSnapping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableGraphicsObjectFrameSnapping",
		name = "Enable effect frame snapping",
		description = "Enable stepped billboard animation frames for graphics objects and effect-like animated visuals, such as spell impact graphics",
		section = snappingSection
	)
	default boolean enableGraphicsObjectFrameSnapping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableObjectFrameSnapping",
		name = "Enable object frame snapping",
		description = "Enable stepped dynamic object billboard animation frames for animated world objects and scenery. RuneLite does not expose a public setter for live DynamicObject frames, so the snapping applies to billboards rather than the original 3D object render.",
		section = snappingSection
	)
	default boolean enableObjectFrameSnapping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "applyToNpcs",
		name = "Apply to NPCs",
		description = "Apply snapping and billboards to NPCs",
		section = targetsSection
	)
	default boolean applyToNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "applyToPlayers",
		name = "Apply to players",
		description = "Apply snapping and billboards to players",
		section = targetsSection
	)
	default boolean applyToPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "applyToProjectiles",
		name = "Apply to projectiles",
		description = "Apply billboard rendering to projectiles where possible",
		section = targetsSection
	)
	default boolean applyToProjectiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "applyToGraphicsObjects",
		name = "Apply to effects",
		description = "Apply billboard rendering to graphics objects and effect-like visuals, such as spell impact graphics and actor-attached effect objects",
		section = targetsSection
	)
	default boolean applyToGraphicsObjects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "applyToObjects",
		name = "Apply to objects",
		description = "Apply billboard rendering to visible world objects and scenery where possible",
		section = targetsSection
	)
	default boolean applyToObjects()
	{
		return false;
	}

	@ConfigItem(
		keyName = "applyToGroundItems",
		name = "Apply to ground items",
		description = "Apply billboard rendering to ground items",
		section = targetsSection
	)
	default boolean applyToGroundItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableSkillingBubbles",
		name = "Enable skilling bubbles",
		description = "Show a thought bubble over your player when non-combat skill XP is gained",
		section = targetsSection
	)
	default boolean enableSkillingBubbles()
	{
		return true;
	}

	@Range(
		min = 1,
		max = 32
	)
	@ConfigItem(
		keyName = "numberOfRotationAngles",
		name = "Number of yaw rotation angles",
		description = "Number of horizontal rotation slices to render",
		section = snappingSection
	)
	default int numberOfYawRotationAngles()
	{
		return 4;
	}

	@Range(
		min = 1,
		max = 8
	)
	@ConfigItem(
		keyName = "numberOfPitchRotationAngles",
		name = "Number of pitch rotation angles",
		description = "Number of vertical rotation slices to render",
		section = snappingSection
	)
	default int numberOfPitchRotationAngles()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "enableRotationSnapping",
		name = "Enable rotation snapping",
		description = "Enable snapped billboard facing angles. RuneLite does not expose a public setter for live NPC world rotation.",
		section = snappingSection
	)
	default boolean enableRotationSnapping()
	{
		return true;
	}

	@Range(
		min = 1,
		max = 60
	)
	@ConfigItem(
		keyName = "animationFrameCount",
		name = "Animation frame count",
		description = "Reduce each animatino FPS",
		section = snappingSection
	)
	default int animationFrameCount()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "enableBillboardCombatSnapping",
		name = "Enable combat snapping",
		description = "Lock mutually interacting NPC and player billboards to combat-facing pitch and yaw",
		section = snappingSection
	)
	default boolean enableBillboardCombatSnapping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enable2dBillboardSprites",
		name = "Enable 2D billboard sprites",
		description = "Hide NPC entities and redraw them as software-rasterized sprite billboards",
		section = billboardSection
	)
	default boolean enable2dBillboardSprites()
	{
		return true;
	}

	@ConfigItem(
		keyName = "renderBillboardQuality",
		name = "Render billboard quality",
		description = "Internal billboard render scale as a percent of full resolution. Lower values reduce CPU cost.",
		section = billboardSection
	)
	default double renderBillboardQuality()
	{
		return 33.0d;
	}

	@Range(
		min = 1,
		max = 255
	)
	@ConfigItem(
		keyName = "billboardRadiusTiles",
		name = "Billboard radius",
		description = "Maximum tile distance for converting NPCs into billboards",
		section = billboardSection
	)
	default int billboardRadiusTiles()
	{
		return 90;
	}

	@Range(
		min = 1,
		max = 1000
	)
	@ConfigItem(
		keyName = "billboardMaxEntities",
		name = "Max billboard entities",
		description = "Maximum number of closest entities that can be billboarded at once",
		section = billboardSection
	)
	default int billboardMaxEntities()
	{
		return 64;
	}

	@Range(
		min = 2,
		max = 256
	)
	@ConfigItem(
		keyName = "billboardPaletteSize",
		name = "(unused) Billboard palette size",
		description = "Maximum number of colors used by each billboard sprite after quantization and dithering",
		section = billboardSection
	)
	default int billboardPaletteSize()
	{
		return 128;
	}

	@Range(
		min = 1,
		max = 64
	)
	@ConfigItem(
		keyName = "BillboardColorBands",
		name = "Billboard color bands",
		description = "Number of brightness bands used by billboard sprite colors",
		section = billboardSection
	)
	default int billboardColorBands()
	{
		return 16;
	}

	@Range(
		min = 50,
		max = 300
	)
	@ConfigItem(
		keyName = "billboardLightBoostPercent",
		name = "Billboard light boost",
		description = "Brightness multiplier for billboard colors as a percent",
		section = billboardSection
	)
	default int billboardLightBoostPercent()
	{
		return 180;
	}

	@ConfigItem(
		keyName = "enableBillboardSpriteOutline",
		name = "Enable sprite outline",
		description = "Draw a 1-pixel outline around each billboard sprite using the selected color",
		section = billboardSection
	)
	default boolean enableBillboardSpriteOutline()
	{
		return false;
	}

	@ConfigItem(
		keyName = "billboardSpriteOutlineColor",
		name = "Sprite outline color",
		description = "Color used for the billboard sprite outline",
		section = billboardSection
	)
	default Color billboardSpriteOutlineColor()
	{
		return new Color(0x333333);
	}

	@ConfigItem(
		keyName = "enableBillboardShadowOutline",
		name = "Enable shadow outline",
		description = "Draw a 1-pixel outline using the average neighboring sprite color, darkened by 33 percent. When enabled, this takes precedence over the solid outline color.",
		section = billboardSection
	)
	default boolean enableBillboardShadowOutline()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableBillboardSpriteInline",
		name = "Enable sprite inline",
		description = "Draw a 1-pixel inline on the inside edge of each billboard sprite using the selected color",
		section = billboardSection
	)
	default boolean enableBillboardSpriteInline()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableBillboardShadowInline",
		name = "Enable shadow inline",
		description = "Draw a 1-pixel inline on the inside edge using the average neighboring sprite color, darkened by 33 percent. When enabled, this takes precedence over the solid inline color.",
		section = billboardSection
	)
	default boolean enableBillboardShadowInline()
	{
		return false;
	}

	@Range(
		min = 1,
		max = 60
	)
	@ConfigItem(
		keyName = "skillingTimeoutSeconds",
		name = "Skilling timeout",
		description = "Seconds to keep a skill icon visible after XP is gained",
		section = billboardSection
	)
	default int skillingTimeoutSeconds()
	{
		return 12;
	}

	@ConfigItem(
		keyName = "enableGlobalTextureBanding",
		name = "Reduce game texture quality",
		description = "Apply billboard-style color bands to in-game model textures",
		section = texturesSection
	)
	default boolean enableGlobalTextureBanding()
	{
		return false;
	}

	@Range(
		min = 1,
		max = 64
	)
	@ConfigItem(
		keyName = "globalTextureColorBands",
		name = "Texture color bands",
		description = "Number of brightness bands used by in-game model textures",
		section = texturesSection
	)
	default int globalTextureColorBands()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "debugDrawBillboardOutline",
		name = "Draw billboard outline",
		description = "Draw a red outline around the four corners of each billboard",
		section = debugSection
	)
	default boolean debugDrawBillboardOutline()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugDrawBillboardPaintOrder",
		name = "Draw billboard paint order",
		description = "Draw each billboard's paint order number in its center",
		section = debugSection
	)
	default boolean debugDrawBillboardPaintOrder()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugDrawBoundingBox",
		name = "Draw bounding box",
		description = "Draw a static footprint and height box for billboarded NPCs, players, projectiles, and ground items",
		section = debugSection
	)
	default boolean debugDrawBoundingBox()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugDrawFrameNumber",
		name = "Draw frame number",
		description = "Draw the in-game animation frame and the frame currently forced by snapping",
		section = debugSection
	)
	default boolean debugDrawFrameNumber()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugShowCacheInvalidations",
		name = "Show cache invalidations",
		description = "Draw billboards as solid red for the frame where their cache entry is invalidated",
		section = debugSection
	)
	default boolean debugShowCacheInvalidations()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugShowRedraws",
		name = "Show redraws",
		description = "Flash a random color for the frame where the cached billboard image is actually redrawn",
		section = debugSection
	)
	default boolean debugShowRedraws()
	{
		return false;
	}
}
