package com.kierenboal.npcsnap.export;

import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.WorldView;

/** Immutable object menu context: RuneLite can reuse the original menu entry. */
public final class BillboardObjectExportTarget
{
	public final int identifier;
	public final int sceneX;
	public final int sceneY;
	public final int worldViewId;
	public final int plane;

	public BillboardObjectExportTarget(int identifier, int sceneX, int sceneY, int worldViewId, int plane)
	{
		this.identifier = identifier;
		this.sceneX = sceneX;
		this.sceneY = sceneY;
		this.worldViewId = worldViewId;
		this.plane = plane;
	}

	public static BillboardObjectExportTarget snapshot(Client client, MenuEntry entry)
	{
		WorldView view = client.getWorldView(entry.getWorldViewId());
		return new BillboardObjectExportTarget(entry.getIdentifier(), entry.getParam0(), entry.getParam1(),
			entry.getWorldViewId(), view == null ? -1 : view.getPlane());
	}
}
