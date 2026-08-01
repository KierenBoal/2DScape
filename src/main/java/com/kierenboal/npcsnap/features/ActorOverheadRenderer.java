package com.kierenboal.npcsnap.features;

import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.FontManager;

public final class ActorOverheadRenderer
{
	private static final int ELEMENT_GAP = 2;
	private static final int HEALTH_WIDTH = 30;
	private static final int HEALTH_HEIGHT = 5;
	private static final Color CHAT_COLOR = Color.YELLOW;
	private static final Color CHAT_SHADOW = Color.BLACK;
	private final Client client;
	private final Map<Actor, List<TrackedHitsplat>> hitsplats = new IdentityHashMap<>();
	private final Map<Actor, AnchorState> anchors = new IdentityHashMap<>();
	private final Set<Actor> drawableActors = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Long, BufferedImage> sprites = new java.util.HashMap<>();

	public ActorOverheadRenderer(Client client)
	{
		this.client = client;
	}

	public boolean canReplace(Actor actor)
	{
		if (actor instanceof Player)
		{
			Player player = (Player) actor;
			HeadIcon prayer = player.getOverheadIcon();
			if (prayer != null && groupedSprite(SpriteID.HEADICONS_PRAYER, prayer.ordinal()) == null)
			{
				return false;
			}
			if (player.getSkullIcon() >= 0 && groupedSprite(SpriteID.HEADICONS_PK, player.getSkullIcon()) == null)
			{
				return false;
			}
		}
		else if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			int[] archives = npc.getOverheadArchiveIds();
			short[] files = npc.getOverheadSpriteIds();
			if (archives != null && files != null)
			{
				for (int i = 0; i < Math.min(archives.length, files.length); i++)
				{
					if (archives[i] >= 0 && files[i] >= 0 && groupedSprite(archives[i], files[i]) == null)
					{
						return false;
					}
				}
			}
		}

