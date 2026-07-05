package com.kierenboal.npcsnap;

import net.runelite.api.Model;

final class BillboardModelStateHash
{
	private BillboardModelStateHash()
	{
	}

	static int hash(Model model)
	{
		if (model == null)
		{
			return 0;
		}

		int hash = 1;
		hash = (31 * hash) + model.getVerticesCount();
		hash = (31 * hash) + model.getModelHeight();
		hash = sampleFloatArrayHash(hash, model.getVerticesX());
		hash = sampleFloatArrayHash(hash, model.getVerticesY());
		hash = sampleFloatArrayHash(hash, model.getVerticesZ());
		hash = sampleByteArrayHash(hash, model.getFaceTransparencies());
		return hash;
	}

	private static int sampleFloatArrayHash(int seed, float[] values)
	{
		if (values == null)
		{
			return (31 * seed) - 1;
		}

		int hash = (31 * seed) + values.length;
		if (values.length > 0)
		{
			hash = (31 * hash) + Float.floatToIntBits(values[0]);
			hash = (31 * hash) + Float.floatToIntBits(values[values.length / 2]);
			hash = (31 * hash) + Float.floatToIntBits(values[values.length - 1]);
		}
		return hash;
	}

	private static int sampleByteArrayHash(int seed, byte[] values)
	{
		if (values == null)
		{
			return (31 * seed) - 1;
		}

		int hash = (31 * seed) + values.length;
		if (values.length > 0)
		{
			hash = (31 * hash) + values[0];
			hash = (31 * hash) + values[values.length / 2];
			hash = (31 * hash) + values[values.length - 1];
		}
		return hash;
	}
}
