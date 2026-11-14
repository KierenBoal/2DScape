package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.SpriteShadowType;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionMask;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShadowPresentationTest
{
	private BufferedImage sprite() throws Exception
	{
		return ImageIO.read(getClass().getResource("/shadows/sprite.png"));
	}
	private SpriteShadowMask mask(BufferedImage sprite, SpriteShadowType type)
	{
		return SpriteShadowRenderer.capture(sprite,type,new SpriteShadowRenderer.Scratch(),
			new BillboardOutlineRenderer.Scratch(),0.1);
	}
	private int peak(BufferedImage image)
	{
		int max=0;
		for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) max=Math.max(max,image.getRGB(x,y)>>>24);
		return max;
	}
	@Test public void stylesPreserveSourceAndHaveDifferentDensity() throws Exception
	{
		BufferedImage sprite=sprite();
		int[] original=sprite.getRGB(0,0,sprite.getWidth(),sprite.getHeight(),null,0,sprite.getWidth());
		SpriteShadowMask hard=mask(sprite,SpriteShadowType.HARD), soft=mask(sprite,SpriteShadowType.RADIAL);
		assertTrue(peak(hard.image)>peak(soft.image));
		SpriteShadowMask blob=mask(sprite,SpriteShadowType.BLOB);
		assertTrue(peak(blob.image)>0); assertTrue(peak(blob.image)<=115);
		for(SpriteShadowType type:SpriteShadowType.values()) mask(sprite,type);
		assertArrayEquals(original,sprite.getRGB(0,0,sprite.getWidth(),sprite.getHeight(),null,0,sprite.getWidth()));
	}
	@Test public void widenedBlobExtendsPastSpriteWithoutClipping()
	{
		BufferedImage source=new BufferedImage(100,40,BufferedImage.TYPE_INT_ARGB);
		for(int x=0;x<100;x++) source.setRGB(x,39,0xffffffff);
		SpriteShadowMask blob=mask(source,SpriteShadowType.BLOB);
		assertTrue(blob.offsetX<0);
		assertTrue(blob.offsetX+blob.image.getWidth()>100);
		assertTrue(blob.image.getWidth()>110);
		assertTrue(blob.image.getHeight()<=23);
	}
	@Test public void angledLightingUsesOneCompleteCachedMask() throws Exception
	{
		SpriteShadowMask mask=mask(sprite(),SpriteShadowType.RADIAL);
		SpriteShadowMask caster=mask.directionalCaster();
		assertNotNull(caster); assertTrue(caster.fullResolution);
		assertSame(caster,mask.directionalCaster());
		assertTrue(caster.offsetY+caster.image.getHeight()>caster.feet+1);
		SpriteShadowMask blob=mask(sprite(),SpriteShadowType.BLOB);
		assertSame(blob,blob.directionalCaster());
	}

	@Test public void colorsMultiplyMaskAlphaAndReuseTintBuffer() throws Exception
	{
		for(SpriteShadowType type:SpriteShadowType.values())
		{
			if(type==SpriteShadowType.OFF) continue;
			SpriteShadowMask mask=mask(sprite(),type);
			BufferedImage tinted=mask.tinted(Color.BLACK);
			int max=peak(tinted);
			assertSame(tinted,mask.tinted(new Color(255,0,255,128)));
			assertEquals((max*128+127)/255,peak(tinted));
			for(int y=0;y<tinted.getHeight();y++) for(int x=0;x<tinted.getWidth();x++)
				assertEquals(0xff00ff,tinted.getRGB(x,y)&0xffffff);
			assertSame(tinted,mask.tinted(new Color(0,0,0,0))); assertEquals(0,peak(tinted));
			assertSame(tinted,mask.tinted(Color.BLACK)); assertEquals(max,peak(tinted));
		}
	}
	@Test public void downwardLiftScalesWithNativePixelsWithoutMovingSprite() throws Exception
	{
		BufferedImage source=sprite(); SpriteShadowMask mask=mask(source,SpriteShadowType.FLAT);
		for(double quality:new double[]{1,0.70,0.33,0.25})
		{
			Rectangle bounds=new Rectangle(50,60,(int)Math.round(source.getWidth()/quality),(int)Math.round(source.getHeight()/quality));
			BillboardDrawGeometry sprite=BillboardDrawGeometry.rectangular(bounds);
			BillboardDrawGeometry lifted=ShadowProjection.downward(mask,sprite,ShadowLighting.DIRECTIONAL_DOWN,400);
			BillboardDrawGeometry unshifted=ShadowProjection.downward(mask,sprite,ShadowLighting.GLOBAL_POINT,400);
			assertNotNull(lifted); assertEquals(unshifted.bounds.x,lifted.bounds.x);
			assertEquals(unshifted.bounds.y-(int)Math.ceil(bounds.height/(double)source.getHeight()),lifted.bounds.y);
			assertEquals(bounds,sprite.bounds);
			assertEquals(400,lifted.projectedQuad.depthAt(0.5,0.5),1e-8);
		}
	}
	@Test public void legacyIgnoresLightingAndLift() throws Exception
	{
		BufferedImage source=sprite(); SpriteShadowMask mask=mask(source,SpriteShadowType.LEGACY);
		BillboardDrawGeometry sprite=BillboardDrawGeometry.rectangular(new Rectangle(0,0,source.getWidth()*3,source.getHeight()*3));
		Rectangle expected=ShadowProjection.downward(mask,sprite,ShadowLighting.DIRECTIONAL_DOWN).bounds;
		for(ShadowLighting mode:ShadowLighting.values()) assertEquals(expected,ShadowProjection.downward(mask,sprite,mode).bounds);
	}
	@Test public void fixedAndCameraLightsUseDifferentReferenceFrames()
	{
		ShadowLightVector world=ShadowLightVector.resolve(ShadowLighting.WORLD_DIRECTIONAL,3210,3200,0,0,0);
		ShadowLightVector rotated=ShadowLightVector.resolve(ShadowLighting.WORLD_DIRECTIONAL,3210,3200,0,0,Math.PI/2);
		assertEquals(world.x,rotated.x,0); assertEquals(world.y,rotated.y,0);
		assertNotEquals(world.view(0).x,world.view(Math.PI).x,1e-8);
		ShadowLightVector a=ShadowLightVector.resolve(ShadowLighting.CAMERA_DIRECTIONAL,0,0,0,0,0).view(0);
		ShadowLightVector b=ShadowLightVector.resolve(ShadowLighting.CAMERA_DIRECTIONAL,0,0,0,0,Math.PI/2).view(Math.PI/2);
		assertEquals(a.x,b.x,1e-8); assertEquals(a.y,b.y,1e-8);
	}
 @Test public void groundProjectionRetainsContactAcrossCameraYawAndPitch() throws Exception
 {
  SpriteShadowMask root=mask(sprite(),SpriteShadowType.RADIAL);
  SpriteShadowMask caster=root.directionalCaster();
  for(int views:new int[]{1,4,8,16}) for(int slice=0;slice<views;slice++)
   for(double pitch:new double[]{Math.PI/8,Math.PI/4})
   {
    double yaw=slice*Math.PI*2/views;
    net.runelite.api.Client client=com.kierenboal.npcsnap.TestProxies.proxy(net.runelite.api.Client.class,
     com.kierenboal.npcsnap.TestProxies.method("getScale",512),
     com.kierenboal.npcsnap.TestProxies.method("getViewportWidth",1024),
     com.kierenboal.npcsnap.TestProxies.method("getViewportHeight",768),
     com.kierenboal.npcsnap.TestProxies.method("getCameraX",4096+(int)Math.round(Math.sin(yaw)*2000)),
     com.kierenboal.npcsnap.TestProxies.method("getCameraY",4096-(int)Math.round(Math.cos(yaw)*2000)),
     com.kierenboal.npcsnap.TestProxies.method("getCameraZ",-800),
     com.kierenboal.npcsnap.TestProxies.method("getCameraYaw",(int)Math.round(yaw*net.runelite.api.Perspective.SINE14.length/(Math.PI*2))),
     com.kierenboal.npcsnap.TestProxies.method("getCameraPitch",(int)Math.round(pitch*net.runelite.api.Perspective.SINE14.length/(Math.PI*2))));
    BillboardDrawGeometry sprite=BillboardDrawGeometry.rectangular(new Rectangle(400,300,96,288));
    BillboardDrawGeometry shadow=ShadowProjection.ground(caster,sprite,new BillboardDepthCalculator(client),
     4096,4096,0,4,yaw,ShadowLightVector.resolve(ShadowLighting.WORLD_DIRECTIONAL,3200,3200,0,0,yaw));
    assertNotNull(shadow);
    net.runelite.api.Point expected=sprite.canvasPoint(caster.centerX/caster.sourceWidth,(caster.feet+1.0)/caster.sourceHeight);
    net.runelite.api.Point actual=shadow.canvasPoint((caster.centerX-caster.offsetX)/caster.image.getWidth(),
     (caster.feet+1.0-caster.offsetY)/caster.image.getHeight());
    assertEquals(expected.getX(),actual.getX(),1); assertEquals(expected.getY(),actual.getY(),1);
   }
 }

	@Test public void pointLightsFollowPositionsAndStayBounded()
	{
		ShadowLightVector east=ShadowLightVector.resolve(ShadowLighting.GLOBAL_POINT,3210,3200,0,0,0);
		ShadowLightVector west=ShadowLightVector.resolve(ShadowLighting.GLOBAL_POINT,3190,3200,0,0,0);
		assertTrue(east.x>0); assertTrue(west.x<0);
		ShadowLightVector centered=ShadowLightVector.resolve(ShadowLighting.PLAYER_LANTERN,3200,3200,3200,3200,0);
		assertEquals(0,Math.hypot(centered.x,centered.y),0);
		ShadowLightVector absent=ShadowLightVector.resolve(ShadowLighting.PLAYER_LANTERN,3200,3200,Double.NaN,Double.NaN,0);
		assertEquals(0,Math.hypot(absent.x,absent.y),0);
		ShadowLightVector distant=ShadowLightVector.resolve(ShadowLighting.GLOBAL_POINT,16000,16000,0,0,0);
		assertTrue(Math.hypot(distant.x,distant.y)<=0.75000001);
	}
	@Test public void separateShadowCompositesBehindSpriteAndExpandsInterest()
	{
		BillboardPerformanceMetrics metrics=new BillboardPerformanceMetrics();
		BillboardFrameBuffer frame=new BillboardFrameBuffer(new BillboardOcclusionMask(metrics),metrics,ignored->BillboardDepthSurface.invalid());
		BufferedImage sprite=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB); sprite.setRGB(0,0,0xffff0000);
		BufferedImage shadow=new BufferedImage(3,1,BufferedImage.TYPE_INT_ARGB);
		for(int x=0;x<3;x++) shadow.setRGB(x,0,0x80000000);
		BillboardRenderResult layer=new BillboardRenderResult(new Rectangle(0,0,3,1),shadow,new Rectangle(3,1));
		BillboardRenderResult result=new BillboardRenderResult(BillboardDrawGeometry.rectangular(new Rectangle(1,0,1,1)),sprite,new Rectangle(1,1),layer);
		PreparedBillboardDraw draw=new PreparedBillboardDraw(null,result,1);
		assertEquals(new Rectangle(0,0,3,1),draw.visualBounds());
		frame.begin(3,1); frame.blit(draw,0,0,3,1,false);
		assertEquals(0x80000000,frame.image().getRGB(0,0)); assertEquals(0xffff0000,frame.image().getRGB(1,0));
		assertEquals(0x80000000,frame.image().getRGB(2,0));
	}
	@Test public void exportsHonorEveryStyleLightingAndTransparentColor() throws Exception
	{
		BufferedImage source=sprite();
		for(SpriteShadowType type:SpriteShadowType.values()) for(ShadowLighting lighting:ShadowLighting.values())
		{
			SpriteCapture capture=new SpriteCapture(source,mask(source,type));
			ShadowLightVector light=ShadowLightVector.resolve(lighting,3210,3210,3200,3200,0);
			ShadowExportImage export=ShadowExportImage.render(capture,lighting,Color.MAGENTA,light,-Math.PI/4);
			assertTrue(export.image.getWidth()>=source.getWidth()); assertTrue(export.image.getHeight()>=source.getHeight());
			ShadowExportImage invisible=ShadowExportImage.render(capture,lighting,new Color(0,0,0,0),light,0);
			assertEquals(source.getWidth(),invisible.image.getWidth()); assertEquals(source.getHeight(),invisible.image.getHeight());
		}
	}
}
