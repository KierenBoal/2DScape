package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.NpcSnapDebug;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public final class CachedBillboard implements TimedCacheEntry
{
	public final BillboardCacheKey key;
	public final Rectangle bounds;
	public final Rectangle contentBounds;
	public final BufferedImage image;
	private long lastUsedMillis;
	private long lastRedrawMillis;
	private boolean dirty;
	private boolean debugFrameRedrawn;
	private boolean debugFrameInvalidated;
	private java.awt.Color debugFrameInvalidatedColor;
	private NpcSnapDebug.StateDebugInfo stateDebugInfo;

	public CachedBillboard(BillboardCacheKey key, Rectangle bounds, BufferedImage image, long lastUsedMillis)
	{
		this(key, bounds, bounds, image, lastUsedMillis);
	}

	public CachedBillboard(
		BillboardCacheKey key,
		Rectangle bounds,
		Rectangle contentBounds,
		BufferedImage image,
		long lastUsedMillis)
	{
		this.key = key;
		this.bounds = new Rectangle(bounds);
		this.contentBounds = new Rectangle(contentBounds);
		this.image = image;
		this.lastUsedMillis = lastUsedMillis;
		this.lastRedrawMillis = lastUsedMillis;
		this.dirty = true;
		this.debugFrameRedrawn = true;
		this.debugFrameInvalidated = false;
		this.debugFrameInvalidatedColor = null;
		this.stateDebugInfo = null;
	}

	public void touch(long nowMillis)
	{
		lastUsedMillis = nowMillis;
	}

	@Override
	public long lastUsedMillis()
	{
		return lastUsedMillis;
	}

	public long lastRedrawMillis()
	{
		return lastRedrawMillis;
	}

	public boolean matchesPreviewKey(BillboardCachePreviewKey previewKey)
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

	public boolean consumeDirty()
	{
		boolean wasDirty = dirty;
		dirty = false;
		return wasDirty;
	}

	public void markDebugFrameRedrawn()
	{
		debugFrameRedrawn = true;
		debugFrameInvalidated = false;
		debugFrameInvalidatedColor = null;
	}

	public boolean consumeDebugFrameRedrawn()
	{
		boolean wasRedrawn = debugFrameRedrawn;
		debugFrameRedrawn = false;
		return wasRedrawn;
	}

	public void markDebugFrameInvalidated()
	{
		markDebugFrameInvalidated(null);
	}

	public void markDebugFrameInvalidated(java.awt.Color color)
	{
		debugFrameInvalidated = true;
		debugFrameInvalidatedColor = color;
	}

	public boolean consumeDebugFrameInvalidated()
	{
		boolean wasInvalidated = debugFrameInvalidated;
		debugFrameInvalidated = false;
		return wasInvalidated;
	}

	public java.awt.Color consumeDebugFrameInvalidatedColor()
	{
		java.awt.Color color = debugFrameInvalidatedColor;
		debugFrameInvalidatedColor = null;
		return color;
	}

	public NpcSnapDebug.StateDebugInfo stateDebugInfo()
	{
		return stateDebugInfo;
	}

	public void stateDebugInfo(NpcSnapDebug.StateDebugInfo stateDebugInfo)
	{
		this.stateDebugInfo = stateDebugInfo;
	}

	public void flush()
	{
		image.flush();
	}
}

