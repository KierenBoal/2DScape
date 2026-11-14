package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.SpriteShadowType;
import com.kierenboal.npcsnap.ShadowLighting;
import com.kierenboal.npcsnap.rendering.*;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class CachedBillboardShadowTest
{
	@Test public void presentationChangesReuseMasksWithoutChangingCapture()
	{
		BufferedImage original = new BufferedImage(30,40,BufferedImage.TYPE_INT_ARGB);
		original.setRGB(8,15,0xffff0000); original.setRGB(15,36,0xff00ff00);
		for(SpriteShadowType type:SpriteShadowType.values())
		{
			if(type==SpriteShadowType.OFF) continue;
			SpriteShadowMask mask=SpriteShadowRenderer.capture(original,type,new SpriteShadowRenderer.Scratch(),new BillboardOutlineRenderer.Scratch(),0.1);
			CachedBillboard cached=new CachedBillboard(null,new Rectangle(0,0,30,40),new Rectangle(0,0,30,40),new SpriteCapture(original,mask),123,null,null);
			BufferedImage tint=mask.tinted(Color.BLACK);
			for(ShadowLighting light:ShadowLighting.values())
			{
				ShadowProjection.downward(mask,BillboardDrawGeometry.rectangular(new Rectangle(0,0,90,120)),light);
				assertSame(tint,mask.tinted(Color.MAGENTA)); assertSame(tint,mask.tinted(Color.BLACK));
				assertSame(mask,cached.shadow); assertEquals(123,cached.lastRedrawMillis());
				assertEquals(0xffff0000,original.getRGB(8,15)); assertEquals(0,original.getRGB(8,16));
			}
		}
	}
}
