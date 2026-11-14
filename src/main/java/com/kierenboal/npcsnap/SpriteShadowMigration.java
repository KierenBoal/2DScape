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
		String old = read.apply("enableBillboardSpriteShadows");
		if (old == null) return;
		if (read.apply("shadowType") == null)
			write.accept("shadowType", "false".equalsIgnoreCase(old) ? "OFF" : "FLAT");
		remove.accept("enableBillboardSpriteShadows");
	}
}
