package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Actor;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class ObservedTileObjectBuilderTest
{
	@Test
	public void gameObjectBuildsSingleRenderablePartAtTileLocation()
	{
		Renderable renderable = proxy(Renderable.class);
		LocalPoint localPoint = new LocalPoint(128, 256);
		GameObject gameObject = proxy(
			GameObject.class,
			method("getRenderable", renderable),
			method("getLocalLocation", localPoint),
			method("getPlane", 2)
		);

		ObservedTileObject observed = ObservedTileObjectBuilder.build(gameObject);

		Assert.assertNotNull(observed);
		Assert.assertSame(gameObject, observed.tileObject);
		Assert.assertEquals(1, observed.parts.size());
		Assert.assertSame(renderable, observed.parts.get(0).renderable);
		Assert.assertEquals(localPoint, observed.parts.get(0).localPoint);
		Assert.assertEquals(2, observed.parts.get(0).plane);
	}

	@Test
	public void decorativeObjectAppliesPerRenderableOffsets()
	{
		Renderable first = proxy(Renderable.class);
		Renderable second = proxy(Renderable.class);
		DecorativeObject decorativeObject = proxy(
			DecorativeObject.class,
			method("getRenderable", first),
			method("getRenderable2", second),
			method("getLocalLocation", new LocalPoint(128, 256)),
			method("getPlane", 1),
			method("getXOffset", 10),
			method("getYOffset", -20),
			method("getXOffset2", -30),
			method("getYOffset2", 40)
		);

		ObservedTileObject observed = ObservedTileObjectBuilder.build(decorativeObject);

		Assert.assertNotNull(observed);
		Assert.assertEquals(2, observed.parts.size());
		Assert.assertSame(first, observed.parts.get(0).renderable);
		Assert.assertEquals(new LocalPoint(138, 236), observed.parts.get(0).localPoint);
		Assert.assertSame(second, observed.parts.get(1).renderable);
		Assert.assertEquals(new LocalPoint(98, 296), observed.parts.get(1).localPoint);
	}

	@Test
	public void buildIgnoresDynamicSceneRenderablesOwnedByOtherPipelines()
	{
		Assert.assertNull(observedGameObject(proxy(Actor.class)));
		Assert.assertNull(observedGameObject(proxy(Projectile.class)));
		Assert.assertNull(observedGameObject(proxy(GraphicsObject.class)));
		Assert.assertNull(observedGameObject(proxy(TileItem.class)));
	}

	private static ObservedTileObject observedGameObject(Renderable renderable)
	{
		GameObject gameObject = proxy(
			GameObject.class,
			method("getRenderable", renderable),
			method("getLocalLocation", new LocalPoint(128, 256)),
			method("getPlane", 0)
		);
		return ObservedTileObjectBuilder.build(gameObject);
	}
}
