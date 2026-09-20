package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.TestProxies;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.TextureProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BillboardAppearanceStateTest
{
	@Test
	public void textureBrightnessMultipliesConfiguredLightBoost()
	{
		assertEquals(125, BillboardBrightness.effectiveLightBoostPercent(clientAtBrightness(0.9d), 125));
		assertEquals(188, BillboardBrightness.effectiveLightBoostPercent(clientAtBrightness(0.75d), 125));
		assertEquals(250, BillboardBrightness.effectiveLightBoostPercent(clientAtBrightness(0.6d), 125));
	}

	@Test
	public void colorOverrideChangesRasterColorAndModelCacheState()
	{
		Model defaultModel = model((byte) 0);
		Model tintedModel = model((byte) 127);
		int original = (1 << 10) | (2 << 7) | 3;
		int expected = (44 << 10) | (6 << 7) | 99;

		assertEquals(original, BillboardColorUtils.applyHslOverride(original, defaultModel));
		assertEquals(expected, BillboardColorUtils.applyHslOverride(original, tintedModel));
		assertNotEquals(BillboardModelStateHash.hash(defaultModel), BillboardModelStateHash.hash(tintedModel));
	}

	@Test
	public void unsetOverrideComponentsPreserveOriginalHueAndSaturation()
	{
		Model tint = TestProxies.proxy(Model.class,
			TestProxies.method("getOverrideAmount", (byte) 64),
			TestProxies.method("getOverrideHue", (byte) -1),
			TestProxies.method("getOverrideSaturation", (byte) -1),
			TestProxies.method("getOverrideLuminance", (byte) 100));
		int original = (12 << 10) | (3 << 7) | 20;
		int expected = (12 << 10) | (3 << 7) | 60;
		assertEquals(expected, BillboardColorUtils.applyHslOverride(original, tint));
	}

	private static Client clientAtBrightness(double brightness)
	{
		TextureProvider provider = TestProxies.proxy(TextureProvider.class,
			TestProxies.method("getBrightness", brightness));
		return TestProxies.proxy(Client.class, TestProxies.method("getTextureProvider", provider));
	}

	private static Model model(byte amount)
	{
		return TestProxies.proxy(Model.class,
			TestProxies.method("getOverrideAmount", amount),
			TestProxies.method("getOverrideHue", (byte) 45),
			TestProxies.method("getOverrideSaturation", (byte) 7),
			TestProxies.method("getOverrideLuminance", (byte) 100));
	}
}
