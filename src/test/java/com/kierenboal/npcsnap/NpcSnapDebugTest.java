package com.kierenboal.npcsnap;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import org.junit.Test;

public class NpcSnapDebugTest
{
	@Test
	public void readyToRedrawColorScalesPriorityRange()
	{
		assertEquals(new Color(128, 0, 128, 255), NpcSnapDebug.readyToRedrawColor(0.0d, 0.0d, 100.0d));
		assertEquals(new Color(255, 0, 255, 255), NpcSnapDebug.readyToRedrawColor(100.0d, 0.0d, 100.0d));
		assertEquals(new Color(192, 0, 192, 255), NpcSnapDebug.readyToRedrawColor(50.0d, 0.0d, 100.0d));
	}

	@Test
	public void readyToRedrawColorUsesHighestPriorityWhenRangeCollapses()
	{
		assertEquals(new Color(255, 0, 255, 255), NpcSnapDebug.readyToRedrawColor(10.0d, 10.0d, 10.0d));
	}
}
