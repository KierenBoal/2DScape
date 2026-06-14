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
		
		if (config.debugDrawBillboardOutline())
		{
			int size = 2;
			Stroke stroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(size));
			graphics.setColor(BILLBOARD_RED);
			graphics.drawRect(bounds.x - (size / 2), bounds.y - (size / 2), bounds.width + size, bounds.height + size);
			graphics.setStroke(stroke);
		}

		if (config.debugShowFrameRedraws() && renderDebug.frameRedrawn)
		{
			int size = 4;
			Stroke stroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(size));
			graphics.setColor(frameRedrawColor(renderDebug));
			graphics.drawRect(bounds.x + (size / 2), bounds.y + (size / 2), bounds.width - size, bounds.height - size);
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

	private Color frameRedrawColor(RenderDebug renderDebug)
	{
		
		int seed = 1;
		seed = (31 * seed) + client.getGameCycle();
		seed = (31 * seed) + renderDebug.paintOrder;
		seed = (31 * seed) + renderDebug.bounds.x;
		seed = (31 * seed) + renderDebug.bounds.y;
		seed = (31 * seed) + renderDebug.bounds.width;
		seed = (31 * seed) + renderDebug.bounds.height;
		
		int pattern = Math.floorMod(seed, 7);
		
		int red = 0;
		int green = 0;
		int blue = 0;
		
		if (pattern == 0)
		{
			red = brightColorChannel(seed, 0);
		}
		
		if (pattern == 1)
		{
			green = brightColorChannel(seed, 8);
		}
		
		if (pattern == 2)
		{
			blue = brightColorChannel(seed, 16);
		}
		
		if (pattern == 3)
		{
			red = brightColorChannel(seed, 0);
			green = brightColorChannel(seed, 8);
		}
		
		if (pattern == 4)
		{
			red = brightColorChannel(seed, 0);
			blue = brightColorChannel(seed, 8);
		}
		
		if (pattern == 5)
		{
			green = brightColorChannel(seed, 0);
			blue = brightColorChannel(seed, 8);
		}
		
		if (pattern == 6)
		{
			red = brightColorChannel(seed, 0);
			blue = brightColorChannel(seed, 8);
		}

		return new Color(red, green, blue, 255);
	}

	private static int brightColorChannel(int seed, int shift)
	{
		return 192 + Math.floorMod(seed >> shift, 64);
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
		private final boolean frameRedrawn;
		private final FrameDebugInfo frameDebugInfo;

		private RenderDebug(
			Rectangle bounds,
			int paintOrder,
			boolean frameRedrawn,
			FrameDebugInfo frameDebugInfo
		)
		{
			this.bounds = bounds;
			this.paintOrder = paintOrder;
			this.frameRedrawn = frameRedrawn;
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
