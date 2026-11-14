package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShadowBlurBoundsTest
{
	private SpriteShadowMask capture(BufferedImage source,SpriteShadowType type)
	{
		return SpriteShadowRenderer.capture(source,type,new SpriteShadowRenderer.Scratch(),
			new BillboardOutlineRenderer.Scratch(),0.1);
	}
	private BufferedImage solid(int w,int h)
	{
		BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
		for(int y=0;y<h;y++) for(int x=0;x<w;x++) image.setRGB(x,y,0xffffffff);
		return image;
	}
	private int alpha(SpriteShadowMask mask,int x,int y)
	{
		x-=mask.offsetX; y-=mask.offsetY;
		return x<0 || y<0 || x>=mask.image.getWidth() || y>=mask.image.getHeight()
			? 0 : mask.image.getRGB(x,y)>>>24;
	}

	@Test public void hardAndSoftFadeBeyondAllFourSourceEdges()
	{
		for(SpriteShadowType style:new SpriteShadowType[]{SpriteShadowType.HARD,SpriteShadowType.RADIAL})
		{
			SpriteShadowMask mask=capture(solid(18,24),style).directionalCaster();
			assertTrue(mask.offsetX<0); assertTrue(mask.offsetY<0);
			assertTrue(mask.offsetX+mask.image.getWidth()>18);
			assertTrue(mask.offsetY+mask.image.getHeight()>24);
			int center=alpha(mask,9,12);
			for(int[] point:new int[][]{{-1,12},{18,12},{9,-1},{9,24}})
			{
				int edge=alpha(mask,point[0],point[1]);
				assertTrue(edge>0); assertTrue(edge<center);
			}
		}
	}

	@Test public void edgeTouchingBlurMatchesTheSameSilhouetteInsideALargerCanvas()
	{
		for(SpriteShadowType style:new SpriteShadowType[]{SpriteShadowType.HARD,SpriteShadowType.RADIAL})
		{
			BufferedImage original=solid(18,24), padded=new BufferedImage(42,48,BufferedImage.TYPE_INT_ARGB);
			padded.setRGB(12,12,18,24,original.getRGB(0,0,18,24,null,0,18),0,18);
			SpriteShadowMask edge=capture(original,style).directionalCaster(), inset=capture(padded,style).directionalCaster();
			for(int y=-6;y<30;y++) for(int x=-6;x<24;x++)
				assertEquals(style+" at "+x+","+y,alpha(inset,x+12,y+12),alpha(edge,x,y));
		}
	}

	@Test public void angledExportContainsOnlyTheSelectedCastWithoutADownwardFootprint()
	{
		BufferedImage source=solid(18,24), empty=new BufferedImage(18,24,BufferedImage.TYPE_INT_ARGB);
		for(SpriteShadowType style:new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.HARD,SpriteShadowType.RADIAL,SpriteShadowType.BLOB})
		{
			SpriteShadowMask root=capture(source,style);
			for(ShadowLighting lighting:ShadowLighting.values())
			{
				if(lighting==ShadowLighting.DIRECTIONAL_DOWN) continue;
				ShadowLightVector light=ShadowLightVector.resolve(lighting,3210,3210,3200,3200,0);
				ShadowExportImage actual=ShadowExportImage.render(new SpriteCapture(empty,root),lighting,Color.BLACK,light,-Math.PI/8);
				ShadowExportImage castOnly=ShadowExportImage.render(new SpriteCapture(empty,root.directionalCaster()),lighting,Color.BLACK,light,-Math.PI/8);
				assertEquals(castOnly.bounds,actual.bounds);
				assertArrayEquals(castOnly.image.getRGB(0,0,actual.bounds.width,actual.bounds.height,null,0,actual.bounds.width),
					actual.image.getRGB(0,0,actual.bounds.width,actual.bounds.height,null,0,actual.bounds.width));
			}
		}
	}

	@Test public void narrowerCastBlurRetainsGapsBetweenSeparatedLimbs()
	{
		BufferedImage source=new BufferedImage(25,32,BufferedImage.TYPE_INT_ARGB);
		for(int y=0;y<32;y++) { source.setRGB(8,y,0xffffffff); source.setRGB(14,y,0xffffffff); }
		for(SpriteShadowType style:new SpriteShadowType[]{SpriteShadowType.HARD,SpriteShadowType.RADIAL})
		{
			SpriteShadowMask cast=capture(source,style).directionalCaster();
			assertTrue(alpha(cast,8,16)>0); assertTrue(alpha(cast,14,16)>0);
			assertEquals(0,alpha(cast,11,16));
			assertTrue(cast.offsetX>=6); assertTrue(cast.offsetX+cast.image.getWidth()<=17);
		}
	}
}
