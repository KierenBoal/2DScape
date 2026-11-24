package com.kierenboal.npcsnap;

import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.runelite.client.config.ConfigManager;

final class SpriteShadowMigration
{
	static void migrate(ConfigManager manager)
	{
		migrate(key -> manager.getConfiguration("npc-snap", key),
			(key, value) -> manager.setConfiguration("npc-snap", key, value),
			key -> manager.unsetConfiguration("npc-snap", key));
	}

	static void migrate(Function<String, String> read, BiConsumer<String, String> write, Consumer<String> remove)
	{
		if ("GLOBAL_POINT".equals(read.apply("shadowLighting")))
		{
			write.accept("shadowLighting", ShadowLighting.WORLD_DIRECTIONAL.name());
		}

		String shadowType = read.apply("shadowType");
		if ("LEGACY".equals(shadowType))
		{
			write.accept("shadowType", SpriteShadowType.FLAT.name());
		}

		String old = read.apply("enableBillboardSpriteShadows");
		if (old == null) return;
		if (shadowType == null)
			write.accept("shadowType", "false".equalsIgnoreCase(old) ? "OFF" : "FLAT");
		remove.accept("enableBillboardSpriteShadows");
	}
}
