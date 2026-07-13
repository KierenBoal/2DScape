package com.kierenboal.npcsnap.rendering;

import java.awt.Color;
import org.junit.Assert;
import org.junit.Test;

public class BillboardAngleColorUtilsTest
{
	@Test
	public void convertsLegacyActorYawToBillboardUnits()
	{
		Assert.assertEquals(4096, BillboardAngleUtils.angleToBillboardUnits(512, 2048));
		Assert.assertEquals(8192, BillboardAngleUtils.angleToBillboardUnits(1024, 2048));
		Assert.assertEquals(12288, BillboardAngleUtils.angleToBillboardUnits(1536, 2048));
	}

	@Test
	public void preservesCameraYawAlreadyInBillboardUnits()
	{
		Assert.assertEquals(4096, BillboardAngleUtils.angleToBillboardUnits(4096, 16384));
		Assert.assertEquals(8192, BillboardAngleUtils.angleToBillboardUnits(8192, 16384));
		Assert.assertEquals(12288, BillboardAngleUtils.angleToBillboardUnits(12288, 16384));
	}

	@Test
	public void wrapsConvertedAngles()
	{
		Assert.assertEquals(0, BillboardAngleUtils.angleToBillboardUnits(2048, 2048));
		Assert.assertEquals(4096, BillboardAngleUtils.angleToBillboardUnits(2560, 2048));
		Assert.assertEquals(12288, BillboardAngleUtils.angleToBillboardUnits(-512, 2048));
	}

	@Test
	public void skipsExactCapeArtifactFaceColor()
	{
		Assert.assertTrue(BillboardColorUtils.isSkippedCapeArtifactFaceColor(new Color(147, 143, 143, 255)));
	}

	@Test
	public void doesNotSkipCapeArtifactRgbWithDifferentAlpha()
	{
		Assert.assertFalse(BillboardColorUtils.isSkippedCapeArtifactFaceColor(new Color(147, 143, 143, 254)));
	}

	@Test
	public void doesNotSkipNearbyGrayFaceColors()
	{
		Assert.assertFalse(BillboardColorUtils.isSkippedCapeArtifactFaceColor(new Color(146, 143, 143, 255)));
		Assert.assertFalse(BillboardColorUtils.isSkippedCapeArtifactFaceColor(new Color(147, 142, 143, 255)));
		Assert.assertFalse(BillboardColorUtils.isSkippedCapeArtifactFaceColor(new Color(147, 143, 142, 255)));
	}

	@Test
	public void applyLightBoostScalesRgbAndPreservesAlpha()
	{
		Color color = BillboardColorUtils.applyLightBoost(new Color(80, 100, 120, 150), 125.0d);

		Assert.assertEquals(new Color(100, 125, 150, 150), color);
	}

	@Test
	public void applyLightBoostClampsRgbToMaximum()
	{
		Color color = BillboardColorUtils.applyLightBoost(new Color(200, 220, 240, 255), 150.0d);

		Assert.assertEquals(new Color(255, 255, 255, 255), color);
	}

	@Test
	public void modulateTexturePixelMultipliesSampleByShadeBeforeBanding()
	{
		int expected = NpcSnapColorBanding.snapTexturePixel(0x402020C0, 8);

		int pixel = BillboardColorUtils.modulateTexturePixel(0x804080C0, new Color(128, 64, 255, 128), 8);

		Assert.assertEquals(expected, pixel);
	}

	@Test
	public void modulateTexturePixelTreatsRgbOnlySamplesAsOpaque()
	{
		int expected = NpcSnapColorBanding.snapTexturePixel(0xFF010203, 8);

		int pixel = BillboardColorUtils.modulateTexturePixel(0x00010203, new Color(255, 255, 255, 255), 8);

		Assert.assertEquals(expected, pixel);
	}

	@Test
	public void resolveFaceColorPrefersUnlitMaterialColorWhenLitIsNotMoreColorful()
	{
		Color color = BillboardColorUtils.resolveFaceColor(
			0,
			new int[] {40},
			new int[] {40},
			new int[] {40},
			new short[] {80},
			200
		);

		Assert.assertEquals(new Color(159, 159, 159, 200), color);
	}

	@Test
	public void resolveFaceColorPrefersColorfulLitColorOverGrayUnlitColor()
	{
		Color color = BillboardColorUtils.resolveFaceColor(
			0,
			new int[] {(1 << 10) | (7 << 7) | 16},
			new int[] {(1 << 10) | (7 << 7) | 16},
			new int[] {(1 << 10) | (7 << 7) | 16},
			new short[] {13},
			255
		);

		Assert.assertTrue(color.getRed() > color.getGreen());
		Assert.assertTrue(color.getGreen() > color.getBlue());
	}

	@Test
	public void resolveFaceColorDoesNotTreatPackedFaceColorsAsDirectRgb()
	{
		Color color = BillboardColorUtils.resolveFaceColor(
			0,
			new int[] {63},
			new int[] {5},
			new int[] {0},
			new short[] {13},
			255
		);

		Assert.assertEquals(new Color(26, 26, 26, 255), color);
	}

	@Test
	public void resolveFaceColorKeepsGrayUnlitColorWhenLitColorIsBlack()
	{
		Color color = BillboardColorUtils.resolveFaceColor(
			0,
			new int[] {0},
			new int[] {0},
			new int[] {0},
			new short[] {13},
			255
		);

		Assert.assertEquals(new Color(26, 26, 26, 255), color);
	}

	@Test
	public void resolveFaceColorUsesFlatPackedColorWhenThirdFaceColorIsMissing()
	{
		Color color = BillboardColorUtils.resolveFaceColor(
			0,
			new int[] {912},
			new int[] {0},
			new int[] {-1},
			null,
			255
		);

		Assert.assertTrue(color.getRed() > 0);
		Assert.assertEquals(0, color.getGreen());
		Assert.assertEquals(0, color.getBlue());
	}

	@Test
	public void resolveFaceColorFallsBackToUnlitColorWhenLitColorsAreMissing()
	{
		Color color = BillboardColorUtils.resolveFaceColor(
			0,
			new int[] {-1},
			new int[] {-1},
			new int[] {-1},
			new short[] {80},
			200
		);

		Assert.assertEquals(new Color(159, 159, 159, 200), color);
	}

	@Test
	public void resolveFaceColorUnpacksMaxSaturationWithoutWashingOutRed()
	{
		Color color = BillboardColorUtils.resolveFaceColor(
			0,
			new int[] {-1},
			new int[] {-1},
			new int[] {-1},
			new short[] {(short) ((7 << 7) | 32)},
			255
		);

		Assert.assertTrue(color.getRed() >= 120);
		Assert.assertTrue(color.getGreen() <= 4);
		Assert.assertTrue(color.getBlue() <= 4);
	}
}
