package com.kierenboal.npcsnap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.NodeCache;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;

final class NpcSnapUiTextureManager
{
	private final Client client;
	private final IntFunction<SpriteSnapshot> spriteLoader;
	private final Map<Integer, SpriteSnapshot> originalSprites = new HashMap<>();
	private final Map<Integer, SpritePixels> bandedSpriteOverrides = new HashMap<>();
	private final Set<Integer> appliedSpriteOverrideIds = new HashSet<>();
	private boolean uiBandingApplied;
	private boolean uiBandingPending = true;
	private int appliedUiBands = -1;
	private double appliedUiQuality = -1.0d;

	NpcSnapUiTextureManager(Client client, IntFunction<SpriteSnapshot> spriteLoader)
	{
		this.client = client;
		this.spriteLoader = spriteLoader;
	}

	void markDirty()
	{
		uiBandingPending = true;
	}

	void sync(boolean enabled, int bands, double quality)
	{
		boolean settingsMatch = uiBandingApplied == enabled
			&& (!enabled || (appliedUiBands == bands && Double.compare(appliedUiQuality, quality) == 0));
		if (!enabled)
		{
			if (!uiBandingPending && settingsMatch)
			{
				return;
			}

			restore();
			uiBandingPending = false;
			return;
		}

		if (uiBandingPending || !settingsMatch)
		{
			restore();
			uiBandingPending = false;
		}

		applyLoadedWidgets(bands, quality);
	}

	void onWidgetLoaded(boolean enabled, int bands, double quality)
	{
		if (!enabled)
		{
			return;
		}

		uiBandingPending = true;
		sync(true, bands, quality);
	}

	void restore()
	{
		boolean hadOverrides = !appliedSpriteOverrideIds.isEmpty()
			|| !bandedSpriteOverrides.isEmpty()
			|| !originalSprites.isEmpty()
			|| uiBandingApplied;
		if (!hadOverrides)
		{
			appliedUiBands = -1;
			appliedUiQuality = -1.0d;
			return;
		}

		Map<Integer, SpritePixels> spriteOverrides = client.getSpriteOverrides();
		if (spriteOverrides != null)
		{
			for (Integer spriteId : appliedSpriteOverrideIds)
			{
				spriteOverrides.remove(spriteId);
			}
		}

		appliedSpriteOverrideIds.clear();
		bandedSpriteOverrides.clear();
		originalSprites.clear();
		uiBandingApplied = false;
		appliedUiBands = -1;
		appliedUiQuality = -1.0d;
		resetWidgetSpriteCache();
	}

	int getAppliedSpriteOverrideCount()
	{
		return appliedSpriteOverrideIds.size();
	}

	int getAppliedWidgetOverrideCount()
	{
		return 0;
	}

	private void applyLoadedWidgets(int bands, double quality)
	{
		Widget[] widgetRoots = client.getWidgetRoots();
		if (widgetRoots == null)
		{
			uiBandingPending = true;
			return;
		}

		Set<Integer> visitedWidgets = new HashSet<>();
		boolean changed = false;
		for (Widget root : widgetRoots)
		{
			changed |= applyWidgetRecursive(root, bands, quality, visitedWidgets);
		}

		uiBandingApplied = true;
		appliedUiBands = bands;
		appliedUiQuality = quality;
		if (changed)
		{
			resetWidgetSpriteCache();
		}
	}

	private boolean applyWidgetRecursive(Widget widget, int bands, double quality, Set<Integer> visitedWidgets)
	{
		if (widget == null || !visitedWidgets.add(widget.getId()))
		{
			return false;
		}

		boolean changed = false;
		int spriteId = widget.getSpriteId();
		if (spriteId >= 0)
		{
			SpritePixels replacement = getOrCreateBandedSprite(spriteId, bands, quality);
			if (replacement != null)
			{
				Map<Integer, SpritePixels> spriteOverrides = client.getSpriteOverrides();
				if (spriteOverrides == null)
				{
					return false;
				}

				if (appliedSpriteOverrideIds.add(spriteId))
				{
					spriteOverrides.put(spriteId, replacement);
					changed = true;
				}
			}
		}

		changed |= visitChildren(widget.getChildren(), bands, quality, visitedWidgets);
		changed |= visitChildren(widget.getDynamicChildren(), bands, quality, visitedWidgets);
		changed |= visitChildren(widget.getStaticChildren(), bands, quality, visitedWidgets);
		changed |= visitChildren(widget.getNestedChildren(), bands, quality, visitedWidgets);
		return changed;
	}

	private boolean visitChildren(Widget[] children, int bands, double quality, Set<Integer> visitedWidgets)
	{
		if (children == null)
		{
			return false;
		}

		boolean changed = false;
		for (Widget child : children)
		{
			changed |= applyWidgetRecursive(child, bands, quality, visitedWidgets);
		}
		return changed;
	}

