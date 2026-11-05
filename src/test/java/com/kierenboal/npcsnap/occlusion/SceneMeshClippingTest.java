package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.client.plugins.gpu.GpuPlugin;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class SceneMeshClippingTest
{
	private Client client(boolean gpu)
	{
		return proxy(Client.class, method("getDrawCallbacks", gpu ? new GpuPlugin() : null),
			method("getScale", 100), method("getViewportWidth", 400), method("getViewportHeight", 400));
	}

	@Test public void rendererPolicyIsSeparateFromSpriteProjection()
	{
		BillboardDepthCalculator gpu = new BillboardDepthCalculator(client(true));
		assertEquals(100d, gpu.sceneNearDepth(), 0d);
		assertNotNull(gpu.projectCanvasPoint(0, 79, 0));
		assertEquals(50d, new BillboardDepthCalculator(client(false)).sceneNearDepth(), 0d);
	}

	@Test public void clippedFacesPreserveCoverageWithoutDoubleTransparency()
	{
		for (boolean gpu : new boolean[] {false, true})
		{
			BillboardDepthCalculator depth = new BillboardDepthCalculator(client(gpu));
			BillboardWorldOcclusionCollector collector = new BillboardWorldOcclusionCollector(client(gpu),
				new NpcSnapConfig() {}, depth, new BillboardPerformanceMetrics(), org.slf4j.LoggerFactory.getLogger(getClass()));
			for (double z : new double[] {-10, 49, 50, 79, 99, 100, 101, 200})
			{
				collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 400, 400), new Rectangle(0, 0, 400, 400));
				collector.addMeshTriangle(new double[] {-100, 0, z}, new double[] {100, 0, z},
					new double[] {0, -100, z}, "test", 128, 0xFFFFFF, null, 0, false);
				assertEquals(z >= depth.sceneNearDepth() ? 1 : 0, collector.occluders().size());
			}
			collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 400, 400), new Rectangle(0, 0, 400, 400));
			collector.addMeshTriangle(new double[] {-100, 0, 200}, new double[] {100, 0, 200},
				new double[] {0, -100, 20}, "test", 128, 0xFFFFFF, null, 0, false);
			assertEquals(2, collector.occluders().size());
			for (BillboardOcclusionQuality quality : BillboardOcclusionQuality.values())
			{
				if (quality == BillboardOcclusionQuality.OFF || quality == BillboardOcclusionQuality.BLOCKY) continue;
				BillboardOcclusionMask mask = new BillboardOcclusionMask();
				mask.prepare(collector.occluders(), quality, 0, 0, 400, 400);
				for (int y = 0; y < 400; y++) for (int x = 0; x < 400; x++)
				{
					int t = mask.transmittanceAt(x, y, 1000);
					assertTrue("Split face must only blend once", t == 128 || t == 255);
				}
			}
			collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 400, 400), new Rectangle(0, 0, 400, 400));
			collector.addMeshTriangle(new double[] {-100, 0, 20}, new double[] {100, 0, 200},
				new double[] {0, -100, 20}, "test", 0, 0xFFFFFF, null, 256, true);
			assertEquals(1, collector.occluders().size());
			collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 400, 400), new Rectangle(0, 0, 400, 400));
			collector.addMeshTriangle(new double[] {-100, 0, 200}, new double[] {0, -100, 200},
				new double[] {100, 0, 200}, "back", 0, 0xFFFFFF, null, 0, false);
			assertTrue(collector.occluders().isEmpty());
			collector.addMeshTriangle(new double[] {Double.NaN, 0, 200}, new double[] {0, 0, 200},
				new double[] {0, -100, 200}, "invalid", 0, 0xFFFFFF, null, 0, false);
			assertTrue(collector.occluders().isEmpty());
		}
	}
}
