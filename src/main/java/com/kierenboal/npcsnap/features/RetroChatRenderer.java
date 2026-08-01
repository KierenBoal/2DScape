package com.kierenboal.npcsnap.features;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

final class RetroChatRenderer
{
	private static final long RANDOM_BUCKET_MILLIS = 200L;
	private static final int EFFECT_MARGIN = 8;
	private static final Map<String, Color> RETRO_COLORS = retroColors();
	private static final Color[] RANDOM_COLORS = RETRO_COLORS.values().toArray(new Color[0]);

	static ParsedChat plain(String text)
	{
		return uniform(text, Color.YELLOW, ColorMode.SOLID, Motion.NONE);
	}

	static ParsedChat parse(String source)
	{
		String text = source == null ? "" : source;
		Color color = Color.YELLOW;
		ColorMode colorMode = ColorMode.SOLID;
		Motion motion = Motion.NONE;
		boolean colorFound = false;
		boolean motionFound = false;

		for (int pass = 0; pass < 2; pass++)
		{
			Prefix prefix = leadingPrefix(text);
			if (prefix == null)
			{
				break;
			}
			String code = prefix.code.toLowerCase(Locale.ROOT);
			Color retro = RETRO_COLORS.get(code);
			if (!colorFound && retro != null)
			{
				color = retro;
				colorFound = true;
				text = text.substring(prefix.length);
				continue;
			}
			if (!colorFound && "ran".equals(code))
			{
				colorMode = ColorMode.RANDOM;
				colorFound = true;
				text = text.substring(prefix.length);
				continue;
			}
			ColorMode parsedColor = builtInColor(code);
			if (!colorFound && parsedColor != null)
			{
				colorMode = parsedColor;
				color = builtInSolidColor(code);
				colorFound = true;
				text = text.substring(prefix.length);
				continue;
			}
			Motion parsedMotion = motion(code);
			if (!motionFound && parsedMotion != null)
			{
				motion = parsedMotion;
				motionFound = true;
				text = text.substring(prefix.length);
				continue;
			}
			break;
		}
		StringBuilder visible = new StringBuilder();
		List<Color> colors = new ArrayList<>();
		List<ColorMode> modes = new ArrayList<>();
		Color currentColor = color;
		ColorMode currentMode = colorMode;
		for (int index = 0; index < text.length();)
		{
			if (text.charAt(index) == '@' && index + 4 < text.length() && text.charAt(index + 4) == '@')
			{
				String code = text.substring(index + 1, index + 4).toLowerCase(Locale.ROOT);
				Color inlineColor = RETRO_COLORS.get(code);
				if (inlineColor != null || "ran".equals(code))
				{
					currentColor = inlineColor == null ? Color.YELLOW : inlineColor;
					currentMode = inlineColor == null ? ColorMode.RANDOM : ColorMode.SOLID;
					index += 5;
					continue;
				}
			}
			visible.append(text.charAt(index++));
			colors.add(currentColor);
			modes.add(currentMode);
		}
		return new ParsedChat(visible.toString(), color, colorMode, motion,
			colors.toArray(new Color[0]), modes.toArray(new ColorMode[0]));
	}

	private static ParsedChat uniform(String text, Color color, ColorMode mode, Motion motion)
	{
		Color[] colors = new Color[text.length()];
		ColorMode[] modes = new ColorMode[text.length()];
		java.util.Arrays.fill(colors, color);
		java.util.Arrays.fill(modes, mode);
		return new ParsedChat(text, color, mode, motion, colors, modes);
	}

	Rectangle bounds(ParsedChat chat, FontMetrics metrics, int centerX, int cursorY)
	{
		int width = metrics.stringWidth(chat.text);
		int margin = chat.motion == Motion.NONE ? 0 : EFFECT_MARGIN;
		return new Rectangle(centerX - width / 2 - margin, cursorY - metrics.getHeight() - margin,
			Math.max(1, width + margin * 2), metrics.getHeight() + margin * 2);
	}

