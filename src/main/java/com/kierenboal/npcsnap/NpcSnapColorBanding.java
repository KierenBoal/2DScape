package com.kierenboal.npcsnap;

import java.awt.Color;

final class NpcSnapColorBanding
{
	private NpcSnapColorBanding()
	{
	}

	static Color snapToRamp(Color color, int colorBands)
	{
		int alpha = color.getAlpha();
		if (alpha == 0)
		{
			return color;
		}

		int rgb = snapRgb(color.getRed(), color.getGreen(), color.getBlue(), colorBands);
		return new Color(
			(rgb >> 16) & 0xFF,
			(rgb >> 8) & 0xFF,
			rgb & 0xFF,
			alpha
		);
	}

	static int snapRgb(int red, int green, int blue, int colorBands)
	{
		float[] hsb = Color.RGBtoHSB(red, green, blue, null);
		float hue = hsb[0];
		float saturation = clamp01(hsb[1] * 1.15f);
		float brightness = snapBrightness(hsb[2], colorBands);

		return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
	}

	static int snapTexturePixel(int pixel, int colorBands)
	{
		int alpha = (pixel >>> 24) & 0xFF;
		int red = (pixel >> 16) & 0xFF;
		int green = (pixel >> 8) & 0xFF;
		int blue = pixel & 0xFF;
		int snapped = snapRgb(red, green, blue, colorBands);

		if (alpha == 0)
		{
			return snapped;
		}

		return (alpha << 24) | snapped;
	}

	static int[] bandPixels(int[] pixels, int colorBands)
	{
		if (pixels == null || pixels.length == 0)
		{
			return new int[0];
		}

		int[] banded = pixels.clone();
		applyBandsInPlace(banded, colorBands);
		return banded;
	}

	static int[] bandSpritePixels(int[] pixels, int colorBands)
	{
		if (pixels == null || pixels.length == 0)
		{
			return new int[0];
		}

		int[] banded = pixels.clone();
		for (int i = 0; i < banded.length; i++)
		{
			int pixel = banded[i];
			if (pixel == 0)
			{
				banded[i] = 0;
				continue;
			}

			// Raw SpritePixels use 0 for transparency and commonly store visible pixels as packed RGB.
			// Preserve that representation so client sprite rendering keeps treating non-zero pixels as visible.
			int snapped = snapRgb((pixel >>> 16) & 0xFF, (pixel >>> 8) & 0xFF, pixel & 0xFF, colorBands);
			banded[i] = snapped != 0 ? snapped : 0x00010101;
		}

		return banded;
	}

	static void applyBandsInPlace(int[] pixels, int colorBands)
	{
		if (pixels == null || pixels.length == 0)
		{
			return;
		}

		for (int i = 0; i < pixels.length; i++)
		{
			pixels[i] = snapTexturePixel(pixels[i], colorBands);
		}
	}

	private static float snapBrightness(float brightness, int colorBands)
	{
		int bands = Math.max(1, colorBands);
		float minBrightness = 0.1f;
		float maxBrightness = 0.9f;
		float compressed = minBrightness + clamp01(brightness) * (maxBrightness - minBrightness);

		if (bands == 1)
		{
			return (minBrightness + maxBrightness) * 0.5f;
		}

		float normalized = (compressed - minBrightness) / (maxBrightness - minBrightness);
		float snapped = Math.round(normalized * (bands - 1)) / (float) (bands - 1);
		return minBrightness + snapped * (maxBrightness - minBrightness);
	}

	private static float clamp01(float value)
	{
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
