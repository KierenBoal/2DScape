package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import com.kierenboal.npcsnap.rendering.BillboardCanvasPoint;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.rendering.BillboardGeometryUtils;
import com.kierenboal.npcsnap.rendering.BillboardPaintOrder;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import com.kierenboal.npcsnap.targeting.BillboardTarget;
import com.kierenboal.npcsnap.targeting.ClassifiedObjectType;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.ObservedTileObject;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

/** Exercises the cached-sprite placement path without a game client. */
public class NpcBillboardWorldScaleTest
{
	@Test
	public void overlappingWorldSpritesKeepTheirOrderWhenCapturedEdgesChange()
	{
		for (boolean gpu : new boolean[] {false, true})
		{
			Fixture fixture = new Fixture(gpu, false);
			BillboardTarget near = worldTarget(fixture.location, proxy(Model.class));
			BillboardTarget far = worldTarget(new LocalPoint(512, 1280), proxy(Model.class));
			int nearAnchor = fixture.overlay.sortBottomY(near, new Rectangle(0, 0, 100, 100));
			int farAnchor = fixture.overlay.sortBottomY(far, new Rectangle(0, 0, 100, 100));
			assertTrue(nearAnchor > farAnchor);
			for (int bottom : new int[] {20, 400, 100, 800, 1, 20})
			{
				Rectangle nearBounds = new Rectangle(0, 0, 100, bottom);
				Rectangle farBounds = new Rectangle(0, 0, 100, 1000 - bottom);
				assertEquals(nearAnchor, fixture.overlay.sortBottomY(near, nearBounds));
				assertEquals(List.of("far", "near"), BillboardPaintOrder.sort(List.of(
					new BillboardPaintOrder.Entry<>("near", nearBounds, 0, BillboardPaintOrder.NO_PRIORITY_GROUP,
						fixture.overlay.sortBottomY(near, nearBounds), 100, 1),
					new BillboardPaintOrder.Entry<>("far", farBounds, 0, BillboardPaintOrder.NO_PRIORITY_GROUP,
						fixture.overlay.sortBottomY(far, farBounds), 200, 2))));
			}
		}
	}

	@Test
	public void worldObjectSortDepthIgnoresLiveModelHeight()
	{
		Fixture fixture = new Fixture(true, false);
		AtomicInteger height = new AtomicInteger(100);
		Renderable renderable = proxy(DynamicObject.class, methodSupplier("getModelHeight", height::get));
		ObservedTileObject observed = worldTarget(fixture.location, renderable).observedTileObject;
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(fixture.client);
		double original = calculator.depth(observed);
		assertTrue(Double.isFinite(original));
		for (int liveHeight : new int[] {0, 1, 1000, 32, 100})
		{
			height.set(liveHeight);
			assertEquals(original, calculator.depth(observed), 0);
		}
	}

	private static BillboardTarget worldTarget(LocalPoint location, Renderable renderable)
	{
		return BillboardTarget.forTileObject(new ObservedTileObject(proxy(GameObject.class),
			List.of(new ObjectRenderablePart(renderable, location, 0)), ClassifiedObjectType.OBJECT), 100);
	}

	@Test
	public void cachedSceneryAndItemsDoNotBreatheWhenLiveModelHeightChanges()
	{
		for (boolean gpu : new boolean[] {false, true})
		{
			Fixture fixture = new Fixture(gpu, false);
			for (Class<? extends Renderable> type : java.util.List.of(Model.class, DynamicObject.class, TileItem.class))
			{
				AtomicInteger height = new AtomicInteger(100);
				Renderable renderable = proxy(type, methodSupplier("getModelHeight", height::get));
				Rectangle source = new Rectangle(-200, -120, 400, 140);
				Rectangle initial = fixture.draw(renderable, source, 0).bounds;
				for (int liveHeight : new int[] {0, 110, 1000, 100, 110, 100})
				{
					height.set(liveHeight);
					assertEquals(type.getSimpleName(), initial, fixture.draw(renderable, source, 0).bounds);
				}
			}
		}
	}

