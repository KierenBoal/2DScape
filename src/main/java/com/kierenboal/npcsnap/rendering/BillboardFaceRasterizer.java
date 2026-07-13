package com.kierenboal.npcsnap.rendering;

import java.awt.Color;
import java.awt.Rectangle;

public final class BillboardFaceRasterizer
{
	private BillboardFaceRasterizer()
	{
	}

	public static void rasterizeTexturedFace(
		int[] imagePixels,
		int imageWidth,
		int imageHeight,
		Rectangle imageBounds,
		double qualityScale,
		FaceDraw face,
		int colorBands)
	{
		TextureSample textureSample = face.getTextureSample();
		if (textureSample == null)
		{
			return;
		}

		float x0 = scaleCoordinate(face.x0, imageBounds.x, qualityScale);
		float y0 = scaleCoordinate(face.y0, imageBounds.y, qualityScale);
		float x1 = scaleCoordinate(face.x1, imageBounds.x, qualityScale);
		float y1 = scaleCoordinate(face.y1, imageBounds.y, qualityScale);
		float x2 = scaleCoordinate(face.x2, imageBounds.x, qualityScale);
		float y2 = scaleCoordinate(face.y2, imageBounds.y, qualityScale);

		float area = BillboardTriangleRasterizer.edge(x0, y0, x1, y1, x2, y2);
		if (Math.abs(area) < 1.0e-6f)
		{
			return;
		}

		int minX = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.floor(Math.min(x0, Math.min(x1, x2))), imageWidth);
		int maxX = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.ceil(Math.max(x0, Math.max(x1, x2))), imageWidth);
		int minY = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.floor(Math.min(y0, Math.min(y1, y2))), imageHeight);
		int maxY = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.ceil(Math.max(y0, Math.max(y1, y2))), imageHeight);
		if (minX > maxX || minY > maxY)
		{
			return;
		}

		Color shade = NpcSnapColorBanding.snapToRamp(face.getColor(), colorBands);
		TextureUvs textureUvs = face.getTextureUvs();
		float inverseArea = 1.0f / area;
		float w0StepX = (y2 - y1) * inverseArea;
		float w1StepX = (y0 - y2) * inverseArea;
		float sampleX = minX + 0.5f;
		float rowW0 = BillboardTriangleRasterizer.edge(x1, y1, x2, y2, sampleX, minY + 0.5f) * inverseArea;
		float rowW1 = BillboardTriangleRasterizer.edge(x2, y2, x0, y0, sampleX, minY + 0.5f) * inverseArea;
		float w0StepY = (x1 - x2) * inverseArea;
		float w1StepY = (x2 - x0) * inverseArea;
		for (int y = minY; y <= maxY; y++)
		{
			int row = y * imageWidth;
			float w0 = rowW0;
			float w1 = rowW1;
			for (int x = minX; x <= maxX; x++)
			{
				float w2 = 1.0f - w0 - w1;
				if (w0 >= 0f && w1 >= 0f && w2 >= 0f)
				{
					float u = (float) BillboardGeometryUtils.wrapUnit((w0 * textureUvs.u0) + (w1 * textureUvs.u1) + (w2 * textureUvs.u2) + textureSample.uOffset);
					float v = (float) BillboardGeometryUtils.wrapUnit((w0 * textureUvs.v0) + (w1 * textureUvs.v1) + (w2 * textureUvs.v2) + textureSample.vOffset);
					int textureX = Math.min(textureSample.entry.width - 1, (int) (u * textureSample.entry.width));
					int textureY = Math.min(textureSample.entry.height - 1, (int) (v * textureSample.entry.height));
					int samplePixel = textureSample.entry.pixels[(textureY * textureSample.entry.width) + textureX];
					if ((samplePixel >>> 24) != 0 || (samplePixel & 0xFFFFFF) != 0)
					{
						int shadedPixel = BillboardColorUtils.modulateTexturePixel(samplePixel, shade, colorBands);
						int pixelIndex = row + x;
						imagePixels[pixelIndex] = BillboardTriangleRasterizer.blendPixel(imagePixels[pixelIndex], shadedPixel);
					}
				}

				w0 += w0StepX;
				w1 += w1StepX;
			}
			rowW0 += w0StepY;
			rowW1 += w1StepY;
		}
	}

	public static void rasterizeSolidFace(
		int[] imagePixels,
		int imageWidth,
		int imageHeight,
		Rectangle imageBounds,
		double qualityScale,
		FaceDraw face,
		int argb)
	{
		BillboardTriangleRasterizer.rasterizeSolidTriangle(
			imagePixels,
			imageWidth,
			imageHeight,
			scaleCoordinate(face.x0, imageBounds.x, qualityScale),
			scaleCoordinate(face.y0, imageBounds.y, qualityScale),
			scaleCoordinate(face.x1, imageBounds.x, qualityScale),
			scaleCoordinate(face.y1, imageBounds.y, qualityScale),
			scaleCoordinate(face.x2, imageBounds.x, qualityScale),
			scaleCoordinate(face.y2, imageBounds.y, qualityScale),
			argb
		);
	}

	private static float scaleCoordinate(int coordinate, int origin, double qualityScale)
	{
		return (float) ((coordinate - origin) * qualityScale);
	}
}
