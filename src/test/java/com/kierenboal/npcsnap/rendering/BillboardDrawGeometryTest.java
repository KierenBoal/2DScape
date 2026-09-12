package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.Point;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardDrawGeometryTest
{
	@Test
	public void horizontalShearKeepsContentBottomFixedAndMovesTop()
	{
		BillboardDrawGeometry geometry = BillboardDrawGeometry.sheared(
			new Rectangle(100, 50, 40, 100), 120.0d, 60.0d, 140.0d, -20.0d, 0.0d);

		assertTrue(geometry.isSheared());
		assertEquals(-20.0d, geometry.offsetAt(60.0d), 0.001d);
		assertEquals(0.0d, geometry.offsetAt(140.0d), 0.001d);
		assertEquals(new Point(100, 60), geometry.contentTopCenter());
		assertEquals(new Rectangle(77, 50, 66, 100), geometry.bounds);
	}

	@Test
	public void inverseMappingRejectsPixelsOutsideShearedRow()
	{
		BillboardDrawGeometry geometry = BillboardDrawGeometry.sheared(
			new Rectangle(1, 0, 2, 2), 2.0d, 0.0d, 2.0d, -1.0d, 0.0d);

		assertEquals(0, geometry.sourceXAt(0, 0, 2));
		assertEquals(1, geometry.sourceXAt(1, 0, 2));
		assertEquals(-1, geometry.sourceXAt(2, 0, 2));
		assertEquals(1, geometry.sourceYAt(1, 2));
	}

	@Test
	public void topAndBottomOffsetsInterpolateWithoutResizingTheSprite()
	{
		BillboardDrawGeometry geometry = BillboardDrawGeometry.sheared(
			new Rectangle(100, 50, 40, 100), 120.0d, 60.0d, 140.0d, -15.0d, 10.0d);

		assertEquals(-15.0d, geometry.offsetAt(60.0d), 0.001d);
		assertEquals(10.0d, geometry.offsetAt(140.0d), 0.001d);
		assertEquals(new Point(105, 60), geometry.contentTopCenter());
	}

	@Test
	public void invalidShearFallsBackButSubpixelShearRemainsContinuous()
	{
		Rectangle bounds = new Rectangle(5, 6, 7, 8);
		BillboardDrawGeometry invalid = BillboardDrawGeometry.sheared(bounds, 8.5d, 10.0d, 10.0d, 20.0d, 0.0d);
		BillboardDrawGeometry tiny = BillboardDrawGeometry.sheared(bounds, 8.5d, 6.0d, 14.0d, 0.25d, 0.0d);

		assertFalse(invalid.isSheared());
		assertTrue(tiny.isSheared());
		assertEquals(bounds, invalid.bounds);
		assertEquals(0.25d, tiny.offsetAt(6.0d), 0.0d);
		assertEquals(5, tiny.rowLeftAt(6));
	}

	@Test
	public void actorSkewKeepsModelOriginFixedWhenPitchedContentExtendsBelowIt()
	{
		Rectangle draw = new Rectangle(100, 50, 50, 60);
		Rectangle image = new Rectangle(-50, -100, 100, 120);
		BillboardDrawGeometry geometry = BillboardDrawGeometry.actorSkew(draw, image, image, 0.1d);

		assertEquals(draw, geometry.unskewedBounds);
		assertEquals(draw.height, geometry.bounds.height);
		assertEquals(new Point(125, 100), geometry.canvasPoint(0.5d, 100.0d / 120.0d));
		assertEquals(0.0d, geometry.offsetAt(100.0d), 1e-10d);
		assertEquals(5.0d, geometry.offsetAt(50.0d), 1e-10d);
		assertEquals(-1.0d, geometry.offsetAt(110.0d), 1e-10d);
	}

	@Test
	public void paddingAndAnimationCropChangesDoNotChangeTheLeanOrPivot()
	{
		BillboardDrawGeometry first = BillboardDrawGeometry.actorSkew(
			new Rectangle(100, 50, 50, 60), new Rectangle(-50, -100, 100, 120),
			new Rectangle(-48, -98, 96, 116), 0.1d);
		BillboardDrawGeometry padded = BillboardDrawGeometry.actorSkew(
			new Rectangle(95, 45, 60, 70), new Rectangle(-60, -110, 120, 140),
			new Rectangle(-40, -90, 80, 100), 0.1d);

		for (int y = 50; y <= 110; y++)
		{
			assertEquals(first.offsetAt(y), padded.offsetAt(y), 1e-10d);
		}
	}
}
