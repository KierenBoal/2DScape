package com.kierenboal.npcspan;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

final class BillboardOutlineRenderer
{
	private static final int[] CARDINAL_X = {-1, 1, 0, 0};
	private static final int[] CARDINAL_Y = {0, 0, -1, 1};
	private static final int[] ADJACENT_X = {-1, 0, 1, -1, 1, -1, 0, 1};
	private static final int[] ADJACENT_Y = {-1, -1, -1, 0, 0, 1, 1, 1};
	private static final double SHADOW_DARKEN_FACTOR = 0.67d;
	private static final double HIGHLIGHT_BRIGHTEN_FACTOR = 1.33d;

	private BillboardOutlineRenderer()
	{
	}

	static void applyOutline(
		BufferedImage image,
		boolean highlightOutline,
		boolean shadowOutline,
		boolean solidOutline,
		boolean highlightInline,
		boolean shadowInline,
		boolean solidInline,
		Color solidOutlineColor)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= 0 || height <= 0 || (!highlightOutline && !shadowOutline && !solidOutline && !highlightInline && !shadowInline && !solidInline))
		{
			return;
		}

		int[] sourcePixels = image.getRGB(0, 0, width, height, null, 0, width);
		int[] resultPixels = sourcePixels.clone();
		boolean[] visited = new boolean[width * height];
		boolean[] exteriorTransparentBoundary = new boolean[width * height];
		boolean[] exteriorOpaqueBoundary = new boolean[width * height];
		ArrayDeque<Integer> queue = new ArrayDeque<>();

		enqueueBorderTransparentPixels(sourcePixels, width, height, visited, queue);
		while (!queue.isEmpty())
		{
			int index = queue.removeFirst();
			int x = index % width;
			int y = index / width;
			if (hasOpaqueNeighbor(sourcePixels, width, height, x, y))
			{
				exteriorTransparentBoundary[index] = true;
				markOpaqueNeighbors(sourcePixels, width, height, x, y, exteriorOpaqueBoundary);
			}

			for (int i = 0; i < CARDINAL_X.length; i++)
			{
				int nextX = x + CARDINAL_X[i];
				int nextY = y + CARDINAL_Y[i];
				if (!isInBounds(width, height, nextX, nextY))
				{
					continue;
				}

				int nextIndex = nextY * width + nextX;
				if (visited[nextIndex] || isOpaque(sourcePixels[nextIndex]))
				{
					continue;
				}

				visited[nextIndex] = true;
				queue.addLast(nextIndex);
			}
		}

		applyExteriorBoundary(sourcePixels, resultPixels, width, height, exteriorTransparentBoundary, highlightOutline, shadowOutline, solidOutline, solidOutlineColor);
		applyInteriorBoundary(sourcePixels, resultPixels, width, height, exteriorOpaqueBoundary, highlightInline, shadowInline, solidInline, solidOutlineColor);
		image.setRGB(0, 0, width, height, resultPixels, 0, width);
	}

	private static void applyExteriorBoundary(
		int[] sourcePixels,
		int[] resultPixels,
		int width,
		int height,
		boolean[] exteriorTransparentBoundary,
		boolean highlightOutline,
		boolean shadowOutline,
		boolean solidOutline,
		Color solidOutlineColor)
	{
		for (int index = 0; index < exteriorTransparentBoundary.length; index++)
		{
			if (!exteriorTransparentBoundary[index])
			{
				continue;
			}

			int x = index % width;
			int y = index / width;
			int outlineArgb = highlightOutline
				? highlightOutlineColor(sourcePixels, width, height, x, y)
				: shadowOutline
					? shadowOutlineColor(sourcePixels, width, height, x, y)
					: solidOutline ? solidOutlineColor.getRGB() : 0;
			if (outlineArgb != 0)
			{
				resultPixels[index] = outlineArgb;
			}
		}
	}

	private static void applyInteriorBoundary(
		int[] sourcePixels,
		int[] resultPixels,
		int width,
		int height,
		boolean[] exteriorOpaqueBoundary,
		boolean highlightInline,
		boolean shadowInline,
		boolean solidInline,
		Color solidOutlineColor)
	{
		for (int index = 0; index < exteriorOpaqueBoundary.length; index++)
		{
			if (!exteriorOpaqueBoundary[index])
			{
				continue;
			}

			int x = index % width;
			int y = index / width;
			int inlineArgb = highlightInline
				? highlightInlineColor(sourcePixels, width, height, x, y)
				: shadowInline
					? shadowInlineColor(sourcePixels, width, height, x, y)
					: solidInline ? solidOutlineColor.getRGB() : 0;
			if (inlineArgb != 0)
			{
				resultPixels[index] = inlineArgb;
			}
		}
	}

	private static void enqueueBorderTransparentPixels(int[] pixels, int width, int height, boolean[] visited, ArrayDeque<Integer> queue)
	{
		for (int x = 0; x < width; x++)
		{
			enqueueTransparentPixel(pixels, width, 0, x, visited, queue);
			enqueueTransparentPixel(pixels, width, height - 1, x, visited, queue);
		}

		for (int y = 1; y < height - 1; y++)
		{
			enqueueTransparentPixel(pixels, width, y, 0, visited, queue);
			enqueueTransparentPixel(pixels, width, y, width - 1, visited, queue);
		}
	}

	private static void enqueueTransparentPixel(int[] pixels, int width, int y, int x, boolean[] visited, ArrayDeque<Integer> queue)
	{
		int index = y * width + x;
		if (visited[index] || isOpaque(pixels[index]))
		{
			return;
		}

		visited[index] = true;
		queue.addLast(index);
	}

	private static int shadowOutlineColor(int[] pixels, int width, int height, int x, int y)
	{
		return darkenedAverageOpaqueNeighborColor(pixels, width, height, x, y);
	}

	private static int highlightOutlineColor(int[] pixels, int width, int height, int x, int y)
	{
		return brightenedAverageOpaqueNeighborColor(pixels, width, height, x, y);
	}

	private static int shadowInlineColor(int[] pixels, int width, int height, int x, int y)
	{
		return darkenedAverageOpaqueNeighborColor(pixels, width, height, x, y);
	}

	private static int highlightInlineColor(int[] pixels, int width, int height, int x, int y)
	{
		return brightenedAverageOpaqueNeighborColor(pixels, width, height, x, y);
	}

	private static int darkenedAverageOpaqueNeighborColor(int[] pixels, int width, int height, int x, int y)
	{
		int color = averageOpaqueNeighborColor(pixels, width, height, x, y);
		if (color == 0)
		{
			return 0;
		}

		int avgRed = darken((color >>> 16) & 0xFF);
		int avgGreen = darken((color >>> 8) & 0xFF);
		int avgBlue = darken(color & 0xFF);
		return 0xFF000000 | (avgRed << 16) | (avgGreen << 8) | avgBlue;
	}

	private static int brightenedAverageOpaqueNeighborColor(int[] pixels, int width, int height, int x, int y)
	{
		int color = averageOpaqueNeighborColor(pixels, width, height, x, y);
		if (color == 0)
		{
			return 0;
		}

		int avgRed = brighten((color >>> 16) & 0xFF);
		int avgGreen = brighten((color >>> 8) & 0xFF);
		int avgBlue = brighten(color & 0xFF);
		return 0xFF000000 | (avgRed << 16) | (avgGreen << 8) | avgBlue;
	}

	private static int averageOpaqueNeighborColor(int[] pixels, int width, int height, int x, int y)
	{
		int red = 0;
		int green = 0;
		int blue = 0;
		int count = 0;
		for (int i = 0; i < ADJACENT_X.length; i++)
		{
			int neighborX = x + ADJACENT_X[i];
			int neighborY = y + ADJACENT_Y[i];
			if (!isInBounds(width, height, neighborX, neighborY))
			{
				continue;
			}

			int argb = pixels[(neighborY * width) + neighborX];
			if (!isOpaque(argb))
			{
				continue;
			}

			red += (argb >>> 16) & 0xFF;
			green += (argb >>> 8) & 0xFF;
			blue += argb & 0xFF;
			count++;
		}

		if (count == 0)
		{
			return 0;
		}

		int avgRed = clampToByte((int) Math.round(red / (double) count));
		int avgGreen = clampToByte((int) Math.round(green / (double) count));
		int avgBlue = clampToByte((int) Math.round(blue / (double) count));
		return (avgRed << 16) | (avgGreen << 8) | avgBlue;
	}

	private static boolean hasOpaqueNeighbor(int[] pixels, int width, int height, int x, int y)
	{
		for (int i = 0; i < ADJACENT_X.length; i++)
		{
			int neighborX = x + ADJACENT_X[i];
			int neighborY = y + ADJACENT_Y[i];
			if (!isInBounds(width, height, neighborX, neighborY))
			{
				continue;
			}

			if (isOpaque(pixels[(neighborY * width) + neighborX]))
			{
				return true;
			}
		}

		return false;
	}

	private static void markOpaqueNeighbors(int[] pixels, int width, int height, int x, int y, boolean[] exteriorOpaqueBoundary)
	{
		for (int i = 0; i < ADJACENT_X.length; i++)
		{
			int neighborX = x + ADJACENT_X[i];
			int neighborY = y + ADJACENT_Y[i];
			if (!isInBounds(width, height, neighborX, neighborY))
			{
				continue;
			}

			int index = (neighborY * width) + neighborX;
			if (isOpaque(pixels[index]))
			{
				exteriorOpaqueBoundary[index] = true;
			}
		}
	}

	private static int darken(double color)
	{
		return clampToByte((int) Math.round(color * SHADOW_DARKEN_FACTOR));
	}

	private static int brighten(double color)
	{
		return clampToByte((int) Math.round(color * HIGHLIGHT_BRIGHTEN_FACTOR));
	}

	private static int clampToByte(int value)
	{
		return Math.max(0, Math.min(255, value));
	}

	private static boolean isOpaque(int argb)
	{
		return ((argb >>> 24) & 0xFF) != 0;
	}

	private static boolean isInBounds(int width, int height, int x, int y)
	{
		return x >= 0 && x < width && y >= 0 && y < height;
	}
}
