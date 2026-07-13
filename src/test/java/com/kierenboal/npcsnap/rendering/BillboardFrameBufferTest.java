package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionMask;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BillboardFrameBufferTest
{
	@Test
	public void blitsOpaqueAndTransparentPixelsIntoViewportBuffer()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(2, 2);
		BufferedImage source = image(2, 2, new int[] {
			0xFFFF0000, 0,
			0x8000FF00, 0xFF0000FF
		});

		buffer.blit(draw(source, new Rectangle(0, 0, 2, 2), 1), 0, 0, 2, 2, false);

		assertEquals(0xFFFF0000, buffer.image().getRGB(0, 0));
		assertEquals(0, buffer.image().getRGB(1, 0));
		assertEquals(0x8000FF00, buffer.image().getRGB(0, 1));
		assertEquals(0xFF0000FF, buffer.image().getRGB(1, 1));
	}

	@Test
	public void higherOpaquePaintOrderProtectsDestinationPixel()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(1, 1);
		buffer.blit(draw(image(1, 1, new int[] {0xFFFF0000}), new Rectangle(0, 0, 1, 1), 10), 0, 0, 1, 1, false);
		buffer.blit(draw(image(1, 1, new int[] {0xFF0000FF}), new Rectangle(0, 0, 1, 1), 5), 0, 0, 1, 1, false);

		assertEquals(0xFFFF0000, buffer.image().getRGB(0, 0));
	}

	@Test
	public void transparentForegroundBlendsOverFartherOpaquePixel()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(1, 1);
		buffer.blit(draw(image(1, 1, new int[] {0x80FF0000}), new Rectangle(0, 0, 1, 1), 10), 0, 0, 1, 1, false);
		buffer.blit(draw(image(1, 1, new int[] {0xFF0000FF}), new Rectangle(0, 0, 1, 1), 5), 0, 0, 1, 1, false);

		assertEquals(0xFF80007F, buffer.image().getRGB(0, 0));
	}

	@Test
	public void fartherTransparentPixelCannotDrawOverNearerOpaquePixel()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(1, 1);
		buffer.blit(draw(image(1, 1, new int[] {0xFFFF0000}), new Rectangle(0, 0, 1, 1), 10), 0, 0, 1, 1, false);
		buffer.blit(draw(image(1, 1, new int[] {0x8000FF00}), new Rectangle(0, 0, 1, 1), 5), 0, 0, 1, 1, false);

		assertEquals(0xFFFF0000, buffer.image().getRGB(0, 0));
	}

	@Test
	public void multipleTransparentLayersCombineNearestToFarthest()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(1, 1);
		buffer.blit(draw(image(1, 1, new int[] {0x80FF0000}), new Rectangle(0, 0, 1, 1), 10), 0, 0, 1, 1, false);
		buffer.blit(draw(image(1, 1, new int[] {0x8000FF00}), new Rectangle(0, 0, 1, 1), 8), 0, 0, 1, 1, false);
		buffer.blit(draw(image(1, 1, new int[] {0xFF0000FF}), new Rectangle(0, 0, 1, 1), 5), 0, 0, 1, 1, false);

		assertEquals(0xFF7F3F40, buffer.image().getRGB(0, 0));
	}

	@Test
	public void beginClearsReusedBuffersAndResizeRecreatesThem()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(1, 1);
		buffer.blit(draw(image(1, 1, new int[] {0xFFFFFFFF}), new Rectangle(0, 0, 1, 1), 1), 0, 0, 1, 1, false);
		buffer.begin(1, 1);
		assertEquals(0, buffer.image().getRGB(0, 0));

		buffer.begin(3, 2);
		assertEquals(3, buffer.image().getWidth());
		assertEquals(2, buffer.image().getHeight());
	}

	@Test
	public void clippingUsesCanvasViewportOffsets()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(2, 2);
		BufferedImage source = image(2, 2, new int[] {
			0xFFFF0000, 0xFF00FF00,
			0xFF0000FF, 0xFFFFFFFF
		});

		buffer.blit(draw(source, new Rectangle(9, 19, 2, 2), 1), 10, 20, 2, 2, false);

		assertEquals(0xFFFFFFFF, buffer.image().getRGB(0, 0));
		assertEquals(0, buffer.image().getRGB(1, 1));
	}

	@Test
	public void ignoresNullInvalidAndOffscreenDraws()
	{
		BillboardFrameBuffer buffer = buffer();
		buffer.begin(1, 1);
		buffer.blit(null, 0, 0, 1, 1, false);
		buffer.blit(new PreparedBillboardDraw(null, null, 0), 0, 0, 1, 1, false);
		buffer.blit(draw(image(1, 1, new int[] {0xFFFFFFFF}), new Rectangle(5, 5, 1, 1), 1), 0, 0, 1, 1, false);

		assertEquals(0, buffer.image().getRGB(0, 0));
	}

	private static BillboardFrameBuffer buffer()
	{
		return new BillboardFrameBuffer(
			new BillboardOcclusionMask(),
			new BillboardPerformanceMetrics(),
			draw -> BillboardDepthSurface.invalid());
	}

	private static PreparedBillboardDraw draw(BufferedImage image, Rectangle bounds, int paintOrder)
	{
		return new PreparedBillboardDraw(
			null,
			new BillboardRenderResult(bounds, image, new Rectangle(0, 0, image.getWidth(), image.getHeight())),
			paintOrder);
	}

	private static BufferedImage image(int width, int height, int[] pixels)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, height, pixels, 0, width);
		return image;
	}
}
