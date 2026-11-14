package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.rendering.BillboardPaintOrder;
import com.kierenboal.npcsnap.targeting.*;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class NpcBillboardActorSortTest
{
	@Test public void foregroundFlyingActorStaysInFrontWhenItsSilhouetteBottomChanges()
	{
		WorldView view=proxy(WorldView.class,method("getId",WorldView.TOPLEVEL),method("isTopLevel",true),
			method("getTileHeights",new int[4][17][17]),method("getTileSettings",new byte[4][16][16]));
		Client client=proxy(Client.class,method("getWorldView",view),method("getTopLevelWorldView",view),
			method("getCameraY",-1000),method("getCameraZ",-600),method("getCameraFpY",-1000f),method("getCameraFpZ",-600f),
			method("getCameraPitch",Perspective.SINE14.length/16),method("getScale",512),
			method("getViewportWidth",800),method("getViewportHeight",600));
		NpcSnapConfig config=new NpcSnapConfig() {};
		NpcBillboardOverlay overlay=new NpcBillboardOverlay(client,null,config,new NpcSnapDebug(client,config),null);
		LocalPoint nearPoint=new LocalPoint(640,640,view), farPoint=new LocalPoint(640,1000,view);
		NPC flying=proxy(NPC.class,method("getIndex",1),method("getWorldView",view),method("getLocalLocation",nearPoint),
			method("getFootprintSize",128),method("getAnimationHeightOffset",200));
		NPC background=proxy(NPC.class,method("getIndex",2),method("getWorldView",view),method("getLocalLocation",farPoint),method("getFootprintSize",128));
		BillboardTarget near=BillboardTarget.forRenderable(BillboardTargetType.NPC,ClassifiedObjectType.UNKNOWN,flying,1,nearPoint,0);
		BillboardTarget far=BillboardTarget.forRenderable(BillboardTargetType.NPC,ClassifiedObjectType.UNKNOWN,background,999,farPoint,0);
		Rectangle backgroundBounds=new Rectangle(200,100,100,200);
		int anchor=overlay.sortBottomY(near,new Rectangle(200,0,100,100));
		double distance=overlay.sortDepth(near);
		assertNotEquals(near.getDepth(),distance,0);
		for(int bottom:new int[]{50,100,150,250,350})
		{
			Rectangle flyingBounds=new Rectangle(200,0,100,bottom);
			assertEquals(anchor,overlay.sortBottomY(near,flyingBounds));
			assertEquals(distance,overlay.sortDepth(near),0);
			List<String> ordered=BillboardPaintOrder.sort(Arrays.asList(
				new BillboardPaintOrder.Entry<>("flying",flyingBounds,1,BillboardPaintOrder.NO_PRIORITY_GROUP,
					overlay.sortBottomY(near,flyingBounds),overlay.sortDepth(near),1),
				new BillboardPaintOrder.Entry<>("background",backgroundBounds,1,BillboardPaintOrder.NO_PRIORITY_GROUP,
					overlay.sortBottomY(far,backgroundBounds),overlay.sortDepth(far),2)));
			assertEquals(Arrays.asList("background","flying"),ordered);
		}
	}
	@Test public void invalidActorAnchorRetainsExistingFallbacks()
	{
		Client client=proxy(Client.class); NpcSnapConfig config=new NpcSnapConfig() {};
		NpcBillboardOverlay overlay=new NpcBillboardOverlay(client,null,config,new NpcSnapDebug(client,config),null);
		BillboardTarget target=BillboardTarget.forRenderable(BillboardTargetType.NPC,ClassifiedObjectType.UNKNOWN,
			proxy(NPC.class),123,new LocalPoint(640,640),0);
		assertEquals(50,overlay.sortBottomY(target,new Rectangle(0,10,20,40)));
		assertEquals(123,overlay.sortDepth(target),0);
	}
}
