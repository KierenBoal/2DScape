package com.kierenboal.npcsnap.features;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.targeting.BillboardWorldViewVisibility;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.Scene;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.SpriteID.Staticons2;
import net.runelite.client.callback.RenderCallbackManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class SkillingThoughtBubbleOverlayTest
{
	@Test
	public void hiddenParentBoatSuppressesBubbleWork()
	{
		AtomicBoolean overlap = new AtomicBoolean(true);
		WorldView boat = proxy(WorldView.class, method("getScene", proxy(Scene.class)));
		WorldEntity parent = proxy(WorldEntity.class, method("getWorldView", boat),
			methodSupplier("isHiddenForOverlap", overlap::get));
		WorldView top = proxy(WorldView.class, method("isTopLevel", true), method("worldEntities",
			proxy(IndexedObjectSet.class, methodSupplier("iterator", () -> Collections.singletonList(parent).iterator()))));
		Client client = proxy(Client.class, method("getTopLevelWorldView", top), method("getGameState", GameState.LOGGED_IN),
			method("getLocalPlayer", proxy(Player.class, method("getWorldView", boat))));
		BillboardWorldViewVisibility visibility = new BillboardWorldViewVisibility(client, new RenderCallbackManager());
		visibility.beginFrame();
		AtomicInteger work = new AtomicInteger();
		SkillingActivityTracker tracker = new SkillingActivityTracker()
		{
			@Override public List<Skill> getRenderableSkills(long nowMillis, long fadeOutMillis)
			{
				work.incrementAndGet();
				return Collections.emptyList();
			}
		};
		SkillingThoughtBubbleOverlay overlay = new SkillingThoughtBubbleOverlay(client, new NpcSnapConfig() {},
			tracker, null, null, visibility);
		overlay.setActive(true);
		Graphics2D graphics = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			overlay.render(graphics);
			assertEquals(0, work.get());
			overlap.set(false);
			overlay.render(graphics);
			assertEquals(1, work.get());
		}
		finally { graphics.dispose(); }
	}

	@Test
	public void sailingUsesTheNamedSailingSkillSprite()
	{
		assertEquals(Staticons2.SAILING, SkillingThoughtBubbleOverlay.skillSpriteId(Skill.SAILING));
	}

	@Test
	public void zoomScalesTheWholeBubbleAndIconsTogether()
	{
		BufferedImage reference = draw(1024);
		BufferedImage distant = draw(315);
		Rectangle referenceBounds = visibleBounds(reference, false);
		Rectangle distantBounds = visibleBounds(distant, false);
		double ratio = 0.790d;
		assertEquals(referenceBounds.width * ratio, distantBounds.width, 3.0d);
		assertEquals(referenceBounds.height * ratio, distantBounds.height, 3.0d);
		Rectangle referenceIcons = visibleBounds(reference, true);
		Rectangle distantIcons = visibleBounds(distant, true);
		assertTrue(referenceIcons.width > 24); // Multiple icons and their spacing.
		assertEquals(referenceIcons.width * ratio, distantIcons.width, 2.0d);
		assertEquals(referenceIcons.height * ratio, distantIcons.height, 2.0d);
	}

	@Test
	public void referenceZoomAndUnavailableZoomKeepTheOriginalSize()
	{
		Point anchor = new Point(300, 240);
		assertTrue(SkillingThoughtBubbleOverlay.bubbleTransform(anchor, 1024).isIdentity());
		assertTrue(SkillingThoughtBubbleOverlay.bubbleTransform(anchor, 0).isIdentity());
		assertTrue(SkillingThoughtBubbleOverlay.bubbleTransform(anchor, -1).isIdentity());
		assertEquals(0.87d, SkillingThoughtBubbleOverlay.bubbleTransform(anchor, 512).getScaleX(), 0.01d);
		AffineTransform transform = SkillingThoughtBubbleOverlay.bubbleTransform(anchor, 315);
		Point2D projectedAnchor = transform.transform(new Point2D.Double(300, 240), null);
		assertEquals(300, projectedAnchor.getX(), 0.0001d);
		assertEquals(240, projectedAnchor.getY(), 0.0001d);
	}

	private static BufferedImage draw(int scale)
	{
		Client client = proxy(Client.class, method("getScale", scale),
			method("getViewportWidth", 800), method("getViewportHeight", 600));
		BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D iconGraphics = icon.createGraphics();
		iconGraphics.setColor(Color.MAGENTA);
		iconGraphics.fillRect(0, 0, 24, 24);
		iconGraphics.dispose();
		SkillingThoughtBubbleOverlay overlay = new SkillingThoughtBubbleOverlay(client, new NpcSnapConfig() {},
			new SkillingActivityTracker(), null, null)
		{
			@Override BufferedImage getSkillImage(Skill skill) { return icon; }
		};
		BufferedImage canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try
		{
			AffineTransform originalTransform = graphics.getTransform();
			Object originalComposite = graphics.getComposite();
			Object originalHints = graphics.getRenderingHints();
			overlay.drawBubble(graphics, proxy(Player.class), new Point(400, 300),
				Arrays.asList(Skill.AGILITY, Skill.SAILING), 10_000L, 1.0f);
			assertEquals(originalTransform, graphics.getTransform());
			assertEquals(originalComposite, graphics.getComposite());
			assertEquals(originalHints, graphics.getRenderingHints());
		}
		finally
		{
			graphics.dispose();
		}
		return canvas;
	}

	private static Rectangle visibleBounds(BufferedImage image, boolean iconsOnly)
	{
		Rectangle bounds = null;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				if ((argb >>> 24) == 0 || (iconsOnly && (argb & 0xffffff) != 0xff00ff))
				{
					continue;
				}
				if (bounds == null)
				{
					bounds = new Rectangle(x, y, 1, 1);
				}
				else
				{
					bounds.add(new Rectangle(x, y, 1, 1));
				}
			}
		}
		return bounds;
	}
}
