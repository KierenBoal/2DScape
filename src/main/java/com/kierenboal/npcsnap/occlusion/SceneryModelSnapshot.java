package com.kierenboal.npcsnap.occlusion;

import java.util.Arrays;
import net.runelite.api.Model;

/** Owns one animated object's data before RuneLite reuses its shared model buffers. */
final class SceneryModelSnapshot
{
	// Identity only: consumers read captured data, never the live model's arrays.
	final Model model;
	final BillboardWorldOcclusionCollector.ModelGeometry geometry;
	final int[] colors1, colors2, colors3;
	final short[] unlitColors;
	final byte[] transparencies;
	final byte transparency;

	private SceneryModelSnapshot(Model model)
	{
		this.model = model;
		geometry = BillboardWorldOcclusionCollector.ModelGeometry.copyOf(model);
		int faces = geometry.faceCount;
		colors1 = copy(model.getFaceColors1(), faces);
		colors2 = copy(model.getFaceColors2(), faces);
		colors3 = copy(model.getFaceColors3(), faces);
		short[] unlit = model.getUnlitFaceColors();
		unlitColors = unlit == null ? null : Arrays.copyOf(unlit, Math.min(unlit.length, faces));
		byte[] alpha = model.getFaceTransparencies();
		transparencies = alpha == null ? null : Arrays.copyOf(alpha, Math.min(alpha.length, faces));
		transparency = model.getTransparency();
	}

	static SceneryModelSnapshot capture(Model model)
	{
		return new SceneryModelSnapshot(model);
	}

	private static int[] copy(int[] values, int count)
	{
		return values == null ? null : Arrays.copyOf(values, Math.min(values.length, count));
	}
}
