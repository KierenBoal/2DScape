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
	public void readyToRedrawColorScalesPriorityRange()
	{
		assertEquals(new Color(128, 0, 128, 255), NpcSnapDebug.readyToRedrawColor(0.0d, 0.0d, 100.0d));
		assertEquals(new Color(255, 0, 255, 255), NpcSnapDebug.readyToRedrawColor(100.0d, 0.0d, 100.0d));
		assertEquals(new Color(192, 0, 192, 255), NpcSnapDebug.readyToRedrawColor(50.0d, 0.0d, 100.0d));
	}

	@Test
	public void readyToRedrawColorUsesHighestPriorityWhenRangeCollapses()
	{
		assertEquals(new Color(255, 0, 255, 255), NpcSnapDebug.readyToRedrawColor(10.0d, 10.0d, 10.0d));
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
