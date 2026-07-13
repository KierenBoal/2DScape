package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

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

