package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.NpcSnapDebug;
import com.kierenboal.npcsnap.targeting.BillboardInteractionState;
import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.Projectile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class BillboardRenderRequestFactoryTest
{
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

		BillboardRenderRequest request = factory.buildGroundItem(item, new GroundItemBillboard(2, point));

		assertSame(item, request.renderable);
		assertSame(model, request.model);
		assertSame(point, request.localPoint);
		assertEquals(2, request.plane);
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
	public void graphicsObjectRequestClampsHeightAndKeepsFrameDebugIdentity()
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
		assertEquals(0, request.verticalOffset);
		assertEquals(88, request.animationId);
		assertEquals(3, request.animationFrame);
		assertEquals(VerticalAnchor.CENTER, request.verticalAnchor);
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

	private static BillboardRenderRequestFactory factory()
	{
		return factory(true, null);
	}

	private static BillboardRenderRequestFactory factory(boolean frameSnappingEnabled, Animation animation)
	{
		Client client = TestProxies.proxy(Client.class, TestProxies.method("loadAnimation", animation));
		NpcSnapConfig config = TestProxies.proxy(NpcSnapConfig.class,
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
		return TestProxies.proxy(WorldView.class, TestProxies.method("getPlane", plane));
	}
}
