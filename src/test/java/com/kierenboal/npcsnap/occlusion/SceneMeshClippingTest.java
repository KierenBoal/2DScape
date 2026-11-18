package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.TextureSample;
import com.kierenboal.npcsnap.rendering.TextureUvs;
import com.kierenboal.npcsnap.state.TextureCacheEntry;
import java.awt.Rectangle;
import java.util.List;
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
				new NpcSnapConfig() {}, depth, org.slf4j.LoggerFactory.getLogger(getClass()));
			for (double z : new double[] {-10, 49, 50, 79, 99, 100, 101, 200})
			{
				collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 400, 400), new Rectangle(0, 0, 400, 400));
				collector.addMeshTriangle(new double[] {-100, 0, z}, new double[] {100, 0, z},
					new double[] {0, -100, z}, "test", 128, 0xFFFFFF, null, 0);
				assertEquals(z >= depth.sceneNearDepth() ? 1 : 0, collector.occluders().size());
			}
			collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 400, 400), new Rectangle(0, 0, 400, 400));
			collector.addMeshTriangle(new double[] {-100, 0, 200}, new double[] {100, 0, 200},
				new double[] {0, -100, 20}, "test", 128, 0xFFFFFF, null, 0);
			assertEquals(2, collector.occluders().size());
			for (BillboardOcclusionQuality quality : BillboardOcclusionQuality.values())
			{
				if (quality == BillboardOcclusionQuality.OFF) continue;
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
				new double[] {0, -100, 20}, "test", 0, 0xFFFFFF, null, 256);
			assertEquals(1, collector.occluders().size());
			collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 400, 400), new Rectangle(0, 0, 400, 400));
			collector.addMeshTriangle(new double[] {-100, 0, 200}, new double[] {0, -100, 200},
				new double[] {100, 0, 200}, "back", 0, 0xFFFFFF, null, 0);
			assertTrue(collector.occluders().isEmpty());
			collector.addMeshTriangle(new double[] {Double.NaN, 0, 200}, new double[] {0, 0, 200},
				new double[] {0, -100, 200}, "invalid", 0, 0xFFFFFF, null, 0);
			assertTrue(collector.occluders().isEmpty());
		}
	}

	@Test public void clippedCutoutUsesPerspectiveCorrectCoverageAtEveryQuality()
	{
		Rectangle viewport = new Rectangle(0, 0, 400, 400);
		TextureSample cutout = new TextureSample(new TextureCacheEntry(new int[] {0, 1, 0, 1}, 2, 2, 0), 0, 0, 0);
		for (boolean gpu : new boolean[] {false, true})
		{
			Client client = client(gpu);
			BillboardWorldOcclusionCollector collector = new BillboardWorldOcclusionCollector(client, new NpcSnapConfig() {},
				new BillboardDepthCalculator(client), org.slf4j.LoggerFactory.getLogger(getClass()));
			collector.addInterest(viewport, viewport);
			collector.addMeshTriangle(new double[] {-100, 0, 200}, new double[] {100, 0, 200},
				new double[] {0, -100, 20}, "spray", 0, 0xFFFFFF, null, 0, null, cutout,
				new TextureUvs(.05f, .2f, .65f, .2f, .9f, .2f));
			assertEquals(2, collector.occluders().size());
			for (BillboardOcclusionQuality quality : BillboardOcclusionQuality.values())
			{
				if (quality == BillboardOcclusionQuality.OFF) continue;
				BillboardOcclusionMask mask = new BillboardOcclusionMask();
				mask.prepare(collector.occluders(), quality, collector.interest());
				assertEquals("A transparent texel does not block", 255, mask.transmittanceAt(174, 180, 1000));
				assertNull(mask.sourceAt(174, 180));
				assertEquals("Perspective interpolation places this pixel on the opaque side", 0,
					mask.transmittanceAt(203, 180, 1000));
				assertEquals("spray", mask.sourceAt(203, 180));
				assertFalse(mask.isOccluded(174, 180, 1000));
				assertTrue(mask.isOccluded(203, 180, 1000));
			}
		}
	}

	@Test public void disjointBillboardRegionsSampleOnlyTheirOwnPixelsAtEveryQuality()
	{
		Rectangle viewport = new Rectangle(90, 90, 80, 80);
		BillboardOcclusionRegions regions = new BillboardOcclusionRegions();
		regions.add(new Rectangle(110, 100, 3, 3), viewport);
		regions.add(new Rectangle(145, 100, 3, 3), viewport);
		TextureSample cutout = new TextureSample(new TextureCacheEntry(new int[] {0, 1, 0, 1}, 2, 2, 0), 0, 0, 0);
		BillboardOcclusionMask.Occluder spray = BillboardOcclusionMask.Occluder.triangle(
			96, 96, 200, 160, 96, 200, 96, 160, 200, "spray", 128, 0x00FF00);
		spray.cutout(cutout, new double[] {.05, .2}, new double[] {.95, .2}, new double[] {.05, .2}, false);
		BillboardOcclusionMask.Occluder opaque = BillboardOcclusionMask.Occluder.triangle(
			96, 96, 250, 160, 96, 250, 96, 160, 250, "stone");
		for (BillboardOcclusionQuality quality : BillboardOcclusionQuality.values())
		{
			if (quality == BillboardOcclusionQuality.OFF) continue;
			BillboardOcclusionMask mask = new BillboardOcclusionMask();
			mask.prepare(List.of(spray, opaque), quality, regions, true);
			assertEquals("stone", mask.sourceAt(111, 101));
			assertEquals("spray", mask.sourceAt(146, 101));
			assertEquals(250, mask.depthAt(111, 101), 0);
			assertEquals(200, mask.depthAt(146, 101), 0);
			assertEquals(0, mask.transmittanceAt(111, 101, 1000));
			assertEquals(255, mask.transmittanceAt(111, 101, 240));
			assertEquals(128, mask.transmittanceAt(146, 101, 240));
			assertEquals(0xFF007F00, mask.tintPixel(146, 101, 240, 0xFF000000));
			assertNull(mask.sourceAt(130, 101));
			assertTrue(Float.isNaN(mask.depthAt(130, 101)));

			int[] row = new int[quality.sampleStep()];
			mask.refinedSampleTransmittance(mask.sampleX(146), 101, 240, row);
			assertEquals(128, row[mask.sampleOffset(146)]);
			assertEquals(255, row[mask.sampleOffset(148)]);
			assertTrue(mask.drainDebugStats().endsWith("cutoutTexelTests=18"));
		}
	}
}
