package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

final class FaceDraw
{
	final int x0;
	final int y0;
	final int x1;
	final int y1;
	final int x2;
	final int y2;
	private final Color color;
	private final double depth;
	private final TextureSample textureSample;
	private final TextureUvs textureUvs;

	FaceDraw(
		int x0,
		int y0,
		int x1,
		int y1,
		int x2,
		int y2,
		Color color,
		double depth,
		TextureSample textureSample,
		TextureUvs textureUvs)
	{
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.color = color;
		this.depth = depth;
		this.textureSample = textureSample;
		this.textureUvs = textureUvs;
	}

	Color getColor()
	{
		return color;
	}

	double getDepth()
	{
		return depth;
	}

	boolean isTextured()
	{
		return textureSample != null && textureUvs != null;
	}

	TextureSample getTextureSample()
	{
		return textureSample;
	}

	TextureUvs getTextureUvs()
	{
		return textureUvs;
	}
}

final class BuiltFaces
{
	final List<FaceDraw> faces;
	final int textureStateHash;

	BuiltFaces(List<FaceDraw> faces, int textureStateHash)
	{
		this.faces = faces;
		this.textureStateHash = textureStateHash;
	}
}

final class TextureSample
{
	final TextureCacheEntry entry;
	final float uOffset;
	final float vOffset;
	final int stateHash;

	TextureSample(TextureCacheEntry entry, float uOffset, float vOffset, int stateHash)
	{
		this.entry = entry;
		this.uOffset = uOffset;
		this.vOffset = vOffset;
		this.stateHash = stateHash;
	}
}

final class TextureUvs
{
	final float u0;
	final float v0;
	final float u1;
	final float v1;
	final float u2;
	final float v2;

	TextureUvs(float u0, float v0, float u1, float v1, float u2, float v2)
	{
		this.u0 = u0;
		this.v0 = v0;
		this.u1 = u1;
		this.v1 = v1;
		this.u2 = u2;
		this.v2 = v2;
	}
}

final class RenderedBillboardImage
{
	final BufferedImage image;

	RenderedBillboardImage(BufferedImage image)
	{
		this.image = image;
	}
}

final class BillboardRenderRequest
{
	final Renderable renderable;
	final Model model;
	final LocalPoint localPoint;
	final int plane;
	final int verticalOffset;
	final int relativeYaw;
	final int relativePitch;
	final int animationId;
	final int animationFrame;
	final int poseAnimationId;
	final int poseAnimationFrame;
	final int animatedTextureId;
	final boolean shouldHoverOutline;
	final boolean shouldInteractOutline;
	final VerticalAnchor verticalAnchor;
	final NpcSnapDebug.FrameDebugInfo frameDebugInfo;

	BillboardRenderRequest(
		Renderable renderable,
		Model model,
		LocalPoint localPoint,
		int plane,
		int verticalOffset,
		int relativeYaw,
		int relativePitch,
		int animationId,
		int animationFrame,
		int poseAnimationId,
		int poseAnimationFrame,
		int animatedTextureId,
		boolean shouldHoverOutline,
		boolean shouldInteractOutline,
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo
	)
	{
		this.renderable = renderable;
		this.model = model;
		this.localPoint = localPoint;
		this.plane = plane;
		this.verticalOffset = verticalOffset;
		this.relativeYaw = relativeYaw;
		this.relativePitch = relativePitch;
		this.animationId = animationId;
		this.animationFrame = animationFrame;
		this.poseAnimationId = poseAnimationId;
		this.poseAnimationFrame = poseAnimationFrame;
		this.animatedTextureId = animatedTextureId;
		this.shouldHoverOutline = shouldHoverOutline;
		this.shouldInteractOutline = shouldInteractOutline;
		this.verticalAnchor = verticalAnchor;
		this.frameDebugInfo = frameDebugInfo;
	}
}

final class BillboardRenderResult
{
	final Rectangle bounds;
	final BufferedImage image;
	final Rectangle sourceBounds;

	BillboardRenderResult(Rectangle bounds, BufferedImage image, Rectangle sourceBounds)
	{
		this.bounds = bounds;
		this.image = image;
		this.sourceBounds = sourceBounds;
	}
}

final class PreparedBillboardDraw
{
	final BillboardRenderRequest request;
	final BillboardRenderResult result;
	final int paintOrder;
	final BufferedImage image;
	final Rectangle bounds;
	final Rectangle sourceBounds;

	PreparedBillboardDraw(BillboardRenderRequest request, BillboardRenderResult result, int paintOrder)
	{
		this.request = request;
		this.result = result;
		this.paintOrder = paintOrder;
		this.image = result != null ? result.image : null;
		this.bounds = result != null ? result.bounds : null;
		this.sourceBounds = result != null ? result.sourceBounds : null;
	}
}
