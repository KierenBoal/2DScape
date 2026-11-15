package com.kierenboal.npcsnap.occlusion;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BillboardOcclusionQualityTest
{
    @Test
    public void retiredSavedQualityValuesHaveExplicitReplacements()
    {
        assertEquals("MEDIUM", BillboardOcclusionQuality.replacementForSavedValue("BLOCKY"));
        assertEquals("HIGH", BillboardOcclusionQuality.replacementForSavedValue("ULTRA"));
        assertEquals("HIGH", BillboardOcclusionQuality.replacementForSavedValue("MAX"));
        assertNull(BillboardOcclusionQuality.replacementForSavedValue(null));
        for (BillboardOcclusionQuality quality : BillboardOcclusionQuality.values())
        {
            assertNull(BillboardOcclusionQuality.replacementForSavedValue(quality.name()));
        }
    }
}
