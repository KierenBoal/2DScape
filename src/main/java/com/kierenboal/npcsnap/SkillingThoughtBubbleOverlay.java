package com.kierenboal.npcsnap;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpriteID.IconStat50x50;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class SkillingThoughtBubbleOverlay extends Overlay
{
	static final long FADE_IN_MILLIS = 500L;
	static final long FADE_OUT_MILLIS = 2_500L;
	private static final Color BUBBLE_FILL = new Color(255, 255, 255, 150);
	private static final Color BUBBLE_OUTLINE = new Color(255, 255, 255, 215);
	private static final int ICON_SIZE = 24;
	private static final int ICON_GAP = 4;
	private static final int BUBBLE_PADDING = 8;
	private static final int MIN_BUBBLE_SIZE = 44;
	private static final int HEAD_GAP = 34;
	private static final int BUBBLE_SIDE_OFFSET = 42;
	private static final int BUBBLE_UP_OFFSET = 32;
	private static final int FACING_PROBE_DISTANCE = 96;
	private static final int VIEWPORT_MARGIN = 8;
	private static final int WOBBLE_POINTS = 48;
	private static final double WOBBLE_AMOUNT = 1.6d;

	private final Client client;
	private final NpcSnapConfig config;
	private final SkillingActivityTracker skillingActivityTracker;
	private final SpriteManager spriteManager;
	private final SkillIconManager skillIconManager;
	private final Map<Skill, BufferedImage> skillImages = new EnumMap<>(Skill.class);

	@Inject
	private SkillingThoughtBubbleOverlay(
		Client client,
		NpcSnapConfig config,
		SkillingActivityTracker skillingActivityTracker,
		SpriteManager spriteManager,
		SkillIconManager skillIconManager
	)
	{
		this.client = client;
		this.config = config;
		this.skillingActivityTracker = skillingActivityTracker;
		this.spriteManager = spriteManager;
		this.skillIconManager = skillIconManager;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableSkillingBubbles() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		long nowMillis = System.currentTimeMillis();
		List<Skill> activeSkills = skillingActivityTracker.getRenderableSkills(nowMillis, FADE_OUT_MILLIS);
		if (activeSkills.isEmpty())
		{
			return null;
		}

		Point anchor = getBubbleAnchor(player);
		if (anchor == null)
		{
			return null;
		}

		float alpha = bubbleAlpha(activeSkills, nowMillis);
		if (alpha <= 0.0f)
		{
			return null;
		}

		drawBubble(graphics, player, anchor, activeSkills, nowMillis, alpha);
		return null;
	}

	private Point getBubbleAnchor(Player player)
	{
		LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null || player.getWorldView() == null)
		{
			return null;
		}

		int zOffset = Math.max(0, player.getAnimationHeightOffset()) + player.getModelHeight() + HEAD_GAP;
		return Perspective.localToCanvas(client, localPoint, player.getWorldView().getPlane(), zOffset);
	}

	private void drawBubble(Graphics2D graphics, Player player, Point anchor, List<Skill> activeSkills, long nowMillis, float alpha)
	{
		int iconCount = activeSkills.size();
		int iconsWidth = (iconCount * ICON_SIZE) + ((iconCount - 1) * ICON_GAP);
		int bubbleWidth = Math.max(MIN_BUBBLE_SIZE, iconsWidth + (BUBBLE_PADDING * 2));
		int bubbleHeight = MIN_BUBBLE_SIZE;
		double time = nowMillis / 1000.0d;
		float introProgress = bubbleIntroProgress(activeSkills, nowMillis);
		BubblePlacement restingPlacement = chooseBubblePlacement(player, anchor, bubbleWidth, bubbleHeight);
		BubblePlacement animatedPlacement = interpolatePlacement(anchor, restingPlacement, bubbleWidth, bubbleHeight, introProgress);
		int bubbleX = animatedPlacement.x + (int) Math.round(Math.sin(time * 1.2d) * 1.2d);
		int bubbleY = animatedPlacement.y + (int) Math.round(Math.cos(time * 1.0d) * 0.8d);
		Shape bubbleShape = createWobblyOval(bubbleX, bubbleY, bubbleWidth, bubbleHeight, time);

		Composite originalComposite = graphics.getComposite();
		Object originalAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

		drawTrailingCircles(graphics, anchor.getX(), anchor.getY(), bubbleX + (bubbleWidth / 2), bubbleY + bubbleHeight, time);

		graphics.setColor(BUBBLE_FILL);
		graphics.fill(bubbleShape);
		graphics.setColor(BUBBLE_OUTLINE);
		graphics.draw(bubbleShape);

		int iconX = bubbleX + ((bubbleWidth - iconsWidth) / 2);
		int iconY = bubbleY + ((bubbleHeight - ICON_SIZE) / 2);
		for (Skill skill : activeSkills)
		{
			BufferedImage image = getSkillImage(skill);
			if (image != null)
			{
				drawContainedImage(graphics, image, iconX, iconY, ICON_SIZE, ICON_SIZE);
			}

			iconX += ICON_SIZE + ICON_GAP;
		}

		graphics.setComposite(originalComposite);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing);
	}

	private static void drawContainedImage(Graphics2D graphics, BufferedImage image, int x, int y, int maxWidth, int maxHeight)
	{
		int imageWidth = image.getWidth();
		int imageHeight = image.getHeight();
		if (imageWidth <= 0 || imageHeight <= 0)
		{
			return;
		}

		double scale = Math.min((double) maxWidth / imageWidth, (double) maxHeight / imageHeight);
		int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
		int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
		int drawX = x + ((maxWidth - drawWidth) / 2);
		int drawY = y + ((maxHeight - drawHeight) / 2);
		graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
	}

	private float bubbleAlpha(List<Skill> activeSkills, long nowMillis)
	{
		float alpha = 0.0f;
		for (Skill skill : activeSkills)
		{
			alpha = Math.max(alpha, skillAlpha(skill, nowMillis));
		}
		return alpha;
	}

	private float bubbleIntroProgress(List<Skill> activeSkills, long nowMillis)
	{
		float progress = 1.0f;
		for (Skill skill : activeSkills)
		{
			long visibleFrom = skillingActivityTracker.getVisibleFromMillis(skill);
			if (visibleFrom <= 0L)
			{
				continue;
			}

			progress = Math.min(progress, clampAlpha((float) (nowMillis - visibleFrom) / FADE_IN_MILLIS));
		}
		return progress;
	}

	private float skillAlpha(Skill skill, long nowMillis)
	{
		long visibleFrom = skillingActivityTracker.getVisibleFromMillis(skill);
		long activeUntil = skillingActivityTracker.getActiveUntilMillis(skill);
		if (visibleFrom <= 0L || activeUntil <= 0L)
		{
			return 0.0f;
		}

		if (nowMillis < visibleFrom + FADE_IN_MILLIS)
		{
			return clampAlpha((float) (nowMillis - visibleFrom) / FADE_IN_MILLIS);
		}

		if (nowMillis <= activeUntil)
		{
			return 1.0f;
		}

		return clampAlpha(1.0f - ((float) (nowMillis - activeUntil) / FADE_OUT_MILLIS));
	}

	private static float clampAlpha(float alpha)
	{
		return Math.max(0.0f, Math.min(1.0f, alpha));
	}

	private BubblePlacement chooseBubblePlacement(Player player, Point anchor, int bubbleWidth, int bubbleHeight)
	{
		int facingSide = projectedFacingSide(player, anchor);
		int screenRoomSide = anchor.getX() < client.getViewportXOffset() + (client.getViewportWidth() / 2) ? 1 : -1;
		BubblePlacement left = candidatePlacement(anchor, bubbleWidth, bubbleHeight, -1);
		BubblePlacement right = candidatePlacement(anchor, bubbleWidth, bubbleHeight, 1);
		double leftScore = placementScore(left, -1, facingSide, screenRoomSide, player);
		double rightScore = placementScore(right, 1, facingSide, screenRoomSide, player);
		return rightScore > leftScore ? right : left;
	}

	private static BubblePlacement interpolatePlacement(Point anchor, BubblePlacement targetPlacement, int bubbleWidth, int bubbleHeight, float progress)
	{
		int startX = anchor.getX() - (bubbleWidth / 2);
		int startY = anchor.getY() - (bubbleHeight / 2);
		int x = Math.round(startX + ((targetPlacement.x - startX) * progress));
		int y = Math.round(startY + ((targetPlacement.y - startY) * progress));
		return new BubblePlacement(x, y, bubbleWidth, bubbleHeight);
	}

	private BubblePlacement candidatePlacement(Point anchor, int bubbleWidth, int bubbleHeight, int side)
	{
		int centerX = anchor.getX() + (side * (BUBBLE_SIDE_OFFSET + (bubbleWidth / 2)));
		int centerY = anchor.getY() - BUBBLE_UP_OFFSET - (bubbleHeight / 2);
		return new BubblePlacement(centerX - (bubbleWidth / 2), centerY - (bubbleHeight / 2), bubbleWidth, bubbleHeight);
	}

	private double placementScore(BubblePlacement placement, int side, int facingSide, int screenRoomSide, Player player)
	{
		double score = 0.0d;
		if (side == facingSide)
		{
			score += 75.0d;
		}
		if (side == screenRoomSide)
		{
			score += 35.0d;
		}

		Rectangle viewport = new Rectangle(
			client.getViewportXOffset() + VIEWPORT_MARGIN,
			client.getViewportYOffset() + VIEWPORT_MARGIN,
			Math.max(0, client.getViewportWidth() - (VIEWPORT_MARGIN * 2)),
			Math.max(0, client.getViewportHeight() - (VIEWPORT_MARGIN * 2))
		);
		score -= overflowAmount(placement.bounds, viewport) * 3.0d;

		Shape hull = player.getConvexHull();
		if (hull != null && hull.getBounds().intersects(placement.bounds))
		{
			score -= 100.0d;
		}

		return score;
	}

	private int projectedFacingSide(Player player, Point anchor)
	{
		LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null || player.getWorldView() == null)
		{
			return 0;
		}

		int orientation = Math.floorMod(player.getCurrentOrientation(), 2048);
		int dx = Perspective.SINE[orientation] * FACING_PROBE_DISTANCE / 65536;
		int dy = Perspective.COSINE[orientation] * FACING_PROBE_DISTANCE / 65536;
		LocalPoint facingPoint = new LocalPoint(localPoint.getX() + dx, localPoint.getY() + dy, player.getWorldView());
		int zOffset = Math.max(0, player.getAnimationHeightOffset()) + player.getModelHeight() + HEAD_GAP;
		Point projectedFacing = Perspective.localToCanvas(client, facingPoint, player.getWorldView().getPlane(), zOffset);
		if (projectedFacing == null)
		{
			return 0;
		}

		int projectedDx = projectedFacing.getX() - anchor.getX();
		if (Math.abs(projectedDx) < 4)
		{
			return 0;
		}

		return projectedDx > 0 ? 1 : -1;
	}

	private static int overflowAmount(Rectangle inner, Rectangle outer)
	{
		int overflow = 0;
		overflow += Math.max(0, outer.x - inner.x);
		overflow += Math.max(0, outer.y - inner.y);
		overflow += Math.max(0, (inner.x + inner.width) - (outer.x + outer.width));
		overflow += Math.max(0, (inner.y + inner.height) - (outer.y + outer.height));
		return overflow;
	}

	private void drawTrailingCircles(Graphics2D graphics, int startX, int startY, int endX, int endY, double time)
	{
		drawTrailCircle(graphics, startX, startY, endX, endY, 0.28d, 7, time + 0.7d);
		drawTrailCircle(graphics, startX, startY, endX, endY, 0.52d, 10, time + 1.5d);
		drawTrailCircle(graphics, startX, startY, endX, endY, 0.76d, 14, time + 2.3d);
	}

	private static void drawTrailCircle(Graphics2D graphics, int startX, int startY, int endX, int endY, double progress, int size, double time)
	{
		double dx = endX - startX;
		double dy = endY - startY;
		double length = Math.max(1.0d, Math.sqrt((dx * dx) + (dy * dy)));
		double perpendicularX = -dy / length;
		double perpendicularY = dx / length;
		double offset = Math.sin((time * 1.4d) + (progress * 4.0d)) * 2.0d;
		int centerX = (int) Math.round(startX + (dx * progress) + (perpendicularX * offset));
		int centerY = (int) Math.round(startY + (dy * progress) + (perpendicularY * offset));
		drawCircle(graphics, centerX, centerY, size, time);
	}

	private static void drawCircle(Graphics2D graphics, int centerX, int centerY, int size, double time)
	{
		int radius = size / 2;
		Shape bubbleShape = createWobblyOval(centerX - radius, centerY - radius, size, size, time);
		graphics.setColor(BUBBLE_FILL);
		graphics.fill(bubbleShape);
		graphics.setColor(BUBBLE_OUTLINE);
		graphics.draw(bubbleShape);
	}

	private static Shape createWobblyOval(int x, int y, int width, int height, double time)
	{
		double centerX = x + (width / 2.0d);
		double centerY = y + (height / 2.0d);
		double radiusX = width / 2.0d;
		double radiusY = height / 2.0d;
		Path2D.Double path = new Path2D.Double();

		for (int i = 0; i <= WOBBLE_POINTS; i++)
		{
			double angle = (Math.PI * 2.0d * i) / WOBBLE_POINTS;
			double noise = (Math.sin((angle * 3.0d) + (time * 1.3d)) * 0.55d)
				+ (Math.sin((angle * 5.0d) - (time * 0.9d)) * 0.35d)
				+ (Math.cos((angle * 2.0d) + (time * 0.7d)) * 0.25d);
			double wobble = noise * WOBBLE_AMOUNT;
			double pointX = centerX + (Math.cos(angle) * (radiusX + wobble));
			double pointY = centerY + (Math.sin(angle) * (radiusY + wobble));

			if (i == 0)
			{
				path.moveTo(pointX, pointY);
			}
			else
			{
				path.lineTo(pointX, pointY);
			}
		}

		path.closePath();
		return path;
	}

	private BufferedImage getSkillImage(Skill skill)
	{
		return skillImages.computeIfAbsent(skill, this::loadSkillImage);
	}

	private BufferedImage loadSkillImage(Skill skill)
	{
		BufferedImage gameSprite = spriteManager.getSprite(skillSpriteId(skill), 0);
		if (gameSprite != null)
		{
			return gameSprite;
		}

		return skillIconManager.getSkillImage(skill);
	}

	private static int skillSpriteId(Skill skill)
	{
		switch (skill)
		{
			case AGILITY:
				return SpriteID.SKILL_AGILITY;
			case HERBLORE:
				return SpriteID.SKILL_HERBLORE;
			case THIEVING:
				return SpriteID.SKILL_THIEVING;
			case CRAFTING:
				return SpriteID.SKILL_CRAFTING;
			case FLETCHING:
				return SpriteID.SKILL_FLETCHING;
			case MINING:
				return SpriteID.SKILL_MINING;
			case SMITHING:
				return SpriteID.SKILL_SMITHING;
			case FISHING:
				return SpriteID.SKILL_FISHING;
			case COOKING:
				return SpriteID.SKILL_COOKING;
			case FIREMAKING:
				return SpriteID.SKILL_FIREMAKING;
			case WOODCUTTING:
				return SpriteID.SKILL_WOODCUTTING;
			case RUNECRAFT:
				return SpriteID.SKILL_RUNECRAFT;
			case FARMING:
				return SpriteID.SKILL_FARMING;
			case HUNTER:
				return SpriteID.SKILL_HUNTER;
			case CONSTRUCTION:
				return SpriteID.SKILL_CONSTRUCTION;
			case SAILING:
				return IconStat50x50._23;
			default:
				return IconStat50x50._0 + skill.ordinal();
		}
	}

	private static final class BubblePlacement
	{
		private final int x;
		private final int y;
		private final Rectangle bounds;

		private BubblePlacement(int x, int y, int width, int height)
		{
			this.x = x;
			this.y = y;
			this.bounds = new Rectangle(x, y, width, height);
		}
	}
}
