package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.TestProxies;

import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class BillboardProjectileGeometryTest
{
	@Test
	public void localPointUsesProjectileWorldLocalCoordinates()
	{
		Projectile projectile = proxy(
			Projectile.class,
			method("getX", 320.75d),
			method("getY", 448.25d)
		);

		LocalPoint localPoint = BillboardProjectileGeometry.localPoint(projectile);

		Assert.assertEquals(new LocalPoint(320, 448), localPoint);
	}

	@Test
	public void drawBoundsFollowProvidedLaunchArcAndImpactCoordinates()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(false));
		Rectangle imageBounds = new Rectangle(-10, -10, 20, 20);
		double[][] positions = {
			{0, -100}, {50, -200}, {100, -250}, {150, -200}, {200, -100}
		};
		for (double[] position : positions)
		{
			Projectile projectile = projectile(position[0], 1000.0d, position[1]);
			Rectangle draw = BillboardProjectileGeometry.drawBounds(calculator, projectile, imageBounds, 1000.0d);
			Assert.assertEquals(new Rectangle((int) (390 + position[0]), (int) (290 + position[1]), 20, 20), draw);
		}
	}

	@Test
	public void nativeHeightAndFractionalCoordinatesArePreservedAcrossCpuAndGpu()
	{
		for (boolean gpu : new boolean[] {false, true})
		{
			BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(gpu));
			Rectangle imageBounds = new Rectangle(-10, -10, 20, 20);
			Assert.assertEquals(new Rectangle(391, 189, 20, 20),
				BillboardProjectileGeometry.drawBounds(calculator, projectile(0.99d, 1000.0d, -100.99d), imageBounds, 1000.0d));
			// Higher launches and unusual impact heights stay exactly where the
			// client's current Z places them, without adding an actor/model height.
			Assert.assertEquals(new Rectangle(390, -10, 20, 20),
				BillboardProjectileGeometry.drawBounds(calculator, projectile(0.0d, 1000.0d, -300.0d), imageBounds, 1000.0d));
			Assert.assertEquals(new Rectangle(390, 330, 20, 20),
				BillboardProjectileGeometry.drawBounds(calculator, projectile(0.0d, 1000.0d, 40.0d), imageBounds, 1000.0d));
		}
	}

	@Test
	public void cachedSpriteDoesNotMoveOrResizeWhenLiveProjectileModelHeightChanges()
	{
		AtomicInteger height = new AtomicInteger(20);
		Projectile projectile = proxy(Projectile.class, method("getX", 100.0d), method("getY", 1000.0d),
			method("getZ", -100.0d), TestProxies.methodSupplier("getModelHeight", height::get));
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(false));
		Rectangle imageBounds = new Rectangle(-10, -10, 20, 20);
		Rectangle first = BillboardProjectileGeometry.drawBounds(calculator, projectile, imageBounds, 1000.0d);
		height.set(400);
		Assert.assertEquals(first, BillboardProjectileGeometry.drawBounds(calculator, projectile, imageBounds, 1000.0d));
		height.set(0);
		Assert.assertEquals(first, BillboardProjectileGeometry.drawBounds(calculator, projectile, imageBounds, 1000.0d));
	}

	@Test
	public void cropAndPaddingKeepTheModelOriginAtTheSameFlightPosition()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(false));
		Projectile projectile = projectile(100.0d, 1000.0d, -100.0d);
		Rectangle first = BillboardProjectileGeometry.drawBounds(calculator, projectile,
			new Rectangle(-10, -10, 20, 20), 1000.0d);
		Rectangle padded = BillboardProjectileGeometry.drawBounds(calculator, projectile,
			new Rectangle(-15, -15, 30, 30), 1000.0d);
		Assert.assertEquals(first.getCenterX(), padded.getCenterX(), 0.0d);
		Assert.assertEquals(first.getCenterY(), padded.getCenterY(), 0.0d);
		Rectangle asymmetric = BillboardProjectileGeometry.drawBounds(calculator, projectile,
			new Rectangle(-10, -30, 20, 40), 1000.0d);
		Assert.assertEquals(new Rectangle(490, 170, 20, 40), asymmetric);
		Assert.assertEquals(200, asymmetric.y + 30);
	}

	@Test
	public void invalidOrNearPlaneFlightPositionsDoNotUseAFallbackAnchor()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(false));
		Rectangle imageBounds = new Rectangle(-10, -10, 20, 20);
		Assert.assertNull(BillboardProjectileGeometry.drawBounds(calculator, projectile(0.0d, 49.0d, -100.0d), imageBounds, 1000.0d));
		Assert.assertNull(BillboardProjectileGeometry.drawBounds(calculator, projectile(0.0d, -100.0d, -100.0d), imageBounds, 1000.0d));
		Assert.assertNull(BillboardProjectileGeometry.drawBounds(calculator, projectile(Double.NaN, 1000.0d, -100.0d), imageBounds, 1000.0d));
		Assert.assertNull(BillboardProjectileGeometry.drawBounds(calculator, projectile(0.0d, 1000.0d, Double.NaN), imageBounds, 1000.0d));
	}

	private static Projectile projectile(double x, double y, double z)
	{
		return proxy(Projectile.class, method("getX", x), method("getY", y), method("getZ", z));
	}

	private static Client projectionClient(boolean gpu)
	{
		return proxy(Client.class, method("isGpu", gpu), method("getScale", 1000),
			method("getViewportWidth", 800), method("getViewportHeight", 600));
	}
}
