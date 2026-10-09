package com.kierenboal.npcsnap.features;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.api.HitsplatID;
import org.junit.Test;

import static org.junit.Assert.*;

public class RetroHitsplatRendererTest
{
	@Test
	public void nativeTypeOverridesAmountIncludingSpecialistZeros()
	{
		assertEquals(new Color(0x3155D9), RetroHitsplatRenderer.fill(HitsplatID.BLOCK_ME, 12));
		assertEquals(new Color(0xE51B17), RetroHitsplatRenderer.fill(HitsplatID.DAMAGE_ME, 0));
		assertEquals(new Color(0x00C000), RetroHitsplatRenderer.fill(HitsplatID.POISON, 0));
		assertEquals(new Color(0x40957E), RetroHitsplatRenderer.fill(HitsplatID.VENOM, 0));
		assertEquals(new Color(0xFCB712), RetroHitsplatRenderer.fill(HitsplatID.DISEASE_BLOCKED, 0));
		assertEquals(RetroHitsplatRenderer.Style.HEAL, RetroHitsplatRenderer.style(HitsplatID.HEAL, 0));
		assertEquals(RetroHitsplatRenderer.Style.SANITY, RetroHitsplatRenderer.style(HitsplatID.SANITY_DRAIN, 0));
		assertEquals(RetroHitsplatRenderer.Style.SANITY_RESTORE, RetroHitsplatRenderer.style(HitsplatID.SANITY_RESTORE, 0));
		assertEquals(RetroHitsplatRenderer.Style.DOOM, RetroHitsplatRenderer.style(HitsplatID.DOOM, 0));
		assertEquals(RetroHitsplatRenderer.Style.UNKNOWN, RetroHitsplatRenderer.style(-123, 0));
		assertEquals(RetroHitsplatRenderer.Style.UNKNOWN, RetroHitsplatRenderer.style(-123, 12));
	}

