package com.kierenboal.npcsnap;

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

	static BillboardCachePreviewKey create(
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

