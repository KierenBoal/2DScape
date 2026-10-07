package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class BillboardOrientationCalculatorTest
{
	@Test
	public void bodyYawUsesCameraToActorRayByDefaultWithCorrectSignAndWrap()
	{
		NpcSnapConfig config = new NpcSnapConfig()
		{
			@Override public boolean enableRotationSnapping() { return false; }
		};
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(
			proxy(Client.class, method("getCameraYaw", 1024)), config);
		int[][] directions = {
			{0, 1000, 0}, {1000, 1000, 14336}, {1000, 0, 12288}, {1000, -1000, 10240},
			{0, -1000, 8192}, {-1000, -1000, 6144}, {-1000, 0, 4096}, {-1000, 1000, 2048}
		};
		for (int[] direction : directions)
		{
			Assert.assertEquals(direction[2], calculator.relativeActorBodyYaw(null, 0,
				new LocalPoint(direction[0], direction[1])));
		}
		Assert.assertEquals(0, calculator.relativeActorBodyYaw(null, 256, new LocalPoint(1000, 1000)));
		Assert.assertEquals(2048, calculator.relativeActorBodyYaw(null, -1536, new LocalPoint(1000, 1000)));
		BillboardOrientationCalculator rightCamera = new BillboardOrientationCalculator(
			proxy(Client.class, method("getCameraX", 2000)), config);
		Assert.assertEquals(2048, rightCamera.relativeActorBodyYaw(null, 0, new LocalPoint(1000, 1000)));
	}

	@Test
	public void ignoreOptionRestoresLegacyYawWithAndWithoutSnapping()
	{
		Client client = proxy(Client.class, method("getCameraYaw", 1024));
		Actor actor = proxy(Actor.class, method("getCurrentOrientation", 512));
		for (boolean snapping : new boolean[] {false, true})
		{
			NpcSnapConfig config = proxy(NpcSnapConfig.class, method("ignoreCameraAwareRotation", true),
				method("enableRotationSnapping", snapping), method("numberOfYawRotationAngles", 8));
			BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);
			Assert.assertEquals(calculator.relativeYaw(actor),
				calculator.relativeActorBodyYaw(actor, 512, new LocalPoint(1000, 1000)));
		}
	}

	@Test
	public void invalidViewingRaysFallBackToCameraYaw()
	{
		NpcSnapConfig config = proxy(NpcSnapConfig.class);
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(
			proxy(Client.class, method("getCameraYaw", 1024), method("getCameraX", 1000),
				method("getCameraY", 1000)), config);
		Assert.assertEquals(5120, calculator.relativeActorBodyYaw(null, 512, null));
		Assert.assertEquals(5120, calculator.relativeActorBodyYaw(null, 512, new LocalPoint(1000, 1000)));
		Assert.assertEquals(5120, calculator.relativeActorBodyYaw(null, 512, new LocalPoint(1001, 1000)));
		for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
		{
			for (boolean invalidX : new boolean[] {false, true})
			{
				BillboardOrientationCalculator gpu = new BillboardOrientationCalculator(proxy(Client.class,
					method("getCameraYaw", 1024), method("isGpu", true),
					method("getCameraFpX", invalidX ? invalid : 0.0f),
					method("getCameraFpY", invalidX ? 0.0f : invalid)), config);
				Assert.assertEquals(5120, gpu.relativeActorBodyYaw(null, 512, new LocalPoint(1000, 1000)));
			}
		}
	}

	@Test
	public void gpuUsesFractionalCameraCoordinatesInsteadOfIntegerCoordinates()
	{
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(proxy(Client.class,
			method("isGpu", true), method("getCameraFpX", 999.5f), method("getCameraFpY", 997.5f),
			method("getCameraX", 2000), method("getCameraY", 0)), proxy(NpcSnapConfig.class));
		Assert.assertEquals(15869, calculator.relativeActorBodyYaw(null, 0, new LocalPoint(1000, 1000)));
	}

	@Test
	public void bodyYawIsCalculatedBeforeOrdinaryAndCombatSnapping()
	{
		Client client = proxy(Client.class, method("getCameraX", 2000), method("getCameraPitch", 2406));
		NpcSnapConfig config = proxy(NpcSnapConfig.class, method("enableRotationSnapping", true),
			method("numberOfYawRotationAngles", 4), method("numberOfPitchRotationAngles", 1),
			method("enableBillboardCombatSnapping", true));
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);
		Assert.assertEquals(2048, calculator.actorBodyRawYaw(0, new LocalPoint(1000, 1000)));
		Assert.assertEquals(4096, calculator.relativeActorBodyYaw(null, 0, new LocalPoint(1000, 1000)));
		Actor[] pair = new Actor[2];
		pair[0] = proxy(Actor.class, TestProxies.methodSupplier("getInteracting", () -> pair[1]));
		pair[1] = proxy(Actor.class, TestProxies.methodSupplier("getInteracting", () -> pair[0]));
		Assert.assertEquals(BillboardAngleUtils.COMBAT_YAW,
			calculator.relativeActorBodyYaw(pair[0], 0, new LocalPoint(1000, 1000)));
		Assert.assertEquals(BillboardAngleUtils.OPPOSITE_COMBAT_YAW,
			calculator.relativeActorBodyYaw(pair[0], 0, new LocalPoint(3000, 1000)));
		Assert.assertEquals(0, calculator.relativePitch(pair[0]));
		Assert.assertEquals(0, calculator.relativePitch());
		BillboardOrientationCalculator ignored = new BillboardOrientationCalculator(client,
			proxy(NpcSnapConfig.class, method("ignoreCameraAwareRotation", true),
				method("enableBillboardCombatSnapping", true)));
		Assert.assertEquals(ignored.relativeYaw(pair[0], 1024),
			ignored.relativeActorBodyYaw(pair[0], 1024, new LocalPoint(1000, 1000)));
	}

	@Test
	public void cameraAwareBodyYawLeavesEffectAndGroundYawUnchanged()
	{
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(
			proxy(Client.class, method("getCameraYaw", 1024)), proxy(NpcSnapConfig.class));
		Actor actor = proxy(Actor.class, method("getCurrentOrientation", 512));
		Assert.assertEquals(2048, calculator.relativeActorBodyYaw(actor, 512, new LocalPoint(1000, 1000)));
		Assert.assertEquals(5120, calculator.relativeYaw(actor));
		Assert.assertEquals(5120, calculator.relativeYaw(proxy(Projectile.class, method("getOrientation", 512))));
		Assert.assertEquals(1024, calculator.relativeGroundItemYaw());
		Assert.assertEquals(1024, calculator.relativeYaw());
	}

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

	@Test
	public void lowProfilePitchUsesGroundMinimumWhenSnapping()
	{
		Client client = proxy(Client.class, method("getCameraPitch", 0));
		NpcSnapConfig config = proxy(NpcSnapConfig.class,
			method("enableRotationSnapping", true),
			method("numberOfPitchRotationAngles", 1));
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);

		Assert.assertEquals(0, calculator.relativePitch());
		Assert.assertEquals(-BillboardAngleUtils.GROUND_ITEM_MIN_PITCH, calculator.relativePitch(true));
	}

	@Test
	public void unsnappedLowProfilePitchRemainsRaw()
	{
		Client client = proxy(Client.class, method("getCameraPitch", 512));
		NpcSnapConfig config = proxy(NpcSnapConfig.class, method("enableRotationSnapping", false));
		BillboardOrientationCalculator calculator = new BillboardOrientationCalculator(client, config);

		Assert.assertEquals(-512, calculator.relativePitch());
		Assert.assertEquals(-512, calculator.relativePitch(true));
	}
}
