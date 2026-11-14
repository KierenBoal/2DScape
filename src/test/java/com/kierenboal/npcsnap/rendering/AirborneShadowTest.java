package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Point;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class AirborneShadowTest
{
	private BufferedImage flyingSprite()
	{
		BufferedImage image=new BufferedImage(32,40,BufferedImage.TYPE_INT_ARGB);
		for(int y=4;y<40;y++) for(int x=12;x<20;x++) image.setRGB(x,y,0xff8844ff);
		for(int x=1;x<31;x++) image.setRGB(x,15,0xff8844ff);
		return image;
	}
	private SpriteShadowMask mask(BufferedImage image, SpriteShadowType type)
	{
		return SpriteShadowRenderer.capture(image,type,new SpriteShadowRenderer.Scratch(),new BillboardOutlineRenderer.Scratch(),0.1);
	}
	private Point contact(BillboardDrawGeometry geometry, SpriteShadowMask mask)
	{
		return geometry.canvasPoint((mask.centerX-mask.offsetX)/mask.image.getWidth(),
			(mask.feet+1.0-mask.offsetY)/mask.image.getHeight());
	}
	@Test public void downwardStylesAnchorToFloorWithoutMovingAirborneBody()
	{
		BufferedImage source=flyingSprite();
		for(SpriteShadowType type:SpriteShadowType.values())
		{
			if(type==SpriteShadowType.OFF) continue;
			SpriteShadowMask mask=mask(source,type);
			for(double quality:new double[]{1,0.70,0.33,0.25})
			{
				Rectangle original=new Rectangle(200,50,(int)Math.round(32/quality),(int)Math.round(40/quality));
				for(boolean worldPlane:new boolean[]{false,true})
				{
					BillboardDrawGeometry body=BillboardDrawGeometry.rectangular(original);
					if(worldPlane) body=BillboardDrawGeometry.projected(BillboardProjectedQuad.create(
						new BillboardCanvasPoint(original.x,original.y,300,0,0),
						new BillboardCanvasPoint(original.getMaxX(),original.y,300,0,0),
						new BillboardCanvasPoint(original.getMaxX(),original.getMaxY(),300,0,0),
						new BillboardCanvasPoint(original.x,original.getMaxY(),300,0,0),new Rectangle(0,0,32,40),new Rectangle(0,0,32,40)));
					Rectangle before=new Rectangle(body.bounds);
					BillboardCanvasPoint floor=new BillboardCanvasPoint(400,500,700,0,0);
					BillboardDrawGeometry shadow=ShadowProjection.downward(mask,body,ShadowLighting.DIRECTIONAL_DOWN,floor);
					assertNotNull(shadow);
					Point planted=contact(shadow,mask);
					double lift=type==SpriteShadowType.LEGACY ? 0 : !worldPlane ? original.height/40.0 :
						body.canvasPoint(mask.centerX/32,(mask.feet+1.0)/40).getY()-body.canvasPoint(mask.centerX/32,mask.feet/40.0).getY();
					assertEquals(floor.x,planted.getX(),1); assertEquals(floor.y-lift,planted.getY(),1);
					assertEquals(before,body.bounds);
					assertEquals(floor.depth,shadow.projectedQuad.depthAt(0.5,0.5),1e-8);
				}
			}
		}
	}
	@Test public void angledCastUsesFloorInsteadOfTheAirborneSilhouette()
	{
		BufferedImage source=flyingSprite();
		Client client=proxy(Client.class,method("getScale",1024),method("getViewportWidth",800),method("getViewportHeight",600),
			method("getCameraX",512),method("getCameraY",0),method("getCameraZ",-600),method("getCameraPitch",net.runelite.api.Perspective.SINE14.length/16));
		BillboardDepthCalculator calculator=new BillboardDepthCalculator(client);
		BillboardCanvasPoint floor=calculator.projectCanvasPoint(512,1024,-100);
		BillboardDrawGeometry body=BillboardDrawGeometry.rectangular(new Rectangle(200,10,96,120));
		for(SpriteShadowType type:new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.HARD,SpriteShadowType.RADIAL,SpriteShadowType.BLOB})
		{
			SpriteShadowMask root=mask(source,type);
			for(ShadowLighting light:ShadowLighting.values())
			{
				if(light==ShadowLighting.DIRECTIONAL_DOWN) continue;
				SpriteShadowMask[] masks={root.directionalCaster()};
				for(int i=0;i<masks.length;i++)
				{
					if(masks[i]==null) continue;
					BillboardDrawGeometry shadow=ShadowProjection.ground(masks[i],body,calculator,512,1024,-100,3,0,
						ShadowLightVector.resolve(light,3210,3210,3200,3200,0),floor);
					assertNotNull(type+" / "+light,shadow);
					Point planted=contact(shadow,masks[i]);
					assertEquals(floor.x,planted.getX(),1); assertEquals(floor.y,planted.getY(),1);
				}
			}
		}
	}
	@Test public void actorExportsExpandToIncludeGroundBelowTheFlyingModel()
	{
		BufferedImage source=flyingSprite();
		BillboardCanvasPoint origin=new BillboardCanvasPoint(16,100,100,0,0);
		for(SpriteShadowType type:SpriteShadowType.values())
		{
			if(type==SpriteShadowType.OFF) continue;
			SpriteShadowMask root=mask(source,type);
			for(ShadowLighting lighting:ShadowLighting.values())
			{
				ShadowExportImage image=ShadowExportImage.render(new SpriteCapture(source,root),lighting,Color.BLACK,
					ShadowLightVector.resolve(lighting,3210,3210,3200,3200,0),-Math.PI/4,origin);
				assertTrue(type+" / "+lighting,image.bounds.getMaxY()>=99);
				assertTrue(image.image.getHeight()>source.getHeight());
				for(int y=0;y<source.getHeight();y++) for(int x=0;x<source.getWidth();x++)
					if((source.getRGB(x,y)>>>24)==255)
						assertEquals(source.getRGB(x,y),image.image.getRGB(x-image.bounds.x,y-image.bounds.y));
			}
		}
	}
}
