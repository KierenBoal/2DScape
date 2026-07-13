package com.kierenboal.npcsnap.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BillboardTriangleRasterizerTest
{
	@Test
	public void rasterizeSolidTriangleFillsCoveredPixels()
	{
		int[] pixels = new int[16];

		BillboardTriangleRasterizer.rasterizeSolidTriangle(pixels, 4, 4, 0f, 0f, 3f, 0f, 0f, 3f, 0xFFFF0000);

		assertEquals(0xFFFF0000, pixels[0]);
		assertNotEquals(0, pixels[1]);
		assertEquals(0, pixels[15]);
	}

	@Test
	public void rasterizeSolidTriangleBlendsTransparentPixels()
	{
		int[] pixels = new int[4];
		pixels[0] = 0xFF0000FF;

		BillboardTriangleRasterizer.rasterizeSolidTriangle(pixels, 2, 2, 0f, 0f, 2f, 0f, 0f, 2f, 0x80FF0000);

		assertEquals(0xFF80007F, pixels[0]);
	}

	@Test
	public void rasterizeSolidTriangleSkipsDegenerateTriangles()
	{
		int[] pixels = new int[9];

		BillboardTriangleRasterizer.rasterizeSolidTriangle(pixels, 3, 3, 1f, 1f, 1f, 1f, 1f, 1f, 0xFFFFFFFF);

		for (int pixel : pixels)
		{
			assertEquals(0, pixel);
		}
	}

	@Test
	public void rasterizeSolidTriangleClipsAtImageBounds()
	{
		int[] pixels = new int[9];

		BillboardTriangleRasterizer.rasterizeSolidTriangle(pixels, 3, 3, -4f, -4f, 2f, 0f, 0f, 2f, 0xFFFFFFFF);

		assertNotEquals(0, pixels[0]);
		assertEquals(0, pixels[8]);
	}
}
