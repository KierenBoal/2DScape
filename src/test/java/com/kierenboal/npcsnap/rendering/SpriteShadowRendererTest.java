package com.kierenboal.npcsnap.rendering;
import com.kierenboal.npcsnap.SpriteShadowType;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpriteShadowRendererTest
{
	@Test public void matchesPythonFixtures() throws Exception
	{
		BufferedImage sprite = ImageIO.read(getClass().getResource("/shadows/sprite.png"));
		for (SpriteShadowType type : new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.RADIAL})
		{
			BufferedImage expected = ImageIO.read(getClass().getResource("/shadows/"+(type==SpriteShadowType.FLAT ? "flat" : "radial")+".png"));
			BufferedImage actual = SpriteShadowRenderer.render(sprite,type);
			assertEquals(expected.getWidth(),actual.getWidth()); assertEquals(expected.getHeight(),actual.getHeight());
			assertArrayEquals(type.toString(),expected.getRGB(0,0,actual.getWidth(),actual.getHeight(),null,0,actual.getWidth()),actual.getRGB(0,0,actual.getWidth(),actual.getHeight(),null,0,actual.getWidth()));
		}
	}
	@Test public void alphaOnlyDeterministicAndCompositedBehindSource() throws Exception
	{
		BufferedImage sprite = ImageIO.read(getClass().getResource("/shadows/sprite.png"));
		BufferedImage recolored = new BufferedImage(sprite.getWidth(),sprite.getHeight(),BufferedImage.TYPE_INT_ARGB);
		for (int y=0;y<sprite.getHeight();y++) for(int x=0;x<sprite.getWidth();x++)
			recolored.setRGB(x,y,(sprite.getRGB(x,y)&0xff000000)|0xff00ff);
		SpriteShadowRenderer.Scratch scratch = new SpriteShadowRenderer.Scratch();
		for (SpriteShadowType type : new SpriteShadowType[]{SpriteShadowType.FLAT,SpriteShadowType.RADIAL})
		{
			BufferedImage a = SpriteShadowRenderer.render(sprite,type,scratch);
			BufferedImage b = SpriteShadowRenderer.render(recolored,type,scratch);
			assertArrayEquals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));
			BufferedImage dest = new BufferedImage(sprite.getWidth()+20,sprite.getHeight()+20,BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = dest.createGraphics();
			try { g.drawImage(sprite,10,10,null); } finally { g.dispose(); }
			SpriteShadowRenderer.compositeInPlace(dest,type);
			for(int y=0;y<sprite.getHeight();y++) for(int x=0;x<sprite.getWidth();x++)
				if((sprite.getRGB(x,y)>>>24)==255) assertEquals(sprite.getRGB(x,y),dest.getRGB(x+10,y+10));
			BufferedImage partial = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
			partial.setRGB(0,0,0x80ff0000);
			SpriteShadowRenderer.compositeInPlace(partial,type);
			assertTrue((partial.getRGB(0,0) >>> 24) >= 128);
			assertTrue(((partial.getRGB(0,0) >> 16) & 255) > ((partial.getRGB(0,0) >> 8) & 255));
		}
	}
	@Test public void emptyAndTinySpritesAreSafe()
	{
		for (SpriteShadowType type : SpriteShadowType.values())
		{
			BufferedImage sprite = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
			assertEquals(7,SpriteShadowRenderer.render(sprite,type).getWidth());
			sprite.setRGB(0,0,0xffffffff);
			SpriteShadowRenderer.render(sprite,type);
		}
	}
}
