package com.kierenboal.npcsnap.occlusion;

import net.runelite.api.Model;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class SceneryFaceColorsTest
{
	@Test
	public void rendererBinCentresAndBrightnessMatchReferencePurple()
	{
		SceneryFaceColors colors = new SceneryFaceColors();
		// HSL (48, 6, 32): the renderer's linear RGB is
		// (0.26904296875, 0.046875, 0.453125), before applying gamma.
		colors.brightness(1d);
		assertEquals(0x450C74, colors.rgb(0xC320));
		colors.brightness(0.8d);
		assertEquals(0x591687, colors.rgb(0xC320));
		colors.brightness(0.6d);
		assertEquals(0x74299F, colors.rgb(0xC320));
		colors.brightness(0.8d);
		assertEquals(0x591687, colors.rgb(0xC320));
		colors.brightness(Double.NaN);
		assertEquals(0x591687, colors.rgb(0xC320));
		assertEquals(0, colors.rgb(0));
		assertEquals(0, colors.rgb(0)); // Black is cached too.
	}

	@Test
	public void litFaceColourWinsOverDarkerOrMoreSaturatedUnlitColour()
	{
		SceneryFaceColors colors = new SceneryFaceColors();
		for (short unlit : new short[] {0, (short) 0xC708})
		{
			assertEquals(0x591687, colors.faceTintRgb(0,
				new int[] {0xC320}, new int[] {0}, new int[] {-1}, new short[] {unlit}, -1, -1, -1, 0));
		}
	}

	@Test
	public void smoothFacesAverageLitRgbAfterGammaAndMissingLitDataFallsBack()
	{
		SceneryFaceColors colors = new SceneryFaceColors();
		assertEquals(0x3B0E5A, colors.faceTintRgb(0,
			new int[] {0xC320}, new int[] {0}, new int[] {0xC320}, null, -1, -1, -1, 0));
		assertEquals(0x591687, colors.faceTintRgb(0, null, null, null, new short[] {(short) 0xC320}, -1, -1, -1, 0));
		assertEquals(0xFFFFFF, colors.faceTintRgb(0, null, null, null, null, -1, -1, -1, 0));
	}

	@Test
	public void capturedOverridesAndColoursSurviveSharedModelReuse()
	{
		int[] lit = {0xC310};
		byte[] amount = {64};
		Model model = proxy(Model.class, method("getFaceCount", 1), method("getFaceColors1", lit),
			method("getVerticesCount", 3), method("getVerticesX", new float[] {0, 1, 0}),
			method("getVerticesY", new float[] {0, 0, 1}), method("getVerticesZ", new float[3]),
			method("getFaceIndices1", new int[] {0}), method("getFaceIndices2", new int[] {1}),
			method("getFaceIndices3", new int[] {2}),
			method("getFaceColors3", new int[] {-1}), methodSupplier("getOverrideAmount", () -> amount[0]),
			method("getOverrideHue", (byte) -1), method("getOverrideSaturation", (byte) -1),
			method("getOverrideLuminance", (byte) 48));
		SceneryModelSnapshot captured = SceneryModelSnapshot.capture(model);
		lit[0] = 0;
		amount[0] = 0;
		assertEquals(0x591687, new SceneryFaceColors().faceTintRgb(0,
			captured.colors1, captured.colors2, captured.colors3, captured.unlitColors,
			captured.overrideHue, captured.overrideSaturation, captured.overrideLuminance, captured.overrideAmount));
	}
}
