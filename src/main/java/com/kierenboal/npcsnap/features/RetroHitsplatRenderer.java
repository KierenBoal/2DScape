package com.kierenboal.npcsnap.features;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.runelite.api.HitsplatID;

/** Flat vector demakes of native hitsplats. No game sprites or textures are required. */
public final class RetroHitsplatRenderer
{
	private static final Color GOLD = new Color(0xFFD65A);
	private static final Color TINTED_DAMAGE_BORDER = new Color(0x752B28);
	private static final Color TINTED_BLOCK_BORDER = new Color(0x252F72);
	private static final Color BURN_CENTER = new Color(0xBA0000);
	private static final Stroke OUTLINE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
	private static final Stroke TRIM_BACKING = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
	private static final Map<Integer, UnknownStyle> UNKNOWN_STYLES = new HashMap<>();
	private static final int UNKNOWN_BLOCK_SIZE = 32;

	enum UnknownShape { CIRCLE, SQUARE, TRIANGLE }

	private static final class UnknownStyle
	{
		final Color color;
		final UnknownShape shape;

		UnknownStyle(Color color, UnknownShape shape)
		{
			this.color = color;
			this.shape = shape;
		}
	}

	enum Style
	{
		DAMAGE(0xE51B17, 0xE51B17, null), BLOCK(0x3155D9, 0x3155D9, null),
		SHIELD(0x0DAB93, 0x08737D, polygon(1, 1, 19, 1, 19, 12, 16, 17, 10, 20, 4, 17, 1, 12)),
		ARMOUR(0xBD4B00, 0xA72400, polygon(5, 2, 8, 0, 12, 0, 15, 2, 20, 4, 18, 9, 15, 7,
			15, 16, 12, 20, 8, 20, 5, 16, 5, 7, 2, 9, 0, 4)),
		CHARGE(0xBCBD00, 0x666600, arrow(true)), UNCHARGE(0x9A9A9A, 0x343826, arrow(false)),
		ALT_CHARGE(0x086758, 0x086758, arrow(true)), ALT_UNCHARGE(0x05454B, 0x05454B, arrow(false)),
		POISE(0xBCBD00, 0x6E6C00, poise()),
		POISON(0x00C000, 0x00C000, burst(12, 0.68)), VENOM(0x40957E, 0x40957E, burst(12, 0.68)),
		DISEASE(0xFCB712, 0xFCB712, burst(10, 0.64)),
		HEAL(0x9813AA, 0x9813AA, polygon(6, 0, 14, 0, 14, 6, 20, 6, 20, 14, 14, 14,
			14, 20, 6, 20, 6, 14, 0, 14, 0, 6, 6, 6)),
		PRAYER(0x6D4AB2, 0x453369, arrow(false)),
		CORRUPTION(0x9030FF, 0x9030FF, polygon(3, 0, 8, 5, 12, 5, 17, 0, 20, 3, 15, 8, 15, 12,
			20, 17, 17, 20, 12, 15, 8, 15, 3, 20, 0, 17, 5, 12, 5, 8, 0, 3)),
		BLEED(0xF30000, 0xF30000, burst(10, 0.68)), BURN(0xF88100, 0xF88100, burst(8, 0.46)),
		SANITY(0x89626D, 0x89626D, brain()), SANITY_RESTORE(0x9813AA, 0x9813AA, brain()),
		DOOM(0x343826, 0x343826, skull()),
		UNKNOWN(0xFFFFFF, 0xFFFFFF, new Ellipse2D.Double(0, 0, 20, 20));

		final Color fill;
		final Color tintedFill;
		final Color detail;
		final Shape shape;

		Style(int fill, int tintedFill, Shape shape)
		{
			this.fill = new Color(fill);
			this.tintedFill = new Color(tintedFill);
			this.detail = this.fill.darker();
			this.shape = shape;
		}
	}

	private RetroHitsplatRenderer()
	{
	}

