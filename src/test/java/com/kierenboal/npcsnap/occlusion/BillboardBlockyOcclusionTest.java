package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class BillboardBlockyOcclusionTest
{
	@Test
	public void optionsAndDefaultRemainSimpleAndRetiredValuesAreMigratable()
	{
		assertEquals(Arrays.asList("OFF", "BLOCKY", "LOW", "MEDIUM", "HIGH"),
			Arrays.asList(Arrays.stream(BillboardOcclusionQuality.values()).map(Enum::name).toArray(String[]::new)));
		assertEquals(BillboardOcclusionQuality.MEDIUM, new NpcSnapConfig() { }.billboardOcclusionQuality());
		assertTrue(BillboardOcclusionQuality.isRetiredValue("ULTRA"));
		assertTrue(BillboardOcclusionQuality.isRetiredValue("MAX"));
		assertFalse(BillboardOcclusionQuality.isRetiredValue(null));
		for (BillboardOcclusionQuality quality : BillboardOcclusionQuality.values())
		{
			assertFalse(BillboardOcclusionQuality.isRetiredValue(quality.name()));
		}
	}

	@Test
	public void blockyFillsTriangleEdgesAndSwitchingBackRestoresRefinement()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		BillboardOcclusionMask.Occluder triangle = BillboardOcclusionMask.Occluder.triangle(
			0, 0, 20, 15, 0, 20, 0, 15, 20);
		mask.prepare(Collections.singletonList(triangle), BillboardOcclusionQuality.BLOCKY, 0, 0, 16, 16);
		assertEquals(16, mask.sampleStep());
		assertEquals(BillboardOcclusionMask.CellResult.OCCLUDED, mask.classifySample(0, 12, 100));
		assertEquals(0, mask.transmittanceAt(12, 12, 100));
		assertEquals(255, mask.transmittanceAt(12, 12, 10));
		assertTrue(mask.drainDebugStats().contains("refinedPixels=0"));
		for (BillboardOcclusionQuality quality : new BillboardOcclusionQuality[] {
			BillboardOcclusionQuality.LOW, BillboardOcclusionQuality.MEDIUM, BillboardOcclusionQuality.HIGH})
		{
			mask.prepare(Collections.singletonList(triangle), quality, 0, 0, 16, 16);
			assertEquals(255, mask.transmittanceAt(12, 12, 100));
			assertEquals(0, mask.transmittanceAt(1, 1, 100));
		}
		mask.prepare(Collections.singletonList(triangle), BillboardOcclusionQuality.OFF, 0, 0, 16, 16);
		assertEquals(255, mask.transmittanceAt(1, 1, 100));
	}

	@Test
	public void blockyKeepsTransparencyAndClipsSparseRegionsWithoutEdgeRefinement()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(Collections.singletonList(new BillboardOcclusionMask.Occluder(
			new Rectangle(0, 0, 2, 2), 20, "glass", 128)), BillboardOcclusionQuality.BLOCKY,
			BillboardOcclusionComposition.TRANSPARENCY_AWARE, 0, 0, 16, 16,
			new Rectangle(0, 0, 16, 16), Collections.singletonList(new Rectangle(4, 4, 4, 4)));
		int[] output = new int[16];
		mask.refinedSampleTransmittance(0, 5, 100, output);
		assertEquals(128, output[5]);
		assertEquals(255, output[0]);
		assertEquals(255, output[15]);
		assertEquals(0xFFFF7F7F, mask.tintPixel(5, 5, 100, 0xFFFF0000));
		assertTrue(mask.drainDebugStats().contains("refinedPixels=0"));
	}
}
