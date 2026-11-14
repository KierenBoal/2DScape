package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.*;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class SupportingTerrainTest
{
	private static final SupportingFootprint SUPPORT = new SupportingFootprint(0,0,54,51,54,51);
	private BillboardOcclusionMask.Occluder surface(TerrainSurface terrain, int transmission)
	{
		BillboardOcclusionMask.Occluder o = new BillboardOcclusionMask.Occluder(
			new Rectangle(0,0,32,32),983.09f,"terrain plane=0",transmission,0x00ff00);
		o.terrain=terrain;
		return o;
	}
	private BillboardOcclusionMask prepared(BillboardOcclusionQuality quality, BillboardOcclusionMask.Occluder... occluders)
	{
		BillboardOcclusionMask mask=new BillboardOcclusionMask();
		mask.prepare(Arrays.asList(occluders),quality,BillboardOcclusionComposition.TRANSPARENCY_AWARE,0,0,32,32,null,null);
		return mask;
	}
	@Test public void supportingGroundIsExcludedInEveryDecisionPath()
	{
		for(BillboardOcclusionQuality quality:BillboardOcclusionQuality.values())
		{
			if(quality==BillboardOcclusionQuality.OFF) continue;
			BillboardOcclusionMask mask=prepared(quality,surface(new TerrainSurface(0,0,54,51,false),0));
			assertEquals(0,mask.transmittanceAt(8,8,1102.99));
			assertEquals(255,mask.transmittanceAt(8,8,1102.99,null,SUPPORT));
			assertFalse(mask.isOccluded(8,8,1102.99,null,SUPPORT));
			assertEquals(0xff123456,mask.tintPixel(8,8,1102.99,0xff123456,null,SUPPORT));
			assertNotEquals(BillboardOcclusionMask.CellResult.OCCLUDED,mask.classifySample(mask.sampleX(8),8,1102.99,null,SUPPORT));
			int[] row=new int[mask.sampleStep()];
			mask.refinedSampleTransmittance(mask.sampleX(8),8,1102.99,row,null,SUPPORT);
			for(int value:row) assertEquals(255,value);
			assertEquals(0,mask.refinedSampleBits(mask.sampleX(8),8,1102.99,null,SUPPORT));
			assertEquals(Float.POSITIVE_INFINITY,mask.depthAt(8,8,null,SUPPORT),0);
			assertNull(mask.sourceAt(8,8,null,SUPPORT));
			assertTrue(mask.terrainDebugAt(8,8,null,SUPPORT).contains("supportingGroundExcluded=true"));
			SupportingFootprint neighbour=new SupportingFootprint(0,0,55,51,55,51);
			assertEquals(0,mask.transmittanceAt(8,8,1102.99,null,neighbour));
		}
	}
	@Test public void foregroundHillsUpperSurfacesAndOtherWorldsRemainOccluders()
	{
		for(TerrainSurface terrain:new TerrainSurface[]{
			new TerrainSurface(0,0,54,50,false),new TerrainSurface(0,0,54,51,true),
			new TerrainSurface(0,1,54,51,false),new TerrainSurface(7,0,54,51,false),null})
		{
			BillboardOcclusionMask mask=prepared(BillboardOcclusionQuality.HIGH,surface(terrain,0));
			assertEquals(0,mask.transmittanceAt(8,8,1102.99,null,SUPPORT));
			assertNotNull(mask.sourceAt(8,8,null,SUPPORT));
		}
	}
	@Test public void supportingTerrainDoesNotHideSceneryInTheSameCell()
	{
		BillboardOcclusionMask mask=prepared(BillboardOcclusionQuality.MEDIUM,
			surface(new TerrainSurface(0,0,54,51,false),0),surface(null,128));
		assertEquals(128,mask.transmittanceAt(8,8,1102.99,null,SUPPORT));
		assertNotEquals(0xff123456,mask.tintPixel(8,8,1102.99,0xff123456,null,SUPPORT));
		mask=prepared(BillboardOcclusionQuality.HIGH,surface(new TerrainSurface(0,0,54,51,false),128));
		assertEquals(0xff123456,mask.tintPixel(8,8,1102.99,0xff123456,null,SUPPORT));
	}
	@Test public void footprintsUseOwningSceneCoordinatesAndExactActorSize()
	{
		WorldView nested=proxy(WorldView.class,method("getId",7),method("getPlane",1));
		NPC actor=proxy(NPC.class,method("getFootprintSize",256),method("getWorldView",nested),
			method("getLocalLocation",new LocalPoint(1280,1536,nested)));
		BillboardRenderRequest request=request(actor,new LocalPoint(7000,6500));
		SupportingFootprint footprint=SupportingFootprint.capture(request,null);
		assertEquals(7,footprint.worldView); assertEquals(1,footprint.plane);
		assertEquals(9,footprint.minX); assertEquals(11,footprint.minY);
		assertEquals(10,footprint.maxX); assertEquals(12,footprint.maxY);
		assertFalse(footprint.contains(new TerrainSurface(0,1,9,11,false)));
		GameObject object=proxy(GameObject.class,method("getWorldView",nested),method("getPlane",1),
			method("getSceneMinLocation",new Point(3,4)),method("getSceneMaxLocation",new Point(5,7)));
		SupportingFootprint objectFootprint=SupportingFootprint.capture(request,object);
		assertEquals(3,objectFootprint.minX); assertEquals(7,objectFootprint.maxY);
	}
	@Test public void loggedCameraAndTargetKeepTheirSupportingSlopeButNotForegroundTerrain()
	{
		// Values from the user's GPU diagnostics; the controlled terrain depths reproduce the bad comparison.
		double circle=Perspective.SINE14.length;
		Client client=proxy(Client.class,method("isGpu",true),method("getCameraYaw",5682),method("getCameraPitch",724),
			method("getCameraFpYaw",(float)(5682*Math.PI*2/circle)),method("getCameraFpPitch",(float)(724*Math.PI*2/circle)),
			method("getCameraFpX",7837.0146484375f),method("getCameraFpY",7191.58837890625f),
			method("getCameraFpZ",-635.3372192382812f),method("getScale",983),
			method("getViewportWidth",782),method("getViewportHeight",503));
		WorldView view=proxy(WorldView.class,method("getId",0),method("getPlane",0));
		NPC actor=proxy(NPC.class,method("getModelHeight",202),method("getFootprintSize",1),
			method("getWorldView",view),method("getLocalLocation",new LocalPoint(6976,6592,view)));
		BillboardRenderRequest request=request(actor,new LocalPoint(6976,6592,view));
		Rectangle bounds=new Rectangle(358,145,66,192);
		BufferedImage sprite=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB); sprite.setRGB(0,0,0xffabcdef);
		PreparedBillboardDraw draw=new PreparedBillboardDraw(request,new BillboardRenderResult(bounds,sprite,
			new Rectangle(-36,-206,73,218)),1);
		BillboardDepthCalculator depth=new BillboardDepthCalculator(client);
		for(boolean supporting:new boolean[]{true,false})
		{
			BillboardOcclusionMask mask=new BillboardOcclusionMask();
			BillboardOcclusionMask.Occluder terrain=new BillboardOcclusionMask.Occluder(bounds,983.09f,"terrain plane=0");
			terrain.terrain=new TerrainSurface(0,0,54,supporting ? 51 : 50,false);
			mask.prepare(Collections.singletonList(terrain),BillboardOcclusionQuality.MEDIUM,
				BillboardOcclusionComposition.HARD_CUTOUT,0,0,782,503,null,null);
			BillboardFrameBuffer frame=new BillboardFrameBuffer(mask,new BillboardPerformanceMetrics(),
				prepared->BillboardDepthSurface.from(depth,prepared,-248));
			for(boolean debug:new boolean[]{false,true})
			{
				frame.begin(782,503);frame.blit(draw,0,0,782,503,debug);
				assertEquals(supporting ? 0xffabcdef : debug ? new java.awt.Color(255,32,32,150).getRGB() : 0,
					frame.image().getRGB(375,289));
			}
		}
	}
	private BillboardRenderRequest request(Renderable actor, LocalPoint point)
	{
		Model model=proxy(Model.class,method("getVerticesCount",2),method("getVerticesY",new float[]{0,-202}),method("getVerticesX",new float[]{-20,20}),method("getVerticesZ",new float[]{0,0}));
		return new BillboardRenderRequest(actor,model,point,0,0,0,0,-1,-1,-1,-1,-1,false,false,VerticalAnchor.BOTTOM,null);
	}
}
