package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.ArrayDeque;

final class BillboardOutlineRenderer
{
	private static final int[] CARDINAL_X = {-1, 1, 0, 0};
	private static final int[] CARDINAL_Y = {0, 0, -1, 1};
	private static final int[] ADJACENT_X = {-1, 0, 1, -1, 1, -1, 0, 1};
	private static final int[] ADJACENT_Y = {-1, -1, -1, 0, 0, 1, 1, 1};
	private static final double SHADOW_DARKEN_FACTOR = 0.67d;
	private static final double HIGHLIGHT_BRIGHTEN_FACTOR = 1.33d;
	private static final int SPRITE_SHADOW_ALPHA = 80;
	private static final int SPRITE_SHADOW_MIN_SPREAD = 2;
	private static final double SPRITE_SHADOW_HEIGHT_RATIO = 0.05d;

	private BillboardOutlineRenderer()
	{
	}

	static void applyOutline(
		BufferedImage image,
		boolean highlightOutline,
		boolean shadowOutline,
		boolean solidOutline,
		boolean spriteShadow,
		boolean highlightInline,
		boolean shadowInline,
		boolean solidInline,
		Color solidOutlineColor)
	{
		applyOutline(image, new Scratch(), highlightOutline, shadowOutline, solidOutline, spriteShadow, highlightInline, shadowInline, solidInline, solidOutlineColor);
	}

