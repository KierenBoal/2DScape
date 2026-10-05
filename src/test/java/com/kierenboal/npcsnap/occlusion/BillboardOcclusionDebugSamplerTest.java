package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.kierenboal.npcsnap.TestProxies.*;

public class BillboardOcclusionDebugSamplerTest
{
	@Test
	public void diagnosticsReportTheActualOcclusionBypassInsteadOfAHypotheticalCutout()
	{
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(Collections.singletonList(new BillboardOcclusionMask.Occluder(
			new Rectangle(10, 20, 4, 4), 20f)), BillboardOcclusionQuality.MEDIUM, 10, 20, 4, 4);
		Client client = proxy(Client.class, method("getCameraFpY", -1000f),
			method("get3dZoom", 512), method("getViewportHeight", 4));
		BillboardDepthCalculator depth = new BillboardDepthCalculator(client);
		BillboardRenderRequest request = new BillboardRenderRequest(
			proxy(Renderable.class, method("getModelHeight", 100)), null, new LocalPoint(0, 0),
			0, 0, 0, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
		PreparedBillboardDraw image = draw(0xFFFFFFFF);
		PreparedBillboardDraw bypass = new PreparedBillboardDraw(request, image.result, 1);
		BillboardOcclusionDebugSampler sampler = new BillboardOcclusionDebugSampler(mask,
			draw -> BillboardDepthSurface.from(depth, draw, 0));
		sampler.collect(Collections.singletonList(bypass), true);
		assertTrue(mask.isOccluded(11, 21, 1000));
		assertEquals(12, sampler.samples().size());
		for (String sample : sampler.samples())
		{
			assertTrue(sample.contains("worldOcclusionEnabled=false"));
			assertTrue(sample.contains("transmittance=255 occluded=false"));
		}
	}

	@Test
	public void disabledAndTransparentDrawsProduceNoSamples()
	{
		BillboardOcclusionDebugSampler sampler = sampler();
		PreparedBillboardDraw opaque = draw(0xFFFFFFFF);

		sampler.collect(List.of(opaque), false);
		sampler.collect(List.of(draw(0)), true);

		assertTrue(sampler.samples().isEmpty());
	}

	@Test
	public void opaqueDrawSamplesConfiguredGridAndFormatsDiagnostics()
	{
		BillboardOcclusionDebugSampler sampler = sampler();

		sampler.collect(List.of(draw(0xFFFFFFFF)), true);

		assertEquals(12, sampler.samples().size());
		assertTrue(sampler.samples().get(0).contains("canvas="));
		assertTrue(sampler.samples().get(0).contains("billboardDepth=NaN"));
		assertTrue(sampler.samples().get(0).contains("occluded=false"));
	}

	@Test
	public void sampleCountIsCappedAndClearAllowsNextFrameSamples()
	{
		BillboardOcclusionDebugSampler sampler = sampler();

		sampler.collect(List.of(draw(0xFFFFFFFF), draw(0xFFFFFFFF), draw(0xFFFFFFFF)), true);
		assertEquals(24, sampler.samples().size());
		sampler.clear();
		assertTrue(sampler.samples().isEmpty());
		sampler.collect(List.of(draw(0xFFFFFFFF)), true);
		assertEquals(12, sampler.samples().size());
	}

	private static BillboardOcclusionDebugSampler sampler()
	{
		return new BillboardOcclusionDebugSampler(
			new BillboardOcclusionMask(),
			draw -> BillboardDepthSurface.invalid());
	}

	private static PreparedBillboardDraw draw(int argb)
	{
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++)
		{
			for (int x = 0; x < 4; x++)
			{
				image.setRGB(x, y, argb);
			}
		}
		Rectangle bounds = new Rectangle(10, 20, 4, 4);
		return new PreparedBillboardDraw(
			null,
			new BillboardRenderResult(bounds, image, new Rectangle(0, 0, 4, 4)),
			1);
	}
}
