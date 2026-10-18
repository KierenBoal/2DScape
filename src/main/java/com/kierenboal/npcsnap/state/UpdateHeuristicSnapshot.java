package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardModelStateHash;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;

import com.kierenboal.npcsnap.rendering.BillboardModelBounds;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.api.Projectile;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Animation;
import net.runelite.api.Model;
import java.util.Arrays;
import net.runelite.api.coords.LocalPoint;

public final class UpdateHeuristicSnapshot
{
	public final double depth;
	public final long positionKey;
	public final int animationHash;
	public final int modelStateHash;
	public final int textureStateHash;
	public final int viewHash;
	public final boolean animated;
	public final int animationIdentityHash;
	public final int appearanceHash;
	public final BillboardModelBounds geometry;

	public UpdateHeuristicSnapshot(
		double depth,
		long positionKey,
		int animationHash,
		int modelStateHash,
		int textureStateHash,
		int viewHash,
		boolean animated)
	{
		this(depth, positionKey, animationHash, modelStateHash, textureStateHash, viewHash,
			animated, animationHash, modelStateHash, null);
	}

	public UpdateHeuristicSnapshot(double depth, long positionKey, int animationHash, int modelStateHash,
		int textureStateHash, int viewHash, boolean animated, int animationIdentityHash,
		int appearanceHash, BillboardModelBounds geometry)
	{
		this.depth = depth;
		this.positionKey = positionKey;
		this.animationHash = animationHash;
		this.modelStateHash = modelStateHash;
		this.textureStateHash = textureStateHash;
		this.viewHash = viewHash;
		this.animated = animated;
		this.animationIdentityHash = animationIdentityHash;
		this.appearanceHash = appearanceHash;
		this.geometry = geometry;
	}

	public static UpdateHeuristicSnapshot empty()
	{
		return new UpdateHeuristicSnapshot(
			Double.POSITIVE_INFINITY,
			Long.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			false);
	}

	public static UpdateHeuristicSnapshot forDepth(double depth)
	{
		return new UpdateHeuristicSnapshot(
			depth,
			Long.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			false);
	}

	public static UpdateHeuristicSnapshot fromRequest(BillboardRenderRequest request, double depth, int animatedTextureOffsetStateHash)
	{
		long positionKey = positionKey(request.localPoint, request.plane);
		int animationHash = 1;
		int modelStateHash = BillboardModelStateHash.hash(request.model);
		int textureStateHash = 1;
		int viewHash = 1;
		viewHash = (31 * viewHash) + request.relativeYaw;
		viewHash = (31 * viewHash) + request.relativePitch;
		animationHash = (31 * animationHash) + request.animationId;
		animationHash = (31 * animationHash) + request.animationFrame;
		animationHash = (31 * animationHash) + request.poseAnimationId;
		animationHash = (31 * animationHash) + request.poseAnimationFrame;
		textureStateHash = (31 * textureStateHash) + request.animatedTextureId;
		textureStateHash = (31 * textureStateHash) + animatedTextureOffsetStateHash;
		boolean animated = request.animationId >= 0
			|| request.poseAnimationId >= 0
			|| request.animationFrame >= 0
			|| request.poseAnimationFrame >= 0
			|| request.animatedTextureId >= 0;
		return new UpdateHeuristicSnapshot(
			depth,
			positionKey,
			animationHash,
			modelStateHash,
			textureStateHash,
			viewHash,
			animated);
	}

	/** Stable identity is deliberately separate from frames, deformed vertices and texture offsets. */
	public static UpdateHeuristicSnapshot forCapture(BillboardRenderRequest request, double depth,
		int textureHash, int viewHash, boolean cadenceControlled, boolean measureGeometry)
	{
		UpdateHeuristicSnapshot live = fromRequest(request, depth, textureHash);
		int identity = 31 * request.animationId + request.poseAnimationId;
		Animation effectAnimation = request.renderable instanceof Projectile ? ((Projectile) request.renderable).getAnimation()
			: request.renderable instanceof GraphicsObject ? ((GraphicsObject) request.renderable).getAnimation() : null;
		if (effectAnimation != null) identity = 31 * identity + effectAnimation.getId();
		int appearance = appearanceHash(request.model);
		if (request.renderable instanceof TileItem)
		{
			TileItem item = (TileItem) request.renderable;
			identity = -1;
			appearance = 31 * appearance + item.getId();
			appearance = 31 * appearance + item.getQuantity();
		}
		else if (request.renderable instanceof NPC)
		{
			appearance = 31 * appearance + ((NPC) request.renderable).getId();
		}
		else if (request.renderable instanceof Player)
		{
			Player player = (Player) request.renderable;
			if (player.getPlayerComposition() != null)
			{
				appearance = 31 * appearance + Arrays.hashCode(player.getPlayerComposition().getEquipmentIds());
				appearance = 31 * appearance + Arrays.hashCode(player.getPlayerComposition().getColors());
			}
		}
		return new UpdateHeuristicSnapshot(depth, live.positionKey, live.animationHash,
			live.modelStateHash, live.textureStateHash, viewHash, cadenceControlled,
			identity, appearance, measureGeometry ? BillboardModelBounds.sample(request.model, request.renderable instanceof Projectile) : null);
	}

	private static int appearanceHash(Model model)
	{
		if (model == null) return 0;
		int hash = 31 * model.getVerticesCount() + model.getFaceCount();
		hash = 31 * hash + model.getOverrideAmount();
		hash = 31 * hash + model.getOverrideHue();
		hash = 31 * hash + model.getOverrideSaturation();
		hash = 31 * hash + model.getOverrideLuminance();
		hash = sample(hash, model.getFaceColors1());
		hash = sample(hash, model.getFaceColors2());
		hash = sample(hash, model.getFaceColors3());
		short[] textures = model.getFaceTextures();
		if (textures != null && textures.length > 0)
		{
			hash = 31 * hash + textures.length;
			hash = 31 * hash + textures[0];
			hash = 31 * hash + textures[textures.length / 2];
			hash = 31 * hash + textures[textures.length - 1];
		}
		return hash;
	}

	private static int sample(int hash, int[] values)
	{
		if (values == null || values.length == 0) return 31 * hash;
		hash = 31 * hash + values.length;
		hash = 31 * hash + values[0];
		hash = 31 * hash + values[values.length / 2];
		return 31 * hash + values[values.length - 1];
	}

	public boolean hasPosition()
	{
		return positionKey != Long.MIN_VALUE;
	}

	public static long positionKey(LocalPoint localPoint, int plane)
	{
		if (localPoint == null)
		{
			return Long.MIN_VALUE;
		}

		long x = localPoint.getX() & 0x1FFFFL;
		long y = localPoint.getY() & 0x1FFFFL;
		long z = plane & 0x3L;
		return (z << 34) | (x << 17) | y;
	}
}

