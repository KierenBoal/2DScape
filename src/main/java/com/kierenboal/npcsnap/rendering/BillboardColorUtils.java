package com.kierenboal.npcsnap.rendering;

import java.awt.Color;

public final class BillboardColorUtils
{
	private static final int CAPE_ARTIFACT_FACE_RED = 147;
	private static final int CAPE_ARTIFACT_FACE_GREEN = 143;
	private static final int CAPE_ARTIFACT_FACE_BLUE = 143;
	private static final int CAPE_ARTIFACT_FACE_ALPHA = 255;

	private BillboardColorUtils()
	{
	}

	public static Color resolveFaceColor(
		int face,
		int[] faceColors1,
		int[] faceColors2,
		int[] faceColors3,
		short[] unlitFaceColors,
		int alpha
	)
	{
		Color unlitColor = null;
		if (unlitFaceColors != null && face < unlitFaceColors.length && unlitFaceColors[face] != -1)
		{
			unlitColor = packedHslToColor(Short.toUnsignedInt(unlitFaceColors[face]), alpha);
		}

		Color litColor = decodedLitFaceColor(face, faceColors1, faceColors2, faceColors3, alpha);

		Color color = chooseFaceColor(unlitColor, litColor);
		if (color != null)
		{
			return color;
		}

		return new Color(0, 0, 0, Math.max(0, Math.min(255, alpha)));
	}

	public static boolean isSkippedCapeArtifactFaceColor(Color color)
	{
		// Fire Cape and Infernal Cape player models include unwanted rear geometry
		// with this exact raw face color; skip it before light boost and rasterizing.
		return color != null
			&& color.getRed() == CAPE_ARTIFACT_FACE_RED
			&& color.getGreen() == CAPE_ARTIFACT_FACE_GREEN
			&& color.getBlue() == CAPE_ARTIFACT_FACE_BLUE
			&& color.getAlpha() == CAPE_ARTIFACT_FACE_ALPHA;
	}

	public static Color decodedLitFaceColor(int face, int[] faceColors1, int[] faceColors2, int[] faceColors3, int alpha)
	{
		if (hasFlatFaceColor(face, faceColors1, faceColors3))
		{
			return packedHslToColor(faceColors1[face], alpha);
		}

		if (hasLitFaceColors(face, faceColors1, faceColors2, faceColors3))
		{
			return averagePackedFaceColor(faceColors1[face], faceColors2[face], faceColors3[face], alpha);
		}

		return null;
	}

	public static Integer faceColorValue(int[] faceColors, int face)
	{
		return faceColors != null && face < faceColors.length ? faceColors[face] : null;
	}

	public static Integer unlitFaceColorValue(short[] unlitFaceColors, int face)
	{
		return unlitFaceColors != null && face < unlitFaceColors.length && unlitFaceColors[face] != -1
			? Short.toUnsignedInt(unlitFaceColors[face])
			: null;
	}

	public static String formatColor(Color color)
	{
		return color == null
			? "null"
			: color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha();
	}

	public static Color applyLightBoost(Color color, double lightBoostPercent)
	{
		double boost = lightBoostPercent / 100.0d;
		int red = Math.min(255, (int) Math.round(color.getRed() * boost));
		int green = Math.min(255, (int) Math.round(color.getGreen() * boost));
		int blue = Math.min(255, (int) Math.round(color.getBlue() * boost));
		return new Color(red, green, blue, color.getAlpha());
	}

	public static int modulateTexturePixel(int samplePixel, Color shade, int colorBands)
	{
		int sampleAlpha = (samplePixel >>> 24) & 0xFF;
		if (sampleAlpha == 0 && (samplePixel & 0xFFFFFF) != 0)
		{
			sampleAlpha = 0xFF;
		}

		int alpha = (sampleAlpha * shade.getAlpha()) / 255;
		int red = (((samplePixel >> 16) & 0xFF) * shade.getRed()) / 255;
		int green = (((samplePixel >> 8) & 0xFF) * shade.getGreen()) / 255;
		int blue = ((samplePixel & 0xFF) * shade.getBlue()) / 255;
		int modulatedPixel = (alpha << 24) | (red << 16) | (green << 8) | blue;
		return NpcSnapColorBanding.snapTexturePixel(modulatedPixel, colorBands);
	}