	static Style style(int type, int amount)
	{
		switch (type)
		{
			case HitsplatID.BLOCK_ME: case HitsplatID.BLOCK_OTHER: return Style.BLOCK;
			case HitsplatID.DAMAGE_ME: case HitsplatID.DAMAGE_OTHER: case HitsplatID.DAMAGE_MAX_ME: return Style.DAMAGE;
			case HitsplatID.DAMAGE_ME_CYAN: case HitsplatID.DAMAGE_OTHER_CYAN: case HitsplatID.DAMAGE_MAX_ME_CYAN: return Style.SHIELD;
			case HitsplatID.DAMAGE_ME_ORANGE: case HitsplatID.DAMAGE_OTHER_ORANGE: case HitsplatID.DAMAGE_MAX_ME_ORANGE: return Style.ARMOUR;
			case HitsplatID.DAMAGE_ME_YELLOW: case HitsplatID.DAMAGE_OTHER_YELLOW: case HitsplatID.DAMAGE_MAX_ME_YELLOW: return Style.CHARGE;
			case HitsplatID.DAMAGE_ME_WHITE: case HitsplatID.DAMAGE_OTHER_WHITE: case HitsplatID.DAMAGE_MAX_ME_WHITE: return Style.UNCHARGE;
			case HitsplatID.CYAN_UP: return Style.ALT_CHARGE;
			case HitsplatID.CYAN_DOWN: return Style.ALT_UNCHARGE;
			case HitsplatID.DAMAGE_ME_POISE: case HitsplatID.DAMAGE_OTHER_POISE: case HitsplatID.DAMAGE_MAX_ME_POISE: return Style.POISE;
			case HitsplatID.POISON: return Style.POISON;
			case HitsplatID.VENOM: return Style.VENOM;
			case HitsplatID.DISEASE: case HitsplatID.DISEASE_BLOCKED: return Style.DISEASE;
			case HitsplatID.HEAL: return Style.HEAL;
			case HitsplatID.PRAYER_DRAIN: return Style.PRAYER;
			case HitsplatID.CORRUPTION: return Style.CORRUPTION;
			case HitsplatID.BLEED: return Style.BLEED;
			case HitsplatID.BURN: return Style.BURN;
			case HitsplatID.SANITY_DRAIN: return Style.SANITY;
			case HitsplatID.SANITY_RESTORE: return Style.SANITY_RESTORE;
			case HitsplatID.DOOM: return Style.DOOM;
			default: return Style.UNKNOWN;
		}
	}

	static boolean tinted(int type)
	{
		switch (type)
		{
			case HitsplatID.BLOCK_OTHER: case HitsplatID.DAMAGE_OTHER:
			case HitsplatID.DAMAGE_OTHER_CYAN: case HitsplatID.DAMAGE_OTHER_ORANGE:
			case HitsplatID.DAMAGE_OTHER_YELLOW: case HitsplatID.DAMAGE_OTHER_WHITE:
			case HitsplatID.DAMAGE_OTHER_POISE: return true;
			default: return false;
		}
	}

	static boolean maxHit(int type)
	{
		switch (type)
		{
			case HitsplatID.DAMAGE_MAX_ME: case HitsplatID.DAMAGE_MAX_ME_CYAN:
			case HitsplatID.DAMAGE_MAX_ME_ORANGE: case HitsplatID.DAMAGE_MAX_ME_YELLOW:
			case HitsplatID.DAMAGE_MAX_ME_WHITE: case HitsplatID.DAMAGE_MAX_ME_POISE: return true;
			default: return false;
		}
	}

	static Color fill(int type, int amount)
	{
		Style style = style(type, amount);
		if (style == Style.UNKNOWN)
		{
			return unknownStyle(type).color;
		}
		return tinted(type) ? style.tintedFill : style.fill;
	}

	/** Stable, conspicuous pastel: two full/half channels and one intermediate channel. */
	private static Color rollUnknownColor(Random random)
	{
		int[] channels = {random.nextBoolean() ? 255 : 128, random.nextBoolean() ? 255 : 128,
			129 + random.nextInt(126)};
		int intermediate = random.nextInt(3);
		int temporary = channels[intermediate];
		channels[intermediate] = channels[2];
		channels[2] = temporary;
		return new Color(channels[0], channels[1], channels[2]);
	}

	static UnknownShape unknownShape(int type)
	{
		return unknownStyle(type).shape;
	}

	private static UnknownStyle rollUnknownStyle(int type, UnknownStyle previous, UnknownStyle next)
	{
		Random random = new Random(type * 0x9E3779B97F4A7C15L);
		Color color;
		do
		{
			color = rollUnknownColor(random);
		}
		while ((previous != null && color.equals(previous.color)) || (next != null && color.equals(next.color)));
		UnknownShape shape;
		do
		{
			shape = UnknownShape.values()[random.nextInt(3)];
		}
		while ((previous != null && shape == previous.shape) || (next != null && shape == next.shape));
		return new UnknownStyle(color, shape);
	}

