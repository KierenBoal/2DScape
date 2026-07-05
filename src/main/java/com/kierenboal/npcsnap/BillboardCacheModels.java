package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

interface TimedCacheEntry
{
	long lastUsedMillis();
}

final class BillboardCacheKey
{
	final int animationId;
	final int animationFrame;
	final int poseAnimationId;
	final int poseAnimationFrame;
	final int relativeYaw;
	final int relativePitch;
	final int colorBands;
	final int lightBoost;
	final int outlinePadding;
	final boolean highlightOutline;
	final boolean shadowOutline;
	final boolean solidOutline;
	final boolean spriteShadow;
	final boolean highlightInline;
	final boolean shadowInline;
	final boolean solidInline;
	final int outlineColor;
	final boolean hoverOutline;
	final boolean interactOutline;
	final int hoverOutlineColor;
	final int interactOutlineColor;
	final int renderQuality;
	final int modelStateHash;
	final int textureStateHash;
	final int animatedTextureOffsetStateHash;

	BillboardCacheKey(
		int animationId,
		int animationFrame,
		int poseAnimationId,
		int poseAnimationFrame,
		int relativeYaw,
		int relativePitch,
		int colorBands,
		int lightBoost,
		int outlinePadding,
		boolean highlightOutline,
		boolean shadowOutline,
		boolean solidOutline,
		boolean spriteShadow,
		boolean highlightInline,
		boolean shadowInline,
		boolean solidInline,
		int outlineColor,
		boolean hoverOutline,
		boolean interactOutline,
		int hoverOutlineColor,
		int interactOutlineColor,
		int renderQuality,
		int modelStateHash,
		int textureStateHash,
		int animatedTextureOffsetStateHash)
	{
		this.animationId = animationId;
		this.animationFrame = animationFrame;
		this.poseAnimationId = poseAnimationId;
		this.poseAnimationFrame = poseAnimationFrame;
		this.relativeYaw = relativeYaw;
		this.relativePitch = relativePitch;
		this.colorBands = colorBands;
		this.lightBoost = lightBoost;
		this.outlinePadding = outlinePadding;
		this.highlightOutline = highlightOutline;
		this.shadowOutline = shadowOutline;
		this.solidOutline = solidOutline;
		this.spriteShadow = spriteShadow;
		this.highlightInline = highlightInline;
		this.shadowInline = shadowInline;
		this.solidInline = solidInline;
		this.outlineColor = outlineColor;
		this.hoverOutline = hoverOutline;
		this.interactOutline = interactOutline;
		this.hoverOutlineColor = hoverOutlineColor;
		this.interactOutlineColor = interactOutlineColor;
		this.renderQuality = renderQuality;
		this.modelStateHash = modelStateHash;
		this.textureStateHash = textureStateHash;
		this.animatedTextureOffsetStateHash = animatedTextureOffsetStateHash;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof BillboardCacheKey))
		{
			return false;
		}

		BillboardCacheKey that = (BillboardCacheKey) other;
		return animationId == that.animationId
			&& animationFrame == that.animationFrame
			&& poseAnimationId == that.poseAnimationId
			&& poseAnimationFrame == that.poseAnimationFrame
			&& relativeYaw == that.relativeYaw
			&& relativePitch == that.relativePitch
			&& colorBands == that.colorBands
			&& lightBoost == that.lightBoost
			&& outlinePadding == that.outlinePadding
			&& highlightOutline == that.highlightOutline
			&& shadowOutline == that.shadowOutline
			&& solidOutline == that.solidOutline
			&& spriteShadow == that.spriteShadow
			&& highlightInline == that.highlightInline
			&& shadowInline == that.shadowInline
			&& solidInline == that.solidInline
			&& outlineColor == that.outlineColor
			&& hoverOutline == that.hoverOutline
			&& interactOutline == that.interactOutline
			&& hoverOutlineColor == that.hoverOutlineColor
			&& interactOutlineColor == that.interactOutlineColor
			&& renderQuality == that.renderQuality
			&& modelStateHash == that.modelStateHash
			&& textureStateHash == that.textureStateHash
			&& animatedTextureOffsetStateHash == that.animatedTextureOffsetStateHash;
	}

	@Override
	public int hashCode()
	{
		int result = animationId;
		result = 31 * result + animationFrame;
		result = 31 * result + poseAnimationId;
		result = 31 * result + poseAnimationFrame;
		result = 31 * result + relativeYaw;
		result = 31 * result + relativePitch;
		result = 31 * result + colorBands;
		result = 31 * result + lightBoost;
		result = 31 * result + outlinePadding;
		result = 31 * result + (highlightOutline ? 1 : 0);
		result = 31 * result + (shadowOutline ? 1 : 0);
		result = 31 * result + (solidOutline ? 1 : 0);
		result = 31 * result + (spriteShadow ? 1 : 0);
		result = 31 * result + (highlightInline ? 1 : 0);
		result = 31 * result + (shadowInline ? 1 : 0);
		result = 31 * result + (solidInline ? 1 : 0);
		result = 31 * result + outlineColor;
		result = 31 * result + (hoverOutline ? 1 : 0);
		result = 31 * result + (interactOutline ? 1 : 0);
		result = 31 * result + hoverOutlineColor;
		result = 31 * result + interactOutlineColor;
		result = 31 * result + renderQuality;
		result = 31 * result + modelStateHash;
		result = 31 * result + textureStateHash;
		result = 31 * result + animatedTextureOffsetStateHash;
		return result;
	}
}

