package com.kierenboal.npcsnap.occlusion;

import java.util.Arrays;

/** Scene colours must use renderer lighting, not the sprite's artistic colour picker. */
final class SceneryFaceColors
{
	private final int[] palette = new int[65536];
	private double brightness = 0.8d;

	void brightness(double value)
	{
		double next = Double.isFinite(value) && value > 0d ? value : 0.8d;
		if (next != brightness)
		{
			brightness = next;
			Arrays.fill(palette, 0);
		}
	}

	int faceTintRgb(int face, int[] colors1, int[] colors2, int[] colors3, short[] unlitColors,
		int overrideHue, int overrideSaturation, int overrideLuminance, int overrideAmount)
	{
		if (hasColor(colors1, face))
		{
			int first = overriddenRgb(colors1[face], overrideHue, overrideSaturation, overrideLuminance, overrideAmount);
			if (!hasColor(colors2, face) || !hasColor(colors3, face)) return first;
			int second = overriddenRgb(colors2[face], overrideHue, overrideSaturation, overrideLuminance, overrideAmount);
			int third = overriddenRgb(colors3[face], overrideHue, overrideSaturation, overrideLuminance, overrideAmount);
			int red = (((first >>> 16) & 255) + ((second >>> 16) & 255) + ((third >>> 16) & 255)) / 3;
			int green = (((first >>> 8) & 255) + ((second >>> 8) & 255) + ((third >>> 8) & 255)) / 3;
			int blue = ((first & 255) + (second & 255) + (third & 255)) / 3;
			return (red << 16) | (green << 8) | blue;
		}
		if (hasColor(colors3, face)) return overriddenRgb(colors3[face], overrideHue, overrideSaturation, overrideLuminance, overrideAmount);
		if (hasColor(colors2, face)) return overriddenRgb(colors2[face], overrideHue, overrideSaturation, overrideLuminance, overrideAmount);
		if (unlitColors != null && face >= 0 && face < unlitColors.length && unlitColors[face] != -1)
		{
			return overriddenRgb(Short.toUnsignedInt(unlitColors[face]), overrideHue, overrideSaturation, overrideLuminance, overrideAmount);
		}
		return 0xFFFFFF;
	}

	private int overriddenRgb(int hsl, int hue, int saturation, int luminance, int amount)
	{
		if (amount > 0)
		{
			int h = component((hsl >>> 10) & 63, hue, amount);
			int s = component((hsl >>> 7) & 7, saturation, amount);
			int l = component(hsl & 127, luminance, amount);
			hsl = ((h << 10) | (s << 7) | l) & 0xFFFF;
		}
		return rgb(hsl);
	}

	private static int component(int current, int replacement, int amount)
	{
		return replacement == -1 ? current : current + ((replacement - current) * amount >> 7);
	}

	int rgb(int packedHsl)
	{
		int hsl = packedHsl & 0xFFFF;
		if (palette[hsl] != 0) return palette[hsl] & 0xFFFFFF;
		// Same bin centres and gamma as RuneLite GPU's hsl_to_rgb.glsl.
		double hue = ((hsl >>> 10) + 0.5d) / 64d;
		double saturation = (((hsl >>> 7) & 7) + 0.5d) / 8d;
		double lightness = (hsl & 127) / 128d;
		int rgb = (hslChannel(hue, saturation, lightness, 0) << 16)
			| (hslChannel(hue, saturation, lightness, 8) << 8)
			| hslChannel(hue, saturation, lightness, 4);
		palette[hsl] = 0x01000000 | rgb;
		return rgb;
	}

	private int hslChannel(double hue, double saturation, double lightness, int offset)
	{
		double phase = (offset + hue * 12d) % 12d;
		double amplitude = saturation * Math.min(lightness, 1d - lightness);
		double value = lightness - amplitude * Math.max(-1d, Math.min(1d, Math.min(phase - 3d, 9d - phase)));
		return Math.max(0, Math.min(255, (int) Math.round(255d * Math.pow(value, brightness))));
	}

	private static boolean hasColor(int[] colors, int face)
	{
		return colors != null && face >= 0 && face < colors.length && colors[face] >= 0;
	}
}
