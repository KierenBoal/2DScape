package com.kierenboal.npcsnap.export;

import java.util.List;

public final class BillboardExportBatch
{
	public final String name;
	public final List<BillboardExportFrame> frames;

	public BillboardExportBatch(String name, List<BillboardExportFrame> frames)
	{
		this.name = name;
		this.frames = frames;
	}
}
