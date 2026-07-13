package com.kierenboal.npcsnap.targeting;

import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

public final class ObjectRenderablePart
{
	public final Renderable renderable;
	public final LocalPoint localPoint;
	public final int plane;

	public ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane)
	{
		this.renderable = renderable;
		this.localPoint = localPoint;
		this.plane = plane;
	}
}

