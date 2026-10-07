package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardFrameBuffer;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;

public class WorldObjectSpriteOcclusionTest
{
	@Test
	public void cachedWorldSpriteKeepsWallOcclusionAcrossLiveHeightAndProfileChanges()
	{
		assertFrames(BillboardOcclusionQuality.HIGH, 0);
	}

	@Test
	public void disabledOcclusionStillShowsTheWholeWorldSprite()
	{
		assertFrames(BillboardOcclusionQuality.OFF, 0xFFFF0000);
	}

	private static void assertFrames(BillboardOcclusionQuality quality, int expectedCoveredPixel)
	{
		Rectangle bounds = new Rectangle(0, 0, 4, 4);
		BufferedImage sprite = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++)
		{
			for (int x = 0; x < 4; x++)
			{
				sprite.setRGB(x, y, 0xFFFF0000);
			}
		}
		BillboardRenderResult cachedSprite = new BillboardRenderResult(bounds, sprite, bounds);
		GameObject object = proxy(GameObject.class);
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(proxy(Client.class,
			method("getCameraFpY", -1000.0f), method("getCameraPitch", 2048),
			method("get3dZoom", 512), method("getViewportHeight", 4)));
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		BillboardFrameBuffer buffer = new BillboardFrameBuffer(mask, new BillboardPerformanceMetrics(),
			draw -> BillboardDepthSurface.from(calculator, draw, 0));
		for (int height : new int[] {100, 0, 100, 1, 32, 1000, 0, 100})
		{
			Model liveModel = proxy(Model.class, method("getVerticesCount", 3),
				method("getVerticesX", new float[] {-50, 50, 0}),
				method("getVerticesY", new float[] {0, 0, -height}), method("getVerticesZ", new float[3]));
			BillboardRenderRequest request = new BillboardRenderRequest(
				proxy(Renderable.class, method("getModelHeight", height)), liveModel, new LocalPoint(0, 0),
				0, 0, 0, 0, -1, -1, -1, -1, -1, false, false, null, null,
				VerticalAnchor.BOTTOM, null, height <= 32);
			mask.prepare(List.of(new BillboardOcclusionMask.Occluder(new Rectangle(0, 0, 2, 4), 500, "wall")),
				quality, 0, 0, 4, 4, bounds);
			buffer.begin(4, 4);
			buffer.blit(new PreparedBillboardDraw(request, cachedSprite, 1, object), 0, 0, 4, 4, false);
			for (int y = 0; y < 4; y++)
			{
				assertEquals("Live model height " + height, expectedCoveredPixel, buffer.image().getRGB(0, y));
				assertEquals(0xFFFF0000, buffer.image().getRGB(3, y));
			}
		}
	}
}