	private static UnknownStyle unknownStyle(int type)
	{
		UnknownStyle cached = UNKNOWN_STYLES.get(type);
		if (cached != null)
		{
			return cached;
		}
		// Fixed-size ID blocks keep generation bounded and independent of lookup
		// order. The last entry also avoids the next block's seeded anchor, so
		// neighbours cannot repeat across a block boundary either.
		int start = type & ~(UNKNOWN_BLOCK_SIZE - 1);
		UnknownStyle next = rollUnknownStyle(start + UNKNOWN_BLOCK_SIZE, null, null);
		UnknownStyle previous = null;
		for (int offset = 0; offset < UNKNOWN_BLOCK_SIZE; offset++)
		{
			int id = start + offset;
			UnknownStyle generated = rollUnknownStyle(id, previous, offset == UNKNOWN_BLOCK_SIZE - 1 ? next : null);
			UNKNOWN_STYLES.put(id, generated);
			previous = generated;
		}
		return UNKNOWN_STYLES.get(type);
	}

	static int unknownWidth(int type, int diameter)
	{
		return unknownShape(type) == UnknownShape.TRIANGLE ? diameter * 2 : diameter;
	}

	private static Shape unknownGeometry(int type, int centerX, int centerY, int diameter)
	{
		double top = centerY - diameter / 2.0, left = centerX - diameter / 2.0;
		switch (unknownShape(type))
		{
			case SQUARE: return new Rectangle2D.Double(left, top, diameter, diameter);
			case TRIANGLE:
				// A wider base leaves room for the number below the pointed tip.
				return polygon(centerX, top, centerX + diameter, top + diameter, centerX - diameter, top + diameter);
			default: return new Ellipse2D.Double(left, top, diameter, diameter);
		}
	}

	static Color border(int type, int amount)
	{
		if (maxHit(type))
		{
			return GOLD;
		}
		Style style = style(type, amount);
		return tinted(type) && (style == Style.DAMAGE || style == Style.BLOCK)
			? (style == Style.DAMAGE ? TINTED_DAMAGE_BORDER : TINTED_BLOCK_BORDER) : Color.BLACK;
	}

