package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BillboardFaceRasterizerTest
{
	@Test
	public void solidFaceUsesImageOriginAndQualityScale()
	{
		int[] pixels = new int[16];
		FaceDraw face = face(10, 20, 12, 20, 10, 22, null, null);

		BillboardFaceRasterizer.rasterizeSolidFace(
			pixels, 4, 4, new Rectangle(10, 20, 2, 2), 1.5d, face, 0xFFFF0000);

		assertEquals(0xFFFF0000, pixels[0]);
		assertNotEquals(0, pixels[1]);
		assertEquals(0, pixels[15]);
	}

	@Test
	public void texturedFaceSamplesAndShadesTexture()
	{
		int[] pixels = new int[4];
		TextureCacheEntry entry = new TextureCacheEntry(new int[] {
			0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF
		}, 2, 2, 0L);
		TextureSample sample = new TextureSample(entry, 0f, 0f, 1);
		TextureUvs uvs = new TextureUvs(0f, 0f, 1f, 0f, 0f, 1f);
		FaceDraw face = face(0, 0, 2, 0, 0, 2, sample, uvs);

		BillboardFaceRasterizer.rasterizeTexturedFace(
			pixels, 2, 2, new Rectangle(0, 0, 2, 2), 1d, face, 16);

		assertNotEquals(0, pixels[0]);
	}

	@Test
	public void texturedFaceSkipsMissingTextureDegenerateFaceAndTransparentTexel()
	{
		int[] pixels = new int[4];
		BillboardFaceRasterizer.rasterizeTexturedFace(
			pixels, 2, 2, new Rectangle(), 1d, face(0, 0, 2, 0, 0, 2, null, null), 16);

		TextureCacheEntry transparent = new TextureCacheEntry(new int[] {0, 0, 0, 0}, 2, 2, 0L);
		TextureSample sample = new TextureSample(transparent, 0f, 0f, 1);
		TextureUvs uvs = new TextureUvs(0f, 0f, 1f, 0f, 0f, 1f);
		BillboardFaceRasterizer.rasterizeTexturedFace(
			pixels, 2, 2, new Rectangle(), 1d, face(1, 1, 1, 1, 1, 1, sample, uvs), 16);
		BillboardFaceRasterizer.rasterizeTexturedFace(
			pixels, 2, 2, new Rectangle(), 1d, face(0, 0, 2, 0, 0, 2, sample, uvs), 16);

		for (int pixel : pixels)
		{
			assertEquals(0, pixel);
		}
	}

	private static FaceDraw face(
		int x0, int y0, int x1, int y1, int x2, int y2,
		TextureSample sample, TextureUvs uvs)
	{
		return new FaceDraw(x0, y0, x1, y1, x2, y2, Color.WHITE, 1d, sample, uvs);
	}
}