	@Test
	public void undocumentedIdsUseStableNonGreyPastelsAndVariedShapesWithoutAdjacentRepeats()
	{
		java.util.Set<Color> colors = new java.util.HashSet<>();
		java.util.Set<RetroHitsplatRenderer.UnknownShape> shapes = new java.util.HashSet<>();
		Color previousColor = null;
		RetroHitsplatRenderer.UnknownShape previousShape = null;
		for (int type = 1001; type <= 1032; type++)
		{
			Color color = RetroHitsplatRenderer.fill(type, 8);
			RetroHitsplatRenderer.UnknownShape shape = RetroHitsplatRenderer.unknownShape(type);
			colors.add(color);
			shapes.add(shape);
			assertNotEquals(previousColor, color);
			assertNotEquals(previousShape, shape);
			previousColor = color;
			previousShape = shape;
			assertEquals(color, RetroHitsplatRenderer.fill(type, 0));
			assertEquals(color, RetroHitsplatRenderer.fill(type, 1234));
			int endpoints = 0;
			for (int channel : new int[] {color.getRed(), color.getGreen(), color.getBlue()})
			{
				assertTrue(channel >= 128 && channel <= 255);
				if (channel == 128 || channel == 255) { endpoints++; }
			}
			assertEquals(2, endpoints);
			assertFalse(color.getRed() == color.getGreen() && color.getGreen() == color.getBlue());
			assertFalse(RetroHitsplatRenderer.maxHit(type));
			assertEquals(Color.BLACK, RetroHitsplatRenderer.border(type, 1234));
			for (int amount : new int[] {0, 8, 1234})
			{
				BufferedImage image = RetroHitsplatPreview.render(type, amount, 80);
				int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
				for (int y = 0; y < image.getHeight(); y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						if (image.getRGB(x, y) != 0)
						{
							left = Math.min(left, x); right = Math.max(right, x);
							top = Math.min(top, y); bottom = Math.max(bottom, y);
						}
					}
				}
				if (shape != RetroHitsplatRenderer.UnknownShape.TRIANGLE)
				{
					assertEquals("Circle and square retain equal dimensions", right - left, bottom - top, 1);
				}
				else
				{
					assertTrue("Triangle has a wider base to fit its number", right - left > bottom - top);
				}
			}
		}
		assertTrue("The sheet should have a broad palette", colors.size() >= 24);
		assertEquals(3, shapes.size());
		// Reverse lookup must not re-roll an already assigned descriptor.
		for (int type = 1032; type >= 1001; type--)
		{
			assertEquals(RetroHitsplatRenderer.fill(type, 0), RetroHitsplatRenderer.fill(type, 8));
			if (type > 1001)
			{
				assertNotEquals(RetroHitsplatRenderer.fill(type, 8), RetroHitsplatRenderer.fill(type - 1, 8));
				assertNotEquals(RetroHitsplatRenderer.unknownShape(type), RetroHitsplatRenderer.unknownShape(type - 1));
			}
		}
	}

	@Test
	public void variantsPreserveSilhouettesAndOnlyClassicBordersAreTinted()
	{
		int[][] families = {
			{HitsplatID.DAMAGE_ME, HitsplatID.DAMAGE_OTHER, HitsplatID.DAMAGE_MAX_ME},
			{HitsplatID.DAMAGE_ME_CYAN, HitsplatID.DAMAGE_OTHER_CYAN, HitsplatID.DAMAGE_MAX_ME_CYAN},
			{HitsplatID.DAMAGE_ME_ORANGE, HitsplatID.DAMAGE_OTHER_ORANGE, HitsplatID.DAMAGE_MAX_ME_ORANGE},
			{HitsplatID.DAMAGE_ME_YELLOW, HitsplatID.DAMAGE_OTHER_YELLOW, HitsplatID.DAMAGE_MAX_ME_YELLOW},
			{HitsplatID.DAMAGE_ME_WHITE, HitsplatID.DAMAGE_OTHER_WHITE, HitsplatID.DAMAGE_MAX_ME_WHITE},
			{HitsplatID.DAMAGE_ME_POISE, HitsplatID.DAMAGE_OTHER_POISE, HitsplatID.DAMAGE_MAX_ME_POISE}
		};
		for (int[] family : families)
		{
			assertEquals(RetroHitsplatRenderer.style(family[0], 8), RetroHitsplatRenderer.style(family[1], 8));
			assertEquals(RetroHitsplatRenderer.style(family[0], 8), RetroHitsplatRenderer.style(family[2], 8));
			assertFalse(RetroHitsplatRenderer.maxHit(family[0]));
			assertTrue(RetroHitsplatRenderer.tinted(family[1]));
			assertTrue(RetroHitsplatRenderer.maxHit(family[2]));
			assertEquals(new Color(0xFFD65A), RetroHitsplatRenderer.border(family[2], 8));
			if (family[0] == HitsplatID.DAMAGE_ME)
			{
				assertEquals(RetroHitsplatRenderer.fill(family[0], 8), RetroHitsplatRenderer.fill(family[1], 8));
			}
			else
			{
				assertNotEquals(RetroHitsplatRenderer.fill(family[0], 8), RetroHitsplatRenderer.fill(family[1], 8));
			}
		}
		assertEquals(RetroHitsplatRenderer.fill(HitsplatID.BLOCK_ME, 0), RetroHitsplatRenderer.fill(HitsplatID.BLOCK_OTHER, 0));
		assertNotEquals(RetroHitsplatRenderer.border(HitsplatID.BLOCK_ME, 0), RetroHitsplatRenderer.border(HitsplatID.BLOCK_OTHER, 0));
		assertNotEquals(RetroHitsplatRenderer.border(HitsplatID.DAMAGE_ME, 8), RetroHitsplatRenderer.border(HitsplatID.DAMAGE_OTHER, 8));
		assertEquals(Color.BLACK, RetroHitsplatRenderer.border(HitsplatID.DAMAGE_ME, Integer.MAX_VALUE));
	}

	@Test
	public void wideNumbersAndMaxTrimStayInsideThePreviewCanvas()
	{
		int[] types = {HitsplatID.DAMAGE_MAX_ME, HitsplatID.BLOCK_OTHER, HitsplatID.DAMAGE_MAX_ME_CYAN,
			HitsplatID.DAMAGE_MAX_ME_ORANGE, HitsplatID.DAMAGE_MAX_ME_YELLOW, HitsplatID.DAMAGE_MAX_ME_WHITE,
			HitsplatID.DAMAGE_MAX_ME_POISE, HitsplatID.CYAN_UP, HitsplatID.CYAN_DOWN, HitsplatID.POISON,
			HitsplatID.VENOM, HitsplatID.DISEASE, HitsplatID.DISEASE_BLOCKED, HitsplatID.HEAL,
			HitsplatID.PRAYER_DRAIN, HitsplatID.CORRUPTION, HitsplatID.BLEED, HitsplatID.BURN,
			HitsplatID.SANITY_DRAIN, HitsplatID.SANITY_RESTORE, HitsplatID.DOOM, 1001, 1002, 1003, 1004};
		for (int type : types)
		{
			for (int amount : new int[] {0, 8, 1234, Integer.MAX_VALUE})
			{
				BufferedImage image = RetroHitsplatPreview.render(type, amount, 140);
				int painted = 0, white = 0;
				for (int y = 0; y < image.getHeight(); y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						int pixel = image.getRGB(x, y);
						if (pixel != 0)
						{
							assertTrue("Clipped type " + type, x > 0 && x < image.getWidth() - 1 && y > 0 && y < image.getHeight() - 1);
							painted++;
							if (pixel == Color.WHITE.getRGB()) { white++; }
						}
					}
				}
				assertTrue(painted > 0);
				assertTrue("Number must remain visible", white > 0);
			}
		}
	}

	@Test
	public void drawingDoesNotLeakGraphicsSettings()
	{
		Graphics2D g = new BufferedImage(60, 40, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			BasicStroke stroke = new BasicStroke(5);
			g.setStroke(stroke);
			g.setColor(Color.MAGENTA);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			RetroHitsplatRenderer.draw(g, HitsplatID.DAMAGE_MAX_ME, 8, 30, 20);
			assertEquals(stroke, g.getStroke());
			assertEquals(Color.MAGENTA, g.getColor());
			assertEquals(RenderingHints.VALUE_ANTIALIAS_ON, g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
		}
		finally
		{
			g.dispose();
		}
	}
}