final class BillboardCachePreviewKey
{
	final int animationId;
	final int animationFrame;
	final int poseAnimationId;
	final int poseAnimationFrame;
	final int relativeYaw;
	final int relativePitch;
	final int colorBands;
	final int lightBoost;
	final int outlinePadding;
	final boolean highlightOutline;
	final boolean shadowOutline;
	final boolean solidOutline;
	final boolean spriteShadow;
	final boolean highlightInline;
	final boolean shadowInline;
	final boolean solidInline;
	final int outlineColor;
	final boolean hoverOutline;
	final boolean interactOutline;
	final int hoverOutlineColor;
	final int interactOutlineColor;
	final int renderQuality;
	final int modelStateHash;
	final int animatedTextureOffsetStateHash;

	BillboardCachePreviewKey(
		int animationId,
		int animationFrame,
		int poseAnimationId,
		int poseAnimationFrame,
		int relativeYaw,
		int relativePitch,
		int colorBands,
		int lightBoost,
		int outlinePadding,
		boolean highlightOutline,
		boolean shadowOutline,
		boolean solidOutline,
		boolean spriteShadow,
		boolean highlightInline,
		boolean shadowInline,
		boolean solidInline,
		int outlineColor,
		boolean hoverOutline,
		boolean interactOutline,
		int hoverOutlineColor,
		int interactOutlineColor,
		int renderQuality,
		int modelStateHash,
		int animatedTextureOffsetStateHash)
	{
		this.animationId = animationId;
		this.animationFrame = animationFrame;
		this.poseAnimationId = poseAnimationId;
		this.poseAnimationFrame = poseAnimationFrame;
		this.relativeYaw = relativeYaw;
		this.relativePitch = relativePitch;
		this.colorBands = colorBands;
		this.lightBoost = lightBoost;
		this.outlinePadding = outlinePadding;
		this.highlightOutline = highlightOutline;
		this.shadowOutline = shadowOutline;
		this.solidOutline = solidOutline;
		this.spriteShadow = spriteShadow;
		this.highlightInline = highlightInline;
		this.shadowInline = shadowInline;
		this.solidInline = solidInline;
		this.outlineColor = outlineColor;
		this.hoverOutline = hoverOutline;
		this.interactOutline = interactOutline;
		this.hoverOutlineColor = hoverOutlineColor;
		this.interactOutlineColor = interactOutlineColor;
		this.renderQuality = renderQuality;
		this.modelStateHash = modelStateHash;
		this.animatedTextureOffsetStateHash = animatedTextureOffsetStateHash;
	}
}

final class CachedBillboard implements TimedCacheEntry
{
	final BillboardCacheKey key;
	final Rectangle bounds;
	final BufferedImage image;
	private long lastUsedMillis;
	private long lastRedrawMillis;
	private boolean dirty;
	private boolean debugFrameRedrawn;
	private boolean debugFrameInvalidated;
	private java.awt.Color debugFrameInvalidatedColor;
	private NpcSnapDebug.StateDebugInfo stateDebugInfo;

