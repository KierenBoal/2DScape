package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardModelStateHash;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;

public final class BillboardCachePreviewKey
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
	public final int animatedTextureOffsetStateHash;

	public static BillboardCachePreviewKey create(
		BillboardRenderRequest request,
		NpcSnapConfig config,
		int outlinePadding,
		int renderQuality,
		int animatedTextureOffsetStateHash)
	{
		return new BillboardCachePreviewKey(
			request.animationId,
			request.animationFrame,
			request.poseAnimationId,
			request.poseAnimationFrame,
			request.relativeYaw,
			request.relativePitch,
			config.billboardColorBands(),
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
			animatedTextureOffsetStateHash);
	}

	public BillboardCachePreviewKey(
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

