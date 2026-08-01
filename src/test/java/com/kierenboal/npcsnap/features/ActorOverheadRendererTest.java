package com.kierenboal.npcsnap.features;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.TestProxies;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.SpritePixels;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActorOverheadRendererTest
{
	@Test
	public void hitsplatColoursUseAmountOnly()
	{
		assertEquals(new java.awt.Color(0x3155D9), ActorOverheadRenderer.hitsplatColor(0));
		assertEquals(new java.awt.Color(0xE51B17), ActorOverheadRenderer.hitsplatColor(1));
		assertEquals(new java.awt.Color(0xE51B17), ActorOverheadRenderer.hitsplatColor(99));
	}

	@Test
	public void classicHitsplatUsesCompactPixelBounds()
	{
		java.awt.Polygon star = ActorOverheadRenderer.hitsplatStar(10, 20, 20, 20);
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
	public void disabledRetroHitsplatsAreNotTracked()
	{
		NpcSnapConfig config = new NpcSnapConfig()
		{
			@Override
			public boolean useRetroHitsplats()
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
	public void billboardRemovalResetsAnchorAndHitsplats()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class));
		NPC actor = TestProxies.proxy(NPC.class);
		Hitsplat hitsplat = TestProxies.proxy(Hitsplat.class,
			TestProxies.method("getDisappearsOnGameCycle", 100));
		renderer.resolveAnchor(actor, new Rectangle(40, 20, 20, 80));
		renderer.recordHitsplat(actor, hitsplat);
		renderer.updateDrawableActors(Collections.singleton(actor));

		renderer.updateDrawableActors(Collections.emptySet());
		assertEquals(0, renderer.trackedHitsplatCount(actor, 1));
		assertEquals(new Point(60, 0), renderer.resolveAnchor(actor, new Rectangle(40, 0, 40, 100)));
	}

	@Test
	public void hitsplatsExpireAndRemainIsolatedByActorIdentity()
	{
		ActorOverheadRenderer renderer = new ActorOverheadRenderer(TestProxies.proxy(Client.class));
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
}
