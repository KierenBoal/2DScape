package com.kierenboal.npcsnap.targeting;

import lombok.RequiredArgsConstructor;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

@RequiredArgsConstructor
public final class ObjectRenderablePart
{
	public final Renderable renderable;
	public final LocalPoint localPoint;
	public final int plane;
	public final int worldHeight;
	public final int modelOrientation;

	public ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane)
	{
		this(renderable, localPoint, plane, Integer.MIN_VALUE);
	}

	public ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane, int worldHeight)
	{
		this(renderable, localPoint, plane, worldHeight, 0);
	}

}

