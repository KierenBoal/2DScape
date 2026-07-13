package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BillboardOcclusionDebugSamplerTest
{
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
