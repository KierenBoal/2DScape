package com.kierenboal.npcsnap.features;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.TestProxies;
import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.SpritePixels;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActorOverheadRendererTest
{
	@Test
	public void classicHitsplatUsesCompactPixelBounds()
	{
		java.awt.Polygon star = RetroHitsplatRenderer.classicStar(10, 20, 20, 20);
		assertEquals(new Rectangle(10, 20, 20, 20), star.getBounds());
		assertEquals(20, star.npoints);
		assertTrue(hasReflexVertex(star));
		assertTrue(star.contains(new Rectangle(14, 25, 12, 10)));
	}

	private static boolean hasReflexVertex(java.awt.Polygon polygon)
	{
		boolean positive = false;
		boolean negative = false;
		for (int i = 0; i < polygon.npoints; i++)
		{
			int previous = (i + polygon.npoints - 1) % polygon.npoints;
			int next = (i + 1) % polygon.npoints;
			int cross = (polygon.xpoints[i] - polygon.xpoints[previous])
				* (polygon.ypoints[next] - polygon.ypoints[i])
				- (polygon.ypoints[i] - polygon.ypoints[previous])
				* (polygon.xpoints[next] - polygon.xpoints[i]);
			positive |= cross > 0;
			negative |= cross < 0;
		}
		return positive && negative;
	}

	@Test
	public void disabledRetroOverheadsAreNotTracked()
	{
		NpcSnapConfig config = new NpcSnapConfig()
		{
			@Override
			public boolean useRetroOverheads()
			{
				return false;
			}
		};
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class), config);
		NPC actor = TestProxies.proxy(NPC.class);
		Hitsplat hitsplat = TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getDisappearsOnGameCycle", 100));
		renderer.recordHitsplat(actor, hitsplat);
		assertEquals(0, renderer.trackedHitsplatCount(actor, 1));
	}

	@Test
	public void disabledRetroOverheadsLeavesNativeOverheadsVisible()
	{
		assertFalse(new ActorOverheadRenderer(TestProxies.proxy(Client.class), config(false))
			.shouldReplace(TestProxies.proxy(NPC.class)));
	}

	@Test
	public void retroOverheadsReplacePrayingPlayersWithTheAlignedPrayer()
	{
		BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		SpritePixels prayer = TestProxies.proxy(SpritePixels.class,
			TestProxies.method("toBufferedImage", image));
		SpritePixels[] prayerSprites = new SpritePixels[HeadIcon.MAGIC.ordinal() + 1];
		prayerSprites[HeadIcon.MAGIC.ordinal()] = prayer;
		Client client = TestProxies.proxy(Client.class,
			TestProxies.method("getSprites", prayerSprites));
		Player player = TestProxies.proxy(Player.class,
			TestProxies.method("getSkullIcon", -1),
			TestProxies.method("getOverheadIcon", HeadIcon.MAGIC));

		assertTrue(new ActorOverheadRenderer(client, config(true))
			.shouldReplace(player));
	}

	@Test
	public void replacementPolicyDoesNotChangeAssetEligibility()
	{
		BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		SpritePixels sprite = TestProxies.proxy(SpritePixels.class,
			TestProxies.method("toBufferedImage", image));
		SpritePixels[] npcSprites = new SpritePixels[4];
		npcSprites[3] = sprite;
		Client client = TestProxies.proxy(Client.class,
			TestProxies.method("getSprites", npcSprites));
		NPC npc = TestProxies.proxy(NPC.class,
			TestProxies.method("getOverheadArchiveIds", new int[] {42}),
			TestProxies.method("getOverheadSpriteIds", new short[] {3}));

		ActorOverheadRenderer renderer = new ActorOverheadRenderer(client, config(false));
		assertTrue(renderer.canReplace(npc));
		assertFalse(renderer.shouldReplace(npc));
	}

	@Test
	public void replacementRequiresEveryNpcOverheadSprite()
	{
		NPC npc = TestProxies.proxy(NPC.class,
			TestProxies.method("getOverheadArchiveIds", new int[] {42}),
			TestProxies.method("getOverheadSpriteIds", new short[] {3}));
		Client missingClient = TestProxies.proxy(Client.class,
			TestProxies.method("getSprites", null));
		assertFalse(new ActorOverheadRenderer(missingClient).canReplace(npc));

		BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		SpritePixels sprite = TestProxies.proxy(SpritePixels.class,
			TestProxies.method("toBufferedImage", image));
		SpritePixels[] npcSprites = new SpritePixels[4];
		npcSprites[3] = sprite;
		Client loadedClient = TestProxies.proxy(Client.class,
			TestProxies.method("getSprites", npcSprites));
		assertTrue(new ActorOverheadRenderer(loadedClient).canReplace(npc));
	}

	@Test
	public void playerWithoutIconsNeedsNoSpriteAssets()
	{
		Player player = TestProxies.proxy(Player.class,
			TestProxies.method("getSkullIcon", -1),
			TestProxies.method("getOverheadIcon", null));
		assertTrue(new ActorOverheadRenderer(TestProxies.proxy(Client.class)).canReplace(player));
	}

	@Test
	public void prayerUsesItsIndexWithinTheSpriteGroup()
	{
		BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
		SpritePixels magic = TestProxies.proxy(SpritePixels.class,
			TestProxies.method("toBufferedImage", image));
		SpritePixels[] prayerSprites = new SpritePixels[HeadIcon.MAGIC.ordinal() + 1];
		prayerSprites[HeadIcon.MAGIC.ordinal()] = magic;
		Client client = TestProxies.proxy(Client.class,
			TestProxies.method("getSprites", prayerSprites));
		Player player = TestProxies.proxy(Player.class,
			TestProxies.method("getSkullIcon", -1),
			TestProxies.method("getOverheadIcon", HeadIcon.MAGIC));

		assertTrue(new ActorOverheadRenderer(client).canReplace(player));
	}

	@Test
	public void sizeChangeGetsOneMidpointFrameThenSnaps()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class));
		NPC actor = TestProxies.proxy(NPC.class);
		assertEquals(new Point(50, 20), renderer.resolveAnchor(actor, new Rectangle(40, 20, 20, 80)));
		assertEquals(new Point(55, 10), renderer.resolveAnchor(actor, new Rectangle(40, 0, 40, 100)));
		assertEquals(new Point(60, 0), renderer.resolveAnchor(actor, new Rectangle(40, 0, 40, 100)));
		assertEquals(new Point(65, 5), renderer.resolveAnchor(actor, new Rectangle(45, 5, 40, 100)));
	}

	@Test
	public void skewedBillboardAnchorsOverheadsToVisibleTopCenter()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class));
		NPC actor = TestProxies.proxy(NPC.class);
		BillboardDrawGeometry geometry = BillboardDrawGeometry.sheared(
			new Rectangle(100, 50, 40, 100), 120.0d, 60.0d, 140.0d, -20.0d, 0.0d);

		assertEquals(new Point(100, 60), renderer.resolveAnchor(actor, geometry));
	}

	@Test
	public void temporaryBillboardRemovalResetsAnchorButPreservesHitsUntilNativeExpiry()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class), config(true));
		NPC actor = TestProxies.proxy(NPC.class);
		Hitsplat hitsplat = TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getDisappearsOnGameCycle", 100));
		renderer.resolveAnchor(actor, new Rectangle(40, 20, 20, 80));
		renderer.recordHitsplat(actor, hitsplat);
		renderer.updateDrawableActors(Collections.singleton(actor));

		renderer.updateDrawableActors(Collections.emptySet());
		assertEquals(1, renderer.trackedHitsplatCount(actor, 1));
		assertEquals(new Point(60, 0), renderer.resolveAnchor(actor, new Rectangle(40, 0, 40, 100)));
		renderer.updateDrawableActors(Collections.singleton(actor));
		assertEquals(1, renderer.trackedHitsplatCount(actor, 99));
		assertEquals(0, renderer.trackedHitsplatCount(actor, 100));
	}

	@Test
	public void hiddenActorsHitsExpireWithoutExtendingTheirLifetimeOnReturn()
	{
		java.util.concurrent.atomic.AtomicInteger cycle = new java.util.concurrent.atomic.AtomicInteger(10);
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class,
			TestProxies.methodSupplier("getGameCycle", cycle::get)), config(true));
		NPC actor = TestProxies.proxy(NPC.class);
		renderer.recordHitsplat(actor, TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getDisappearsOnGameCycle", 100)));
		renderer.updateDrawableActors(Collections.singleton(actor));
		cycle.set(50);
		renderer.updateDrawableActors(Collections.emptySet());
		assertEquals(1, renderer.trackedHitsplatCount(actor, cycle.get()));
		cycle.set(100);
		renderer.render(null, Collections.emptyList());
		renderer.updateDrawableActors(Collections.singleton(actor));
		assertEquals(0, renderer.trackedHitsplatCount(actor, cycle.get()));
	}

	@Test
	public void actorDespawnAndFullClearStillDiscardActiveHitsImmediately()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class), config(true));
		NPC first = TestProxies.proxy(NPC.class);
		NPC second = TestProxies.proxy(NPC.class);
		Hitsplat hit = TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getDisappearsOnGameCycle", 100));
		renderer.recordHitsplat(first, hit);
		renderer.recordHitsplat(second, hit);
		renderer.clear(first);
		assertEquals(0, renderer.trackedHitsplatCount(first, 1));
		assertEquals(1, renderer.trackedHitsplatCount(second, 1));
		renderer.clear();
		assertEquals(0, renderer.trackedHitsplatCount(second, 1));
	}

	@Test
	public void shortHitsplatsLastTwoTicksWithoutShorteningLongerNativeLifetimes()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class,
			TestProxies.method("getGameCycle", 100)), config(true));
		NPC shortHit = TestProxies.proxy(NPC.class);
		NPC longHit = TestProxies.proxy(NPC.class);
		renderer.recordHitsplat(shortHit, TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getDisappearsOnGameCycle", 130)));
		renderer.recordHitsplat(longHit, TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getDisappearsOnGameCycle", 200)));
		assertEquals(1, renderer.trackedHitsplatCount(shortHit, 130));
		assertEquals(1, renderer.trackedHitsplatCount(shortHit, 159));
		assertEquals(0, renderer.trackedHitsplatCount(shortHit, 160));
		assertEquals(1, renderer.trackedHitsplatCount(longHit, 199));
		assertEquals(0, renderer.trackedHitsplatCount(longHit, 200));
	}

	@Test
	public void hitsplatsExpireAndRemainIsolatedByActorIdentity()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class), config(true));
		NPC first = TestProxies.proxy(NPC.class);
		NPC second = TestProxies.proxy(NPC.class);
		Hitsplat hitsplat = TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getHitsplatType", 1),
			TestProxies.method("getAmount", 12),
			TestProxies.method("getDisappearsOnGameCycle", 100));

		renderer.recordHitsplat(first, hitsplat);
		assertEquals(1, renderer.trackedHitsplatCount(first, 99));
		assertEquals(0, renderer.trackedHitsplatCount(second, 99));
		assertEquals(0, renderer.trackedHitsplatCount(first, 100));
	}

	@Test
	public void recordedHitsplatKeepsItsTypeAndAmountUntilNativeExpiry()
	{
		java.util.concurrent.atomic.AtomicInteger cycle = new java.util.concurrent.atomic.AtomicInteger(1);
		java.util.concurrent.atomic.AtomicInteger type = new java.util.concurrent.atomic.AtomicInteger(HitsplatID.POISON);
		java.util.concurrent.atomic.AtomicInteger amount = new java.util.concurrent.atomic.AtomicInteger(0);
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class,
			TestProxies.methodSupplier("getGameCycle", cycle::get)), config(true));
		NPC actor = TestProxies.proxy(NPC.class);
		renderer.recordHitsplat(actor, TestProxies.proxy(Hitsplat.class,
			TestProxies.methodSupplier("getHitsplatType", type::get),
			TestProxies.methodSupplier("getAmount", amount::get),
			TestProxies.method("getDisappearsOnGameCycle", 100)));
		type.set(HitsplatID.HEAL);
		amount.set(99);
		BillboardRenderRequest request = new BillboardRenderRequest(actor, null, null, 0, 0, 0, 0,
			-1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
		Rectangle bounds = new Rectangle(90, 20, 20, 90);
		PreparedBillboardDraw draw = new PreparedBillboardDraw(request,
			new BillboardRenderResult(bounds, null, bounds), 0);
		BufferedImage actual = renderOverheads(renderer, draw);
		BufferedImage expected = new BufferedImage(200, 140, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = expected.createGraphics();
		try
		{
			g.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			RetroHitsplatRenderer.draw(g, HitsplatID.POISON, 0, 100, 50);
		}
		finally
		{
			g.dispose();
		}
		assertArrayEquals(expected.getRGB(0, 0, 200, 140, null, 0, 200), actual.getRGB(0, 0, 200, 140, null, 0, 200));
		cycle.set(100);
		BufferedImage expired = renderOverheads(renderer, draw);
		assertArrayEquals(new int[200 * 140], expired.getRGB(0, 0, 200, 140, null, 0, 200));
	}

	private static BufferedImage renderOverheads(ActorOverheadRenderer renderer, PreparedBillboardDraw draw)
	{
		BufferedImage image = new BufferedImage(200, 140, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			renderer.render(g, Collections.singletonList(draw));
		}
		finally
		{
			g.dispose();
		}
		return image;
	}

	@Test
	public void chatCollisionMovesTextAboveOccupiedBounds()
	{
		Rectangle occupied = new Rectangle(20, 20, 50, 15);
		List<Rectangle> bounds = new ArrayList<>();
		bounds.add(occupied);
		Rectangle resolved = ActorOverheadRenderer.resolveChatCollision(
			new Rectangle(20, 20, 50, 15), bounds, 17);

		assertEquals(3, resolved.y);
		assertFalse(resolved.intersects(occupied));
	}

	private static NpcSnapConfig config(boolean retroOverheads)
	{
		return new NpcSnapConfig()
		{
			@Override
			public boolean useRetroOverheads()
			{
				return retroOverheads;
			}
		};
	}
}
