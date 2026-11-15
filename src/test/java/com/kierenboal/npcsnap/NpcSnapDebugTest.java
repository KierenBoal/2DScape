package com.kierenboal.npcsnap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.kierenboal.npcsnap.TestProxies.proxy;

import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.state.BillboardRedrawReason;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import net.runelite.api.Client;
import org.junit.Test;

public class NpcSnapDebugTest
{
	@Test public void spriteBoundsAlsoShowCyanProjectedShadowPolygons()
	{
		java.util.concurrent.atomic.AtomicBoolean enabled=new java.util.concurrent.atomic.AtomicBoolean();
		NpcSnapConfig config=new NpcSnapConfig()
		{
			@Override public boolean debugDrawBillboardOutline() { return enabled.get(); }
		};
		assertEquals(false,new NpcSnapConfig() {}.debugDrawBillboardOutline());
		assertEquals(false,config.debugDrawBillboardOutline());
		NpcSnapDebug debug=new NpcSnapDebug(proxy(Client.class),config);
		BillboardDrawGeometry geometry=BillboardDrawGeometry.projected(
			com.kierenboal.npcsnap.rendering.BillboardProjectedQuad.create(
				new com.kierenboal.npcsnap.rendering.BillboardCanvasPoint(10,20,100,0,0),
				new com.kierenboal.npcsnap.rendering.BillboardCanvasPoint(40,10,100,0,0),
				new com.kierenboal.npcsnap.rendering.BillboardCanvasPoint(60,40,100,0,0),
				new com.kierenboal.npcsnap.rendering.BillboardCanvasPoint(30,50,100,0,0),
				new Rectangle(0,0,8,8),new Rectangle(0,0,8,8)));
		BufferedImage texture=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
		com.kierenboal.npcsnap.rendering.BillboardRenderResult second=new com.kierenboal.npcsnap.rendering.BillboardRenderResult(
			new Rectangle(70,60,10,10),texture,new Rectangle(8,8));
		com.kierenboal.npcsnap.rendering.BillboardRenderResult shadow=new com.kierenboal.npcsnap.rendering.BillboardRenderResult(
			geometry,texture,new Rectangle(8,8),second);
		BufferedImage image=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics=image.createGraphics();
		try
		{
			graphics.setColor(Color.ORANGE);
			java.awt.Stroke stroke=new java.awt.BasicStroke(3); graphics.setStroke(stroke);
			debug.drawShadowBounds(graphics,shadow);
			assertEquals(0,image.getRGB(10,20));
			enabled.set(true); debug.drawShadowBounds(graphics,shadow);
			assertEquals(Color.CYAN.getRGB(),image.getRGB(10,20));
			assertEquals(Color.CYAN.getRGB(),image.getRGB(40,10));
			assertEquals(Color.CYAN.getRGB(),image.getRGB(70,60));
			assertEquals(0,image.getRGB(10,10)); // Empty corner of the enclosing box.
			assertEquals(Color.ORANGE,graphics.getColor()); assertEquals(stroke,graphics.getStroke());
			debug.drawShadowBounds(graphics,null);
		}
		finally { graphics.dispose(); }
	}

	@Test
	public void metricsContextHasFourCompactLines()
	{
		NpcSnapDebug debug = new NpcSnapDebug(proxy(Client.class), new NpcSnapConfig() {});
		java.util.List<String> context = debug.performanceContext(java.util.Map.of(
			"Actors", 7L, "Items", 4L, "Effects", 5L, "Boats", 1L,
			"Faces available", 32_000L, "Faces inspected", 22_500L, "Faces accepted", 7_000L));
		assertEquals(4, context.size());
		assertEquals("Player (unavailable)", context.get(0));
		assertTrue(context.get(1).startsWith("Camera (X:"));
		assertTrue(context.get(1).contains("Pitch: 0, Yaw: 0, Zoom: 0"));
		assertTrue(context.get(2).contains("Actors: 7, Items: 4, Effects: 5, Boats: 1"));
		assertTrue(context.get(3).contains("Faces: 32.0k, Inspected: 22.5k, Accepted: 7.0k"));
		assertEquals("999", NpcSnapDebug.compactCount(999));
		assertEquals("1.2m", NpcSnapDebug.compactCount(1_200_000));
		assertTrue(NpcSnapDebug.performanceRowCapacityForContextHeight(600, 15, 4 * 12)
			> NpcSnapDebug.performanceRowCapacityForContextHeight(600, 15, 4 * 15));
	}

