package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Projectile;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class BillboardOrientationCalculatorTest
{
	@Test
	public void unsnappedActorYawAddsCameraAndActorOrientation()
	{
		Client client = proxy(Client.class, method("getCameraYaw", 1024));
		NpcSnapConfig config = proxy(NpcSnapConfig.class, method("enableRotationSnapping", false));
		Actor actor = proxy(Actor.class, method("getCurrentOrientation", 512));
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);

		Assert.assertEquals(5120, calculator.relativeYaw(actor));
	}

	@Test
	public void unsnappedProjectileYawAddsCameraAndProjectileOrientation()
	{
		Client client = proxy(Client.class, method("getCameraYaw", 2048));
		NpcSnapConfig config = proxy(NpcSnapConfig.class, method("enableRotationSnapping", false));
		Projectile projectile = proxy(Projectile.class, method("getOrientation", 1024));
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);

		Assert.assertEquals(10240, calculator.relativeYaw(projectile));
	}

	@Test
	public void unsnappedPitchIsClampedAndInvertedForModelSpace()
	{
		Client client = proxy(Client.class, method("getCameraPitch", 12000));
		NpcSnapConfig config = proxy(NpcSnapConfig.class, method("enableRotationSnapping", false));
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);

		Assert.assertEquals(-BillboardAngleUtils.MAX_PITCH, calculator.relativePitch());
	}

	@Test
	public void unsnappedGroundItemPitchIsInvertedForModelSpace()
	{
		Client client = proxy(Client.class, method("getCameraPitch", 1024));
		NpcSnapConfig config = proxy(NpcSnapConfig.class, method("enableRotationSnapping", false));
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);

		Assert.assertEquals(-1024, calculator.relativeGroundItemPitch());
	}
}
