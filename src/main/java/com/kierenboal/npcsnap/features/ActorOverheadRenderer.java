package com.kierenboal.npcsnap.features;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
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
	private static final Color CHAT_SHADOW = Color.BLACK;
	private final Client client;
	private final NpcSnapConfig config;
	private final RetroChatRenderer chatRenderer = new RetroChatRenderer();
	private final Map<Actor, List<TrackedHitsplat>> hitsplats = new IdentityHashMap<>();
	private final Map<Actor, AnchorState> anchors = new IdentityHashMap<>();
	private final Set<Actor> drawableActors = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Long, BufferedImage> sprites = new java.util.HashMap<>();

	public ActorOverheadRenderer(Client client, NpcSnapConfig config)
	{
		this.client = client;
		this.config = config;
	}

	ActorOverheadRenderer(Client client)
	{
		this(client, new NpcSnapConfig() { });
	}

	public boolean canReplace(Actor actor)
	{
		if (actor == null)
		{
			return false;
		}

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

	/**
	 * Returns whether this actor's complete native overhead pass can be replaced.
	 * RuneLite exposes actor-wide suppression for 2D overheads, so all custom
	 * replacements must be enabled before the native pass is hidden.
	 */
	public boolean shouldReplace(Actor actor)
	{
		if (!retroOverheadReplacementEnabled())
		{
			return false;
		}

		if (!config.alignOverheadPrayers() && actor instanceof Player
			&& ((Player) actor).getOverheadIcon() != null)
		{
			return false;
		}

		return canReplace(actor);
	}

	public void recordHitsplat(Actor actor, Hitsplat hitsplat)
	{
		if (!retroOverheadReplacementEnabled())
		{
			clearTrackedOverheadState();
			return;
		}
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
		clearTrackedOverheadState();
		sprites.clear();
	}

	private void clearTrackedOverheadState()
	{
		hitsplats.clear();
		anchors.clear();
		drawableActors.clear();
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
		if (!retroOverheadReplacementEnabled())
		{
			clearTrackedOverheadState();
			return;
		}
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
			if (!shouldReplace(actor))
			{
				clear(actor);
				continue;
			}
			Point anchor = draw.geometry != null
				? resolveAnchor(actor, draw.geometry)
				: resolveAnchor(actor, draw.bounds);
			renderActor(graphics, actor, draw.bounds, anchor, occupiedChatBounds);
		}
	}

	private boolean retroOverheadReplacementEnabled()
	{
		return config.useRetroOverheads();
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
			RetroChatRenderer.ParsedChat chat = RetroChatRenderer.parse(overheadText);
			Font font = FontManager.getRunescapeBoldFont();
			graphics.setFont(font);
			FontMetrics metrics = graphics.getFontMetrics(font);
			Rectangle textBounds = chatRenderer.bounds(chat, metrics, centerX, cursorY);
			textBounds = resolveChatCollision(textBounds, occupiedChatBounds, textBounds.height + ELEMENT_GAP);
			occupiedChatBounds.add(textBounds);
			chatRenderer.draw(graphics, chat, textBounds, actor, System.currentTimeMillis(), CHAT_SHADOW);
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
		graphics.setColor(new Color(255, 0, 0));
		graphics.fillRect(x, y, HEALTH_WIDTH, HEALTH_HEIGHT);
		graphics.setColor(new Color(0, 255, 0));
		graphics.fillRect(x, y, filled, HEALTH_HEIGHT);
	}

	private void drawHitsplats(Graphics2D graphics, Actor actor, Rectangle billboard, Point anchor)
	{
		List<TrackedHitsplat> active = hitsplats.get(actor);
		if (active == null || active.isEmpty())
		{
			return;
		}
		Font font = FontManager.getRunescapeSmallFont();
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics(font);
		int centerX = anchor.x;
		int centerY = anchor.y + Math.max(12, billboard.height / 3);
		for (int i = 0; i < active.size(); i++)
		{
			TrackedHitsplat hitsplat = active.get(i);
			String text = Integer.toString(hitsplat.amount);
			int width = Math.max(20, metrics.stringWidth(text) + 12);
			int x = centerX - (width / 2) + hitsplatOffsetX(i);
			int y = centerY + hitsplatOffsetY(i);
			graphics.setColor(hitsplatColor(hitsplat.amount));
			java.awt.Polygon star = hitsplatStar(x, y - 15, width, 20);
			graphics.fillPolygon(star);
			graphics.setColor(Color.BLACK);
			graphics.drawPolygon(star);
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
		return resolveAnchor(actor, billboard, new Point(billboard.x + (billboard.width / 2), billboard.y));
	}

	Point resolveAnchor(Actor actor, BillboardDrawGeometry geometry)
	{
		return resolveAnchor(actor, geometry.bounds, awtPoint(geometry.contentTopCenter()));
	}

	private Point resolveAnchor(Actor actor, Rectangle billboard, Point target)
	{
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

	private static Point awtPoint(net.runelite.api.Point point)
	{
		return new Point(point.getX(), point.getY());
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

	static Color hitsplatColor(int amount)
	{
		return amount == 0 ? new Color(0x3155D9) : new Color(0xE51B17);
	}

	static java.awt.Polygon hitsplatStar(int x, int y, int width, int height)
	{
		int right = x + width;
		int bottom = y + height;
		int midX = x + width / 2;
		int midY = y + height / 2;
		int bodyHalfWidth = Math.max(6, (width * 3) / 10);
		int bodyHalfHeight = Math.max(5, height / 4);
		int spikeHalfWidth = Math.max(2, width / 10);
		int spikeHalfHeight = Math.max(2, height / 10);
		int bodyLeft = midX - bodyHalfWidth;
		int bodyRight = midX + bodyHalfWidth;
		int bodyTop = midY - bodyHalfHeight;
		int bodyBottom = midY + bodyHalfHeight;
		return new java.awt.Polygon(
			new int[] {midX - spikeHalfWidth, midX, midX + spikeHalfWidth,
				bodyRight, right - 2, bodyRight, right, bodyRight, right - 2,
				bodyRight, midX + spikeHalfWidth, midX, midX - spikeHalfWidth,
				bodyLeft, x + 2, bodyLeft, x, bodyLeft, x + 2, bodyLeft},
			new int[] {bodyTop, y, bodyTop,
				bodyTop, y + 2, midY - spikeHalfHeight, midY, midY + spikeHalfHeight, bottom - 2,
				bodyBottom, bodyBottom, bottom, bodyBottom,
				bodyBottom, bottom - 2, midY + spikeHalfHeight, midY, midY - spikeHalfHeight, y + 2, bodyTop}, 20);
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