	@Test
	public void metricsCapacityUsesBothViewportMarginsAndResizesBeyondTwentyRows()
	{
		assertEquals(34, NpcSnapDebug.performanceRowCapacityForContextHeight(600, 15, 3 * 15));
		assertEquals(14, NpcSnapDebug.performanceRowCapacityForContextHeight(300, 15, 3 * 15));
		assertEquals(0, NpcSnapDebug.performanceRowCapacityForContextHeight(40, 15, 3 * 15));
		int rows = NpcSnapDebug.performanceRowCapacityForContextHeight(600, 15, 3 * 15);
		assertTrue(8 + 10 + (rows + 4) * 15 <= 600 - 8);
	}

	@Test
	public void metricsDrawingClipsToViewportMarginsIncludingTinyViews()
	{
		java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
		com.kierenboal.npcsnap.state.BillboardPerformanceMetrics timings =
			new com.kierenboal.npcsnap.state.BillboardPerformanceMetrics(clock::get);
		timings.beginFrame(true);
		clock.set(500_000_000L);
		timings.finishFrame();
		NpcSnapDebug debug = new NpcSnapDebug(proxy(Client.class), new NpcSnapConfig() {});
		for (int height : new int[] {40, 300, 600})
		{
			BufferedImage image = new BufferedImage(900, 700, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			Rectangle viewport = new Rectangle(20, 30, 800, height);
			try { debug.drawPerformanceMetrics(graphics, viewport, timings.rows(100), java.util.Collections.emptyMap()); }
			finally { graphics.dispose(); }
			Rectangle permitted = new Rectangle(28, 38, 784, height - 16);
			int painted = 0;
			for (int y = 0; y < image.getHeight(); y++)
				for (int x = 0; x < image.getWidth(); x++)
					if ((image.getRGB(x, y) >>> 24) != 0)
					{
						assertTrue(permitted.contains(x, y));
						painted++;
					}
			assertTrue(painted > 0);
		}
	}

	@Test
	public void redrawReasonsRenderInsideTheFrameOnlyWhenItWasRedrawn()
	{
		NpcSnapConfig config = new NpcSnapConfig()
		{
			@Override public boolean debugShowFrameRedraws() { return true; }
		};
		NpcSnapDebug debug = new NpcSnapDebug(proxy(Client.class), config);
		for (int width : new int[] {0, 1, 2, 3})
		{
			BufferedImage image = new BufferedImage(700, 200, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try
			{
				debug.drawBillboardDebugForeground(graphics, NpcSnapDebug.RenderDebug.forGeometry(
					BillboardDrawGeometry.rectangular(new Rectangle(20, 20, 180, 160)), 0, width, 123, false, null, null,
					EnumSet.of(BillboardRedrawReason.FRAME_COUNT, BillboardRedrawReason.ACTOR_RESIZE)));
			}
			finally
			{
				graphics.dispose();
			}
			int whitePixels = 0;
			for (int y = 80; y < 120; y++)
			{
				for (int x = 30; x < 670; x++)
				{
					if (image.getRGB(x, y) == Color.WHITE.getRGB())
					{
					whitePixels++;
					}
				}
			}
			assertTrue(width > 0 ? whitePixels > 0 : whitePixels == 0);
			// Sample the middle of the top edge, away from text and side strokes.
			int strokePixels = 0;
			for (int y = 10; y < 30; y++)
			{
				if ((image.getRGB(100, y) >>> 24) != 0) strokePixels++;
			}
			assertEquals(width, strokePixels);
		}
	}

	@Test
	public void redrawSettingAlsoMarksDelayedUpdates()
	{
		NpcSnapConfig config = new NpcSnapConfig()
		{
			@Override public boolean debugShowFrameRedraws() { return true; }
		};
		BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			new NpcSnapDebug(proxy(Client.class), config).drawBillboardDebugForeground(graphics,
				NpcSnapDebug.RenderDebug.forGeometry(
					BillboardDrawGeometry.rectangular(new Rectangle(20, 20, 40, 40)),
					0, 0, 0, true, Color.MAGENTA, null, EnumSet.noneOf(BillboardRedrawReason.class)));
		}
		finally
		{
			graphics.dispose();
		}
		assertEquals(Color.MAGENTA.getRGB(), image.getRGB(40, 18));
	}
}
