package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionComposition;

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
		assertTrue(config.applyToCustomUiTextures());
		assertFalse(config.deterministicAnimationLooping());
		assertFalse(config.useRetroOverheads());
		assertTrue(config.alignOverheadPrayers());
		assertFalse(config.ignoreProjectionSkewCorrection());
		assertFalse(config.logBillboardAnimationData());
		assertFalse(config.enableShiftRightClickExportPng());
	}

	@Test
	public void retroOverheadOptionsHaveStablePublicConfiguration()
		throws NoSuchMethodException
	{
		assertConfig("useRetroOverheads", "useRetroOverheads", "Use retro HP bar, overhead chat and hitsplats", 11);
		assertLegacyConfigHidden("useRetroHpBar");
		assertLegacyConfigHidden("useRetroHitsplats");
		assertLegacyConfigHidden("useRetroChatEffects");
		assertConfig("alignOverheadPrayers", "alignOverheadPrayers", "Align overhead prayers", 12);
	}

	@Test
	public void retroProjectionOptionsHaveStablePublicConfiguration()
		throws NoSuchMethodException
	{
		assertConfig("ignoreProjectionSkewCorrection", "ignoreProjectionSkewCorrection", "Ignore projection skew correction", 13);
	}

	@Test
	public void unsupportedWorldTextureOptionsRemainHiddenForCompatibility()
		throws NoSuchMethodException
	{
		for (String method : new String[] {"enableGlobalTextureBanding", "globalTextureSpriteQuality", "globalTextureColorBands"})
		{
			ConfigItem item = NpcSnapConfig.class.getMethod(method).getAnnotation(ConfigItem.class);
			assertEquals(method, item.keyName());
			assertTrue(item.hidden());
		}

		assertFalse(config.enableGlobalTextureBanding());
	}

	private static void assertLegacyConfigHidden(String method)
		throws NoSuchMethodException
	{
		ConfigItem item = NpcSnapConfig.class.getMethod(method).getAnnotation(ConfigItem.class);
		assertTrue(item.hidden());
	}

	private static void assertConfig(String method, String key, String name, int position)
		throws NoSuchMethodException
	{
		ConfigItem item = NpcSnapConfig.class.getMethod(method).getAnnotation(ConfigItem.class);
		assertEquals(key, item.keyName());
		assertEquals(name, item.name());
		assertEquals(NpcSnapConfig.retroSection, item.section());
		assertEquals(position, item.position());
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
		assertFalse(config.debugLogProjectionSkew());
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
		assertEquals(BillboardOcclusionComposition.TRANSPARENCY_AWARE, config.billboardOcclusionComposition());
		assertEquals("Transparency-aware", BillboardOcclusionComposition.TRANSPARENCY_AWARE.toString());
		assertEquals("Hard cutout", BillboardOcclusionComposition.HARD_CUTOUT.toString());
		assertEquals(BillboardOcclusionComposition.TRANSPARENCY_AWARE, BillboardOcclusionComposition.normalize(null));
		assertEquals(90, config.billboardRadiusTiles());
		assertEquals(128, config.billboardMaxEntities());
		assertEquals(64, config.billboardMaxDrawsPerFrame());
		assertEquals(16, config.billboardColorBands());
		assertEquals(10, config.skillingTimeoutSeconds());
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
