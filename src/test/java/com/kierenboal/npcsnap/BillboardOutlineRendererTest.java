package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BillboardOutlineRendererTest
{
	@Test
	public void applyOutlineDrawsSolidExteriorOutline()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFFFFFFFF);

		BillboardOutlineRenderer.applyOutline(image, false, false, true, false, false, false, false, new Color(0xDDDDDD));

		assertEquals(0xFFDDDDDD, image.getRGB(2, 1));
		assertEquals(0xFFDDDDDD, image.getRGB(1, 2));
		assertEquals(0xFFDDDDDD, image.getRGB(3, 2));
		assertEquals(0xFFDDDDDD, image.getRGB(2, 3));
		assertEquals(0x00000000, image.getRGB(1, 1));
		assertEquals(0x00000000, image.getRGB(3, 3));
		assertEquals(0xFFFFFFFF, image.getRGB(2, 2));
		assertEquals(0x00000000, image.getRGB(0, 0));
	}

	@Test
	public void applyOutlineDoesNotFillInteriorTransparentHoles()
	{
		BufferedImage image = new BufferedImage(7, 7, BufferedImage.TYPE_INT_ARGB);
		for (int y = 1; y <= 5; y++)
		{
			for (int x = 1; x <= 5; x++)
			{
				if (x == 3 && y == 3)
				{
					continue;
				}

				image.setRGB(x, y, 0xFFFFFFFF);
			}
		}

		BillboardOutlineRenderer.applyOutline(image, false, false, true, false, false, false, false, new Color(0xDDDDDD));

		assertEquals(0x00000000, image.getRGB(3, 3));
		assertEquals(0xFFDDDDDD, image.getRGB(0, 3));
	}

	@Test
	public void applyOutlineBuildsShadowOutlineFromNeighborColors()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFFFF0000);

		BillboardOutlineRenderer.applyOutline(image, false, true, false, false, false, false, false, new Color(0xDDDDDD));

		assertEquals(0xFFAB0000, image.getRGB(2, 1));
		assertEquals(0xFFFF0000, image.getRGB(2, 2));
	}

	@Test
	public void applyOutlineDrawsSolidInteriorInline()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFFFFFFFF);

		BillboardOutlineRenderer.applyOutline(image, false, false, false, false, false, false, true, new Color(0xDDDDDD));

		assertEquals(0xFFDDDDDD, image.getRGB(2, 2));
		assertEquals(0x00000000, image.getRGB(2, 1));
	}

	@Test
	public void applyOutlineBuildsShadowInteriorInlineFromNeighborColors()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFFFF0000);
		image.setRGB(2, 3, 0xFF00FF00);

		BillboardOutlineRenderer.applyOutline(image, false, false, false, false, false, true, false, new Color(0xDDDDDD));

		assertEquals(0xFF00AB00, image.getRGB(2, 2));
		assertEquals(0xFFAB0000, image.getRGB(2, 3));
	}

	@Test
	public void applyOutlineSupportsExteriorAndInteriorAtSameTime()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFFFFFFFF);

		BillboardOutlineRenderer.applyOutline(image, false, false, true, false, false, false, true, new Color(0xDDDDDD));

		assertEquals(0xFFDDDDDD, image.getRGB(2, 2));
		assertEquals(0xFFDDDDDD, image.getRGB(2, 1));
	}

	@Test
	public void applyOutlineBuildsHighlightOutlineFromNeighborColors()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFF804000);

		BillboardOutlineRenderer.applyOutline(image, true, false, false, false, false, false, false, new Color(0xDDDDDD));

		assertEquals(0xFFAA5500, image.getRGB(2, 1));
		assertEquals(0xFF804000, image.getRGB(2, 2));
	}

	@Test
	public void applyOutlineBuildsHighlightInlineFromNeighborColors()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFFFF0000);
		image.setRGB(2, 3, 0xFF00FF00);

		BillboardOutlineRenderer.applyOutline(image, false, false, false, false, true, false, false, new Color(0xDDDDDD));

		assertEquals(0xFF00FF00, image.getRGB(2, 2));
		assertEquals(0xFFFF0000, image.getRGB(2, 3));
	}

	@Test
	public void applyOutlineDrawsSpriteShadowOnlyInTransparentPixels()
	{
		BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(10, 19, 0xFFFFFFFF);

		BillboardOutlineRenderer.applyOutline(image, false, false, false, true, false, false, false, new Color(0xDDDDDD));

		assertEquals(0xFFFFFFFF, image.getRGB(10, 19));
		assertEquals(0x3C000000, image.getRGB(9, 19));
		assertEquals(0x00000000, image.getRGB(10, 18));
	}

	@Test
	public void applyOutlinePreservesSolidOutlineAlpha()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFFFFFFFF);

		BillboardOutlineRenderer.applyOutline(image, false, false, true, false, false, false, false, new Color(0x90FF0000, true));

		assertEquals(0x90FF0000, image.getRGB(2, 1));
	}

	@Test
	public void applyOutlinePreservesNeighborAlphaForGeneratedColors()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0x80804000);

		BillboardOutlineRenderer.applyOutline(image, true, false, false, false, false, false, false, new Color(0xDDDDDD));

		assertEquals(0x80AA5500, image.getRGB(2, 1));
	}

	@Test
	public void applyOutlineUsesHighestPriorityExteriorColorOnSingleFootprint()
	{
		BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(2, 2, 0xFF804000);

		BillboardOutlineRenderer.applyOutline(image, true, true, true, false, false, false, false, new Color(0x9000FF00, true));

		assertEquals(0xFFAA5500, image.getRGB(2, 1));
		assertEquals(0x00000000, image.getRGB(1, 1));
	}

	@Test
	public void captureExteriorBoundaryIndicesDoesNotLeakStaleIndicesFromLargerImage()
	{
		BillboardOutlineRenderer.Scratch scratch = new BillboardOutlineRenderer.Scratch();

		BufferedImage largeImage = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		largeImage.setRGB(4, 4, 0xFFFFFFFF);
		BillboardOutlineRenderer.captureExteriorBoundaryIndices(largeImage, scratch);

		BufferedImage smallImage = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		smallImage.setRGB(1, 1, 0xFFFFFFFF);
		int[] indices = BillboardOutlineRenderer.captureExteriorBoundaryIndices(smallImage, scratch);

		for (int index : indices)
		{
			if (index < 0 || index >= 9)
			{
				throw new AssertionError("Boundary index leaked past current image: " + index);
			}
		}
	}
}