	private static boolean hasLitFaceColors(int face, int[] faceColors1, int[] faceColors2, int[] faceColors3)
	{
		return hasLitFaceColor(faceColors1, face)
			&& hasLitFaceColor(faceColors2, face)
			&& hasLitFaceColor(faceColors3, face);
	}

	private static boolean hasLitFaceColor(int[] faceColors, int face)
	{
		return faceColors != null && face < faceColors.length && faceColors[face] >= 0;
	}

	private static boolean hasFlatFaceColor(int face, int[] faceColors1, int[] faceColors3)
	{
		return hasLitFaceColor(faceColors1, face)
			&& faceColors3 != null
			&& face < faceColors3.length
			&& faceColors3[face] == -1;
	}

	private static Color averagePackedFaceColor(int packed1, int packed2, int packed3, int alpha)
	{
		Color vertexA = packedHslToColor(packed1, alpha);
		Color vertexB = packedHslToColor(packed2, alpha);
		Color vertexC = packedHslToColor(packed3, alpha);
		int red = (vertexA.getRed() + vertexB.getRed() + vertexC.getRed()) / 3;
		int green = (vertexA.getGreen() + vertexB.getGreen() + vertexC.getGreen()) / 3;
		int blue = (vertexA.getBlue() + vertexB.getBlue() + vertexC.getBlue()) / 3;
		return new Color(red, green, blue, alpha);
	}

	private static Color chooseFaceColor(Color unlitColor, Color litColor)
	{
		Color selected = unlitColor != null ? unlitColor : litColor;
		selected = chooseMoreColorful(selected, litColor);
		return selected;
	}

	private static Color chooseMoreColorful(Color current, Color candidate)
	{
		if (candidate != null
			&& maxRgb(candidate) > 2
			&& (current == null || colorfulness(candidate) > colorfulness(current) + 0.03f))
		{
			return candidate;
		}

		return current;
	}

	private static float colorfulness(Color color)
	{
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		return hsb[1] * hsb[2];
	}

	private static int maxRgb(Color color)
	{
		return Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
	}

	private static Color packedHslToColor(int packedHsl, int alpha)
	{
		int hue = (packedHsl >> 10) & 0x3F;
		int saturation = (packedHsl >> 7) & 0x07;
		int lightness = packedHsl & 0x7F;
		float h = hue / 64.0f;
		float s = saturation / 7.0f;
		float l = lightness / 128.0f;

		float r;
		float g;
		float b;

		if (s == 0.0f)
		{
			r = l;
			g = l;
			b = l;
		}
		else
		{
			float q = l < 0.5f ? l * (1.0f + s) : l + s - (l * s);
			float p = 2.0f * l - q;
			r = hueToRgb(p, q, h + (1.0f / 3.0f));
			g = hueToRgb(p, q, h);
			b = hueToRgb(p, q, h - (1.0f / 3.0f));
		}

		return new Color(clamp(r), clamp(g), clamp(b), Math.max(0, Math.min(255, alpha)));
	}

	private static float hueToRgb(float p, float q, float t)
	{
		if (t < 0)
		{
			t += 1.0f;
		}
		if (t > 1)
		{
			t -= 1.0f;
		}
		if (t < (1.0f / 6.0f))
		{
			return p + ((q - p) * 6.0f * t);
		}
		if (t < 0.5f)
		{
			return q;
		}
		if (t < (2.0f / 3.0f))
		{
			return p + ((q - p) * ((2.0f / 3.0f) - t) * 6.0f);
		}
		return p;
	}

	private static int clamp(float component)
	{
		return Math.max(0, Math.min(255, Math.round(component * 255.0f)));
	}
}
