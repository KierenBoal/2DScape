package com.kierenboal.npcsnap.rendering;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import net.runelite.api.Point;
import org.junit.Assert;
import org.junit.Test;

public class BillboardGeometryUtilsTest
{
	@Test
	public void wrapUnitKeepsValuesInsideUnitInterval()
	{
		Assert.assertEquals(0.25d, BillboardGeometryUtils.wrapUnit(0.25d), 0.00001d);
		Assert.assertEquals(0.25d, BillboardGeometryUtils.wrapUnit(1.25d), 0.00001d);
		Assert.assertEquals(0.75d, BillboardGeometryUtils.wrapUnit(-0.25d), 0.00001d);
	}

	@Test
	public void expandedBoundsAddsPaddingAndRejectsInvalidInputs()
	{
		Rectangle bounds = new Rectangle(10, 20, 30, 40);
		Assert.assertEquals(new Rectangle(8, 18, 34, 44), BillboardGeometryUtils.expandedBounds(bounds, 2));
		Assert.assertEquals(bounds, BillboardGeometryUtils.expandedBounds(bounds, 0));
		Assert.assertNull(BillboardGeometryUtils.expandedBounds(null, 2));
	}

	@Test
	public void sourceBoundsAndDistanceValidationRejectInvalidValues()
	{
		Assert.assertTrue(BillboardGeometryUtils.isUsableSourceBounds(new Rectangle(0, 0, 1, 1)));
		Assert.assertFalse(BillboardGeometryUtils.isUsableSourceBounds(null));
		Assert.assertFalse(BillboardGeometryUtils.isUsableSourceBounds(new Rectangle()));
		Assert.assertTrue(BillboardGeometryUtils.isUsableDistance(1d));
		Assert.assertFalse(BillboardGeometryUtils.isUsableDistance(0d));
		Assert.assertFalse(BillboardGeometryUtils.isUsableDistance(Double.NaN));
		Assert.assertFalse(BillboardGeometryUtils.isUsableDistance(Double.POSITIVE_INFINITY));
	}

	@Test
	public void sizeProjectionAndAspectHelpersClampInvalidResults()
	{
		Assert.assertEquals(15, BillboardGeometryUtils.scaledSize(10, 1.5d));
		Assert.assertEquals(-1, BillboardGeometryUtils.scaledSize(0, 1.5d));
		Assert.assertEquals(-1, BillboardGeometryUtils.scaledSize(10, Double.NaN));
		Assert.assertEquals(20, BillboardGeometryUtils.projectedHeight(new Point(0, 30), new Point(0, 10)));
		Assert.assertEquals(0, BillboardGeometryUtils.projectedHeight(null, new Point(0, 10)));
		Assert.assertEquals(50, BillboardGeometryUtils.aspectWidth(new Rectangle(0, 0, 100, 40), 20));
		Assert.assertEquals(-1, BillboardGeometryUtils.aspectWidth(new Rectangle(), 20));
	}

	@Test
	public void drawSizeAndCanvasCoordinateGuardsEnforceSafetyLimits()
	{
		Assert.assertTrue(BillboardGeometryUtils.isUsableDrawSize(1, 1));
		Assert.assertFalse(BillboardGeometryUtils.isUsableDrawSize(0, 1));
		Assert.assertFalse(BillboardGeometryUtils.isUsableDrawSize(Integer.MAX_VALUE, 1));
		Assert.assertTrue(BillboardGeometryUtils.isUsableCanvasCoordinate(100));
		Assert.assertFalse(BillboardGeometryUtils.isUsableCanvasCoordinate(Integer.MAX_VALUE));
	}

