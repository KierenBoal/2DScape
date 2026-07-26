package com.kierenboal.npcsnap.export;

import com.kierenboal.npcsnap.rendering.BillboardAngleUtils;
import java.util.ArrayList;
import java.util.List;

public final class BillboardExportAngles
{
	private BillboardExportAngles()
	{
	}

	public static List<Integer> yaws(int count)
	{
		int angleCount = Math.max(1, count);
		List<Integer> angles = new ArrayList<>(angleCount);
		for (int i = 0; i < angleCount; i++)
		{
			angles.add((int) (((long) i * BillboardAngleUtils.BILLBOARD_FULL_CIRCLE) / angleCount));
		}
		return angles;
	}

	public static List<Integer> pitches(int count, boolean groundItem)
	{
		int angleCount = Math.max(1, count);
		int minimum = groundItem ? BillboardAngleUtils.GROUND_ITEM_MIN_PITCH : 0;
		List<Integer> angles = new ArrayList<>(angleCount);
		if (angleCount == 1)
		{
			angles.add(-minimum);
			return angles;
		}
		for (int i = 0; i < angleCount; i++)
		{
			angles.add(-(minimum + (int) Math.round(
				(i * (double) (BillboardAngleUtils.MAX_PITCH - minimum)) / (angleCount - 1))));
		}
		return angles;
	}
}
