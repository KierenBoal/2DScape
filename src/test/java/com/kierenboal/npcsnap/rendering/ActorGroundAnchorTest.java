package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.TestProxies;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class ActorGroundAnchorTest
{
	@Test public void topLevelAnchorIgnoresFlyingModelAndAnimationHeight()
	{
		int[][][] heights=new int[4][17][17];
		for(int[][] plane:heights) for(int[] row:plane) java.util.Arrays.fill(row,-100);
		WorldView view=proxy(WorldView.class,method("isTopLevel",true),method("getId",WorldView.TOPLEVEL),
			method("getPlane",0),method("getTileHeight",-100),method("getTileHeights",heights),method("getTileSettings",new byte[4][16][16]));
		Client client=proxy(Client.class,method("getTopLevelWorldView",view),method("getWorldView",view),
			method("getCameraX",512),method("getCameraY",-1000),method("getCameraZ",-600),
			method("getCameraFpX",512f),method("getCameraFpY",-1000f),method("getCameraFpZ",-600f),
			method("getScale",512),method("getViewportWidth",800),method("getViewportHeight",600));
		LocalPoint point=new LocalPoint(640,640,view);
		Actor actor=proxy(Actor.class,method("getWorldView",view),method("getLocalLocation",point),method("getFootprintSize",256),
			methodSupplier("getModel",()->{ throw new AssertionError("Ground anchor must not retrieve the model"); }),
			methodSupplier("getModelHeight",()->{ throw new AssertionError("Ground anchor must not use animated height"); }),
			methodSupplier("getAnimationHeightOffset",()->{ throw new AssertionError("Ground anchor must not use flight offset"); }));
		ActorGroundAnchor anchor=ActorGroundAnchor.resolve(client,new BillboardDepthCalculator(client),actor);
		assertNotNull(anchor); assertSame(point,anchor.localPoint); assertEquals(-100,anchor.height,0);
		assertEquals(Math.sqrt(128d*128+1640d*1640+500d*500),anchor.cameraDistance,1e-8);
		BillboardCanvasPoint expected=new BillboardDepthCalculator(client).projectCanvasPoint(640,640,-100);
		assertEquals(expected.x,anchor.canvas.x,0); assertEquals(expected.y,anchor.canvas.y,0);
	}
	@Test public void nestedAnchorUsesTheTransformedDeckRatherThanTheSea()
	{
		AtomicBoolean hidden=new AtomicBoolean();
		WorldView nested=proxy(WorldView.class,method("getId",7),method("getPlane",0),method("getTileHeight",-40),
			method("getMainWorldProjection",TestProxies.projection(512,10,1024,512)));
		WorldEntity owner=proxy(WorldEntity.class,method("getWorldView",nested),method("getLocalLocation",new LocalPoint(64,64)),
			methodSupplier("isHiddenForOverlap",hidden::get));
		@SuppressWarnings("unchecked") IndexedObjectSet<WorldEntity> entities=proxy(IndexedObjectSet.class,
			methodSupplier("iterator",()->Collections.singleton(owner).iterator()));
		WorldView top=proxy(WorldView.class,method("isTopLevel",true),method("getTileHeight",100),method("worldEntities",entities));
		Client client=proxy(Client.class,method("getTopLevelWorldView",top),method("getScale",512),method("getViewportWidth",800),method("getViewportHeight",600));
		Actor actor=proxy(Actor.class,method("getWorldView",nested),method("getLocalLocation",new LocalPoint(64,96,nested)),
			method("getAnimationHeightOffset",250),method("getModelHeight",700));
		BillboardDepthCalculator calculator=new BillboardDepthCalculator(client);
		ActorGroundAnchor anchor=ActorGroundAnchor.resolve(client,calculator,actor);
		assertNotNull(anchor); assertEquals(608,anchor.localPoint.getX()); assertEquals(960,anchor.localPoint.getY());
		assertEquals(70,anchor.height,0);
		assertEquals(calculator.projectCanvasPoint(608,960,70).y,anchor.canvas.y,0);
		hidden.set(true); assertNull(ActorGroundAnchor.resolve(client,calculator,actor));
	}
	@Test public void unresolvedActorsDoNotProduceInvalidAnchors()
	{
		Client client=proxy(Client.class);
		assertNull(ActorGroundAnchor.resolve(client,new BillboardDepthCalculator(client),null));
		assertNull(ActorGroundAnchor.resolve(client,new BillboardDepthCalculator(client),proxy(Actor.class)));
	}
}
