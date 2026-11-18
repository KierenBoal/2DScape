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
	final short[] textures;
	final byte[] textureFaces;
	final int[] texIndices1, texIndices2, texIndices3;
	final byte[] transparencies;
	final byte transparency;
	final int overrideHue, overrideSaturation, overrideLuminance, overrideAmount;

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
		short[] faceTextures = model.getFaceTextures();
		textures = faceTextures == null ? null : Arrays.copyOf(faceTextures, Math.min(faceTextures.length, faces));
		byte[] mappings = textures == null ? null : model.getTextureFaces();
		textureFaces = mappings == null ? null : Arrays.copyOf(mappings, Math.min(mappings.length, faces));
		texIndices1 = mappings == null ? null : copy(model.getTexIndices1(), Integer.MAX_VALUE);
		texIndices2 = mappings == null ? null : copy(model.getTexIndices2(), Integer.MAX_VALUE);
		texIndices3 = mappings == null ? null : copy(model.getTexIndices3(), Integer.MAX_VALUE);
		byte[] alpha = model.getFaceTransparencies();
		transparencies = alpha == null ? null : Arrays.copyOf(alpha, Math.min(alpha.length, faces));
		transparency = model.getTransparency();
		overrideHue = model.getOverrideHue();
		overrideSaturation = model.getOverrideSaturation();
		overrideLuminance = model.getOverrideLuminance();
		overrideAmount = model.getOverrideAmount();
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
