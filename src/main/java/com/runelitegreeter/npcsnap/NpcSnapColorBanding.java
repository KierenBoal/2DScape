package com.runelitegreeter.npcsnap;

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
		float h = hsb[0];
		float s = clamp01(hsb[1] * 1.15f);
		float b = snapBrightness(hsb[2], colorBands);

		int bands = Math.max(1, colorBands);
		if (bands == 1)
		{
			b = 0.5f;
		}
		else
		{
			int band = Math.min(bands - 1, (int) (b * bands));
			b = band / (float) (bands - 1);
		}

		return Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
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

	private static float snapBrightness(float brightness, int colorBands)
	{
		int bands = Math.max(1, colorBands);
		float minBrightness = 0.1f;
		float maxBrightness = 0.9f;
		float compressed = minBrightness + brightness * (maxBrightness - minBrightness);

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
