package com.kierenboal.npcsnap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpriteShadowMigrationTest
{
	private void migrate(Map<String,String> profile)
	{
		SpriteShadowMigration.migrate(profile::get,profile::put,profile::remove);
	}
	@Test public void migratesOnceAndAllowsReset()
	{
		for (boolean enabled : new boolean[]{false,true})
		{
			Map<String,String> profile = new HashMap<>();
			profile.put("enableBillboardSpriteShadows",String.valueOf(enabled));
			migrate(profile);
			assertEquals(enabled ? "FLAT" : "OFF",profile.get("shadowType"));
			assertFalse(profile.containsKey("enableBillboardSpriteShadows"));
			migrate(profile);
			profile.remove("shadowType"); migrate(profile);
			assertNull(profile.get("shadowType"));
		}
	}
	@Test public void preservesChoicesAndKeepsProfilesIndependent()
	{
		for (SpriteShadowType type : SpriteShadowType.values())
		{
			Map<String,String> profile = new HashMap<>();
			profile.put("shadowType",type.name());profile.put("enableBillboardSpriteShadows","false");
			migrate(profile); assertEquals(type.name(),profile.get("shadowType"));
		}
		Map<String,String> fresh = new HashMap<>();migrate(fresh);assertTrue(fresh.isEmpty());
		assertEquals(SpriteShadowType.FLAT,new NpcSnapConfig(){}.shadowType());
	}
}
