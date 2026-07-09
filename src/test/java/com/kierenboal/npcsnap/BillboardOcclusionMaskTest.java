package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardOcclusionMaskTest
{
	@Test
	public void qualityLevelsTradeAccuracyForSamplingCost()
	{
		assertEquals(16, BillboardOcclusionQuality.LOW.sampleStep());
		assertEquals(8, BillboardOcclusionQuality.MEDIUM.sampleStep());
		assertEquals(4, BillboardOcclusionQuality.HIGH.sampleStep());
		assertEquals(2, BillboardOcclusionQuality.ULTRA.sampleStep());
		assertEquals(1, BillboardOcclusionQuality.MAX.sampleStep());
		assertTrue(BillboardOcclusionQuality.LOW.sampleStep() > BillboardOcclusionQuality.MEDIUM.sampleStep());
		assertTrue(BillboardOcclusionQuality.MEDIUM.sampleStep() > BillboardOcclusionQuality.HIGH.sampleStep());
		assertTrue(BillboardOcclusionQuality.HIGH.sampleStep() > BillboardOcclusionQuality.ULTRA.sampleStep());
		assertTrue(BillboardOcclusionQuality.ULTRA.sampleStep() > BillboardOcclusionQuality.MAX.sampleStep());
		assertTrue(BillboardOcclusionQuality.LOW.vertexStride() > BillboardOcclusionQuality.MEDIUM.vertexStride());
		assertTrue(BillboardOcclusionQuality.MEDIUM.vertexStride() > BillboardOcclusionQuality.HIGH.vertexStride());
		assertEquals(BillboardOcclusionQuality.HIGH.vertexStride(), BillboardOcclusionQuality.ULTRA.vertexStride());
		assertEquals(BillboardOcclusionQuality.ULTRA.vertexStride(), BillboardOcclusionQuality.MAX.vertexStride());
	}

	@Test
	public void ultraRasterizesMoreCellsThanHigh()
	{
		BillboardOcclusionMask high = new BillboardOcclusionMask();
		high.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 20f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);

		BillboardOcclusionMask ultra = new BillboardOcclusionMask();
		ultra.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 20f)),
			BillboardOcclusionQuality.ULTRA,
			0,
			0,
			16,
			16
		);

		assertTrue(ultra.coveredCellCount() > high.coveredCellCount());
	}

	@Test
	public void maxRasterizesMoreCellsThanUltra()
	{
		BillboardOcclusionMask ultra = new BillboardOcclusionMask();
		ultra.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 20f)),
			BillboardOcclusionQuality.ULTRA,
			0,
			0,
			16,
			16
		);

		BillboardOcclusionMask max = new BillboardOcclusionMask();
		max.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 20f)),
			BillboardOcclusionQuality.MAX,
			0,
			0,
			16,
			16
		);

		assertTrue(max.coveredCellCount() > ultra.coveredCellCount());
	}

	@Test
	public void offQualityDoesNotOcclude()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 1f)),
			BillboardOcclusionQuality.OFF,
			0,
			0,
			16,
			16
		);

		assertFalse(mask.isOccluded(2, 2, 100d));
		assertEquals(0, mask.coveredCellCount());
	}

	@Test
	public void nullQualityDoesNotOcclude()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 1f)),
			null,
			0,
			0,
			16,
			16
		);

		assertFalse(mask.isOccluded(2, 2, 100d));
		assertEquals(0, mask.coveredCellCount());
	}

	@Test
	public void shapeCoverageAndDepthBiasDetermineOcclusion()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 8, 8), 20f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);

		assertTrue(mask.isOccluded(2, 2, 40d));
		assertFalse(mask.isOccluded(2, 2, 28d));
		assertFalse(mask.isOccluded(12, 12, 40d));
		assertEquals(4, mask.coveredCellCount());
	}

	@Test
	public void reportsCoverageOnlyForRowsWithFiniteDepthCells()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 8, 8, 8), 20f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);

		assertFalse(mask.hasCoverageAt(2));
		assertTrue(mask.hasCoverageAt(10));
	}

	@Test
	public void sampleCellLookupMatchesCanvasLookupForCoarseQuality()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 20f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);

		int sampleX = mask.sampleX(6);
		assertEquals(mask.isOccluded(6, 6, 40d), mask.isOccludedSample(sampleX, 6, 40d));
		assertEquals(4, mask.sampleStep());
	}

	@Test
	public void triangleDepthIsInterpolatedNearEachVertex()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(BillboardOcclusionMask.Occluder.triangle(0, 0, 10f, 10, 0, 110f, 0, 10, 210f)),
			BillboardOcclusionQuality.MAX,
			0,
			0,
			12,
			12
		);

		assertEquals(40f, mask.depthAt(1, 1), 0.01f);
		assertEquals(110f, mask.depthAt(8, 1), 0.01f);
		assertEquals(180f, mask.depthAt(1, 8), 0.01f);
	}

	@Test
	public void farPartOfTriangleDoesNotUseNearestVertexDepth()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(BillboardOcclusionMask.Occluder.triangle(0, 0, 10f, 10, 0, 100f, 0, 10, 100f)),
			BillboardOcclusionQuality.MAX,
			0,
			0,
			12,
			12
		);

		assertTrue(mask.isOccluded(1, 1, 50d));
		assertFalse(mask.isOccluded(8, 1, 50d));
	}

	@Test
	public void flatFallbackKeepsConstantDepth()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 12, 12), 25f)),
			BillboardOcclusionQuality.MAX,
			0,
			0,
			12,
			12
		);

		assertEquals(25f, mask.depthAt(1, 1), 0.0f);
		assertEquals(25f, mask.depthAt(10, 10), 0.0f);
	}

	@Test
	public void nearestOverlappingOccluderWins()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Arrays.asList(
				new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 8, 8), 20f),
				new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 8, 8), 10f)
			),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);

		assertTrue(mask.isOccluded(2, 2, 19d));
		assertEquals(4, mask.coveredCellCount());
	}

	@Test
	public void debugRenderFadesFartherDepthsTowardBlack()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Arrays.asList(
				new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 4, 4), 10f),
				new BillboardOcclusionMask.Occluder(new Rectangle(4, 0, 4, 4), 30f)
			),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			8,
			4
		);

		BufferedImage image = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			mask.drawDebug(graphics);
		}
		finally
		{
			graphics.dispose();
		}

		Color near = new Color(image.getRGB(1, 1), true);
		Color far = new Color(image.getRGB(5, 1), true);
		assertTrue(near.getBlue() > far.getBlue());
		assertTrue(near.getGreen() > far.getGreen());
		assertEquals(10f, mask.nearestDepth(), 0.0f);
		assertEquals(30f, mask.furthestDepth(), 0.0f);
	}

	@Test
	public void debugRenderDrawsRasterBounds()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(4, 4, 4, 4), 10f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16,
			new Rectangle(4, 4, 8, 8)
		);

		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			mask.drawDebug(graphics);
		}
		finally
		{
			graphics.dispose();
		}

		Color border = new Color(image.getRGB(4, 4), true);
		assertTrue(border.getAlpha() > 0);
		assertTrue(border.getBlue() > 0);
	}

	@Test
	public void debugRenderDrawsRasterBoundsForEmptyMask()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.emptyList(),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16,
			new Rectangle(4, 4, 8, 8)
		);

		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			mask.drawDebug(graphics);
		}
		finally
		{
			graphics.dispose();
		}

		Color border = new Color(image.getRGB(4, 4), true);
		assertTrue(border.getAlpha() > 0);
		assertFalse(mask.isOccluded(6, 6, 100d));
		assertEquals(0, mask.coveredCellCount());
	}

	@Test
	public void interestBoundsLimitRasterizedArea()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 16, 16), 20f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16,
			new Rectangle(0, 0, 8, 8)
		);

		assertTrue(mask.isOccluded(2, 2, 40d));
		assertFalse(mask.isOccluded(12, 12, 40d));
		assertEquals(4, mask.coveredCellCount());
	}

	@Test
	public void activeRegionsSkipGapsInsideUnionBounds()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 32, 8), 20f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			32,
			8,
			new Rectangle(0, 0, 32, 8),
			Arrays.asList(new Rectangle(0, 0, 8, 8), new Rectangle(24, 0, 8, 8))
		);

		assertTrue(mask.isOccluded(2, 2, 40d));
		assertFalse(mask.isOccluded(16, 2, 40d));
		assertTrue(mask.isOccluded(26, 2, 40d));
		assertEquals(8, mask.coveredCellCount());
	}

	@Test
	public void emptyPrepareClearsPreviousMask()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 8, 8), 20f)),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);
		assertTrue(mask.isOccluded(2, 2, 40d));

		mask.prepare(Collections.emptyList(), BillboardOcclusionQuality.HIGH, 0, 0, 16, 16);

		assertFalse(mask.isOccluded(2, 2, 40d));
		assertEquals(0, mask.coveredCellCount());
	}
}
