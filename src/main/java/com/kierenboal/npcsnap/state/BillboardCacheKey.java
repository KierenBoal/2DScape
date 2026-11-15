package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.SpriteShadowType;
import com.kierenboal.npcsnap.rendering.BillboardBrightness;
import com.kierenboal.npcsnap.rendering.BillboardModelStateHash;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
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
	public final SpriteShadowType shadowType;
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
		return create(request, config, outlinePadding, colorBands, renderQuality,
			textureStateHash, animatedTextureOffsetStateHash,
			BillboardBrightness.configuredLightBoostPercent(config, request.model));
	}

	public static BillboardCacheKey create(
		BillboardRenderRequest request,
		NpcSnapConfig config,
		int outlinePadding,
		int colorBands,
		int renderQuality,
		int textureStateHash,
		int animatedTextureOffsetStateHash,
		int effectiveLightBoost)
	{
		return new BillboardCacheKey(
			request.animationId,
			request.animationFrame,
			request.poseAnimationId,
			request.poseAnimationFrame,
			request.relativeYaw,
			request.relativePitch,
			colorBands,
			effectiveLightBoost,
			outlinePadding,
			config.enableBillboardHighlightOutline(),
			config.enableBillboardShadowOutline(),
			config.enableBillboardSpriteOutline(),
			config.shadowType(),
			config.enableBillboardHighlightInline(),
			config.enableBillboardShadowInline(),
			config.enableBillboardSpriteInline(),
			config.billboardSpriteOutlineColor().getRGB(),
			request.shouldHoverOutline,
			request.shouldInteractOutline,
			request.hoverOutlineColor != null ? request.hoverOutlineColor.getRGB() : 0,
			request.interactionOutlineColor != null ? request.interactionOutlineColor.getRGB() : 0,
			renderQuality,
			BillboardModelStateHash.hash(request.model),
			textureStateHash,
			animatedTextureOffsetStateHash);
	}

	public static BillboardCacheKey preview(
		BillboardRenderRequest request, NpcSnapConfig config, int outlinePadding,
		int renderQuality, int animatedTextureOffsetStateHash, int effectiveLightBoost)
	{
		return create(request, config, outlinePadding, config.billboardColorBands(),
			renderQuality, 0, animatedTextureOffsetStateHash, effectiveLightBoost);
	}

	public static BillboardCacheKey preview(
		BillboardRenderRequest request, NpcSnapConfig config, int outlinePadding,
		int renderQuality, int animatedTextureOffsetStateHash)
	{
		return preview(request, config, outlinePadding, renderQuality,
			animatedTextureOffsetStateHash,
			BillboardBrightness.configuredLightBoostPercent(config, request.model));
	}

	public boolean matchesPreview(BillboardCacheKey preview)
	{
		return preview != null
			&& animationId == preview.animationId
			&& animationFrame == preview.animationFrame
			&& poseAnimationId == preview.poseAnimationId
			&& poseAnimationFrame == preview.poseAnimationFrame
			&& relativeYaw == preview.relativeYaw
			&& relativePitch == preview.relativePitch
			&& colorBands == preview.colorBands
			&& lightBoost == preview.lightBoost
			&& outlinePadding == preview.outlinePadding
			&& highlightOutline == preview.highlightOutline
			&& shadowOutline == preview.shadowOutline
			&& solidOutline == preview.solidOutline
			&& spriteShadow == preview.spriteShadow
			&& shadowType == preview.shadowType
			&& highlightInline == preview.highlightInline
			&& shadowInline == preview.shadowInline
			&& solidInline == preview.solidInline
			&& outlineColor == preview.outlineColor
			&& hoverOutline == preview.hoverOutline
			&& interactOutline == preview.interactOutline
			&& hoverOutlineColor == preview.hoverOutlineColor
			&& interactOutlineColor == preview.interactOutlineColor
			&& renderQuality == preview.renderQuality
			&& modelStateHash == preview.modelStateHash
			&& animatedTextureOffsetStateHash == preview.animatedTextureOffsetStateHash;
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
		SpriteShadowType shadowType,
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
		this.spriteShadow = shadowType != SpriteShadowType.OFF;
		this.shadowType = shadowType;
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




}

