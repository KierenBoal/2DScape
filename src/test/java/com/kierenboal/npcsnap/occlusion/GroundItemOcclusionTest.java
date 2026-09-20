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
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroundItemOcclusionTest
{
	private static final Rectangle BOUNDS = new Rectangle(0, 0, 4, 4);

	@Test
	public void flatAndZeroHeightItemsUseActorDepthPlane()
	{
		BillboardDepthCalculator calculator = calculator();
		for (int height : new int[] {0, 1, 32, 100})
		{
			BillboardDepthSurface surface = BillboardDepthSurface.from(calculator,
				request(height, true, flatModel(), new LocalPoint(0, 0)), BOUNDS, BOUNDS, 0);
			assertTrue(surface.supportsWorldOcclusion());
			for (int row = 0; row < BOUNDS.height; row++)
			{
				double expected = calculator.cameraForwardDepthOnVerticalPlane(0, 0, row);
				assertEquals(expected, surface.depthAtRow(4, row), 0.001d);
				assertEquals(expected, surface.depthAt(0, row, 4, 4, row), 0.001d);
			}
		}
	}

	@Test
	public void inventorySpriteWithoutModelStillHasOcclusionButMissingLocationDoesNot()
	{
		assertTrue(BillboardDepthSurface.from(calculator(),
			request(0, false, null, new LocalPoint(0, 0)), BOUNDS, BOUNDS, 0).supportsWorldOcclusion());
		assertFalse(BillboardDepthSurface.from(calculator(),
			request(0, true, flatModel(), null), BOUNDS, BOUNDS, 0).supportsWorldOcclusion());
	}

	@Test
	public void itemPixelsRespectPartialWallsTransparencyAndDisabledOcclusion()
	{
		for (boolean inventorySprite : new boolean[] {false, true})
		{
			BillboardRenderRequest request = request(0, true, inventorySprite ? null : flatModel(), new LocalPoint(0, 0));
			for (BillboardOcclusionQuality quality : BillboardOcclusionQuality.values())
			{
				if (quality == BillboardOcclusionQuality.BLOCKY)
				{
					continue; // Exact partial-wall edges are intentionally absent in Blocky.
				}
				assertPixels(request, quality, 500f, 0,
					quality == BillboardOcclusionQuality.OFF ? 0xFFFF0000 : 0);
				assertPixels(request, quality, 5000f, 0, 0xFFFF0000);
				assertPixels(request, quality, 500f, 128,
					quality == BillboardOcclusionQuality.OFF ? 0xFFFF0000 : 0xFFFF7F7F);
			}
		}
	}

	private static void assertPixels(BillboardRenderRequest request, BillboardOcclusionQuality quality,
		float occluderDepth, int transmittance, int expectedCoveredPixel)
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(Collections.singletonList(new BillboardOcclusionMask.Occluder(
			new Rectangle(0, 0, 2, 4), occluderDepth, "wall", transmittance)),
			quality, BillboardOcclusionComposition.TRANSPARENCY_AWARE, 0, 0, 4, 4, BOUNDS, Collections.singletonList(BOUNDS));
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++)
		{
			for (int x = 0; x < 4; x++)
			{
				image.setRGB(x, y, 0xFFFF0000);
			}
		}
		PreparedBillboardDraw draw = new PreparedBillboardDraw(request, new BillboardRenderResult(BOUNDS, image, BOUNDS), 1);
		BillboardFrameBuffer buffer = new BillboardFrameBuffer(mask, new BillboardPerformanceMetrics(),
			d -> BillboardDepthSurface.from(calculator(), d.request, d.sourceBounds, d.bounds, 0));
		buffer.begin(4, 4);
		buffer.blit(draw, 0, 0, 4, 4, false);
		for (int y = 0; y < 4; y++)
		{
			assertEquals(quality.name(), expectedCoveredPixel, buffer.image().getRGB(0, y));
			assertEquals(quality.name(), 0xFFFF0000, buffer.image().getRGB(3, y));
		}
	}

	private static BillboardRenderRequest request(int height, boolean lowProfile, Model model, LocalPoint point)
	{
		return new BillboardRenderRequest(proxy(TileItem.class, method("getModelHeight", height)), model,
			point, 0, 0, 0, 0, -1, -1, -1, -1, -1, false, false, null, null,
			VerticalAnchor.BOTTOM, null, lowProfile);
	}

	private static Model flatModel()
	{
		return proxy(Model.class, method("getVerticesCount", 4),
			method("getVerticesX", new float[] {-50, 50, 50, -50}),
			method("getVerticesY", new float[] {0, 0, 0, 0}),
			method("getVerticesZ", new float[] {-50, -50, 50, 50}));
	}

	private static BillboardDepthCalculator calculator()
	{
		return new BillboardDepthCalculator(proxy(Client.class,
			method("getCameraFpY", -1000.0f), method("getCameraPitch", 2048),
			method("get3dZoom", 512), method("getViewportHeight", 4)));
	}
}
