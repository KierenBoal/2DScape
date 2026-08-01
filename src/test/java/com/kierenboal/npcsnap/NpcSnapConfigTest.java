package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;

import java.awt.Color;
import org.junit.Test;
import net.runelite.client.config.ConfigItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcSnapConfigTest
{
	private final NpcSnapConfig config = new NpcSnapConfig() { };

	@Test
	public void defaultsEnableTheCoreLocalRenderingFeatures()
	{
		assertTrue(config.enable2dBillboardSprites());
		assertTrue(config.enableAnimationFrameSnapping());
		assertTrue(config.enableRotationSnapping());
		assertTrue(config.enableSkillingBubbles());
		assertTrue(config.enableBillboardSpriteShadows());
		assertFalse(config.applyToBoats());
		assertFalse(config.deterministicAnimationLooping());
		assertFalse(config.logBillboardAnimationData());
		assertFalse(config.enableShiftRightClickExportPng());
	}

	@Test
	public void boatsAreHiddenAndDisabled()
		throws NoSuchMethodException
	{
		ConfigItem item = NpcSnapConfig.class.getMethod("applyToBoats").getAnnotation(ConfigItem.class);
		assertTrue(item.hidden());
		assertFalse(config.applyToBoats());
	}

	@Test
	public void removedPerTargetFrameAndGroundItemColourSettingsAreNotExposed()
	{
		assertFalse(hasMethod("enableProjectileFrameSnapping"));
		assertFalse(hasMethod("enableGraphicsObjectFrameSnapping"));
		assertFalse(hasMethod("enableObjectFrameSnapping"));
		assertFalse(hasMethod("groundItemSpriteColorBands"));
	}

	private static boolean hasMethod(String name)
	{
		for (java.lang.reflect.Method method : NpcSnapConfig.class.getDeclaredMethods())
		{
			if (name.equals(method.getName()))
			{
				return true;
			}
		}

		return false;
	}

	@Test
	public void defaultsKeepOptionalExpensiveAndDebugFeaturesOff()
	{
		assertFalse(config.applyToObjects());
		assertFalse(config.renderBillboardsOnAllPlanes());
		assertFalse(config.enableGlobalTextureBanding());
		assertFalse(config.enableUiTextureBanding());
		assertFalse(config.debugDrawBillboardOutline());
		assertFalse(config.debugDrawBillboardPaintOrder());
		assertFalse(config.debugPerformanceMetrics());
		assertFalse(config.debugLogClassifications());
	}

	@Test
	public void defaultsRetainRenderingBudgetsAndQuality()
	{
		assertEquals(4, config.numberOfYawRotationAngles());
		assertEquals(1, config.numberOfPitchRotationAngles());
		assertEquals(4, config.animationFrameCount());
		assertEquals(33.0d, config.renderBillboardQuality(), 0d);
		assertEquals(100.0d, config.globalTextureSpriteQuality(), 0d);
		assertEquals(BillboardOcclusionQuality.MEDIUM, config.billboardOcclusionQuality());
		assertEquals(90, config.billboardRadiusTiles());
		assertEquals(128, config.billboardMaxEntities());
		assertEquals(16, config.billboardMaxDrawsPerFrame());
		assertEquals(16, config.billboardColorBands());
		assertEquals(15, config.skillingTimeoutSeconds());
	}

	@Test
	public void defaultsRetainInteractionOutlineColors()
	{
		assertTrue(config.enablePlayerInteractionOutline());
		assertTrue(config.enableNpcInteractionOutline());
		assertTrue(config.enableGroundItemInteractionOutline());
		assertEquals(new Color(0x9000FFFF, true), config.playerHoverOutlineColor());
		assertEquals(new Color(0x90FF0000, true), config.playerInteractionOutlineColor());
		assertEquals(new Color(0x90FFFF00, true), config.npcHoverOutlineColor());
		assertEquals(new Color(0x90FF0000, true), config.npcInteractionOutlineColor());
		assertEquals(new Color(0x9000FFFF, true), config.groundItemHoverOutlineColor());
		assertEquals(new Color(0x90FF0000, true), config.groundItemInteractionOutlineColor());
	}
}
