package com.kierenboal.npcsnap;

import java.util.List;
import net.runelite.api.TileObject;

final class ObservedTileObject
{
	final TileObject tileObject;
	final List<ObjectRenderablePart> parts;
	ClassifiedObjectType classifiedType;
	ClassifiedObjectType lastLoggedClassification;
	String lastLoggedClassificationReason;

	ObservedTileObject(TileObject tileObject, List<ObjectRenderablePart> parts, ClassifiedObjectType classifiedType)
	{
		this.tileObject = tileObject;
		this.parts = parts;
		this.classifiedType = classifiedType;
		this.lastLoggedClassification = null;
		this.lastLoggedClassificationReason = null;
	}
}

