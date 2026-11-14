package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.SpriteShadowType;
import com.kierenboal.npcsnap.TestProxies;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShadowRotationTest
{
	private SpriteShadowMask caster(SpriteShadowType style, double quality)
	{
		int width=(int)Math.round(48*quality), height=(int)Math.round(96*quality);
		BufferedImage source=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
		for(int y=0;y<height;y++) for(int x=width/3;x<width*2/3;x++) source.setRGB(x,y,0xffffffff);
		// Asymmetric weapon and wing reach the occupied bounds.
		for(int x=0;x<width;x++) source.setRGB(x,height/4,0xffffffff);
		source.setRGB(width-1,height/2,0xffffffff);
		return SpriteShadowRenderer.capture(source,style,new SpriteShadowRenderer.Scratch(),
			new BillboardOutlineRenderer.Scratch(),0.1).directionalCaster();
	}

	private Client camera(double x,double y,double z,double yaw,double pitch)
	{
		return TestProxies.proxy(Client.class,
			TestProxies.method("isGpu",true),
			TestProxies.method("getCameraFpX",(float)x),TestProxies.method("getCameraFpY",(float)y),
			TestProxies.method("getCameraFpZ",(float)z),TestProxies.method("getCameraFpYaw",(float)yaw),
			TestProxies.method("getCameraFpPitch",(float)pitch),
			TestProxies.method("getCameraYaw",(int)Math.round(yaw*Perspective.SINE14.length/(Math.PI*2))),
			TestProxies.method("getCameraPitch",(int)Math.round(pitch*Perspective.SINE14.length/(Math.PI*2))),
			TestProxies.method("getScale",1652),TestProxies.method("getViewportWidth",823),
			TestProxies.method("getViewportHeight",504));
	}

	private void assertThicknessAndAnchor(BillboardDrawGeometry draw,SpriteShadowMask mask,
		double anchorX,double anchorY,double minimumWidth)
	{
		assertNotNull(draw); assertNotNull(draw.projectedQuad);
		double u=(mask.centerX-mask.offsetX)/mask.image.getWidth();
		double v=(mask.feet+1.0-mask.offsetY)/mask.image.getHeight();
		Point foot=draw.canvasPoint(u,v), tip=draw.canvasPoint(u,0);
		assertEquals(anchorX,foot.getX(),1); assertEquals(anchorY,foot.getY(),1);
		double dx=tip.getX()-anchorX,dy=tip.getY()-anchorY,length=Math.hypot(dx,dy);
		assertTrue(length>0);
		Polygon polygon=draw.projectedQuad.polygon();
		double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
		for(int i=0;i<polygon.npoints;i++)
		{
			double across=((polygon.xpoints[i]-anchorX)*dy-(polygon.ypoints[i]-anchorY)*dx)/length;
			min=Math.min(min,across); max=Math.max(max,across);
		}
		// Polygon conversion rounds each corner and the tip to screen pixels.
		assertTrue("perpendicular width="+(max-min)+", required="+minimumWidth,max-min>=minimumWidth-2);
	}

	@Test public void cameraFacingCaptureWidthAndWorldCastDirectionUseIndependentAxes()
	{
		SpriteShadowMask mask=caster(SpriteShadowType.FLAT,1);
		for(ShadowLighting mode:new ShadowLighting[]{ShadowLighting.GLOBAL_POINT,ShadowLighting.PLAYER_LANTERN,
			ShadowLighting.WORLD_DIRECTIONAL,ShadowLighting.CAMERA_DIRECTIONAL})
		{
			for(int step=0;step<720;step++)
			{
				double yaw=step*Math.PI/360;
				ShadowLightVector light=ShadowLightVector.resolve(mode,3158,3484,3150,3482,yaw);
				double[] basis=ShadowProjection.groundBasis(mask,yaw,light);
				assertEquals(Math.cos(yaw),basis[0],1e-12);
				assertEquals(Math.sin(yaw),basis[1],1e-12);
				// Moving toward the silhouette's top must cast away from the light.
				assertTrue(-basis[2]*light.x-basis[3]*light.y>0);
				assertEquals(0,basis[2]*light.y-basis[3]*light.x,1e-12);
				double[] adjacent=ShadowProjection.groundBasis(mask,yaw+1e-5,light);
				assertEquals(basis[2],adjacent[2],1e-12);
				assertEquals(basis[3],adjacent[3],1e-12);
			}
		}
	}

	@Test public void diagonalStripGetsThickerWithoutMovingItsAxisOrFoot()
	{
		SpriteShadowMask mask=caster(SpriteShadowType.FLAT,1);
		double u=(mask.centerX-mask.offsetX)/mask.image.getWidth();
		double v=(mask.feet+1.0-mask.offsetY)/mask.image.getHeight();
		for(double thinWidth:new double[]{0,0.01,0.1})
		{
			BillboardCanvasPoint[] points=new BillboardCanvasPoint[4];
			for(int i=0;i<4;i++)
			{
				double across=(i==1 || i==2 ? 1 : 0)-u,along=v-(i>=2 ? 1 : 0);
				points[i]=new BillboardCanvasPoint(400+200*along+30*across,
					400-100*along+(-15+thinWidth)*across,100,0,0);
			}
			BillboardDrawGeometry draw=ShadowProjection.quad(points,mask,400,400,24);
			assertThicknessAndAnchor(draw,mask,400,400,24);
			Point tip=draw.canvasPoint(u,0);
			assertEquals(600,tip.getX(),1); assertEquals(300,tip.getY(),1);
		}
	}

	@Test public void loggedCameraAndOrbitKeepThicknessDirectionAndContactAcrossQualities()
	{
		double loggedYaw=4184*Math.PI*2/Perspective.SINE14.length;
		double loggedPitch=2174*Math.PI*2/Perspective.SINE14.length;
		for(SpriteShadowType style:new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.HARD,
			SpriteShadowType.RADIAL,SpriteShadowType.BLOB}) for(double quality:new double[]{1,0.70,0.33,0.25})
		{
			SpriteShadowMask mask=caster(style,quality);
			BillboardDrawGeometry sprite=BillboardDrawGeometry.rectangular(new Rectangle(365,137,107,187));
			for(int step=-1;step<64;step++)
			{
				double yaw=step<0 ? loggedYaw : step*Math.PI/32;
				Client client=step<0 ? camera(7141.01953125,7783.93115234375,-1716.547119140625,yaw,loggedPitch)
					: camera(5952+Math.sin(yaw)*1600,7744-Math.cos(yaw)*1600,-1716,yaw,loggedPitch);
				BillboardDepthCalculator calculator=new BillboardDepthCalculator(client);
				BillboardCanvasPoint receiver=calculator.projectCanvasPoint(5952,7744,-304);
				assertNotNull(receiver);
				for(ShadowLighting mode:new ShadowLighting[]{ShadowLighting.GLOBAL_POINT,ShadowLighting.PLAYER_LANTERN,
					ShadowLighting.WORLD_DIRECTIONAL,ShadowLighting.CAMERA_DIRECTIONAL})
				{
					ShadowLightVector light=ShadowLightVector.resolve(mode,3158,3484,3150,3482,yaw);
					double unit=117.0/mask.sourceWidth;
					BillboardDrawGeometry draw=ShadowProjection.ground(mask,sprite,calculator,5952,7744,-304,
						unit,yaw,light,receiver);
					assertThicknessAndAnchor(draw,mask,receiver.x,receiver.y,ShadowProjection.minimumCastWidth(mask,sprite));
					double height=(mask.feet+1-mask.offsetY)*mask.visibleHeight/mask.projectionSpan()*unit;
					BillboardCanvasPoint expected=calculator.projectCanvasPoint(5952+height*light.x,7744+height*light.y,-304);
					Point actual=draw.canvasPoint((mask.centerX-mask.offsetX)/mask.image.getWidth(),0);
					double maxLength=mask.fullResolution ? 187*mask.visibleHeight/(double)mask.sourceHeight*0.5 : Double.POSITIVE_INFINITY;
					double dx=expected.x-receiver.x,dy=expected.y-receiver.y;
					double shorten=Math.min(1,maxLength/Math.hypot(dx,dy));
					assertEquals(receiver.x+dx*shorten,actual.getX(),1);
					assertEquals(receiver.y+dy*shorten,actual.getY(),1);
					// The shadow's source-left to source-right must agree with the
					// capture's camera-right axis, even when it is almost edge-on.
					double footV=(mask.feet+1.0-mask.offsetY)/mask.image.getHeight();
					Point left=draw.canvasPoint(0,footV),right=draw.canvasPoint(1,footV);
					// This rectangular capture has screen-horizontal source-right.
					// Exactly vertical edges may round to the same X coordinate.
					assertTrue(right.getX()>=left.getX());
				}
			}
		}
	}

	@Test public void exportCastStaysVisibleAcrossSidewaysLightRotations()
	{
		for(SpriteShadowType style:new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.HARD,SpriteShadowType.RADIAL})
		{
			SpriteShadowMask mask=caster(style,1);
			// Hide the body so only the exported cast contributes to these measurements.
			SpriteCapture capture=new SpriteCapture(new BufferedImage(mask.sourceWidth,mask.sourceHeight,
				BufferedImage.TYPE_INT_ARGB),mask);
			for(int step=0;step<24;step++)
			{
				ShadowLightVector light=ShadowLightVector.resolve(ShadowLighting.CAMERA_DIRECTIONAL,0,0,0,0,step*Math.PI/12);
				ShadowExportImage out=ShadowExportImage.render(capture,ShadowLighting.CAMERA_DIRECTIONAL,
					Color.MAGENTA,light,-Math.PI/8);
				double dx=light.x,dy=-Math.sin(Math.PI/8)*light.y,length=Math.hypot(dx,dy);
				double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
				int pixels=0;
				for(int y=0;y<out.image.getHeight();y++) for(int x=0;x<out.image.getWidth();x++)
				{
					int pixel=out.image.getRGB(x,y);
					if((pixel>>>24)==0) continue;
					assertEquals(0xff00ff,pixel&0xffffff);
					double across=((x+out.bounds.x-mask.centerX)*dy-(y+out.bounds.y-mask.feet-1)*dx)/length;
					min=Math.min(min,across); max=Math.max(max,across); pixels++;
				}
				assertTrue(pixels>10);
				// Blur padding is transparent and a one-row weapon can alias during
				// nearest-neighbour reduction; measure against the occupied source.
				assertTrue("export width="+(max-min),max-min>=Math.max(2,ShadowProjection.minimumCastWidth(mask,mask.sourceWidth)/3));
			}
		}
	}

	@Test public void blurredStylesUseASmallerGuardThanCrispSilhouettes()
	{
		assertEquals(10,ShadowProjection.minimumCastWidth(caster(SpriteShadowType.HARD,1),100),0);
		assertEquals(10,ShadowProjection.minimumCastWidth(caster(SpriteShadowType.RADIAL,1),100),0);
		assertEquals(25,ShadowProjection.minimumCastWidth(caster(SpriteShadowType.FLAT,1),100),0);
		assertEquals(2,ShadowProjection.minimumCastWidth(caster(SpriteShadowType.RADIAL,1),8),0);
	}

	@Test public void yawSweepUsesTheSameSourceMappingForEverySilhouetteRow()
	{
		SpriteShadowMask mask=caster(SpriteShadowType.FLAT,1);
		int checked=0;
		for(int yawUnits:new int[]{880,3100,5800,8400,11200,14198})
		{
			double yaw=yawUnits*Math.PI*2/Perspective.SINE14.length;
			BillboardDepthCalculator calculator=new BillboardDepthCalculator(camera(
				4096+Math.sin(yaw)*2000,4096-Math.cos(yaw)*2000,-1600,yaw,Math.PI/4));
			ShadowLightVector light=ShadowLightVector.resolve(ShadowLighting.GLOBAL_POINT,3210,3210,0,0,yaw);
			BillboardCanvasPoint floor=calculator.projectCanvasPoint(4096,4096,0);
			BillboardDrawGeometry sprite=BillboardDrawGeometry.rectangular(new Rectangle(400,200,24,192));
			double scale=mask.visibleHeight/mask.projectionSpan();
			// A well-conditioned cast must match direct world projection at every
			// row, including asymmetric weapon pixels, with no extra flip or shear.
			BillboardCanvasPoint left=calculator.projectCanvasPoint(4096-24*Math.cos(yaw),4096-24*Math.sin(yaw),0);
			BillboardCanvasPoint right=calculator.projectCanvasPoint(4096+24*Math.cos(yaw),4096+24*Math.sin(yaw),0);
			BillboardCanvasPoint tip=calculator.projectCanvasPoint(4096+mask.visibleHeight*light.x*scale,
				4096+mask.visibleHeight*light.y*scale,0);
			double ax=tip.x-floor.x,ay=tip.y-floor.y,length=Math.hypot(ax,ay);
			double width=Math.abs((right.x-left.x)*ay-(right.y-left.y)*ax)/length;
			if(width<ShadowProjection.minimumCastWidth(mask,sprite)+2) continue;
			checked++;
			BillboardDrawGeometry result=ShadowProjection.ground(mask,sprite,calculator,4096,4096,0,1,yaw,light,floor);
			assertNotNull(result);
			for(double u:new double[]{0.1,0.3,0.5,0.8}) for(double v:new double[]{0.1,0.4,0.9,1})
			{
				double x=mask.offsetX+u*mask.image.getWidth()-mask.centerX;
				double y=(mask.feet+1-mask.offsetY-v*mask.image.getHeight())*scale;
				BillboardCanvasPoint expected=calculator.projectCanvasPoint(4096+x*Math.cos(yaw)+y*light.x,
					4096+x*Math.sin(yaw)+y*light.y,0);
				Point actual=result.canvasPoint(u,v);
				assertEquals(expected.x,actual.getX(),1); assertEquals(expected.y,actual.getY(),1);
			}
		}
		assertTrue("well-conditioned views checked="+checked,checked>=3);
	}

	@Test public void lengthLimitPreservesPerspectiveFootLineWidthAndLightDirection()
	{
		for(SpriteShadowType style:new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.HARD,SpriteShadowType.RADIAL})
		{
			SpriteShadowMask mask=caster(style,1);
			double u=(mask.centerX-mask.offsetX)/mask.image.getWidth();
			double v=(mask.feet+1.0-mask.offsetY)/mask.image.getHeight();
			BillboardCanvasPoint[] points=new BillboardCanvasPoint[4];
			for(int i=0;i<4;i++)
			{
				double x=(i==1 || i==2 ? 1 : 0)-u,y=(i>=2 ? 1 : 0)-v;
				double depth=1000+10*x+200*y;
				points[i]=new BillboardCanvasPoint(400+(40000*x-250000*y)/depth,
					400+(1000*x-100000*y)/depth,depth,0,0);
			}
			BillboardDrawGeometry original=ShadowProjection.quad(points,mask,400,400,0);
			Point oldTip=original.canvasPoint(u,0),left=original.canvasPoint(0,v),right=original.canvasPoint(1,v);
			ShadowProjection.limitCastLength(points,mask,400,400,70);
			BillboardDrawGeometry limited=ShadowProjection.quad(points,mask,400,400,0);
			assertNotNull(limited);
			Point tip=limited.canvasPoint(u,0);
			assertEquals(70,Math.hypot(tip.getX()-400,tip.getY()-400),1);
			assertEquals(new Point(400,400),limited.canvasPoint(u,v));
			assertEquals(1000,limited.projectedQuad.depthAt(u,v),1e-8);
			assertEquals(left,limited.canvasPoint(0,v)); assertEquals(right,limited.canvasPoint(1,v));
			double oldLength=Math.hypot(oldTip.getX()-400,oldTip.getY()-400);
			assertEquals((oldTip.getX()-400)/oldLength,(tip.getX()-400)/70.0,0.02);
			assertEquals((oldTip.getY()-400)/oldLength,(tip.getY()-400)/70.0,0.02);
			BillboardCanvasPoint first=points[0];
			ShadowProjection.limitCastLength(points,mask,400,400,80);
			assertSame(first,points[0]);
		}
	}
}
