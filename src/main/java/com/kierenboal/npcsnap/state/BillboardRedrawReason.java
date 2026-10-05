package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.BillboardConstants;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.EnumSet;
import java.util.StringJoiner;

/** Independent causes of a dirty sprite, carried from its update plan to debug drawing. */
public enum BillboardRedrawReason
{
	INITIAL_DRAW("Initial sprite"),
	FRAME_COUNT("Frame count"),
	ACTOR_RESIZE("Clickbox/hull size change"),
	ANIMATION_CHANGE("Animation change"),
	INTERACTION_CHANGE("Hover/interaction change"),
	VIEW_CHANGE("View angle change"),
	MODEL_CHANGE("Model change"),
	TEXTURE_CHANGE("Texture change"),
	QUALITY_CHANGE("Quality change"),
	APPEARANCE_CHANGE("Appearance change"),
	BOUNDS_CHANGE("Sprite bounds change"),
	CACHE_EXPIRY("Cache expiry");

	private final String label;

	BillboardRedrawReason(String label)
	{
		this.label = label;
	}

	public static String format(Collection<BillboardRedrawReason> reasons)
	{
		StringJoiner labels = new StringJoiner(", ");
		for (BillboardRedrawReason reason : reasons)
		{
			labels.add(reason.label);
		}
		return labels.toString();
	}

	public static EnumSet<BillboardRedrawReason> cacheChanges(CachedBillboard displayed, BillboardCacheKey current,
		Rectangle imageBounds, long nowMillis)
	{
		EnumSet<BillboardRedrawReason> reasons = EnumSet.noneOf(BillboardRedrawReason.class);
		if (displayed == null)
		{
			reasons.add(INITIAL_DRAW);
			return reasons;
		}
		BillboardCacheKey previous = displayed.key;
		if (previous.animationFrame != current.animationFrame || previous.poseAnimationFrame != current.poseAnimationFrame)
		{
			reasons.add(FRAME_COUNT);
		}
		if (previous.animationId != current.animationId || previous.poseAnimationId != current.poseAnimationId)
		{
			reasons.add(ANIMATION_CHANGE);
		}
		if (previous.hoverOutline != current.hoverOutline || previous.interactOutline != current.interactOutline
			|| previous.hoverOutlineColor != current.hoverOutlineColor || previous.interactOutlineColor != current.interactOutlineColor)
		{
			reasons.add(INTERACTION_CHANGE);
		}
		if (previous.relativeYaw != current.relativeYaw || previous.relativePitch != current.relativePitch)
		{
			reasons.add(VIEW_CHANGE);
		}
		if (previous.modelStateHash != current.modelStateHash)
		{
			reasons.add(MODEL_CHANGE);
		}
		if (previous.textureStateHash != current.textureStateHash
			|| previous.animatedTextureOffsetStateHash != current.animatedTextureOffsetStateHash)
		{
			reasons.add(TEXTURE_CHANGE);
		}
		if (previous.renderQuality != current.renderQuality)
		{
			reasons.add(QUALITY_CHANGE);
		}
		if (previous.colorBands != current.colorBands || previous.lightBoost != current.lightBoost
			|| previous.outlinePadding != current.outlinePadding || previous.highlightOutline != current.highlightOutline
			|| previous.shadowOutline != current.shadowOutline || previous.solidOutline != current.solidOutline
			|| previous.spriteShadow != current.spriteShadow || previous.highlightInline != current.highlightInline
			|| previous.shadowInline != current.shadowInline || previous.solidInline != current.solidInline
			|| previous.outlineColor != current.outlineColor)
		{
			reasons.add(APPEARANCE_CHANGE);
		}
		if (!displayed.bounds.equals(imageBounds))
		{
			reasons.add(BOUNDS_CHANGE);
		}
		if (nowMillis - displayed.lastRedrawMillis() > BillboardConstants.CACHE_TTL_MILLIS)
		{
			reasons.add(CACHE_EXPIRY);
		}
		return reasons;
	}
}
