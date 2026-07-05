package com.kierenboal.npcsnap;

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
}
