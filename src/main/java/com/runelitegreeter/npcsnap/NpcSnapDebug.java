package com.runelitegreeter.npcsnap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;

@Singleton
class NpcSnapDebug
{
	private static final Color BILLBOARD_RED = new Color(255, 0, 0, 220);
	private static final Color INVALIDATION_RED = new Color(255, 0, 0);
	private static final Color BOUNDING_BOX_CYAN = new Color(0, 220, 255, 220);
	private static final Color TEXT_BACKGROUND = new Color(0, 0, 0, 170);
	private static final Color TEXT_FOREGROUND = Color.WHITE;
	private static final int LOCAL_TILE_SIZE = 128;

	private final Client client;
	private final NpcSnapConfig config;
	private final Map<Actor, FrameState> actorFrames = new IdentityHashMap<>();

	@Inject
	private NpcSnapDebug(Client client, NpcSnapConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void clearFrameStates()
	{
		actorFrames.clear();
	}

	void recordActorFrames(Actor actor, int animationId, int animationFrame, int forcedAnimationFrame, int poseFrame, int forcedPoseFrame)
	{
		if (actor == null)
		{
			return;
		}

		actorFrames.put(actor, new FrameState(animationId, animationFrame, forcedAnimationFrame, poseFrame, forcedPoseFrame));
	}

	void drawBillboardDebug(Graphics2D graphics, RenderDebug renderDebug)
	{
		Rectangle bounds = renderDebug.bounds;
		if (bounds == null)
		{
			return;
		}

		if (config.debugShowCacheInvalidations() && renderDebug.cacheInvalidated)
		{
			graphics.setColor(INVALIDATION_RED);
			graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}

		if (config.debugDrawBillboardOutline())
		{
			Stroke stroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(2.0f));
			graphics.setColor(BILLBOARD_RED);
			graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			graphics.setStroke(stroke);
		}

		if (config.debugDrawBillboardPaintOrder())
		{
			drawCenteredText(graphics, Integer.toString(renderDebug.paintOrder), bounds.x + (bounds.width / 2), bounds.y + (bounds.height / 2));
		}

		if (config.debugDrawFrameNumber())
		{
			String frameText = frameText(renderDebug);
			if (frameText != null)
			{
				drawCenteredText(graphics, frameText, bounds.x + (bounds.width / 2), bounds.y + Math.max(12, bounds.height / 4));
			}
		}
	}

	void drawActorBoundingBox(Graphics2D graphics, Actor actor)
	{
		LocalPoint localPoint = actor.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		Model model = actor.getModel();
		if (model == null)
		{
			return;
		}

		int height = Math.max(1, actor.getModelHeight());
		drawProjectedBox(graphics, modelBox(localPoint.getX(), localPoint.getY(), actor.getWorldView().getPlane(), 0, height, model));
	}

	void drawProjectileBoundingBox(Graphics2D graphics, Projectile projectile)
	{
		Model model = projectile.getModel();
		if (model == null)
		{
			return;
		}

		int height = Math.max(1, projectile.getModelHeight());
		LocalPoint localPoint = new LocalPoint((int) projectile.getX(), (int) projectile.getY());
		int verticalOffset = Perspective.getTileHeight(client, localPoint, projectile.getFloor()) - (int) Math.round(projectile.getZ());
		drawProjectedBox(graphics, modelBox(localPoint.getX(), localPoint.getY(), projectile.getFloor(), verticalOffset, height, model));
	}

	void drawGroundItemBoundingBox(Graphics2D graphics, TileItem item, LocalPoint localPoint, int plane)
	{
		if (localPoint == null)
		{
			return;
		}

		Model model = item.getModel();
		if (model == null)
		{
			return;
		}

		int height = Math.max(1, item.getModelHeight());
		drawProjectedBox(graphics, modelBox(localPoint.getX(), localPoint.getY(), plane, 0, height, model));
	}

	private void drawProjectedBox(Graphics2D graphics, ProjectedBox box)
	{
		Stroke stroke = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.setColor(BOUNDING_BOX_CYAN);
		for (int i = 0; i < 4; i++)
		{
			int next = (i + 1) % 4;
			drawProjectedLine(graphics, box.base[i], box.base[next]);
			drawProjectedLine(graphics, box.top[i], box.top[next]);
			drawProjectedLine(graphics, box.base[i], box.top[i]);
		}
		graphics.setStroke(stroke);
	}

	private ProjectedBox modelBox(int localX, int localY, int plane, int verticalOffset, int height, Model model)
	{
		ModelBounds bounds = modelBounds(model);
		Point[] base = new Point[4];
		Point[] top = new Point[4];
		int[] x = new int[]{localX + bounds.minX, localX + bounds.maxX, localX + bounds.maxX, localX + bounds.minX};
		int[] y = new int[]{localY + bounds.minZ, localY + bounds.minZ, localY + bounds.maxZ, localY + bounds.maxZ};
		for (int i = 0; i < x.length; i++)
		{
			LocalPoint point = new LocalPoint(x[i], y[i]);
			base[i] = Perspective.localToCanvas(client, point, plane, verticalOffset);
			top[i] = Perspective.localToCanvas(client, point, plane, verticalOffset + height);
		}

		return new ProjectedBox(base, top);
	}

