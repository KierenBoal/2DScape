package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;

import java.awt.Color;
import org.junit.Test;

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
		assertFalse(config.deterministicAnimationLooping());
		assertFalse(config.logBillboardAnimationData());
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
		assertTrue(config.enableBillboardHoverOutline());
		assertTrue(config.enableBillboardInteractionOutline());
		assertEquals(new Color(0x90FF0000, true), config.billboardInteractionOutlineColor());
		assertEquals(new Color(0x90FFFF00, true), config.billboardHoverOutlineColor());
	}
}
