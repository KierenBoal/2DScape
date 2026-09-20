package com.kierenboal.npcsnap;

final class RendererAvailabilityWarning
{
	// Give a GPU renderer time to register during login or plugin startup.
	private static final int MISSING_TICKS_BEFORE_WARNING = 2;
	private int missingTicks;
	private boolean warned;

	boolean shouldWarn(boolean loggedIn, boolean rendererAvailable)
	{
		if (!loggedIn)
		{
			missingTicks = 0;
			return false;
		}

		if (rendererAvailable)
		{
			reset();
			return false;
		}

		if (warned || ++missingTicks < MISSING_TICKS_BEFORE_WARNING)
		{
			return false;
		}

		warned = true;
		return true;
	}

	void reset()
	{
		missingTicks = 0;
		warned = false;
	}
}
