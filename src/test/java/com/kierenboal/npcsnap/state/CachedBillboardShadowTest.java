package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardOutlineRenderer;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.*;

public class CachedBillboardShadowTest
{
	@Test
	public void pitchChangesReusePresentationBufferWithoutAccumulatingShadowsOrChangingCapture()
	{
		BufferedImage original = new BufferedImage(30, 40, BufferedImage.TYPE_INT_ARGB);
		// Floating arm and feet give the enlarged band visible pixels to shadow.
		original.setRGB(8, 15, 0xFFFF0000);
		original.setRGB(15, 36, 0xFF00FF00);
		CachedBillboard cached = new CachedBillboard(null, new Rectangle(0, 0, 30, 40), original, 123);
		BillboardOutlineRenderer.Scratch scratch = new BillboardOutlineRenderer.Scratch();
		BufferedImage result = cached.withSpriteShadow(0.10, scratch);
		int[] lowPitch = result.getRGB(0, 0, 30, 40, null, 0, 30);
		assertSame(result, cached.withSpriteShadow(0.80, scratch));
		assertTrue((result.getRGB(8, 16) >>> 24) > 0);
		assertEquals(0, lowPitch[16 * 30 + 8]);
		assertEquals(0xFFFF0000, result.getRGB(8, 15));
		assertEquals(0xFF00FF00, result.getRGB(15, 36));
		assertSame(result, cached.withSpriteShadow(0.10, scratch));
		assertArrayEquals(lowPitch, result.getRGB(0, 0, 30, 40, null, 0, 30));
		assertEquals(0, original.getRGB(8, 16));
		assertEquals(123, cached.lastRedrawMillis());
	}
}