		return !isHintTarget(actor) || groupedSprite(SpriteID.HEADICONS_HINT, 0) != null;
	}

	public void recordHitsplat(Actor actor, Hitsplat hitsplat)
	{
		if (actor == null || hitsplat == null)
		{
			return;
		}
		hitsplats.computeIfAbsent(actor, ignored -> new ArrayList<>()).add(new TrackedHitsplat(
			hitsplat.getHitsplatType(), hitsplat.getAmount(), hitsplat.getDisappearsOnGameCycle()));
	}

	public void clear(Actor actor)
	{
		hitsplats.remove(actor);
		anchors.remove(actor);
		drawableActors.remove(actor);
	}

	public void clear()
	{
		hitsplats.clear();
		anchors.clear();
		drawableActors.clear();
		sprites.clear();
	}

	public void updateDrawableActors(Set<Actor> current)
	{
		List<Actor> removed = new ArrayList<>();
		for (Actor actor : drawableActors)
		{
			if (!current.contains(actor))
			{
				removed.add(actor);
			}
		}
		for (Actor actor : removed)
		{
			clear(actor);
		}
		drawableActors.addAll(current);
	}

	public void render(Graphics2D graphics, List<PreparedBillboardDraw> draws)
	{
		if (graphics == null || draws == null || draws.isEmpty())
		{
			return;
		}

		expireHitsplats(client.getGameCycle());
		List<Rectangle> occupiedChatBounds = new ArrayList<>();
		for (PreparedBillboardDraw draw : draws)
		{
			if (draw == null || draw.request == null || !(draw.request.renderable instanceof Actor)
				|| draw.bounds == null || draw.bounds.isEmpty())
			{
				continue;
			}
			Actor actor = (Actor) draw.request.renderable;
			if (!canReplace(actor))
			{
				continue;
			}
			Point anchor = resolveAnchor(actor, draw.bounds);
			renderActor(graphics, actor, draw.bounds, anchor, occupiedChatBounds);
		}
	}

	private void renderActor(Graphics2D graphics, Actor actor, Rectangle billboard, Point anchor,
		List<Rectangle> occupiedChatBounds)
	{
		int centerX = anchor.x;
		int cursorY = anchor.y - ELEMENT_GAP;

		if (actor.getHealthRatio() >= 0 && actor.getHealthScale() > 0)
		{
			cursorY -= HEALTH_HEIGHT;
			drawHealthBar(graphics, centerX, cursorY, actor.getHealthRatio(), actor.getHealthScale());
			cursorY -= ELEMENT_GAP;
		}

		for (BufferedImage icon : icons(actor))
		{
			cursorY -= icon.getHeight();
			graphics.drawImage(icon, centerX - (icon.getWidth() / 2), cursorY, null);
			cursorY -= ELEMENT_GAP;
		}

		String overheadText = actor.getOverheadText();
		if (overheadText != null && !overheadText.isEmpty() && actor.getOverheadCycle() > 0)
		{
			Font font = FontManager.getRunescapeBoldFont();
			graphics.setFont(font);
			FontMetrics metrics = graphics.getFontMetrics(font);
			int width = metrics.stringWidth(overheadText);
			int height = metrics.getHeight();
			Rectangle textBounds = new Rectangle(centerX - (width / 2), cursorY - height, width, height);
			textBounds = resolveChatCollision(textBounds, occupiedChatBounds, height + ELEMENT_GAP);
			occupiedChatBounds.add(textBounds);
			int baseline = textBounds.y + metrics.getAscent();
			graphics.setColor(CHAT_SHADOW);
			graphics.drawString(overheadText, textBounds.x + 1, baseline + 1);
			graphics.setColor(CHAT_COLOR);
			graphics.drawString(overheadText, textBounds.x, baseline);
		}

		drawHitsplats(graphics, actor, billboard, anchor);
	}

	private List<BufferedImage> icons(Actor actor)
	{
		List<BufferedImage> icons = new ArrayList<>();
		if (isHintTarget(actor))
		{
			add(icons, groupedSprite(SpriteID.HEADICONS_HINT, 0));
		}
		if (actor instanceof Player)
		{
			Player player = (Player) actor;
			if (player.getSkullIcon() >= 0)
			{
				add(icons, groupedSprite(SpriteID.HEADICONS_PK, player.getSkullIcon()));
			}
			if (player.getOverheadIcon() != null)
			{
				add(icons, groupedSprite(SpriteID.HEADICONS_PRAYER, player.getOverheadIcon().ordinal()));
			}
		}
		else if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			int[] archives = npc.getOverheadArchiveIds();
			short[] files = npc.getOverheadSpriteIds();
			if (archives != null && files != null)
			{
				for (int i = 0; i < Math.min(archives.length, files.length); i++)
				{
					if (archives[i] >= 0 && files[i] >= 0)
					{
						add(icons, groupedSprite(archives[i], files[i]));
					}
				}
			}
		}
		return icons;
	}

	private void drawHealthBar(Graphics2D graphics, int centerX, int y, int ratio, int scale)
	{
		int x = centerX - (HEALTH_WIDTH / 2);
		int filled = Math.max(0, Math.min(HEALTH_WIDTH, (int) Math.round(HEALTH_WIDTH * (ratio / (double) scale))));
		graphics.setColor(Color.BLACK);
		graphics.fillRect(x - 1, y - 1, HEALTH_WIDTH + 2, HEALTH_HEIGHT + 2);
		graphics.setColor(new Color(0x8B0000));
		graphics.fillRect(x, y, HEALTH_WIDTH, HEALTH_HEIGHT);
		graphics.setColor(new Color(0x00C000));
		graphics.fillRect(x, y, filled, HEALTH_HEIGHT);
	}

	private void drawHitsplats(Graphics2D graphics, Actor actor, Rectangle billboard, Point anchor)
	{
		List<TrackedHitsplat> active = hitsplats.get(actor);
		if (active == null || active.isEmpty())
		{
			return;
		}
		Font font = FontManager.getRunescapeBoldFont();
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics(font);
		int centerX = anchor.x;
		int centerY = anchor.y + Math.max(12, billboard.height / 3);
		for (int i = 0; i < active.size(); i++)
		{
			TrackedHitsplat hitsplat = active.get(i);
			String text = Integer.toString(hitsplat.amount);
			int width = Math.max(18, metrics.stringWidth(text) + 10);
			int x = centerX - (width / 2) + hitsplatOffsetX(i);
			int y = centerY + hitsplatOffsetY(i);
			graphics.setColor(hitsplatColor(hitsplat.type));
			graphics.fillOval(x, y - 12, width, 16);
			graphics.setColor(Color.BLACK);
			graphics.drawOval(x, y - 12, width, 16);
			int textX = x + ((width - metrics.stringWidth(text)) / 2);
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, textX + 1, y + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(text, textX, y);
		}
	}

	private BufferedImage groupedSprite(int archive, int spriteIndex)
	{
		long key = spriteKey(archive, spriteIndex);
		if (sprites.containsKey(key))
		{
			return sprites.get(key);
		}
		SpritePixels[] loaded = client.getSprites(client.getIndexSprites(), archive, 0);
		BufferedImage image = loaded != null && spriteIndex >= 0 && spriteIndex < loaded.length
			&& loaded[spriteIndex] != null ? loaded[spriteIndex].toBufferedImage() : null;
		if (image != null)
		{
			sprites.put(key, image);
		}
		return image;
	}

	private static long spriteKey(int archive, int index)
	{
		return ((long) archive << 32) ^ (index & 0xFFFFFFFFL);
	}

	private boolean isHintTarget(Actor actor)
	{
		return actor instanceof Player && client.getHintArrowPlayer() == actor
			|| actor instanceof NPC && client.getHintArrowNpc() == actor;
	}

	private void expireHitsplats(int gameCycle)
	{
		Iterator<Map.Entry<Actor, List<TrackedHitsplat>>> iterator = hitsplats.entrySet().iterator();
		while (iterator.hasNext())
		{
			List<TrackedHitsplat> actorHitsplats = iterator.next().getValue();
			actorHitsplats.removeIf(hitsplat -> hitsplat.expiresOnCycle <= gameCycle);
			if (actorHitsplats.isEmpty())
			{
				iterator.remove();
			}
		}
	}

	int trackedHitsplatCount(Actor actor, int gameCycle)
	{
		expireHitsplats(gameCycle);
		List<TrackedHitsplat> active = hitsplats.get(actor);
		return active == null ? 0 : active.size();
	}

	Point resolveAnchor(Actor actor, Rectangle billboard)
	{
		Point target = new Point(billboard.x + (billboard.width / 2), billboard.y);
		AnchorState state = anchors.get(actor);
		if (state == null)
		{
			anchors.put(actor, new AnchorState(billboard, target));
			return target;
		}

		boolean sizeChanged = billboard.width != state.lastBounds.width
			|| billboard.height != state.lastBounds.height;
		Point resolved;
		if (sizeChanged)
		{
			resolved = new Point((state.displayed.x + target.x) / 2, (state.displayed.y + target.y) / 2);
		}
		else
		{
			resolved = target;
		}
		state.lastBounds = new Rectangle(billboard);
		state.displayed = resolved;
		return resolved;
	}

	static Rectangle resolveChatCollision(Rectangle desired, List<Rectangle> occupied, int verticalStep)
	{
		Rectangle resolved = new Rectangle(desired);
		while (intersectsAny(resolved, occupied))
		{
			resolved.y -= Math.max(1, verticalStep);
		}
		return resolved;
	}

	private static boolean intersectsAny(Rectangle bounds, List<Rectangle> occupied)
	{
		for (Rectangle other : occupied)
		{
			if (bounds.intersects(other))
			{
				return true;
			}
		}
		return false;
	}

	private static void add(List<BufferedImage> images, BufferedImage image)
	{
		if (image != null)
		{
			images.add(image);
		}
	}

	private static int hitsplatOffsetX(int index)
	{
		return new int[] {0, -20, 20, 0}[index % 4];
	}

	private static int hitsplatOffsetY(int index)
	{
		return new int[] {0, 16, 16, 32}[index % 4];
	}

	static Color hitsplatColor(int type)
	{
		if (type == HitsplatID.HEAL || type == HitsplatID.SANITY_RESTORE)
		{
			return new Color(0x287A36);
		}
		if (type == HitsplatID.POISON || type == HitsplatID.VENOM || type == HitsplatID.DISEASE)
		{
			return new Color(0x397A24);
		}
		if (type == HitsplatID.PRAYER_DRAIN || type == HitsplatID.CYAN_UP || type == HitsplatID.CYAN_DOWN)
		{
			return new Color(0x25859A);
		}
		return new Color(0x8B1A1A);
	}

	private static final class TrackedHitsplat
	{
		private final int type;
		private final int amount;
		private final int expiresOnCycle;

		private TrackedHitsplat(int type, int amount, int expiresOnCycle)
		{
			this.type = type;
			this.amount = amount;
			this.expiresOnCycle = expiresOnCycle;
		}
	}

	private static final class AnchorState
	{
		private Rectangle lastBounds;
		private Point displayed;
		private AnchorState(Rectangle bounds, Point displayed)
		{
			this.lastBounds = new Rectangle(bounds);
			this.displayed = displayed;
		}
	}
}
