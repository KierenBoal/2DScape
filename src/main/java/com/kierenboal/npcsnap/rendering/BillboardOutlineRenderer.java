package com.kierenboal.npcsnap.rendering;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public final class BillboardOutlineRenderer
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

	public static void applyOutline(
		BufferedImage image,
		Scratch scratch,
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

		if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return;
		}

		int pixelCount = width * height;
		int[] sourcePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		scratch.ensureCapacity(pixelCount);
		System.arraycopy(sourcePixels, 0, scratch.resultPixels, 0, pixelCount);
		populateBoundaryMasks(sourcePixels, width, height, scratch);

		applyBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorOpaqueBoundary,
			highlightInline, shadowInline, solidInline, solidOutlineColor);
		applyBoundary(sourcePixels, scratch.resultPixels, width, height, scratch.exteriorTransparentBoundary,
			highlightOutline, shadowOutline, solidOutline, solidOutlineColor);
		System.arraycopy(scratch.resultPixels, 0, sourcePixels, 0, pixelCount);
	}

	public static final class Scratch
	{
		private int[] resultPixels = new int[0];
		private boolean[] visited = new boolean[0];
		private boolean[] exteriorTransparentBoundary = new boolean[0];
		private boolean[] exteriorOpaqueBoundary = new boolean[0];
		private int[] queue = new int[0];
		private int queueHead;
		private int queueTail;

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
			queue = new int[capacity];
		}

		private void clearQueue()
		{
			queueHead = 0;
			queueTail = 0;
		}

		private void enqueue(int index)
		{
			queue[queueTail++] = index;
		}

		private boolean hasQueuedPixels()
		{
			return queueHead < queueTail;
		}

		private int dequeue()
		{
			return queue[queueHead++];
		}
	}

	private static void applyBoundary(
		int[] sourcePixels,
		int[] resultPixels,
		int width,
		int height,
		boolean[] boundary,
		boolean highlight,
		boolean shadow,
		boolean solid,
		Color solidColor)
	{
		if (!highlight && !shadow && !solid)
		{
			return;
		}
		double factor = highlight ? HIGHLIGHT_BRIGHTEN_FACTOR : SHADOW_DARKEN_FACTOR;
		for (int index = 0; index < boundary.length; index++)
		{
			if (!boundary[index])
			{
				continue;
			}

			int color = highlight || shadow
				? shadeNeighbors(sourcePixels, width, height, index % width, index / width, factor)
				: 0;
			if (color == 0 && solid)
			{
				color = solidColor.getRGB();
			}
			if (color != 0)
			{
				resultPixels[index] = color;
			}
		}
	}

	private static void enqueueBorderTransparentPixels(int[] pixels, int width, int height, Scratch scratch)
	{
		for (int x = 0; x < width; x++)
		{
			enqueueTransparentPixel(pixels, width, 0, x, scratch);
			enqueueTransparentPixel(pixels, width, height - 1, x, scratch);
		}

		for (int y = 1; y < height - 1; y++)
		{
			enqueueTransparentPixel(pixels, width, y, 0, scratch);
			enqueueTransparentPixel(pixels, width, y, width - 1, scratch);
		}
	}

	private static void enqueueTransparentPixel(int[] pixels, int width, int y, int x, Scratch scratch)
	{
		int index = y * width + x;
		if (scratch.visited[index] || isOpaque(pixels[index]))
		{
			return;
		}

		scratch.visited[index] = true;
		scratch.enqueue(index);
	}

	private static int shadeNeighbors(int[] pixels, int width, int height, int x, int y, double factor)
	{
		int color = averageOpaqueNeighborColor(pixels, width, height, x, y);
		if (color == 0)
		{
			return 0;
		}

		int alpha = (color >>> 24) & 0xFF;
		int avgRed = scaleChannel((color >>> 16) & 0xFF, factor);
		int avgGreen = scaleChannel((color >>> 8) & 0xFF, factor);
		int avgBlue = scaleChannel(color & 0xFF, factor);
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

	private static int scaleChannel(int color, double factor)
	{
		return clampToByte((int) Math.round(color * factor));
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
		scratch.clearQueue();

		enqueueBorderTransparentPixels(sourcePixels, width, height, scratch);
		while (scratch.hasQueuedPixels())
		{
			int index = scratch.dequeue();
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
				scratch.enqueue(nextIndex);
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
