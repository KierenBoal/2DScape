package com.kierenboal.npcsnap;

import java.awt.Color;
import org.junit.Assert;
import org.junit.Test;

public class NpcBillboardOverlayAngleTest
{
	@Test
	public void convertsLegacyActorYawToBillboardUnits()
	{
		Assert.assertEquals(4096, NpcBillboardOverlay.angleToBillboardUnits(512, 2048));
		Assert.assertEquals(8192, NpcBillboardOverlay.angleToBillboardUnits(1024, 2048));
		Assert.assertEquals(12288, NpcBillboardOverlay.angleToBillboardUnits(1536, 2048));
	}

	@Test
	public void preservesCameraYawAlreadyInBillboardUnits()
	{
		Assert.assertEquals(4096, NpcBillboardOverlay.angleToBillboardUnits(4096, 16384));
		Assert.assertEquals(8192, NpcBillboardOverlay.angleToBillboardUnits(8192, 16384));
		Assert.assertEquals(12288, NpcBillboardOverlay.angleToBillboardUnits(12288, 16384));
	}

	@Test
	public void wrapsConvertedAngles()
	{
		Assert.assertEquals(0, NpcBillboardOverlay.angleToBillboardUnits(2048, 2048));
		Assert.assertEquals(4096, NpcBillboardOverlay.angleToBillboardUnits(2560, 2048));
		Assert.assertEquals(12288, NpcBillboardOverlay.angleToBillboardUnits(-512, 2048));
	}

	@Test
	public void skipsExactCapeArtifactFaceColor()
	{
		Assert.assertTrue(NpcBillboardOverlay.isSkippedCapeArtifactFaceColor(new Color(147, 143, 143, 255)));
	}

	@Test
	public void doesNotSkipCapeArtifactRgbWithDifferentAlpha()
	{
		Assert.assertFalse(NpcBillboardOverlay.isSkippedCapeArtifactFaceColor(new Color(147, 143, 143, 254)));
	}

	@Test
	public void doesNotSkipNearbyGrayFaceColors()
	{
		Assert.assertFalse(NpcBillboardOverlay.isSkippedCapeArtifactFaceColor(new Color(146, 143, 143, 255)));
		Assert.assertFalse(NpcBillboardOverlay.isSkippedCapeArtifactFaceColor(new Color(147, 142, 143, 255)));
		Assert.assertFalse(NpcBillboardOverlay.isSkippedCapeArtifactFaceColor(new Color(147, 143, 142, 255)));
	}

	@Test
	public void resolveFaceColorPrefersUnlitMaterialColorWhenLitIsNotMoreColorful()
	{
		Color color = NpcBillboardOverlay.resolveFaceColor(
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
		Color color = NpcBillboardOverlay.resolveFaceColor(
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
		Color color = NpcBillboardOverlay.resolveFaceColor(
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
		Color color = NpcBillboardOverlay.resolveFaceColor(
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
		Color color = NpcBillboardOverlay.resolveFaceColor(
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
		Color color = NpcBillboardOverlay.resolveFaceColor(
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
		Color color = NpcBillboardOverlay.resolveFaceColor(
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
