package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardFrameBuffer;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.runelite.api.Client;
import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

/** Headless timing fixture. Includes buffer clearing and same-frame mask publication. */
public final class CrowdRenderBenchmark
{
	private final Rectangle viewport = new Rectangle(960, 640);
	private final BillboardOcclusionRegions interest = new BillboardOcclusionRegions();
	private final BillboardOcclusionMask mask = new BillboardOcclusionMask();
	private final List<BillboardOcclusionMask.Occluder> faces = new ArrayList<>();
	private final List<PreparedBillboardDraw> draws = new ArrayList<>();
	private final BillboardFrameBuffer frame;

	CrowdRenderBenchmark(boolean dense)
	{
		this(dense, dense ? 2400 : 8);
	}

	private CrowdRenderBenchmark(boolean dense, int triangles)
	{
		Random rng = new Random(4118);
		BillboardDepthCalculator depth = new BillboardDepthCalculator(proxy(Client.class,
			method("getViewportWidth", 960), method("getViewportHeight", 640), method("getScale", 512)));
		depth.beginRender();
		frame = new BillboardFrameBuffer(mask, draw -> new BillboardDepthSurface(depth, 0, 800,
			0, 100, draw.sourceBounds, draw.bounds));
		BufferedImage sprite = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) sprite.getRaster().getDataBuffer()).getData();
		for (int i = 0; i < pixels.length; i++)
			pixels[i] = i % 16 < 3 ? 0 : 0xff407030 + i;
		BufferedImage shadow = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		shadow.setRGB(0, 0, 0x70000000);
		for (int i = 0; i < (dense ? 80 : 3); i++)
		{
			Rectangle body = new Rectangle(rng.nextInt(880), rng.nextInt(530), 48, 88);
			Rectangle ground = new Rectangle(body.x + 12, body.y + 68, 65, 24);
			PreparedBillboardDraw draw = new PreparedBillboardDraw(null,
				new BillboardRenderResult(body, sprite, sprite.getRaster().getBounds()), i + 1, null);
			draw.shadow = new BillboardRenderResult(ground, shadow, new Rectangle(1, 1));
			draws.add(draw);
			interest.add(draw.visualBounds(), viewport);
		}
		faces.add(new BillboardOcclusionMask.Occluder(new Rectangle(220, 0, 240, 640), 180));
		faces.add(new BillboardOcclusionMask.Occluder(new Rectangle(510, 30, 120, 490), 250, "glass", 120));
		for (int i = 0; i < triangles; i++)
		{
			int x = rng.nextInt(960), y = rng.nextInt(640);
			faces.add(BillboardOcclusionMask.Occluder.triangle(x, y, 850, x + 40, y + 5, 830,
				x + 10, y + 45, 880));
		}
	}

	int[] pixels()
	{
		return ((DataBufferInt) frame.image().getRaster().getDataBuffer()).getData();
	}

	void prepare(Executor worker)
	{
		mask.prepare(faces, BillboardOcclusionQuality.MEDIUM, interest, false, worker);
	}

	void composite(boolean debug)
	{
		frame.begin(viewport.width, viewport.height);
		for (int i = draws.size() - 1; i >= 0; i--)
		{
			frame.blit(draws.get(i), 0, 0, viewport.width, viewport.height, debug, false, !debug);
		}
	}

	void measure(boolean dense, boolean debug, Executor worker)
	{
		long[][] times = new long[3][200];
		for (int n = -150; n < times[0].length; n++)
		{
			long start = System.nanoTime();
			prepare(worker);
			long prepared = System.nanoTime();
			composite(debug);
			long end = System.nanoTime();
			if (n >= 0)
			{
				times[0][n] = end - start;
				times[1][n] = prepared - start;
				times[2][n] = end - prepared;
			}
		}
		System.out.printf("%s %s debug=%b hash=%08x skippedPixels=%d", dense ? "crowded" : "sparse",
			worker == null ? "serial" : "two-band", debug, Arrays.hashCode(pixels()), frame.skippedPixels());
		for (long[] sample : times)
		{
			Arrays.sort(sample);
			System.out.printf(" %.3f/%.3f", sample[99] / 1e6, sample[189] / 1e6);
		}
		System.out.println();
	}

	public static void main(String[] args)
	{
		System.out.println("Milliseconds p50/p95: total, mask, composite");
		if (args.length > 0)
		{
			ExecutorService worker = Executors.newSingleThreadExecutor();
			try
			{
				for (String count : args)
				{
					int faces = Integer.parseInt(count);
					System.out.println("Occluders: " + faces);
					CrowdRenderBenchmark fixture = new CrowdRenderBenchmark(true, faces - 2);
					fixture.measure(true, false, null);
					fixture.measure(true, false, worker);
				}
			}
			finally
			{
				worker.shutdownNow();
			}
			return;
		}
		for (boolean dense : new boolean[] {false, true})
		{
			CrowdRenderBenchmark fixture = new CrowdRenderBenchmark(dense);
			ExecutorService worker = Executors.newSingleThreadExecutor();
			try
			{
				for (boolean debug : new boolean[] {false, true})
				{
					fixture.measure(dense, debug, null);
					if (dense) fixture.measure(dense, debug, worker);
				}
			}
			finally
			{
				worker.shutdownNow();
			}
		}
	}
}
