package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class FullSilhouetteShadowTest
{
	private BufferedImage armedSprite()
	{
		BufferedImage source=new BufferedImage(48,96,BufferedImage.TYPE_INT_ARGB);
		for(int y=10;y<96;y++) for(int x=20;x<26;x++) source.setRGB(x,y,0xffffffff);
		for(int x=3;x<45;x++) source.setRGB(x,9,0xffffffff);
		for(int y=12;y<91;y++) source.setRGB(35+y%3,y,0xff112233);
		source.setRGB(0,40,0x80123456);
		return source;
	}
	private SpriteShadowMask capture(BufferedImage source,SpriteShadowType type)
	{
		return SpriteShadowRenderer.capture(source,type,new SpriteShadowRenderer.Scratch(),new BillboardOutlineRenderer.Scratch(),0.1);
	}
	private int alpha(SpriteShadowMask mask,int x,int y)
	{
		x-=mask.offsetX; y-=mask.offsetY;
		return x<0 || y<0 || x>=mask.image.getWidth() || y>=mask.image.getHeight() ? 0 : mask.image.getRGB(x,y)>>>24;
	}
	@Test public void flatCastRetainsEveryNativeRowAndWeaponPixel()
	{
		BufferedImage source=armedSprite();
		SpriteShadowMask root=capture(source,SpriteShadowType.FLAT), cast=root.directionalCaster();
		assertTrue(cast.fullResolution); assertFalse(root.fullResolution);
		assertTrue(cast.image.getHeight()>root.image.getHeight()*4);
		for(int y=0;y<source.getHeight();y++) for(int x=0;x<source.getWidth();x++)
			assertEquals((int)((source.getRGB(x,y)>>>24)/255f*0.49f*255),alpha(cast,x,y));
		assertEquals(0,alpha(cast,33,30)); assertTrue(alpha(cast,35,30)>0);
		assertSame(cast,root.directionalCaster());
	}
	@Test public void lightingAndColorUseCachedSilhouetteWithoutChangingSource()
	{
		BufferedImage source=armedSprite();
		int[] original=source.getRGB(0,0,48,96,null,0,48);
		for(SpriteShadowType type:new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.HARD,SpriteShadowType.RADIAL})
		{
			SpriteShadowMask root=capture(source,type), cast=root.directionalCaster();
			BufferedImage tint=cast.tinted(Color.BLACK);
			for(ShadowLighting light:ShadowLighting.values())
			{
				ShadowExportImage.render(new SpriteCapture(source,root),light,Color.MAGENTA,
					ShadowLightVector.resolve(light,3210,3210,3200,3200,0),-Math.PI/4);
				assertSame(cast,root.directionalCaster()); assertSame(tint,cast.tinted(Color.BLACK));
			}
		}
		assertArrayEquals(original,source.getRGB(0,0,48,96,null,0,48));
	}
	@Test public void fullDetailScalesWithCaptureQualityInsteadOfUsingFiveRows()
	{
		BufferedImage original=armedSprite();
		for(double quality:new double[]{1,0.70,0.33,0.25})
		{
			BufferedImage source=new BufferedImage((int)Math.round(48*quality),(int)Math.round(96*quality),BufferedImage.TYPE_INT_ARGB);
			Graphics2D g=source.createGraphics();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g.drawImage(original,0,0,source.getWidth(),source.getHeight(),null);
			}
			finally { g.dispose(); }
			SpriteShadowMask root=capture(source,SpriteShadowType.FLAT);
			assertTrue(root.directionalCaster().image.getHeight()>source.getHeight()*0.7);
			assertTrue(root.image.getHeight()<=5);
		}
	}
	@Test public void edgeOnLightsRemainFiniteAndBoundedAcrossViews()
	{
		SpriteShadowMask mask=capture(armedSprite(),SpriteShadowType.FLAT).directionalCaster();
		for(int views:new int[]{1,4,8,32}) for(int view=0;view<views;view++)
		{
			double yaw=view*Math.PI*2/views;
			for(ShadowLighting light:ShadowLighting.values())
			{
				double[] basis=ShadowProjection.groundBasis(mask,yaw,ShadowLightVector.resolve(light,3210,3200,3200,3200,yaw));
				assertTrue(Double.isFinite(basis[2])); assertTrue(Double.isFinite(basis[3]));
				assertEquals(1,Math.hypot(basis[0],basis[1]),1e-12);
				assertEquals(Math.cos(yaw),basis[0],1e-12);
				assertEquals(Math.sin(yaw),basis[1],1e-12);
				assertTrue(Math.hypot(basis[2],basis[3])<=0.750001);
			}
		}
	}
	@Test public void blobAndLegacyRetainTheirOriginalCastPaths()
	{
		for(SpriteShadowType type:new SpriteShadowType[]{SpriteShadowType.BLOB,SpriteShadowType.LEGACY})
		{
			SpriteShadowMask mask=capture(armedSprite(),type);
			assertFalse(mask.fullResolution);
			assertSame(mask,mask.directionalCaster());
		}
	}
}