	private static ModelBounds modelBounds(Model model)
	{
		int vertexCount = model.getVerticesCount();
		float[] verticesX = model.getVerticesX();
		float[] verticesZ = model.getVerticesZ();
		if (vertexCount <= 0 || verticesX == null || verticesZ == null)
		{
			int half = LOCAL_TILE_SIZE / 2;
			return new ModelBounds(-half, half, -half, half);
		}

		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < vertexCount; i++)
		{
			int x = Math.round(verticesX[i]);
			int z = Math.round(verticesZ[i]);
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}

		if (minX == Integer.MAX_VALUE)
		{
			int half = LOCAL_TILE_SIZE / 2;
			return new ModelBounds(-half, half, -half, half);
		}

		return new ModelBounds(minX, maxX, minZ, maxZ);
	}

	private static void drawProjectedLine(Graphics2D graphics, Point a, Point b)
	{
		if (a == null || b == null)
		{
			return;
		}

		graphics.drawLine(a.getX(), a.getY(), b.getX(), b.getY());
	}

	private String frameText(RenderDebug renderDebug)
	{
		if (renderDebug.actor != null)
		{
			FrameState state = actorFrames.get(renderDebug.actor);
			if (state == null)
			{
				return "Anim: " + renderDebug.actor.getAnimation()
					+ ", Frame " + renderDebug.actor.getAnimationFrame()
					+ " (" + renderDebug.actor.getAnimationFrame() + ")";
			}

			return "Anim: " + state.animationId + ", Frame " + state.forcedAnimationFrame + " (" + state.animationFrame + ")";
		}

		if (renderDebug.projectile != null)
		{
			int frame = renderDebug.projectile.getAnimationFrame();
			return "Anim: " + renderDebug.projectile.getId() + ", Frame " + frame + " (" + frame + ")";
		}

		return null;
	}

	private void drawCenteredText(Graphics2D graphics, String text, int centerX, int centerY)
	{
		Font font = graphics.getFont().deriveFont(Font.BOLD, 12.0f);
		Font oldFont = graphics.getFont();
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();
		int padding = 3;
		int width = metrics.stringWidth(text);
		int height = metrics.getHeight();
		int x = centerX - (width / 2);
		int y = centerY - (height / 2);

		graphics.setColor(TEXT_BACKGROUND);
		graphics.fillRect(x - padding, y - padding, width + (padding * 2), height + (padding * 2));
		graphics.setColor(TEXT_FOREGROUND);
		graphics.drawString(text, x, y + metrics.getAscent());
		graphics.setFont(oldFont);
	}

	static final class RenderDebug
	{
		private final Actor actor;
		private final Projectile projectile;
		private final TileItem groundItem;
		private final LocalPoint groundItemLocalPoint;
		private final int groundItemPlane;
		private final Rectangle bounds;
		private final int paintOrder;
		private final boolean cacheInvalidated;

		private RenderDebug(
			Actor actor,
			Projectile projectile,
			TileItem groundItem,
			LocalPoint groundItemLocalPoint,
			int groundItemPlane,
			Rectangle bounds,
			int paintOrder,
			boolean cacheInvalidated
		)
		{
			this.actor = actor;
			this.projectile = projectile;
			this.groundItem = groundItem;
			this.groundItemLocalPoint = groundItemLocalPoint;
			this.groundItemPlane = groundItemPlane;
			this.bounds = bounds;
			this.paintOrder = paintOrder;
			this.cacheInvalidated = cacheInvalidated;
		}

		static RenderDebug forActor(Actor actor, Rectangle bounds, int paintOrder, boolean cacheInvalidated)
		{
			return new RenderDebug(actor, null, null, null, 0, bounds, paintOrder, cacheInvalidated);
		}

		static RenderDebug forProjectile(Projectile projectile, Rectangle bounds, int paintOrder, boolean cacheInvalidated)
		{
			return new RenderDebug(null, projectile, null, null, 0, bounds, paintOrder, cacheInvalidated);
		}

		static RenderDebug forGroundItem(TileItem item, LocalPoint localPoint, int plane, Rectangle bounds, int paintOrder, boolean cacheInvalidated)
		{
			return new RenderDebug(null, null, item, localPoint, plane, bounds, paintOrder, cacheInvalidated);
		}
	}

	private static final class FrameState
	{
		private final int animationId;
		private final int animationFrame;
		private final int forcedAnimationFrame;
		private final int poseFrame;
		private final int forcedPoseFrame;

		private FrameState(int animationId, int animationFrame, int forcedAnimationFrame, int poseFrame, int forcedPoseFrame)
		{
			this.animationId = animationId;
			this.animationFrame = animationFrame;
			this.forcedAnimationFrame = forcedAnimationFrame;
			this.poseFrame = poseFrame;
			this.forcedPoseFrame = forcedPoseFrame;
		}
	}

	private static final class ProjectedBox
	{
		private final Point[] base;
		private final Point[] top;

		private ProjectedBox(Point[] base, Point[] top)
		{
			this.base = base;
			this.top = top;
		}
	}

	private static final class ModelBounds
	{
		private final int minX;
		private final int maxX;
		private final int minZ;
		private final int maxZ;

		private ModelBounds(int minX, int maxX, int minZ, int maxZ)
		{
			this.minX = minX;
			this.maxX = maxX;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}
	}
}
