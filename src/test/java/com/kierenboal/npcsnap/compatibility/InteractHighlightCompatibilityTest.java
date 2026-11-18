package com.kierenboal.npcsnap.compatibility;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.events.ConfigChanged;
import org.junit.Test;

import static org.junit.Assert.*;

public class InteractHighlightCompatibilityTest
{
	private static final Object[] TARGETS = {
		com.kierenboal.npcsnap.TestProxies.proxy(net.runelite.api.NPC.class),
		com.kierenboal.npcsnap.TestProxies.proxy(net.runelite.api.Player.class),
		com.kierenboal.npcsnap.TestProxies.proxy(net.runelite.api.TileItem.class),
		com.kierenboal.npcsnap.TestProxies.proxy(net.runelite.api.TileObject.class)};
	private static final String[] TYPES = {"npc", "player", "item", "object"};

	@Test
	public void onlySelectedTargetTypesAreSuppressedAndOriginalChoicesAreRestored()
	{
		for (int selected = 0; selected < TYPES.length; selected++)
		{
			Memory compatibility = new Memory();
			for (String type : TYPES) compatibility.put(type + "ShowHover", "false");
			compatibility.synchronize(true, selected == 0, selected == 1, selected == 2, selected == 3);
			for (int index = 0; index < TYPES.length; index++)
			{
				assertEquals("false", compatibility.live(TYPES[index] + "ShowHover"));
				assertEquals(index == selected ? "false" : null, compatibility.live(TYPES[index] + "ShowInteract"));
			}
			assertNull(compatibility.color(TARGETS[selected], false, false, 10));
			compatibility.synchronize(true, false, false, false, false);
			for (String type : TYPES)
			{
				assertEquals("false", compatibility.live(type + "ShowHover"));
				assertNull(compatibility.live(type + "ShowInteract"));
			}
			assertEquals(4, compatibility.values.size());
		}
	}

	@Test
	public void pluginDisableAndShutdownRestoreTrueFalseAndUnsetValues()
	{
		for (boolean shutdown : new boolean[] {false, true})
		{
			Memory compatibility = new Memory();
			compatibility.put("itemShowHover", "false");
			compatibility.put("objectShowInteract", "true");
			Map<String, String> original = new HashMap<>(compatibility.values);
			compatibility.synchronize(true, true, true, true, true);
			if (shutdown) compatibility.restore();
			else
			{
				compatibility.active = false;
				compatibility.synchronize(true, true, true, true, true);
			}
			assertEquals(original, compatibility.values);
		}
	}

	@Test
	public void staleStashRestoresItemsAndObjectsAfterInterruptedSession()
	{
		Memory compatibility = new Memory();
		compatibility.put("objectShowHover", "true");
		Map<String, String> original = new HashMap<>(compatibility.values);
		compatibility.synchronize(true, false, false, true, true);
		Memory restarted = new Memory();
		restarted.values = compatibility.values;
		restarted.restoreStaleStash();
		assertEquals(original, restarted.values);
	}

	@Test
	public void profileSwitchNeverCopiesStashedValuesIntoAnotherProfile()
	{
		Memory compatibility = new Memory();
		compatibility.put("itemShowHover", "true");
		Map<String, String> firstProfile = compatibility.values;
		compatibility.synchronize(true, true, true, true, true);
		compatibility.values = new HashMap<>();
		compatibility.put("itemShowHover", "false");
		compatibility.onProfileChanged(true, false, false, true, false);
		assertNull(compatibility.color(TARGETS[2], false, false, 0));
		compatibility.restore();
		assertEquals("false", compatibility.live("itemShowHover"));
		assertEquals(1, compatibility.values.size());
		compatibility.values = firstProfile;
		compatibility.onProfileChanged(false, false, false, false, false);
		assertEquals("true", compatibility.live("itemShowHover"));
		assertEquals(1, compatibility.values.size());
	}

	@Test
	public void enablingAnySuppressedSettingOptsOutWithoutOverwritingTheUserChoice()
	{
		for (String type : TYPES)
		{
			Memory compatibility = new Memory();
			compatibility.put(type + "ShowHover", "false");
			compatibility.synchronize(true, true, true, true, true);
			ConfigChanged event = new ConfigChanged();
			event.setGroup("interacthighlight");
			event.setKey(type + "ShowHover");
			event.setNewValue("true");
			assertTrue(compatibility.isUserOverride(event));
			compatibility.put(event.getKey(), "true");
			compatibility.optOut(event.getKey());
			assertEquals("true", compatibility.live(event.getKey()));
			assertEquals(1, compatibility.values.size());
			assertFalse(compatibility.isUserOverride(event));
		}
	}

	@Test
	public void standaloneAndDualGeometryUseSavedColorsAndClickTiming()
	{
		Memory compatibility = new Memory();
		for (boolean active : new boolean[] {false, true})
		{
			compatibility.active = active;
			compatibility.synchronize(false, true, true, true, true);
			assertTrue(compatibility.values.isEmpty());
			var config = compatibility.settings();
			assertEquals(config.npcAttackHoverHighlightColor(), compatibility.color(TARGETS[0], true, true, 0));
			assertEquals(java.awt.Color.WHITE, compatibility.color(TARGETS[0], true, true, 5));
			assertEquals(config.npcAttackHighlightColor(), compatibility.color(TARGETS[0], true, true, 10));
			assertEquals(config.npcInteractHighlightColor(), compatibility.color(TARGETS[0], true, false, 15));
		}
	}

	@Test
	public void standaloneReflectsLiveVisibilityEditsWithoutTouchingNativeSettings()
	{
		Memory compatibility = new Memory();
		compatibility.active = false;
		compatibility.synchronize(true, true, true, true, true);
		assertTrue(compatibility.values.isEmpty());
		ConfigChanged event = new ConfigChanged();
		event.setGroup("interacthighlight");
		event.setKey("itemShowHover");
		compatibility.put(event.getKey(), "false");
		compatibility.settingsChanged(event);
		assertNull(compatibility.color(TARGETS[2], false, false, 0));
		assertNotNull(compatibility.color(TARGETS[2], true, false, 10));
		assertEquals(1, compatibility.values.size());
	}

	private static final class Memory extends InteractHighlightCompatibility
	{
		Map<String, String> values = new HashMap<>();
		boolean active = true;
		@Override net.runelite.client.plugins.interacthighlight.InteractHighlightConfig settings()
		{
			return new net.runelite.client.plugins.interacthighlight.InteractHighlightConfig() {};
		}
		void put(String key, String value) { setConfiguration("interacthighlight", key, value); }
		String live(String key) { return getConfiguration("interacthighlight", key); }
		@Override String getConfiguration(String group, String key) { return values.get(group + "." + key); }
		@Override void setConfiguration(String group, String key, String value) { values.put(group + "." + key, value); }
		@Override void unsetConfiguration(String group, String key) { values.remove(group + "." + key); }
		@Override boolean isInteractHighlightActive() { return active; }
		@Override void onClientThread(Runnable action) { action.run(); }
	}
}