	CachedBillboard(BillboardCacheKey key, Rectangle bounds, BufferedImage image, long lastUsedMillis)
	{
		this.key = key;
		this.bounds = new Rectangle(bounds);
		this.image = image;
		this.lastUsedMillis = lastUsedMillis;
		this.lastRedrawMillis = lastUsedMillis;
		this.dirty = true;
		this.debugFrameRedrawn = true;
		this.debugFrameInvalidated = false;
		this.debugFrameInvalidatedColor = null;
		this.stateDebugInfo = null;
	}

	void touch(long nowMillis)
	{
		lastUsedMillis = nowMillis;
	}

	@Override
	public long lastUsedMillis()
	{
		return lastUsedMillis;
	}

	long lastRedrawMillis()
	{
		return lastRedrawMillis;
	}

	boolean matchesPreviewKey(BillboardCachePreviewKey previewKey)
	{
		return previewKey != null
			&& key.animationId == previewKey.animationId
			&& key.animationFrame == previewKey.animationFrame
			&& key.poseAnimationId == previewKey.poseAnimationId
			&& key.poseAnimationFrame == previewKey.poseAnimationFrame
			&& key.relativeYaw == previewKey.relativeYaw
			&& key.relativePitch == previewKey.relativePitch
			&& key.colorBands == previewKey.colorBands
			&& key.lightBoost == previewKey.lightBoost
			&& key.outlinePadding == previewKey.outlinePadding
			&& key.highlightOutline == previewKey.highlightOutline
			&& key.shadowOutline == previewKey.shadowOutline
			&& key.solidOutline == previewKey.solidOutline
			&& key.spriteShadow == previewKey.spriteShadow
			&& key.highlightInline == previewKey.highlightInline
			&& key.shadowInline == previewKey.shadowInline
			&& key.solidInline == previewKey.solidInline
			&& key.outlineColor == previewKey.outlineColor
			&& key.hoverOutline == previewKey.hoverOutline
			&& key.interactOutline == previewKey.interactOutline
			&& key.hoverOutlineColor == previewKey.hoverOutlineColor
			&& key.interactOutlineColor == previewKey.interactOutlineColor
			&& key.renderQuality == previewKey.renderQuality
			&& key.modelStateHash == previewKey.modelStateHash
			&& key.animatedTextureOffsetStateHash == previewKey.animatedTextureOffsetStateHash;
	}

	boolean consumeDirty()
	{
		boolean wasDirty = dirty;
		dirty = false;
		return wasDirty;
	}

	void markDebugFrameRedrawn()
	{
		debugFrameRedrawn = true;
		debugFrameInvalidated = false;
		debugFrameInvalidatedColor = null;
	}

	boolean consumeDebugFrameRedrawn()
	{
		boolean wasRedrawn = debugFrameRedrawn;
		debugFrameRedrawn = false;
		return wasRedrawn;
	}

	void markDebugFrameInvalidated()
	{
		markDebugFrameInvalidated(null);
	}

	void markDebugFrameInvalidated(java.awt.Color color)
	{
		debugFrameInvalidated = true;
		debugFrameInvalidatedColor = color;
	}

	boolean consumeDebugFrameInvalidated()
	{
		boolean wasInvalidated = debugFrameInvalidated;
		debugFrameInvalidated = false;
		return wasInvalidated;
	}

	java.awt.Color consumeDebugFrameInvalidatedColor()
	{
		java.awt.Color color = debugFrameInvalidatedColor;
		debugFrameInvalidatedColor = null;
		return color;
	}

	NpcSnapDebug.StateDebugInfo stateDebugInfo()
	{
		return stateDebugInfo;
	}

	void stateDebugInfo(NpcSnapDebug.StateDebugInfo stateDebugInfo)
	{
		this.stateDebugInfo = stateDebugInfo;
	}

	void flush()
	{
		image.flush();
	}
}

final class TextureCacheEntry implements TimedCacheEntry
{
	final int[] pixels;
	final int width;
	final int height;
	private long lastUsedMillis;

	TextureCacheEntry(int[] pixels, int width, int height, long lastUsedMillis)
	{
		this.pixels = pixels;
		this.width = width;
		this.height = height;
		this.lastUsedMillis = lastUsedMillis;
	}

	void touch(long nowMillis)
	{
		lastUsedMillis = nowMillis;
	}

	@Override
	public long lastUsedMillis()
	{
		return lastUsedMillis;
	}
}