	void draw(Graphics2D graphics, ParsedChat chat, Rectangle bounds, Object actor, long nowMillis, Color shadow)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		int textWidth = metrics.stringWidth(chat.text);
		int margin = chat.motion == Motion.NONE ? 0 : EFFECT_MARGIN;
		int startX = bounds.x + margin + Math.max(0, (bounds.width - margin * 2 - textWidth) / 2);
		int baseline = bounds.y + margin + metrics.getAscent();
		Shape oldClip = graphics.getClip();
		graphics.clip(bounds);
		long phase = Math.floorMod(nowMillis, 4000L);
		int x = startX + wholeTextX(chat.motion, phase, bounds.width, textWidth);
		for (int i = 0; i < chat.text.length(); i++)
		{
			String character = chat.text.substring(i, i + 1);
			int y = baseline + characterY(chat.motion, phase, i);
			int charX = x + characterX(chat.motion, phase, i);
			Color color = color(chat, actor, nowMillis, i);
			graphics.setColor(shadow);
			graphics.drawString(character, charX + 1, y + 1);
			graphics.setColor(color);
			graphics.drawString(character, charX, y);
			x += metrics.stringWidth(character);
		}
		graphics.setClip(oldClip);
	}

	static Color color(ParsedChat chat, Object actor, long nowMillis, int characterIndex)
	{
		long phase = Math.floorMod(nowMillis, 4000L);
		ColorMode mode = characterIndex < chat.colorModes.length ? chat.colorModes[characterIndex] : chat.colorMode;
		Color solidColor = characterIndex < chat.colors.length ? chat.colors[characterIndex] : chat.color;
		switch (mode)
		{
			case RANDOM:
				long bucket = Math.floorDiv(nowMillis, RANDOM_BUCKET_MILLIS);
				int seed = System.identityHashCode(actor) * 31 + chat.text.hashCode() + characterIndex * 131;
				return RANDOM_COLORS[Math.floorMod(mix(seed, bucket), RANDOM_COLORS.length)];
			case FLASH1:
				return (phase / 250L) % 2 == 0 ? Color.RED : Color.YELLOW;
			case FLASH2:
				return (phase / 250L) % 2 == 0 ? Color.CYAN : Color.BLUE;
			case FLASH3:
				return (phase / 250L) % 2 == 0 ? new Color(0x80FF80) : new Color(0x008000);
			case GLOW1:
				return cycle(phase, characterIndex, Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN);
			case GLOW2:
				return cycle(phase, characterIndex, Color.RED, Color.MAGENTA, Color.BLUE, new Color(0x800000));
			case GLOW3:
				return cycle(phase, characterIndex, Color.WHITE, Color.GREEN, Color.WHITE, Color.CYAN);
			default:
				return solidColor;
		}
	}

	private static int mix(int seed, long bucket)
	{
		long value = bucket ^ seed;
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdl;
		value ^= value >>> 33;
		return (int) value;
	}

	private static Color cycle(long phase, int characterIndex, Color... colors)
	{
		return colors[Math.floorMod((int) (phase / 200L) + characterIndex / 2, colors.length)];
	}

	private static int characterY(Motion motion, long phase, int index)
	{
		if (motion == Motion.WAVE || motion == Motion.WAVE2)
		{
			return (int) Math.round(Math.sin((phase / 180.0) + index * (motion == Motion.WAVE ? 0.7 : 0.45)) * 5.0);
		}
		if (motion == Motion.SHAKE)
		{
			return Math.floorMod(mix(index, phase / 80L), 7) - 3;
		}
		if (motion == Motion.SLIDE)
		{
			return (int) Math.round(-7.0 * Math.cos(phase * Math.PI / 2000.0));
		}
		return 0;
	}

	private static int characterX(Motion motion, long phase, int index)
	{
		if (motion == Motion.WAVE2)
		{
			return (int) Math.round(Math.sin((phase / 210.0) + index * 0.55) * 3.0);
		}
		if (motion == Motion.SHAKE)
		{
			return Math.floorMod(mix(index + 17, phase / 80L), 5) - 2;
		}
		return 0;
	}

	private static int wholeTextX(Motion motion, long phase, int boundsWidth, int textWidth)
	{
		if (motion != Motion.SCROLL)
		{
			return 0;
		}
		int distance = boundsWidth + textWidth;
		return boundsWidth - (int) (distance * phase / 4000L);
	}

	private static Prefix leadingPrefix(String text)
	{
		if (text.startsWith("@") && text.length() >= 5 && text.charAt(4) == '@')
		{
			return new Prefix(text.substring(1, 4), 5);
		}
		int colon = text.indexOf(':');
		if (colon > 0 && colon <= 7)
		{
			String code = text.substring(0, colon).toLowerCase(Locale.ROOT);
			if (builtInColor(code) != null || motion(code) != null)
			{
				return new Prefix(code, colon + 1);
			}
		}
		return null;
	}

	private static ColorMode builtInColor(String code)
	{
		switch (code)
		{
			case "yellow": case "red": case "green": case "cyan": case "purple": case "white": return ColorMode.SOLID;
			case "flash1": return ColorMode.FLASH1;
			case "flash2": return ColorMode.FLASH2;
			case "flash3": return ColorMode.FLASH3;
			case "glow1": return ColorMode.GLOW1;
			case "glow2": return ColorMode.GLOW2;
			case "glow3": return ColorMode.GLOW3;
			default: return null;
		}
	}

	private static Color builtInSolidColor(String code)
	{
		switch (code)
		{
			case "red": return Color.RED;
			case "green": return Color.GREEN;
			case "cyan": return Color.CYAN;
			case "purple": return new Color(0x800080);
			case "white": return Color.WHITE;
			default: return Color.YELLOW;
		}
	}

	private static Motion motion(String code)
	{
		switch (code)
		{
			case "wave": return Motion.WAVE;
			case "wave2": return Motion.WAVE2;
			case "shake": return Motion.SHAKE;
			case "slide": return Motion.SLIDE;
			case "scroll": return Motion.SCROLL;
			default: return null;
		}
	}

	private static Map<String, Color> retroColors()
	{
		Map<String, Color> colors = new LinkedHashMap<>();
		colors.put("red", new Color(0xFF0000)); colors.put("dre", new Color(0x800000));
		colors.put("lre", new Color(0xFF6060)); colors.put("ora", new Color(0xFF9040));
		colors.put("or1", new Color(0xFF7F00)); colors.put("or2", new Color(0xC06000));
		colors.put("or3", new Color(0xFF4000)); colors.put("yel", new Color(0xFFFF00));
		colors.put("gr1", new Color(0xC0FF00)); colors.put("gre", new Color(0x00C000));
		colors.put("gr2", new Color(0x40FF40)); colors.put("gr3", new Color(0x80FF00));
		colors.put("blu", new Color(0x0000FF)); colors.put("cya", new Color(0x00FFFF));
		colors.put("mag", new Color(0xFF00FF)); colors.put("bla", Color.BLACK);
		colors.put("whi", Color.WHITE);
		return Collections.unmodifiableMap(colors);
	}

	static final class ParsedChat
	{
		final String text;
		final Color color;
		final ColorMode colorMode;
		final Motion motion;
		final Color[] colors;
		final ColorMode[] colorModes;

		private ParsedChat(String text, Color color, ColorMode colorMode, Motion motion,
			Color[] colors, ColorMode[] colorModes)
		{
			this.text = text;
			this.color = color;
			this.colorMode = colorMode;
			this.motion = motion;
			this.colors = colors;
			this.colorModes = colorModes;
		}
	}

	enum ColorMode { SOLID, RANDOM, FLASH1, FLASH2, FLASH3, GLOW1, GLOW2, GLOW3 }
	enum Motion { NONE, WAVE, WAVE2, SHAKE, SLIDE, SCROLL }

	private static final class Prefix
	{
		private final String code;
		private final int length;
		private Prefix(String code, int length) { this.code = code; this.length = length; }
	}
}
