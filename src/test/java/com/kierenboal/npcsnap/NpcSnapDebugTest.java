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
			> NpcSnapDebug.performanceRowCapacity(600, 15, 4));
	}

	@Test
	public void metricsCapacityUsesBothViewportMarginsAndResizesBeyondTwentyRows()
	{
		assertEquals(34, NpcSnapDebug.performanceRowCapacity(600, 15, 3));
		assertEquals(14, NpcSnapDebug.performanceRowCapacity(300, 15, 3));
		assertEquals(0, NpcSnapDebug.performanceRowCapacity(40, 15, 3));
		int rows = NpcSnapDebug.performanceRowCapacity(600, 15, 3);
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
			try { debug.drawPerformanceMetrics(graphics, viewport, timings.rows(100)); }
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
		for (boolean redrawn : new boolean[] {false, true})
		{
			BufferedImage image = new BufferedImage(700, 200, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try
			{
				debug.drawBillboardDebugForeground(graphics, NpcSnapDebug.RenderDebug.forGeometry(
					BillboardDrawGeometry.rectangular(new Rectangle(20, 20, 180, 160)), 0, redrawn, false, null, null,
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
			assertTrue(redrawn ? whitePixels > 0 : whitePixels == 0);
		}
	}
}
