package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.ActorWorldPlane;
import com.kierenboal.npcsnap.rendering.BillboardCanvasPoint;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.rendering.BillboardFrameBuffer;
import com.kierenboal.npcsnap.rendering.BillboardGeometryUtils;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import com.kierenboal.npcsnap.targeting.WorldViewLocationResolver;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class BillboardProjectionRegressionTest
{
	@Test
	public void landAndRotatedBoatSpritesRespectForegroundAndBackgroundAtSceneScale()
	{
		for (boolean gpu : new boolean[] {false, true})
			for (boolean nested : new boolean[] {false, true})
				for (boolean worldPlane : new boolean[] {false, true})
					checkSprite(gpu, nested, worldPlane);
	}

	private static void checkSprite(boolean gpu, boolean nested, boolean worldPlane)
	{
		WorldView boat = proxy(WorldView.class, method("getMainWorldProjection", projection(416, -70, 1464, 512)));
		WorldEntity owner = proxy(WorldEntity.class, method("getWorldView", boat),
			method("getLocalLocation", new LocalPoint(512, 1400)));
		WorldView top = proxy(WorldView.class, method("isTopLevel", true), method("getTileHeight", 100),
			method("worldEntities", proxy(IndexedObjectSet.class,
				methodSupplier("iterator", () -> Collections.singletonList(owner).iterator()))));
		Client client = proxy(Client.class, method("isGpu", gpu), method("getTopLevelWorldView", top),
			method("getCameraX", 512), method("getCameraZ", -600),
			method("getCameraFpX", 512.25f), method("getCameraFpZ", -600.5f),
			method("getCameraPitch", 1043), method("getCameraFpPitch", .40013f),
			method("getCameraFpYaw", .00017f), method("getScale", 1000), method("get3dZoom", 512),
			method("getViewportWidth", 800), method("getViewportHeight", 600),
			method("getViewportXOffset", 17), method("getViewportYOffset", 29));
		BillboardDepthCalculator camera = new BillboardDepthCalculator(client);
		camera.beginRender();
		WorldViewLocationResolver.ResolvedLocation actor = resolve(client, top, boat, nested, 512, 1400, 0);
		assertNotNull(actor);
		assertEquals(0d, actor.height, 0d); // Parent and deck height are each applied once.
		Rectangle source = new Rectangle(-50, -200, 100, 200);
		Model model = proxy(Model.class, method("getVerticesCount", 4),
			method("getVerticesX", new float[] {-50, 50, 50, -50}),
			method("getVerticesY", new float[] {-200, -200, 0, 0}), method("getVerticesZ", new float[4]));
		BillboardRenderRequest request = new BillboardRenderRequest(
			proxy(Renderable.class, method("getModelHeight", 200)), model, actor.mainPoint,
			0, 0, 0, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
		BillboardDrawGeometry geometry = worldPlane
			? ActorWorldPlane.project(camera, actor.mainPoint, actor.height,
				ActorWorldPlane.viewingBearing(client, actor.mainPoint), source, source)
			: BillboardDrawGeometry.rectangular(BillboardGeometryUtils.projectedDrawBounds(source,
				camera.projectCanvasPoint(actor.mainPoint.getX(), actor.mainPoint.getY(), actor.height), client.getScale()));
		BufferedImage sprite = new BufferedImage(100, 200, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((java.awt.image.DataBufferInt) sprite.getRaster().getDataBuffer()).getData();
		Arrays.fill(pixels, 0xFF804020);
		PreparedBillboardDraw draw = new PreparedBillboardDraw(request, new BillboardRenderResult(geometry, sprite, source), 1);
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		BillboardFrameBuffer buffer = new BillboardFrameBuffer(mask, new BillboardPerformanceMetrics(),
			prepared -> BillboardDepthSurface.from(camera, prepared, actor.height));
		for (BillboardOcclusionQuality quality : new BillboardOcclusionQuality[] {BillboardOcclusionQuality.MEDIUM, BillboardOcclusionQuality.HIGH})
			for (int sceneryY : new int[] {1250, 1550})
			{
				// A real projected vertical wall covers the left half of the sprite.
				BillboardCanvasPoint[] corners = new BillboardCanvasPoint[4];
				int[] x = {420, 507, 507, 420}, height = {-350, -350, 100, 100};
				for (int i = 0; i < 4; i++)
				{
					WorldViewLocationResolver.ResolvedLocation vertex = resolve(client, top, boat, nested, x[i], sceneryY, height[i]);
					corners[i] = camera.projectCanvasPoint(vertex.mainPoint.getX(), vertex.mainPoint.getY(), vertex.height);
				}
				mask.prepare(List.of(triangle(corners[0], corners[1], corners[2]), triangle(corners[0], corners[2], corners[3])),
					quality, 17, 29, 800, 600);
				buffer.begin(800, 600);
				buffer.blit(draw, 17, 29, 800, 600, false);
				for (double row : new double[] {.25d, .5d, .75d})
				{
					Point left = geometry.canvasPoint(.25d, row), right = geometry.canvasPoint(.75d, row);
					String context = "gpu=" + gpu + " boat=" + nested + " worldPlane=" + worldPlane + " sceneryY=" + sceneryY;
					assertEquals(context, sceneryY < 1400 ? 0 : 0xFF804020, buffer.image().getRGB(left.getX() - 17, left.getY() - 29));
					assertEquals(context, 0xFF804020, buffer.image().getRGB(right.getX() - 17, right.getY() - 29));
				}
			}
		camera.endRender();
	}

	private static WorldViewLocationResolver.ResolvedLocation resolve(Client client, WorldView top, WorldView boat,
		boolean nested, int x, int y, double height)
	{
		return nested
			? WorldViewLocationResolver.resolve(client, boat, new LocalPoint(1464 - y, x - 416, boat), 0, height - 30)
			: WorldViewLocationResolver.resolve(client, top, new LocalPoint(x, y, top), 0, height);
	}

	private static BillboardOcclusionMask.Occluder triangle(BillboardCanvasPoint a, BillboardCanvasPoint b, BillboardCanvasPoint c)
	{
		return BillboardOcclusionMask.Occluder.triangle((int) Math.round(a.x), (int) Math.round(a.y), (float) a.depth,
			(int) Math.round(b.x), (int) Math.round(b.y), (float) b.depth, (int) Math.round(c.x), (int) Math.round(c.y), (float) c.depth);
	}
}
