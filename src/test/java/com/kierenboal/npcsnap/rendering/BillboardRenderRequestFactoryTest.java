package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.NpcSnapDebug;
import com.kierenboal.npcsnap.targeting.BillboardInteractionState;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.BillboardTarget;
import com.kierenboal.npcsnap.targeting.ObservedTileObjectBuilder;
import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.Projectile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.WorldEntity;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpotanimID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BillboardRenderRequestFactoryTest
{
	@Test
	public void worldObjectRequestReportsTheLiveFrameActuallyCapturedByGetModel()
	{
		java.util.concurrent.atomic.AtomicInteger frame = new java.util.concurrent.atomic.AtomicInteger(4);
		Model model = TestProxies.proxy(Model.class);
		Animation animation = TestProxies.proxy(Animation.class,
			TestProxies.method("getId", 88), TestProxies.method("getNumFrames", 10));
		DynamicObject renderable = TestProxies.proxy(DynamicObject.class,
			TestProxies.method("getAnimation", animation), TestProxies.methodSupplier("getAnimFrame", frame::get),
			TestProxies.methodSupplier("getModel", () -> { frame.set(5); return model; }));
		GameObject object = TestProxies.proxy(GameObject.class, TestProxies.method("getId", 1),
			TestProxies.method("getLocalLocation", new LocalPoint(128, 128)),
			TestProxies.method("getRenderable", renderable));
		BillboardTarget target = BillboardTarget.forTileObject(ObservedTileObjectBuilder.build(object), 100.0d);
		BillboardRenderRequest request = factory(true, animation).build(target, target.observedTileObject.parts.get(0));
		assertSame(model, request.model);
		assertEquals(88, request.animationId);
		assertEquals(5, request.animationFrame);
	}

	@Test
	public void nestedActorBodyUsesMainWorldViewingRayAndOwningEntityOrientation()
	{
		WorldView nested = TestProxies.proxy(WorldView.class);
		WorldView top = worldView(0);
		LocalPoint mainLocation = new LocalPoint(1000, 1000, top);
		WorldEntity owner = TestProxies.proxy(WorldEntity.class, TestProxies.method("getWorldView", nested),
			TestProxies.method("getOrientation", 512), TestProxies.method("transformToMainWorld", mainLocation));
		WorldView main = TestProxies.proxy(WorldView.class, TestProxies.method("isTopLevel", true),
			TestProxies.method("worldEntities", TestProxies.proxy(IndexedObjectSet.class,
				TestProxies.methodSupplier("iterator", () -> java.util.Collections.singletonList(owner).iterator()))));
		Actor actor = TestProxies.proxy(Actor.class, TestProxies.method("getWorldView", nested),
			TestProxies.method("getLocalLocation", new LocalPoint(200, 300, nested)), TestProxies.method("getCurrentOrientation", 256));
		Client client = TestProxies.proxy(Client.class, TestProxies.method("getTopLevelWorldView", main));
		for (boolean ignore : new boolean[] {false, true})
		{
			NpcSnapConfig config = new NpcSnapConfig()
			{
				@Override public boolean enableRotationSnapping() { return false; }
				@Override public boolean ignoreCameraAwareRotation() { return ignore; }
			};
			BillboardRenderRequestFactory factory = new BillboardRenderRequestFactory(client, config,
				new NpcSnapDebug(client, config), new AnimationFrameSnapper(client),
				new BillboardOrientationCalculator(client, config), new BillboardInteractionState());
			BillboardRenderRequest request = factory.buildActor(actor);
			assertSame(mainLocation, request.localPoint);
			assertEquals(ignore ? 6144 : 4096, request.relativeYaw);
			assertEquals(2048, factory.buildActorSpotAnimation(TestProxies.proxy(ActorSpotAnim.class), actor).relativeYaw);
		}
	}

	@Test
	public void actorRequestsPreserveSignedSubmersionOffsets()
	{
		Actor actor = TestProxies.proxy(Actor.class,
			TestProxies.method("getWorldView", worldView(0)),
			TestProxies.method("getLocalLocation", new LocalPoint(128, 128)),
			TestProxies.method("getAnimationHeightOffset", -80));
		assertEquals(-80, factory().buildActor(actor).verticalOffset);
	}
	@Test
	public void groundItemRequestUsesTrackedLocationQuantityAndGroundOrientation()
	{
		BillboardRenderRequestFactory factory = factory();
		Model model = TestProxies.proxy(Model.class);
		TileItem item = TestProxies.proxy(TileItem.class,
			TestProxies.method("getId", 42),
			TestProxies.method("getQuantity", 7),
			TestProxies.method("getModel", model));
		LocalPoint point = new LocalPoint(128, 256, worldView(2));

		BillboardRenderRequest request = factory.buildGroundItem(item, new GroundItemBillboard(2, point, 96));

		assertSame(item, request.renderable);
		assertSame(model, request.model);
		assertSame(point, request.localPoint);
		assertEquals(2, request.plane);
		assertEquals(96, request.verticalOffset);
		assertEquals(42, request.animationId);
		assertEquals(7, request.animationFrame);
		assertEquals(42, request.animatedTextureId);
		assertEquals(VerticalAnchor.BOTTOM, request.verticalAnchor);
		assertFalse(request.shouldHoverOutline);
		assertFalse(request.shouldInteractOutline);
	}

	@Test
	public void missingGroundItemStateProducesNoRequest()
	{
		assertNull(factory().buildGroundItem(TestProxies.proxy(TileItem.class), null));
	}

	@Test
	public void graphicsObjectRequestUsesWorldHeightAndKeepsFrameDebugIdentity()
	{
		Model model = TestProxies.proxy(Model.class);
		LocalPoint point = new LocalPoint(300, 400, worldView(1));
		GraphicsObject object = TestProxies.proxy(GraphicsObject.class,
			TestProxies.method("getId", 88),
			TestProxies.method("getAnimationFrame", 3),
			TestProxies.method("getZ", -20),
			TestProxies.method("getLevel", 1),
			TestProxies.method("getLocation", point),
			TestProxies.method("getModel", model));

		BillboardRenderRequest request = factory().buildGraphicsObject(object);

		assertSame(model, request.model);
		assertEquals(20, request.verticalOffset);
		assertEquals(88, request.animationId);
		assertEquals(3, request.animationFrame);
		assertEquals(VerticalAnchor.BOTTOM, request.verticalAnchor);
	}

	@Test
	public void lowProfileProjectileAndGraphicsCarryProfileAndGroundPitch()
	{
		Model model = TestProxies.proxy(Model.class,
			TestProxies.method("getVerticesCount", 4),
			TestProxies.method("getVerticesX", new float[] {-100, 100, 100, -100}),
			TestProxies.method("getVerticesY", new float[] {0, 0, -1, -1}),
			TestProxies.method("getVerticesZ", new float[] {-100, -100, 100, 100}));
		GraphicsObject graphicsObject = TestProxies.proxy(GraphicsObject.class,
			TestProxies.method("getModelHeight", 32),
			TestProxies.method("getModel", model),
			TestProxies.method("getLocation", new LocalPoint(128, 128, worldView(0))));
		Projectile projectile = TestProxies.proxy(Projectile.class,
			TestProxies.method("getModelHeight", 32),
			TestProxies.method("getModel", model));

		BillboardRenderRequest graphicsRequest = factory().buildGraphicsObject(graphicsObject);
		BillboardRenderRequest projectileRequest = factory().buildProjectile(projectile);

		assertTrue(graphicsRequest.lowProfile);
		assertEquals(-BillboardAngleUtils.GROUND_ITEM_MIN_PITCH, graphicsRequest.relativePitch);
		assertTrue(projectileRequest.lowProfile);
		assertEquals(-BillboardAngleUtils.GROUND_ITEM_MIN_PITCH, projectileRequest.relativePitch);
		assertEquals(VerticalAnchor.BOTTOM, projectileRequest.verticalAnchor);
	}

	@Test
	public void baseActorsDoNotUseLowProfileExemption()
	{
		Model model = TestProxies.proxy(Model.class,
			TestProxies.method("getVerticesCount", 4),
			TestProxies.method("getVerticesX", new float[] {-20, 20, 20, -20, 0}),
			TestProxies.method("getVerticesY", new float[] {0, 0, -100, -100, 0}),
			TestProxies.method("getVerticesZ", new float[] {-20, -20, 20, 20, 0}));
		WorldView topLevel = TestProxies.proxy(WorldView.class,
			TestProxies.method("isTopLevel", true),
			TestProxies.method("getPlane", 0));
		Client client = TestProxies.proxy(Client.class,
			TestProxies.method("getTopLevelWorldView", topLevel));
		NpcSnapConfig config = TestProxies.proxy(NpcSnapConfig.class,
			TestProxies.method("enableRotationSnapping", true),
			TestProxies.method("numberOfYawRotationAngles", 4),
			TestProxies.method("numberOfPitchRotationAngles", 1));
		Actor actor = TestProxies.proxy(Actor.class,
			TestProxies.method("getModel", model),
			TestProxies.method("getModelHeight", 16),
			TestProxies.method("getWorldView", topLevel),
			TestProxies.method("getLocalLocation", new LocalPoint(128, 128, topLevel)));
		BillboardRenderRequestFactory factory = new BillboardRenderRequestFactory(
			client,
			config,
			new NpcSnapDebug(client, config),
			new AnimationFrameSnapper(client),
			new BillboardOrientationCalculator(client, config),
			new BillboardInteractionState());

		BillboardRenderRequest request = factory.buildActor(actor);

		assertFalse(request.lowProfile);
		assertEquals(0, request.relativePitch);
		assertTrue(BillboardDepthSurface.supportsVerticalPlaneOcclusion(request.model));
	}

	@Test
	public void actorSpotAnimationUsesEffectOriginAndOwnHeightOffset()
	{
		Model model = TestProxies.proxy(Model.class);
		LocalPoint point = new LocalPoint(300, 400, worldView(1));
		WorldView worldView = worldView(1);
		Actor actor = TestProxies.proxy(Actor.class,
			TestProxies.method("getLocalLocation", point),
			TestProxies.method("getWorldView", worldView),
			TestProxies.method("getAnimationHeightOffset", 24));
		ActorSpotAnim animation = TestProxies.proxy(ActorSpotAnim.class,
			TestProxies.method("getId", 88),
			TestProxies.method("getFrame", 3),
			TestProxies.method("getHeight", 40),
			TestProxies.method("getModel", model));

		BillboardRenderRequest request = factory().buildActorSpotAnimation(animation, actor);

		assertSame(point, request.localPoint);
		assertSame(model, request.model);
		assertEquals(40, request.verticalOffset);
		assertEquals(VerticalAnchor.BOTTOM, request.verticalAnchor);
	}

	@Test
	public void bindingSpotAnimationsUseGroundContactAnchor()
	{
		Model model = TestProxies.proxy(Model.class);
		WorldView worldView = worldView(1);
		Actor actor = TestProxies.proxy(Actor.class,
			TestProxies.method("getLocalLocation", new LocalPoint(300, 400, worldView)),
			TestProxies.method("getWorldView", worldView));

		for (int id : new int[] {SpotanimID.BIND_IMPACT, SpotanimID.SNARE_IMPACT, SpotanimID.ENTANGLE_IMPACT})
		{
			ActorSpotAnim animation = TestProxies.proxy(ActorSpotAnim.class,
				TestProxies.method("getId", id),
				TestProxies.method("getHeight", 40),
				TestProxies.method("getModel", model));

			BillboardRenderRequest request = factory().buildActorSpotAnimation(animation, actor);

			assertEquals("binding id=" + id, 0, request.verticalOffset);
			assertEquals("binding id=" + id, VerticalAnchor.GROUND_CONTACT, request.verticalAnchor);
		}
	}

	@Test
	public void effectsAndProjectilesFollowTheMasterFrameRateSwitch()
	{
		Animation animation = TestProxies.proxy(Animation.class,
			TestProxies.method("getId", 88),
			TestProxies.method("getNumFrames", 10));
		GraphicsObject effect = TestProxies.proxy(GraphicsObject.class,
			TestProxies.method("getId", 88),
			TestProxies.method("getAnimation", animation),
			TestProxies.method("getAnimationFrame", 4),
			TestProxies.method("getZ", 0),
			TestProxies.method("getLevel", 0),
			TestProxies.method("getLocation", new LocalPoint(128, 128, worldView(0))));
		Projectile projectile = TestProxies.proxy(Projectile.class,
			TestProxies.method("getId", 88),
			TestProxies.method("getAnimation", animation),
			TestProxies.method("getAnimationFrame", 4));

		assertEquals(3, factory(true, animation).buildGraphicsObject(effect).animationFrame);
		assertEquals(4, factory(false, animation).buildGraphicsObject(effect).animationFrame);
		assertEquals(3, factory(true, animation).buildProjectile(projectile).animationFrame);
		assertEquals(4, factory(false, animation).buildProjectile(projectile).animationFrame);
	}

	@Test
	public void actorSpotAnimationRequiresParentAndLocation()
	{
		ActorSpotAnim animation = TestProxies.proxy(ActorSpotAnim.class);

		assertNull(factory().buildActorSpotAnimation(animation, null));
	}

	@Test
	public void transientObjectModelFailureSkipsThePart()
	{
		net.runelite.api.Renderable renderable = TestProxies.proxy(
			net.runelite.api.Renderable.class,
			TestProxies.methodSupplier("getModel", () ->
			{
				throw new NullPointerException("transient model state");
			}));
		ObjectRenderablePart part = new ObjectRenderablePart(renderable, new LocalPoint(128, 128, worldView(0)), 0);

		assertNull(factory().buildStaticObjectPart(part));
	}

	@Test
	public void staticObjectPartUsesTheAvailableModel()
	{
		Model model = TestProxies.proxy(Model.class);
		net.runelite.api.Renderable renderable = TestProxies.proxy(
			net.runelite.api.Renderable.class,
			TestProxies.method("getModel", model));
		ObjectRenderablePart part = new ObjectRenderablePart(renderable, new LocalPoint(128, 128, worldView(0)), 0);

		assertSame(model, factory().buildStaticObjectPart(part).model);
	}

	@Test
	public void worldObjectPitchUsesTheSameCameraRangeAsActors()
	{
		Model model = TestProxies.proxy(Model.class,
			TestProxies.method("getVerticesCount", 1),
			TestProxies.method("getVerticesX", new float[] {0}),
			TestProxies.method("getVerticesY", new float[] {0}),
			TestProxies.method("getVerticesZ", new float[] {0}));
		net.runelite.api.Renderable renderable = TestProxies.proxy(
			net.runelite.api.Renderable.class,
			TestProxies.method("getModel", model),
			TestProxies.method("getModelHeight", 32));
		ObjectRenderablePart part = new ObjectRenderablePart(
			renderable, new LocalPoint(128, 128, worldView(0)), 0);
		Client client = TestProxies.proxy(Client.class,
			TestProxies.method("getCameraPitch", 2048));
		NpcSnapConfig config = TestProxies.proxy(NpcSnapConfig.class,
			TestProxies.method("enableRotationSnapping", true),
			TestProxies.method("numberOfYawRotationAngles", 4),
			TestProxies.method("numberOfPitchRotationAngles", 4));
		BillboardOrientationCalculator orientation = new BillboardOrientationCalculator(client, config);
		BillboardRenderRequestFactory factory = new BillboardRenderRequestFactory(
			client,
			config,
			new NpcSnapDebug(client, config),
			new AnimationFrameSnapper(client),
			orientation,
			new BillboardInteractionState());

		BillboardRenderRequest request = factory.buildStaticObjectPart(part);

		assertTrue(request.lowProfile);
		assertEquals(orientation.relativePitch(), request.relativePitch);
		assertTrue(request.relativePitch != orientation.relativePitch(true));
	}

	private static BillboardRenderRequestFactory factory()
	{
		return factory(true, null);
	}

	private static BillboardRenderRequestFactory factory(boolean frameSnappingEnabled, Animation animation)
	{
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		NpcSnapConfig config = TestProxies.proxy(NpcSnapConfig.class,
			TestProxies.method("enableRotationSnapping", true),
			TestProxies.method("numberOfYawRotationAngles", 4),
			TestProxies.method("numberOfPitchRotationAngles", 1),
			TestProxies.method("animationFrameCount", 3),
			TestProxies.method("enableAnimationFrameSnapping", frameSnappingEnabled));
		return new BillboardRenderRequestFactory(
			client,
			config,
			new NpcSnapDebug(client, config),
			new AnimationFrameSnapper(client),
			new BillboardOrientationCalculator(client, config),
			new BillboardInteractionState());
	}

	private static WorldView worldView(int plane)
	{
		return TestProxies.proxy(WorldView.class, TestProxies.method("getPlane", plane),
			TestProxies.method("isTopLevel", true));
	}
}
