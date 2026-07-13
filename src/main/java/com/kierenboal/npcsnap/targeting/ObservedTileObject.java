package com.kierenboal.npcsnap.targeting;

import java.util.List;
import net.runelite.api.TileObject;

public final class ObservedTileObject
{
	public final TileObject tileObject;
	public final List<ObjectRenderablePart> parts;
	public ClassifiedObjectType classifiedType;
	public ClassifiedObjectType lastLoggedClassification;
	public String lastLoggedClassificationReason;

	public ObservedTileObject(TileObject tileObject, List<ObjectRenderablePart> parts, ClassifiedObjectType classifiedType)
	{
		this.tileObject = tileObject;
		this.parts = parts;
		this.classifiedType = classifiedType;
		this.lastLoggedClassification = null;
		this.lastLoggedClassificationReason = null;
	}
}

