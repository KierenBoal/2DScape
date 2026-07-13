package com.kierenboal.npcsnap.rendering;

public final class BillboardTriangleRasterizer
{
	private static final float MIN_TRIANGLE_AREA = 1.0e-6f;

	private BillboardTriangleRasterizer()
	{
	}

	public static void rasterizeSolidTriangle(
		int[] pixels,
		int width,
		int height,
		float x0,
		float y0,
		float x1,
		float y1,
		float x2,
		float y2,
		int argb)
	{
		if (((argb >>> 24) & 0xFF) == 0)
		{
			return;
		}

		float area = edge(x0, y0, x1, y1, x2, y2);
		if (Math.abs(area) < MIN_TRIANGLE_AREA)
		{
			return;
		}

		int minX = clampRasterCoordinate((int) Math.floor(Math.min(x0, Math.min(x1, x2))), width);
		int maxX = clampRasterCoordinate((int) Math.ceil(Math.max(x0, Math.max(x1, x2))), width);
		int minY = clampRasterCoordinate((int) Math.floor(Math.min(y0, Math.min(y1, y2))), height);
		int maxY = clampRasterCoordinate((int) Math.ceil(Math.max(y0, Math.max(y1, y2))), height);
		if (minX > maxX || minY > maxY)
		{
			return;
		}

		for (int y = minY; y <= maxY; y++)
		{
			float py = y + 0.5f;
			int row = y * width;
			for (int x = minX; x <= maxX; x++)
			{
				float px = x + 0.5f;
				float w0 = edge(x1, y1, x2, y2, px, py) / area;
				float w1 = edge(x2, y2, x0, y0, px, py) / area;
				float w2 = 1.0f - w0 - w1;
				if (w0 < 0f || w1 < 0f || w2 < 0f)
				{
					continue;
				}

				int index = row + x;
				pixels[index] = blendPixel(pixels[index], argb);
			}
		}
	}

	public static int blendPixel(int destination, int source)
	{
		int sourceAlpha = (source >>> 24) & 0xFF;
		if (sourceAlpha <= 0)
		{
			return destination;
		}

		if (sourceAlpha >= 0xFF)
		{
			return source;
		}

		int destinationAlpha = (destination >>> 24) & 0xFF;
		int outAlpha = sourceAlpha + ((destinationAlpha * (255 - sourceAlpha)) / 255);
		if (outAlpha <= 0)
		{
			return 0;
		}

		int inverseAlpha = 255 - sourceAlpha;
		int sourceRed = (source >> 16) & 0xFF;
		int sourceGreen = (source >> 8) & 0xFF;
		int sourceBlue = source & 0xFF;
		int destinationRed = (destination >> 16) & 0xFF;
		int destinationGreen = (destination >> 8) & 0xFF;
		int destinationBlue = destination & 0xFF;
		int outRed = ((sourceRed * sourceAlpha) + (destinationRed * destinationAlpha * inverseAlpha / 255)) / outAlpha;
		int outGreen = ((sourceGreen * sourceAlpha) + (destinationGreen * destinationAlpha * inverseAlpha / 255)) / outAlpha;
		int outBlue = ((sourceBlue * sourceAlpha) + (destinationBlue * destinationAlpha * inverseAlpha / 255)) / outAlpha;
		return (outAlpha << 24) | (outRed << 16) | (outGreen << 8) | outBlue;
	}

	public static float edge(float ax, float ay, float bx, float by, float px, float py)
	{
		return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
	}

	public static int clampRasterCoordinate(int coordinate, int dimension)
	{
		return Math.max(0, Math.min(dimension - 1, coordinate));
	}
}
