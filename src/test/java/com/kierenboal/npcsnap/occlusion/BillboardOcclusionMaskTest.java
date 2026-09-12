package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardFrameBuffer;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Polygon;
import net.runelite.api.Client;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
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
	public void runeLiteHullRetainsItsInteriorAtHighAndLow()
	{
		net.runelite.api.geometry.SimplePolygon hull = new net.runelite.api.geometry.SimplePolygon(
			new int[] {0, 64, 0}, new int[] {0, 0, 64}, 3);
		for (BillboardOcclusionQuality quality : new BillboardOcclusionQuality[] {
			BillboardOcclusionQuality.HIGH, BillboardOcclusionQuality.LOW})
		{
			BillboardOcclusionMask mask = new BillboardOcclusionMask();
			mask.prepare(Collections.singletonList(BillboardOcclusionMask.Occluder.vertical(
				hull, 64, 100f, 0, 200f, "runelite-hull")), quality, 0, 0, 64, 64);
			assertTrue(mask.isOccluded(20, 20, 300d));
			assertFalse(mask.isOccluded(48, 48, 300d));
			assertEquals(BillboardOcclusionMask.CellResult.OCCLUDED,
				mask.classifySample(mask.sampleX(20), 20, 300d));
		}
	}
	@Test
	public void verticalFallbackDoesNotFillItsBoundingRectangle()
	{
		Polygon shape = new Polygon(
			new int[] {0, 12, 0}, new int[] {0, 0, 12}, 3);
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(Collections.singletonList(BillboardOcclusionMask.Occluder.vertical(
			shape, 12, 100f, 0, 200f, "wall")),
			BillboardOcclusionQuality.HIGH, 0, 0, 12, 12);
		assertTrue(mask.isOccluded(1, 1, 300d));
		assertFalse(mask.isOccluded(10, 10, 300d));
		assertEquals(null, mask.sourceAt(10, 10));
	}

	@Test
	public void compositorRefinesTriangleEdgesWithinOneCoarseCell()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(Collections.singletonList(BillboardOcclusionMask.Occluder.triangle(
			0, 0, 20f, 3, 0, 20f, 0, 3, 20f)),
			BillboardOcclusionQuality.HIGH, 0, 0, 4, 4);
		// The center misses this triangle, but the cell must still be considered.
		assertEquals(BillboardOcclusionMask.CellResult.REFINE, mask.classifySample(0, 1, 1000d));
		Client client = proxy(
			Client.class,
			method("getCameraFpY", -1000f),
			method("get3dZoom", 512),
			method("getViewportHeight", 4));
		BillboardDepthSurface surface =
			new BillboardDepthSurface(
				new BillboardDepthCalculator(client),
				0, 0, 0d, 4, new Rectangle(0, -4, 4, 4), new Rectangle(0, 0, 4, 4), 0, 0);
		BillboardFrameBuffer buffer =
			new BillboardFrameBuffer(mask,
				new BillboardPerformanceMetrics(), draw -> surface);
		BufferedImage sprite = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++)
		{
			for (int x = 0; x < 4; x++) { sprite.setRGB(x, y, 0xFFFFFFFF); }
		}
		buffer.begin(4, 4);
		buffer.blit(new PreparedBillboardDraw(null,
			new BillboardRenderResult(new Rectangle(0, 0, 4, 4),
				sprite, new Rectangle(0, 0, 4, 4)), 1), 0, 0, 4, 4, false);
		assertEquals(0, buffer.image().getRGB(1, 1));
		assertEquals(0xFFFFFFFF, buffer.image().getRGB(3, 1));
		assertFalse(mask.isOccluded(-1, 1, 1000d));

		// Exercise full interior cells as well as refined edges at both reported qualities.
		for (BillboardOcclusionQuality quality : new BillboardOcclusionQuality[] {
			BillboardOcclusionQuality.HIGH, BillboardOcclusionQuality.LOW})
		{
			mask.prepare(Collections.singletonList(BillboardOcclusionMask.Occluder.triangle(
				0, 0, 20f, 31, 0, 20f, 0, 31, 20f)), quality, 0, 0, 32, 32);
			for (int y = 0; y < 32; y++)
			{
				for (int x = 0; x < 32; x++)
				{
					BillboardOcclusionMask.CellResult result = mask.classifySample(mask.sampleX(x), y, 1000d);
					boolean hidden = result == BillboardOcclusionMask.CellResult.OCCLUDED
						|| (result == BillboardOcclusionMask.CellResult.REFINE
							&& (mask.refinedSampleBits(mask.sampleX(x), y, 1000d) & (1 << mask.sampleOffset(x))) != 0);
					assertEquals(x + y <= 31, hidden);
				}
			}
		}
	}
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
	public void verticalFallbackTracksPerspectiveDepthByRow()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(BillboardOcclusionMask.Occluder.vertical(
				new Rectangle(0, 0, 12, 12),
				10, 100f,
				0, 200f,
				"vertical-wall")),
			BillboardOcclusionQuality.MAX,
			0,
			0,
			12,
			12
		);

		assertEquals(200f, mask.depthAt(5, 0), 0.01f);
		assertEquals(133.33f, mask.depthAt(5, 5), 0.02f);
		assertEquals(100f, mask.depthAt(5, 10), 0.01f);
		assertEquals("vertical-wall", mask.sourceAt(5, 5));
		assertFalse(mask.isOccluded(5, 0, 200d));
		assertTrue(mask.isOccluded(5, 0, 220d));
	}

	@Test
	public void coarseVerticalFallbackRecomputesDepthWithinEachMaskRow()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Collections.singletonList(BillboardOcclusionMask.Occluder.vertical(
				new Rectangle(0, 0, 16, 16),
				16, 100f,
				0, 400f,
				"vertical-wall")),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);

		for (int y = 0; y < 16; y++)
		{
			double t = (y - 16) / -16.0d;
			double surfaceDepth = 1.0d / (((1.0d - t) / 100.0d) + (t / 400.0d));
			assertEquals(surfaceDepth, mask.depthAt(2, y), 0.02d);
			assertFalse(mask.isOccluded(2, y, surfaceDepth));
			assertTrue(mask.isOccluded(2, y, surfaceDepth + 20.0d));
		}
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
	public void reportsSourceOfNearestOccluder()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(
			Arrays.asList(
				new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 8, 8), 20f, "far-wall"),
				new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 8, 8), 10f, "near-wall")
			),
			BillboardOcclusionQuality.HIGH,
			0,
			0,
			16,
			16
		);

		assertEquals("near-wall", mask.sourceAt(2, 2));
		assertEquals(null, mask.sourceAt(12, 12));
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
