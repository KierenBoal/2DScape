package com.kierenboal.npcsnap;

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

@Singleton
class NpcSnapDebug
{
	private static final Color BILLBOARD_RED = new Color(255, 0, 0, 220);
	private static final Color INVALIDATION_RED = new Color(255, 0, 0);
	private static final Color TEXT_BACKGROUND = new Color(0, 0, 0, 170);
	private static final Color TEXT_FOREGROUND = Color.WHITE;

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

	FrameDebugInfo actorFrameDebugInfo(Actor actor)
	{
		if (actor == null)
		{
			return null;
		}

		FrameState state = actorFrames.get(actor);
		if (state == null)
		{
			return new FrameDebugInfo(actor.getAnimation(), actor.getAnimationFrame(), actor.getAnimationFrame(), actor.getPoseAnimationFrame(), actor.getPoseAnimationFrame());
		}

		return new FrameDebugInfo(state.animationId, state.animationFrame, state.forcedAnimationFrame, state.poseFrame, state.forcedPoseFrame);
	}

	void drawBillboardDebug(Graphics2D graphics, RenderDebug renderDebug)
	{
	}

	void drawBillboardDebugForeground(Graphics2D graphics, RenderDebug renderDebug)
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

	private String frameText(RenderDebug renderDebug)
	{
		FrameDebugInfo frameDebugInfo = renderDebug.frameDebugInfo;
		if (frameDebugInfo != null)
		{
			return "Anim: " + frameDebugInfo.animationId + ", Frame " + frameDebugInfo.forcedAnimationFrame + " (" + frameDebugInfo.animationFrame + ")";
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
		private final Rectangle bounds;
		private final int paintOrder;
		private final boolean cacheInvalidated;
		private final FrameDebugInfo frameDebugInfo;

		private RenderDebug(
			Rectangle bounds,
			int paintOrder,
			boolean cacheInvalidated,
			FrameDebugInfo frameDebugInfo
		)
		{
			this.bounds = bounds;
			this.paintOrder = paintOrder;
			this.cacheInvalidated = cacheInvalidated;
			this.frameDebugInfo = frameDebugInfo;
		}

		static RenderDebug forBounds(Rectangle bounds, int paintOrder, boolean cacheInvalidated, boolean spriteRedrawn, FrameDebugInfo frameDebugInfo)
		{
			return new RenderDebug(bounds, paintOrder, cacheInvalidated || spriteRedrawn, frameDebugInfo);
		}
	}

	static final class FrameDebugInfo
	{
		private final int animationId;
		private final int animationFrame;
		private final int forcedAnimationFrame;
		private final int poseFrame;
		private final int forcedPoseFrame;

		private FrameDebugInfo(int animationId, int animationFrame, int forcedAnimationFrame, int poseFrame, int forcedPoseFrame)
		{
			this.animationId = animationId;
			this.animationFrame = animationFrame;
			this.forcedAnimationFrame = forcedAnimationFrame;
			this.poseFrame = poseFrame;
			this.forcedPoseFrame = forcedPoseFrame;
		}

		static FrameDebugInfo of(int animationId, int animationFrame, int forcedAnimationFrame)
		{
			return new FrameDebugInfo(animationId, animationFrame, forcedAnimationFrame, -1, -1);
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
}
