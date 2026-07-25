package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.TestProxies;
import com.kierenboal.npcsnap.features.GroundItemBillboard;

import java.util.Collections;
import java.util.AbstractMap;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class BillboardHoverInteractionResolverTest
{
	@Test
	public void recognizesActorHoverActions()
	{
		Assert.assertTrue(BillboardHoverInteractionResolver.isActorHoverAction(MenuAction.NPC_FIRST_OPTION));
		Assert.assertTrue(BillboardHoverInteractionResolver.isActorHoverAction(MenuAction.PLAYER_FIRST_OPTION));
		Assert.assertTrue(BillboardHoverInteractionResolver.isActorHoverAction(MenuAction.WIDGET_TARGET_ON_PLAYER));
	}

	@Test
	public void rejectsNonActorHoverActions()
	{
		Assert.assertFalse(BillboardHoverInteractionResolver.isActorHoverAction(null));
		Assert.assertFalse(BillboardHoverInteractionResolver.isActorHoverAction(MenuAction.WALK));
		Assert.assertFalse(BillboardHoverInteractionResolver.isActorHoverAction(MenuAction.CC_OP));
		Assert.assertFalse(BillboardHoverInteractionResolver.isActorHoverAction(MenuAction.WORLD_ENTITY_FIRST_OPTION));
	}

	@Test
	public void recognizesWorldEntityActorInteractions()
	{
		Assert.assertTrue(BillboardHoverInteractionResolver.isActorInteractionAction(MenuAction.WORLD_ENTITY_FIRST_OPTION));
		Assert.assertTrue(BillboardHoverInteractionResolver.isActorInteractionAction(MenuAction.NPC_FIRST_OPTION));
	}

	@Test
	public void recognizesAndResolvesGroundItemActions()
	{
		Assert.assertTrue(BillboardHoverInteractionResolver.isGroundItemAction(MenuAction.GROUND_ITEM_FIRST_OPTION));
		Assert.assertTrue(BillboardHoverInteractionResolver.isGroundItemAction(MenuAction.ITEM_USE_ON_GROUND_ITEM));
		Assert.assertFalse(BillboardHoverInteractionResolver.isGroundItemAction(MenuAction.WALK));

		WorldView worldView = proxy(WorldView.class, method("getBaseX", 0), method("getBaseY", 0));
		LocalPoint point = new LocalPoint(10 << 7, 20 << 7, worldView);
		TileItem item = proxy(TileItem.class, method("getId", 42));
		MenuEntry entry = proxy(
			MenuEntry.class,
			method("getIdentifier", 42),
			method("getParam0", 10),
			method("getParam1", 20));

		Assert.assertSame(
			item,
			BillboardHoverInteractionResolver.groundItem(
				entry,
				Collections.singletonList(new AbstractMap.SimpleEntry<>(
					item, new GroundItemBillboard(0, point)))));
	}

	@Test
	public void hoveredActorUsesTopMenuEntryWhenMenuIsClosed()
	{
		Actor actor = proxy(Actor.class);
		MenuEntry walk = proxy(MenuEntry.class, method("getType", MenuAction.WALK));
		MenuEntry attack = proxy(MenuEntry.class, method("getType", MenuAction.NPC_FIRST_OPTION), method("getActor", actor));
		Client client = proxy(
			Client.class,
			method("getMenuEntries", new MenuEntry[] { walk, attack }),
			method("isMenuOpen", false)
		);

		Assert.assertSame(actor, BillboardHoverInteractionResolver.hoveredActor(client));
	}

	@Test
	public void hoveredActorUsesMouseRowWhenMenuIsOpen()
	{
		Actor actor = proxy(Actor.class);
		MenuEntry bottom = proxy(MenuEntry.class, method("getType", MenuAction.WALK));
		MenuEntry middle = proxy(MenuEntry.class, method("getType", MenuAction.NPC_FIRST_OPTION), method("getActor", actor));
		MenuEntry top = proxy(MenuEntry.class, method("getType", MenuAction.CC_OP));
		Client client = proxy(
			Client.class,
			method("getMenuEntries", new MenuEntry[] { bottom, middle, top }),
			method("isMenuOpen", true),
			method("getMenuX", 10),
			method("getMenuY", 20),
			method("getMenuWidth", 150),
			method("getMouseCanvasPosition", new Point(40, 55))
		);

		Assert.assertSame(actor, BillboardHoverInteractionResolver.hoveredActor(client));
	}

	@Test
	public void interactedActorPrefersExplicitActorOverLocalPlayerTarget()
	{
		Actor explicitActor = proxy(Actor.class);
		Actor playerTarget = proxy(Actor.class);
		Player localPlayer = proxy(Player.class, method("getInteracting", playerTarget));
		Client client = proxy(Client.class, method("getLocalPlayer", localPlayer));

		Assert.assertSame(explicitActor, BillboardHoverInteractionResolver.interactedActor(client, explicitActor));
		Assert.assertSame(playerTarget, BillboardHoverInteractionResolver.interactedActor(client, null));
	}
}
