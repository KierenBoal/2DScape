package com.kierenboal.npcsnap.rendering;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;

public final class BillboardProjectileGeometry
{
	private BillboardProjectileGeometry()
	{
	}

	public static LocalPoint localPoint(Projectile projectile)
	{
		return new LocalPoint((int) projectile.getX(), (int) projectile.getY());
	}

	public static int verticalOffset(Client client, Projectile projectile)
	{
		LocalPoint localPoint = localPoint(projectile);
		int tileHeight = Perspective.getTileHeight(client, localPoint, projectile.getFloor());
		return tileHeight - (int) Math.round(projectile.getZ());
	}
}
