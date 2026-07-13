package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardModelStateHash;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;

public final class BillboardCacheKey
{
	public final int animationId;
	public final int animationFrame;
	public final int poseAnimationId;
	public final int poseAnimationFrame;
	public final int relativeYaw;
	public final int relativePitch;
	public final int colorBands;
	public final int lightBoost;
	public final int outlinePadding;
	public final boolean highlightOutline;
	public final boolean shadowOutline;
	public final boolean solidOutline;
	public final boolean spriteShadow;
	public final boolean highlightInline;
	public final boolean shadowInline;
	public final boolean solidInline;
	public final int outlineColor;
	public final boolean hoverOutline;
	public final boolean interactOutline;
	public final int hoverOutlineColor;
	public final int interactOutlineColor;
	public final int renderQuality;
	public final int modelStateHash;
	public final int textureStateHash;
	public final int animatedTextureOffsetStateHash;

	public static BillboardCacheKey create(
		BillboardRenderRequest request,
		NpcSnapConfig config,
		int outlinePadding,
		int colorBands,
		int renderQuality,
		int textureStateHash,
		int animatedTextureOffsetStateHash)
	{
		return new BillboardCacheKey(
			request.animationId,
			request.animationFrame,
			request.poseAnimationId,
			request.poseAnimationFrame,
			request.relativeYaw,
			request.relativePitch,
			colorBands,
			config.billboardLightBoostPercent(),
			outlinePadding,
			config.enableBillboardHighlightOutline(),
			config.enableBillboardShadowOutline(),
			config.enableBillboardSpriteOutline(),
			config.enableBillboardSpriteShadows(),
			config.enableBillboardHighlightInline(),
			config.enableBillboardShadowInline(),
			config.enableBillboardSpriteInline(),
			config.billboardSpriteOutlineColor().getRGB(),
			request.shouldHoverOutline,
			request.shouldInteractOutline,
			config.billboardHoverOutlineColor().getRGB(),
			config.billboardInteractionOutlineColor().getRGB(),
			renderQuality,
			BillboardModelStateHash.hash(request.model),
			textureStateHash,
			animatedTextureOffsetStateHash);
	}

	public BillboardCacheKey(
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