	@Test
	public void projectionShearIsSymmetricBoundedAndContinuousAcrossTheCameraAxis()
	{
		Assert.assertEquals(0.0d, BillboardGeometryUtils.projectionShearSlope(0.0d, 1.0d), 0.0d);
		double previous = -0.20d;
		for (int i = -1000; i <= 1000; i++)
		{
			double right = i / 1000.0d;
			double slope = BillboardGeometryUtils.projectionShearSlope(right, 1.0d);
			Assert.assertEquals(slope, -BillboardGeometryUtils.projectionShearSlope(-right, 1.0d), 1e-12d);
			Assert.assertTrue(Math.abs(slope) <= 0.20d);
			Assert.assertTrue(slope >= previous && slope - previous < 0.0011d);
			previous = slope;
		}
		Assert.assertTrue(BillboardGeometryUtils.projectionShearSlope(0.10d, 1.0d) > 0.09d);
	}

	@Test
	public void projectionShearFadesBeforeWorldUpBecomesHorizontalOrInverted()
	{
		Assert.assertEquals(0.0d, BillboardGeometryUtils.projectionShearSlope(0.4d, 0.15d), 0.0d);
		Assert.assertEquals(0.0d, BillboardGeometryUtils.projectionShearSlope(0.4d, 0.0d), 0.0d);
		Assert.assertEquals(0.0d, BillboardGeometryUtils.projectionShearSlope(0.4d, -0.5d), 0.0d);
		Assert.assertEquals(0.0d, BillboardGeometryUtils.projectionShearSlope(Double.NaN, 1.0d), 0.0d);
		Assert.assertEquals(0.0d, BillboardGeometryUtils.projectionShearSlope(1.0d, Double.POSITIVE_INFINITY), 0.0d);
		double previous = 0.0d;
		for (int i = 150; i <= 500; i++)
		{
			double up = i / 1000.0d;
			double slope = BillboardGeometryUtils.projectionShearSlope(up * 0.2d, up);
			Assert.assertTrue(slope >= previous && slope - previous < 0.001d);
			previous = slope;
		}
	}

	@Test
	public void projectedBoundsKeepModelOriginAndUniformScaleForPitchedFrames()
	{
		Rectangle source = new Rectangle(-50, -100, 100, 120);
		BillboardCanvasPoint base = new BillboardCanvasPoint(300.25d, 500.25d, 1000.0d, 0.0d, 1.0d);
		Rectangle bounds = BillboardGeometryUtils.projectedDrawBounds(source, base, 500.0d);

		Assert.assertEquals(new Rectangle(275, 450, 50, 60), bounds);
		// The visible content is allowed below the origin; it is not pulled up to it.
		Assert.assertEquals(500.0d, bounds.y - source.y * 0.5d, 0.001d);
		Assert.assertNull(BillboardGeometryUtils.projectedDrawBounds(source, base, Double.NaN));
		Assert.assertNull(BillboardGeometryUtils.projectedDrawBounds(source, null, 500.0d));
	}

	@Test
	public void backFaceCheckHandlesWindingAndInvalidIndices()
	{
		float[] x = {0, 2, 0};
		float[] y = {0, 0, 2};
		float[] depth = {1, 1, 1};
		Assert.assertTrue(BillboardGeometryUtils.isBackFace(x, y, depth, 0, 1, 2));
		Assert.assertFalse(BillboardGeometryUtils.isBackFace(x, y, depth, 0, 2, 1));
	}

	@Test
	public void computesUnionOfFaceCoordinates()
	{
		FaceDraw first = face(-2, 3, 5, 8, 1, -4);
		FaceDraw second = face(10, 2, 4, 12, 6, 7);

		Assert.assertEquals(new Rectangle(-2, -4, 13, 17), BillboardGeometryUtils.computeBounds(List.of(first, second)));
		Assert.assertEquals(new Rectangle(), BillboardGeometryUtils.computeBounds(List.of()));
	}

	private static FaceDraw face(int x0, int y0, int x1, int y1, int x2, int y2)
	{
		return new FaceDraw(x0, y0, x1, y1, x2, y2, Color.WHITE, 1d, null, null);
	}
}
