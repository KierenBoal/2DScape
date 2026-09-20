package com.kierenboal.npcsnap.rendering;

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

public final class NpcSnapUiTextureManager
{
	private final Client client;
	private final IntFunction<SpriteSnapshot> spriteLoader;
	private final Map<Integer, SpriteSnapshot> originalSprites = new HashMap<>();
	private final Map<Integer, AppliedOverride> appliedSpriteOverrides = new HashMap<>();
	private final Map<Integer, AppliedOverride> appliedWidgetOverrides = new HashMap<>();
	private boolean uiBandingApplied;
	private boolean uiBandingPending = true;
	private int appliedUiBands = -1;
	private double appliedUiQuality = -1.0d;
	private boolean appliedCustomUis = true;

	public NpcSnapUiTextureManager(Client client, IntFunction<SpriteSnapshot> spriteLoader)
	{
		this.client = client;
		this.spriteLoader = spriteLoader;
	}

	public void markDirty()
	{
		uiBandingPending = true;
	}

	public void sync(boolean enabled, int bands, double quality)
	{
		sync(enabled, bands, quality, true);
	}

	public void sync(boolean enabled, int bands, double quality, boolean customUis)
	{
		boolean settingsMatch = uiBandingApplied == enabled
			&& (!enabled || (appliedUiBands == bands && Double.compare(appliedUiQuality, quality) == 0
				&& appliedCustomUis == customUis));
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

		applyLoadedWidgets(bands, quality, customUis);
	}

	public void onWidgetLoaded(boolean enabled, int bands, double quality)
	{
		onWidgetLoaded(enabled, bands, quality, true);
	}

	public void onWidgetLoaded(boolean enabled, int bands, double quality, boolean customUis)
	{
		if (!enabled)
		{
			return;
		}

		uiBandingPending = true;
		sync(true, bands, quality, customUis);
	}

	public void restore()
	{
		boolean hadOverrides = !appliedSpriteOverrides.isEmpty()
			|| !appliedWidgetOverrides.isEmpty()
			|| !originalSprites.isEmpty()
			|| uiBandingApplied;
		if (!hadOverrides)
		{
			appliedUiBands = -1;
			appliedUiQuality = -1.0d;
			return;
		}

		restoreSlots(client.getSpriteOverrides(), appliedSpriteOverrides);
		restoreSlots(client.getWidgetSpriteOverrides(), appliedWidgetOverrides);
		originalSprites.clear();
		uiBandingApplied = false;
		appliedUiBands = -1;
		appliedUiQuality = -1.0d;
		resetWidgetSpriteCache();
	}

	public int getAppliedSpriteOverrideCount()
	{
		return appliedSpriteOverrides.size();
	}

	public int getAppliedWidgetOverrideCount()
	{
		return appliedWidgetOverrides.size();
	}

	private void applyLoadedWidgets(int bands, double quality, boolean customUis)
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
			changed |= applyWidgetRecursive(root, bands, quality, customUis, visitedWidgets);
		}

		uiBandingApplied = true;
		appliedUiBands = bands;
		appliedUiQuality = quality;
		appliedCustomUis = customUis;
		if (changed)
		{
			resetWidgetSpriteCache();
		}
	}

	private boolean applyWidgetRecursive(Widget widget, int bands, double quality, boolean customUis,
		Set<Integer> visitedWidgets)
	{
		if (widget == null || !visitedWidgets.add(widget.getId()))
		{
			return false;
		}

		boolean changed = false;
		int widgetId = widget.getId();
		Map<Integer, SpritePixels> widgetOverrides = client.getWidgetSpriteOverrides();
		AppliedOverride widgetApplied = appliedWidgetOverrides.get(widgetId);
		if (widgetOverrides != null && (widgetOverrides.get(widgetId) != null
			|| (widgetApplied != null && widgetOverrides.get(widgetId) == widgetApplied.replacement)))
		{
			changed |= applySlot(widgetOverrides, appliedWidgetOverrides, widgetId, widget.getSpriteId(),
				bands, quality, customUis);
		}
		else if (widget.getSpriteId() >= 0)
		{
			changed |= applySlot(client.getSpriteOverrides(), appliedSpriteOverrides, widget.getSpriteId(),
				widget.getSpriteId(), bands, quality, customUis);
		}

		changed |= visitChildren(widget.getChildren(), bands, quality, customUis, visitedWidgets);
		changed |= visitChildren(widget.getDynamicChildren(), bands, quality, customUis, visitedWidgets);
		changed |= visitChildren(widget.getStaticChildren(), bands, quality, customUis, visitedWidgets);
		changed |= visitChildren(widget.getNestedChildren(), bands, quality, customUis, visitedWidgets);
		return changed;
	}

	private boolean visitChildren(Widget[] children, int bands, double quality, boolean customUis,
		Set<Integer> visitedWidgets)
	{
		if (children == null)
		{
			return false;
		}

		boolean changed = false;
		for (Widget child : children)
		{
			changed |= applyWidgetRecursive(child, bands, quality, customUis, visitedWidgets);
		}
		return changed;
	}

	private boolean applySlot(Map<Integer, SpritePixels> overrides, Map<Integer, AppliedOverride> applied,
		int slotId, int spriteId, int bands, double quality, boolean customUis)
	{
		if (overrides == null)
		{
			return false;
		}
		AppliedOverride owned = applied.get(slotId);
		SpritePixels current = overrides.get(slotId);
		if (owned != null && current == owned.replacement)
		{
			return false;
		}
		applied.remove(slotId);
		SpriteSnapshot source = customUis && current != null
			? SpriteSnapshot.of(current)
			: originalSprites.computeIfAbsent(spriteId, key -> spriteLoader.apply(key));
		if (source == null || source.getPixels().length == 0)
		{
			return false;
		}
		SpritePixels replacement = client.createSpritePixels(
			NpcSnapColorBanding.bandSpritePixels(source.toResampledCanvasPixels(quality), bands),
			source.getCanvasWidth(), source.getCanvasHeight());
		if (replacement == null)
		{
			return false;
		}
		applied.put(slotId, new AppliedOverride(current, replacement));
		overrides.put(slotId, replacement);
		return true;
	}

	private static void restoreSlots(Map<Integer, SpritePixels> overrides, Map<Integer, AppliedOverride> applied)
	{
		if (overrides != null)
		{
			for (Map.Entry<Integer, AppliedOverride> entry : applied.entrySet())
			{
				AppliedOverride owned = entry.getValue();
				if (overrides.get(entry.getKey()) == owned.replacement)
				{
					if (owned.displaced == null)
					{
						overrides.remove(entry.getKey());
					}
					else
					{
						overrides.put(entry.getKey(), owned.displaced);
					}
				}
			}
		}
		applied.clear();
	}

	private static final class AppliedOverride
	{
		private final SpritePixels displaced;
		private final SpritePixels replacement;

		private AppliedOverride(SpritePixels displaced, SpritePixels replacement)
		{
			this.displaced = displaced;
			this.replacement = replacement;
		}
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
	public static class SpriteSnapshot
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

		public static SpriteSnapshot of(SpritePixels spritePixels)
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