	private SpritePixels getOrCreateBandedSprite(int spriteId, int bands, double quality)
	{
		SpritePixels cached = bandedSpriteOverrides.get(spriteId);
		if (cached != null)
		{
			return cached;
		}

		SpriteSnapshot original = originalSprites.computeIfAbsent(spriteId, key -> spriteLoader.apply(key));
		if (original == null || original.getPixels().length == 0)
		{
			return null;
		}

		SpritePixels banded = client.createSpritePixels(
			NpcSnapColorBanding.bandSpritePixels(original.toResampledCanvasPixels(quality), bands),
			original.getCanvasWidth(),
			original.getCanvasHeight()
		);

		if (banded == null)
		{
			return null;
		}

		bandedSpriteOverrides.put(spriteId, banded);
		return banded;
	}

	private void resetWidgetSpriteCache()
	{
		NodeCache widgetSpriteCache = client.getWidgetSpriteCache();
		if (widgetSpriteCache != null)
		{
			widgetSpriteCache.reset();
		}
	}

	@Value
	static class SpriteSnapshot
	{
		int[] pixels;
		int width;
		int height;
		int maxWidth;
		int maxHeight;
		int offsetX;
		int offsetY;

		static SpriteSnapshot of(int[] pixels, int width, int height)
		{
			return new SpriteSnapshot(pixels, width, height, width, height, 0, 0);
		}

		static SpriteSnapshot of(SpritePixels spritePixels)
		{
			if (spritePixels == null)
			{
				return null;
			}

			int[] pixels = spritePixels.getPixels();
			if (pixels == null)
			{
				return null;
			}

			return new SpriteSnapshot(
				pixels.clone(),
				spritePixels.getWidth(),
				spritePixels.getHeight(),
				spritePixels.getMaxWidth(),
				spritePixels.getMaxHeight(),
				spritePixels.getOffsetX(),
				spritePixels.getOffsetY()
			);
		}

		int getCanvasWidth()
		{
			return Math.max(width, maxWidth);
		}

		int getCanvasHeight()
		{
			return Math.max(height, maxHeight);
		}

		int[] toCanvasPixels()
		{
			int canvasWidth = getCanvasWidth();
			int canvasHeight = getCanvasHeight();
			if (canvasWidth <= 0 || canvasHeight <= 0)
			{
				return new int[0];
			}

			if (pixels.length == canvasWidth * canvasHeight)
			{
				return pixels.clone();
			}

			int[] canvasPixels = new int[canvasWidth * canvasHeight];
			if (width <= 0 || height <= 0 || pixels.length < width * height)
			{
				return canvasPixels;
			}

			int drawX = clamp(offsetX, 0, Math.max(0, canvasWidth - width));
			int drawY = clamp(offsetY, 0, Math.max(0, canvasHeight - height));
			for (int y = 0; y < height; y++)
			{
				System.arraycopy(
					pixels,
					y * width,
					canvasPixels,
					((drawY + y) * canvasWidth) + drawX,
					width
				);
			}

			return canvasPixels;
		}

		int[] toResampledCanvasPixels(double quality)
		{
			int canvasWidth = getCanvasWidth();
			int canvasHeight = getCanvasHeight();
			if (canvasWidth <= 0 || canvasHeight <= 0 || width <= 0 || height <= 0 || pixels.length < width * height)
			{
				return new int[0];
			}

			int[] canvasPixels = new int[canvasWidth * canvasHeight];
			double clampedQuality = Math.max(1.0d, Math.min(100.0d, quality));
			if (clampedQuality >= 99.999d)
			{
				int[] sourceCanvas = toCanvasPixels();
				System.arraycopy(sourceCanvas, 0, canvasPixels, 0, Math.min(sourceCanvas.length, canvasPixels.length));
				return canvasPixels;
			}

			int targetWidth = scaledSize(width, clampedQuality);
			int targetHeight = scaledSize(height, clampedQuality);
			int drawX = clamp(offsetX, 0, Math.max(0, canvasWidth - width));
			int drawY = clamp(offsetY, 0, Math.max(0, canvasHeight - height));
			int[] reducedPixels = new int[targetWidth * targetHeight];

			for (int y = 0; y < targetHeight; y++)
			{
				int sourceY = Math.min(height - 1, (int) Math.floor((y * (double) height) / targetHeight));
				for (int x = 0; x < targetWidth; x++)
				{
					int sourceX = Math.min(width - 1, (int) Math.floor((x * (double) width) / targetWidth));
					reducedPixels[(y * targetWidth) + x] = pixels[(sourceY * width) + sourceX];
				}
			}

			for (int y = 0; y < height; y++)
			{
				int reducedY = Math.min(targetHeight - 1, (int) Math.floor((y * (double) targetHeight) / height));
				for (int x = 0; x < width; x++)
				{
					int reducedX = Math.min(targetWidth - 1, (int) Math.floor((x * (double) targetWidth) / width));
					canvasPixels[((drawY + y) * canvasWidth) + drawX + x] = reducedPixels[(reducedY * targetWidth) + reducedX];
				}
			}

			return canvasPixels;
		}

		private static int clamp(int value, int min, int max)
		{
			return Math.max(min, Math.min(max, value));
		}

		private static int scaledSize(int sourceSize, double quality)
		{
			if (sourceSize <= 0)
			{
				return 0;
			}

			double scaled = sourceSize * (quality / 100.0d);
			return Math.max(1, Math.min(sourceSize, (int) Math.round(scaled)));
		}
	}
}
