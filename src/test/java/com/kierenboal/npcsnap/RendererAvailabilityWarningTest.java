package com.kierenboal.npcsnap;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RendererAvailabilityWarningTest
{
	@Test
	public void warnsOnceAfterRendererHasBeenMissingForTwoGameTicks()
	{
		RendererAvailabilityWarning warning = new RendererAvailabilityWarning();

		assertFalse(warning.shouldWarn(true, false));
		assertTrue(warning.shouldWarn(true, false));
		assertFalse(warning.shouldWarn(true, false));
	}

	@Test
	public void rendererRecoveryAllowsAnotherWarningAfterItDisappears()
	{
		RendererAvailabilityWarning warning = new RendererAvailabilityWarning();

		assertFalse(warning.shouldWarn(true, true));
		assertFalse(warning.shouldWarn(true, false));
		assertTrue(warning.shouldWarn(true, false));
		assertFalse(warning.shouldWarn(true, true));
		assertFalse(warning.shouldWarn(true, false));
		assertTrue(warning.shouldWarn(true, false));
	}

	@Test
	public void loadingPausesTheCheckWithoutRepeatingAnExistingWarning()
	{
		RendererAvailabilityWarning warning = new RendererAvailabilityWarning();

		assertFalse(warning.shouldWarn(true, false));
		assertFalse(warning.shouldWarn(false, false));
		assertFalse(warning.shouldWarn(true, false));
		assertTrue(warning.shouldWarn(true, false));
		assertFalse(warning.shouldWarn(false, false));
		assertFalse(warning.shouldWarn(true, false));
	}
}