	@Test
	public void inventoryItemScaleAlsoIgnoresLiveHeight()
	{
		Fixture fixture = new Fixture(true, true);
		AtomicInteger height = new AtomicInteger(100);
		TileItem item = proxy(TileItem.class, methodSupplier("getModelHeight", height::get));
		Rectangle source = new Rectangle(-18, -32, 36, 32);
		Rectangle initial = fixture.draw(item, source, 20).bounds;
		height.set(1000);
		assertEquals(initial, fixture.draw(item, source, 20).bounds);
		Rectangle modelSprite = new Fixture(true, false).draw(item, source, 20).bounds;
		assertTrue("Inventory zoom adjustment must remain active", initial.width < modelSprite.width);
	}

	@Test
	public void shortWideSpritesScaleEachDimensionWithoutAmplifyingHeightRounding()
	{
		Fixture fixture = new Fixture(false, false);
		Renderable scenery = proxy(Model.class, method("getModelHeight", 100));
		Rectangle source = new Rectangle(-500, -3, 1000, 6);
		BillboardCanvasPoint origin = new BillboardDepthCalculator(fixture.client)
			.projectCanvasPoint(fixture.location, 0, 0);
		Rectangle bounds = fixture.draw(scenery, source, 0).bounds;
		assertEquals(BillboardGeometryUtils.scaledSize(source.width, 512.0d / origin.depth), bounds.width);
		assertEquals(BillboardGeometryUtils.scaledSize(source.height, 512.0d / origin.depth), bounds.height);
	}

	@Test
	public void spritesStillRespondToZoomAndGroundItemStackHeight()
	{
		Fixture fixture = new Fixture(true, false);
		TileItem item = proxy(TileItem.class, method("getModelHeight", 100));
		Rectangle source = new Rectangle(-50, -100, 100, 100);
		Rectangle initial = fixture.draw(item, source, 0).bounds;
		fixture.scale.set(768);
		Rectangle zoomed = fixture.draw(item, source, 0).bounds;
		assertTrue(zoomed.width > initial.width);
		assertTrue(zoomed.height > initial.height);
		Rectangle raised = fixture.draw(item, source, 50).bounds;
		assertTrue(raised.y < zoomed.y);
	}

	private static final class Fixture
	{
		private final AtomicInteger scale = new AtomicInteger(512);
		private final LocalPoint location = new LocalPoint(512, 1024);
		private final Client client;
		private final NpcBillboardOverlay overlay;

		private Fixture(boolean gpu, boolean inventorySprites)
		{
			WorldView view = proxy(WorldView.class, method("getTileSettings", new byte[4][16][16]),
				method("getTileHeights", new int[4][17][17]));
			client = proxy(Client.class, method("getTopLevelWorldView", view), method("getWorldView", view),
				method("isGpu", gpu), method("getCameraX", 512), method("getCameraFpX", 512.0f),
				method("getCameraZ", -600), method("getCameraFpZ", -600.0f),
				method("getCameraPitch", 1024), method("getCameraFpPitch", (float) (Math.PI / 8.0d)),
				methodSupplier("getScale", scale::get), method("getViewportWidth", 1600),
				method("getViewportHeight", 1200), method("getViewportXOffset", 10),
				method("getViewportYOffset", 20));
			NpcSnapConfig config = new NpcSnapConfig()
			{
				@Override public boolean useInventorySpritesForGroundItems() { return inventorySprites; }
			};
			overlay = new NpcBillboardOverlay(client, null, config, new NpcSnapDebug(client, config),
				new AnimationFrameSnapper(client), null);
		}

		private BillboardDrawGeometry draw(Renderable renderable, Rectangle source, int verticalOffset)
		{
			BillboardRenderRequest request = new BillboardRenderRequest(renderable, null, location, 0,
				verticalOffset, 0, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
			BillboardDrawGeometry geometry = overlay.buildDrawGeometry(request, renderable, source, source);
			assertNotNull(geometry);
			return geometry;
		}
	}
}
