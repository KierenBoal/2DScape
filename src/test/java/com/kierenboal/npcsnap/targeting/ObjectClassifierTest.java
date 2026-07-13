package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.DynamicObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Renderable;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class ObjectClassifierTest
{
	@Test
	public void dynamicGroundObjectIsNotAnEffect()
	{
		DynamicObject dynamicObject = proxy(DynamicObject.class);
		GroundObject groundObject = proxy(GroundObject.class, method("getRenderable", dynamicObject));

		Assert.assertEquals(ClassifiedObjectType.UNKNOWN, ObjectClassifier.classifyTileObject(groundObject, null));
	}

	@Test
	public void graphicsGroundObjectIsAnEffect()
	{
		GraphicsObject graphicsObject = proxy(GraphicsObject.class);
		GroundObject groundObject = proxy(GroundObject.class, method("getRenderable", graphicsObject));

		Assert.assertEquals(ClassifiedObjectType.EFFECT, ObjectClassifier.classifyTileObject(groundObject, null));
	}

	@Test
	public void dynamicRenderableIsStillAnObject()
	{
		Renderable dynamicObject = proxy(DynamicObject.class);

		Assert.assertEquals(ClassifiedObjectType.OBJECT, ObjectClassifier.classifyRenderable(dynamicObject));
	}
}