	static void applyOutline(
		BufferedImage image,
		Scratch scratch,
		boolean highlightOutline,
		boolean shadowOutline,
		boolean solidOutline,
		boolean spriteShadow,
		boolean highlightInline,
		boolean shadowInline,
		boolean solidInline,
		Color solidOutlineColor)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= 0 || height <= 0 || (!highlightOutline && !shadowOutline && !solidOutline && !spriteShadow && !highlightInline && !shadowInline && !solidInline))
		{
			return;
		}

		if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return;
		}

		int pixelCount = width * height;
		int[] sourcePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		scratch.ensureCapacity(pixelCount);
		System.arraycopy(sourcePixels, 0, scratch.resultPixels, 0, pixelCount);
		populateBoundaryMasks(sourcePixels, width, height, scratch);

		applyInteriorBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorOpaqueBoundary, false, false, solidInline, solidOutlineColor);
		applyInteriorBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorOpaqueBoundary, false, shadowInline, false, solidOutlineColor);
		applyInteriorBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorOpaqueBoundary, highlightInline, false, false, solidOutlineColor);
		applyExteriorBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorTransparentBoundary, false, false, solidOutline, solidOutlineColor);
		applyExteriorBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorTransparentBoundary, false, shadowOutline, false, solidOutlineColor);
		applyExteriorBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorTransparentBoundary, highlightOutline, false, false, solidOutlineColor);
		if (spriteShadow)
		{
			applySpriteShadow(sourcePixels, scratch.resultPixels, width, height);
		}
		System.arraycopy(scratch.resultPixels, 0, sourcePixels, 0, pixelCount);
	}

	static int[] captureExteriorBoundaryIndices(BufferedImage image, Scratch scratch)
	{
		if (image == null)
		{
			return new int[0];
		}

		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= 0 || height <= 0)
		{
			return new int[0];
		}

		if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return new int[0];
		}

		int[] sourcePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int pixelCount = width * height;
		scratch.ensureCapacity(pixelCount);
		populateBoundaryMasks(sourcePixels, width, height, scratch);
		int count = 0;
		for (int index = 0; index < pixelCount; index++)
		{
			if (scratch.exteriorTransparentBoundary[index])
			{
				count++;
			}
		}

		int[] indices = new int[count];
		int next = 0;
		for (int index = 0; index < pixelCount; index++)
		{
			if (scratch.exteriorTransparentBoundary[index])
			{
				indices[next++] = index;
			}
		}

		return indices;
	}

	static void drawDynamicOutline(Graphics2D graphics, BufferedImage image, Rectangle drawRect, int[] exteriorBoundaryIndices, Color outlineColor)
	{
		if (graphics == null || image == null || drawRect == null || outlineColor == null || outlineColor.getAlpha() == 0 || exteriorBoundaryIndices == null || exteriorBoundaryIndices.length == 0)
		{
			return;
		}

		int width = image.getWidth();
		int height = image.getHeight();
		if (width <= 0 || height <= 0 || drawRect.width <= 0 || drawRect.height <= 0)
		{
			return;
		}

		graphics.setColor(outlineColor);
		for (int index : exteriorBoundaryIndices)
		{
			int x = index % width;
			int y = index / width;
			int minX = drawRect.x + (int) Math.floor((x * (double) drawRect.width) / width);
			int maxX = drawRect.x + (int) Math.ceil(((x + 1) * (double) drawRect.width) / width);
			int minY = drawRect.y + (int) Math.floor((y * (double) drawRect.height) / height);
			int maxY = drawRect.y + (int) Math.ceil(((y + 1) * (double) drawRect.height) / height);
			if (maxX <= minX || maxY <= minY)
			{
				continue;
			}

			graphics.fillRect(minX, minY, maxX - minX, maxY - minY);
		}
	}

	static final class Scratch
	{
		private int[] resultPixels = new int[0];
		private boolean[] visited = new boolean[0];
		private boolean[] exteriorTransparentBoundary = new boolean[0];
		private boolean[] exteriorOpaqueBoundary = new boolean[0];
		private final ArrayDeque<Integer> queue = new ArrayDeque<>();

		private void ensureCapacity(int capacity)
		{
			if (resultPixels.length >= capacity)
			{
				return;
			}

			resultPixels = new int[capacity];
			visited = new boolean[capacity];
			exteriorTransparentBoundary = new boolean[capacity];
			exteriorOpaqueBoundary = new boolean[capacity];
		}
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

	private static void applySpriteShadow(int[] sourcePixels, int[] resultPixels, int width, int height)
	{
		int shadowBandHeight = Math.max(1, (int) Math.ceil(height * SPRITE_SHADOW_HEIGHT_RATIO));
		int shadowBandStartY = Math.max(0, height - shadowBandHeight);
		int minX = width;
		int maxX = -1;
		int[] columnBottoms = new int[width];
		Arrays.fill(columnBottoms, -1);

		for (int y = shadowBandStartY; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int argb = sourcePixels[(y * width) + x];
				if (!isOpaque(argb))
				{
					continue;
				}

				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				columnBottoms[x] = Math.max(columnBottoms[x], y);
			}
		}

		if (maxX < minX)
		{
			return;
		}

		int verticalSpread = Math.max(1, shadowBandHeight);
		int horizontalSpread = Math.max(SPRITE_SHADOW_MIN_SPREAD, verticalSpread + 1);
		for (int x = Math.max(0, minX - horizontalSpread); x <= Math.min(width - 1, maxX + horizontalSpread); x++)
		{
			for (int y = shadowBandStartY; y < height; y++)
			{
				int index = (y * width) + x;
				if (isOpaque(sourcePixels[index]))
				{
					continue;
				}

				int alpha = spriteShadowAlpha(columnBottoms, width, x, y, shadowBandStartY, verticalSpread, horizontalSpread);
				if (alpha <= 0)
				{
					continue;
				}

				resultPixels[index] = blendShadow(resultPixels[index], alpha);
			}
		}
	}

	private static int spriteShadowAlpha(
		int[] columnBottoms,
		int width,
		int x,
		int y,
		int shadowBandStartY,
		int verticalSpread,
		int horizontalSpread)
	{
		double strongest = 0.0d;
		for (int sampleX = Math.max(0, x - horizontalSpread); sampleX <= Math.min(width - 1, x + horizontalSpread); sampleX++)
		{
			int bottomY = columnBottoms[sampleX];
			if (bottomY < shadowBandStartY || y < bottomY)
			{
				continue;
			}

			int dx = Math.abs(x - sampleX);
			int dy = y - bottomY;
			if (dy > verticalSpread)
			{
				continue;
			}

			double horizontalAllowance = horizontalSpread * (1.0d - (dy / (double) (verticalSpread + 1)));
			if (dx > horizontalAllowance)
			{
				continue;
			}

			double horizontalFactor = 1.0d - Math.min(1.0d, dx / Math.max(1.0d, horizontalAllowance));
			double verticalFactor = 1.0d - (dy / (double) (verticalSpread + 1));
			strongest = Math.max(strongest, horizontalFactor * verticalFactor);
		}

		return clampToByte((int) Math.round(SPRITE_SHADOW_ALPHA * strongest));
	}

	private static int blendShadow(int existingArgb, int alpha)
	{
		if (alpha <= 0)
		{
			return existingArgb;
		}

		int existingAlpha = (existingArgb >>> 24) & 0xFF;
		int blendedAlpha = Math.max(existingAlpha, alpha);
		return (blendedAlpha << 24);
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

		int alpha = (color >>> 24) & 0xFF;
		int avgRed = darken((color >>> 16) & 0xFF);
		int avgGreen = darken((color >>> 8) & 0xFF);
		int avgBlue = darken(color & 0xFF);
		return (alpha << 24) | (avgRed << 16) | (avgGreen << 8) | avgBlue;
	}

	private static int brightenedAverageOpaqueNeighborColor(int[] pixels, int width, int height, int x, int y)
	{
		int color = averageOpaqueNeighborColor(pixels, width, height, x, y);
		if (color == 0)
		{
			return 0;
		}

		int alpha = (color >>> 24) & 0xFF;
		int avgRed = brighten((color >>> 16) & 0xFF);
		int avgGreen = brighten((color >>> 8) & 0xFF);
		int avgBlue = brighten(color & 0xFF);
		return (alpha << 24) | (avgRed << 16) | (avgGreen << 8) | avgBlue;
	}

	private static int averageOpaqueNeighborColor(int[] pixels, int width, int height, int x, int y)
	{
		int alpha = 0;
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

			alpha += (argb >>> 24) & 0xFF;
			red += (argb >>> 16) & 0xFF;
			green += (argb >>> 8) & 0xFF;
			blue += argb & 0xFF;
			count++;
		}

		if (count == 0)
		{
			return 0;
		}

		int avgAlpha = clampToByte((int) Math.round(alpha / (double) count));
		int avgRed = clampToByte((int) Math.round(red / (double) count));
		int avgGreen = clampToByte((int) Math.round(green / (double) count));
		int avgBlue = clampToByte((int) Math.round(blue / (double) count));
		return (avgAlpha << 24) | (avgRed << 16) | (avgGreen << 8) | avgBlue;
	}

	private static boolean hasOpaqueNeighbor(int[] pixels, int width, int height, int x, int y)
	{
		for (int i = 0; i < CARDINAL_X.length; i++)
		{
			int neighborX = x + CARDINAL_X[i];
			int neighborY = y + CARDINAL_Y[i];
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
		for (int i = 0; i < CARDINAL_X.length; i++)
		{
			int neighborX = x + CARDINAL_X[i];
			int neighborY = y + CARDINAL_Y[i];
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

	private static void populateBoundaryMasks(int[] sourcePixels, int width, int height, Scratch scratch)
	{
		int pixelCount = width * height;
		Arrays.fill(scratch.visited, 0, pixelCount, false);
		Arrays.fill(scratch.exteriorTransparentBoundary, 0, pixelCount, false);
		Arrays.fill(scratch.exteriorOpaqueBoundary, 0, pixelCount, false);
		scratch.queue.clear();

		enqueueBorderTransparentPixels(sourcePixels, width, height, scratch.visited, scratch.queue);
		while (!scratch.queue.isEmpty())
		{
			int index = scratch.queue.removeFirst();
			int x = index % width;
			int y = index / width;
			if (hasOpaqueNeighbor(sourcePixels, width, height, x, y))
			{
				scratch.exteriorTransparentBoundary[index] = true;
				markOpaqueNeighbors(sourcePixels, width, height, x, y, scratch.exteriorOpaqueBoundary);
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
				if (scratch.visited[nextIndex] || isOpaque(sourcePixels[nextIndex]))
				{
					continue;
				}

				scratch.visited[nextIndex] = true;
				scratch.queue.addLast(nextIndex);
			}
		}
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