	/** Caller supplies the existing hitsplat font and the number's baseline. */
	public static void draw(Graphics2D graphics, int type, int amount, int centerX, int baselineY)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			FontMetrics metrics = g.getFontMetrics();
			String text = Integer.toString(amount);
			int width = Math.max(20, metrics.stringWidth(text) + 12);
			int x = centerX - width / 2, y = baselineY - 15;
			Style style = style(type, amount);
			boolean classic = style == Style.DAMAGE || style == Style.BLOCK;
			Shape shape = style == Style.UNKNOWN
				? unknownGeometry(type, centerX, baselineY - 5, width)
				: classic ? classicStar(x, y, width, 20)
				: new AffineTransform(width / 20.0, 0, 0, 1, x, y).createTransformedShape(style.shape);
			g.setStroke(maxHit(type) ? TRIM_BACKING : OUTLINE);
			g.setColor(Color.BLACK);
			g.draw(shape);
			g.setColor(fill(type, amount));
			g.fill(shape);
			g.setStroke(OUTLINE);
			g.setColor(border(type, amount));
			g.draw(shape);
			if (!classic && style != Style.UNKNOWN)
			{
				details(g, style, x, y, width);
			}
			int textX = centerX - metrics.stringWidth(text) / 2;
			g.setColor(Color.BLACK);
			g.drawString(text, textX + 1, baselineY + 1);
			g.setColor(Color.WHITE);
			g.drawString(text, textX, baselineY);
		}
		finally
		{
			g.dispose();
		}
	}

	private static void details(Graphics2D g, Style style, int x, int y, int width)
	{
		g.setColor(style.detail);
		if (style == Style.SHIELD)
		{
			g.drawLine(x + 2, y + 5, x + width - 2, y + 5);
			g.drawLine(x + width / 2, y + 2, x + width / 2, y + 18);
		}
		else if (style == Style.SANITY || style == Style.SANITY_RESTORE)
		{
			g.drawLine(x + width / 2, y + 2, x + width / 2, y + 18);
			g.drawPolyline(new int[] {x + width / 4, x + width / 3, x + width / 4}, new int[] {y + 4, y + 8, y + 16}, 3);
			g.drawPolyline(new int[] {x + 3 * width / 4, x + 2 * width / 3, x + 3 * width / 4}, new int[] {y + 4, y + 8, y + 16}, 3);
		}
		else if (style == Style.DOOM)
		{
			g.setColor(Color.BLACK);
			g.fillRect(x + width / 4, y + 6, Math.max(2, width / 5), 3);
			g.fillRect(x + 3 * width / 5, y + 6, Math.max(2, width / 5), 3);
		}
		else if (style == Style.BURN)
		{
			g.setColor(BURN_CENTER);
			g.fillOval(x + width / 4, y + 5, width / 2, 10);
		}
	}

	private static Shape polygon(double... coordinates)
	{
		Path2D.Double path = new Path2D.Double();
		path.moveTo(coordinates[0], coordinates[1]);
		for (int i = 2; i < coordinates.length; i += 2)
		{
			path.lineTo(coordinates[i], coordinates[i + 1]);
		}
		path.closePath();
		return path;
	}

	private static Shape arrow(boolean up)
	{
		Shape down = polygon(0, 0, 5, 3, 5, 12, 0, 12, 10, 20, 20, 12, 15, 12, 15, 3, 20, 0, 10, 4);
		return up ? new AffineTransform(1, 0, 0, -1, 0, 20).createTransformedShape(down) : down;
	}

	private static Shape burst(int points, double innerRadius)
	{
		Path2D.Double path = new Path2D.Double();
		for (int i = 0; i < points * 2; i++)
		{
			double angle = -Math.PI / 2 + i * Math.PI / points;
			double radius = (i % 2 == 0 ? 1 : innerRadius) * 10;
			double x = 10 + Math.cos(angle) * radius, y = 10 + Math.sin(angle) * radius;
			if (i == 0) { path.moveTo(x, y); } else { path.lineTo(x, y); }
		}
		path.closePath();
		return path;
	}

	private static Shape brain()
	{
		Path2D.Double path = new Path2D.Double();
		path.moveTo(10, 1);
		path.curveTo(6, -1, 3, 3, 3, 5);
		path.curveTo(-1, 6, -1, 12, 3, 14);
		path.curveTo(3, 18, 7, 21, 10, 19);
		path.curveTo(13, 21, 17, 18, 17, 14);
		path.curveTo(21, 12, 21, 6, 17, 5);
		path.curveTo(17, 3, 14, -1, 10, 1);
		path.closePath();
		return path;
	}

	private static Shape poise()
	{
		Path2D.Double path = new Path2D.Double();
		path.moveTo(1, 0);
		path.curveTo(3, 2, 3, 6, 5, 8);
		path.curveTo(8, 4, 14, 4, 17, 8);
		path.curveTo(19, 10, 17, 14, 19, 18);
		path.lineTo(20, 20);
		path.curveTo(14, 18, 19, 14, 16, 12);
		path.curveTo(12, 16, 5, 16, 3, 12);
		path.curveTo(1, 10, 3, 5, 1, 0);
		path.closePath();
		return path;
	}

	private static Shape skull()
	{
		return polygon(1, 2, 4, 5, 4, 2, 8, 0, 12, 0, 16, 2, 16, 5, 19, 2, 20, 7,
			17, 10, 16, 14, 14, 16, 14, 20, 6, 20, 6, 16, 4, 14, 3, 10, 0, 7);
	}

	static Polygon classicStar(int x, int y, int width, int height)
	{
		int right = x + width, bottom = y + height;
		int midX = x + width / 2, midY = y + height / 2;
		int bodyHalfWidth = Math.max(6, (width * 3) / 10), bodyHalfHeight = Math.max(5, height / 4);
		int spikeHalfWidth = Math.max(2, width / 10), spikeHalfHeight = Math.max(2, height / 10);
		int bodyLeft = midX - bodyHalfWidth, bodyRight = midX + bodyHalfWidth;
		int bodyTop = midY - bodyHalfHeight, bodyBottom = midY + bodyHalfHeight;
		return new Polygon(
			new int[] {midX - spikeHalfWidth, midX, midX + spikeHalfWidth,
				bodyRight, right - 2, bodyRight, right, bodyRight, right - 2,
				bodyRight, midX + spikeHalfWidth, midX, midX - spikeHalfWidth,
				bodyLeft, x + 2, bodyLeft, x, bodyLeft, x + 2, bodyLeft},
			new int[] {bodyTop, y, bodyTop, bodyTop, y + 2, midY - spikeHalfHeight, midY,
				midY + spikeHalfHeight, bottom - 2, bodyBottom, bodyBottom, bottom, bodyBottom,
				bodyBottom, bottom - 2, midY + spikeHalfHeight, midY, midY - spikeHalfHeight, y + 2, bodyTop}, 20);
	}
}
