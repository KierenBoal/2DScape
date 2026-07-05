package com.kierenboal.npcsnap;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;

final class BillboardProjectileGeometry
{
	private BillboardProjectileGeometry()
	{
	}

	static LocalPoint localPoint(Projectile projectile)
	{
		return new LocalPoint((int) projectile.getX(), (int) projectile.getY());
	}

	static int verticalOffset(Client client, Projectile projectile)
	{
		LocalPoint localPoint = localPoint(projectile);
		int tileHeight = Perspective.getTileHeight(client, localPoint, projectile.getFloor());
		return tileHeight - (int) Math.round(projectile.getZ());
	}
}
