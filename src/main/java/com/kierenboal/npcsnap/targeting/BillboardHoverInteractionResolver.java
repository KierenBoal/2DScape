package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;

public final class BillboardHoverInteractionResolver
{
	private BillboardHoverInteractionResolver()
	{
	}

	public static Actor hoveredActor(Client client)
	{
		Renderable target = hoveredTarget(client, null);
		return target instanceof Actor ? (Actor) target : null;
	}

	public static Renderable hoveredTarget(Client client, Iterable<Map.Entry<TileItem, GroundItemBillboard>> groundItems)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries == null || menuEntries.length == 0)
		{
			return null;
		}

		MenuEntry hoveredEntry = client.isMenuOpen()
			? hoveredMenuEntry(client, menuEntries)
			: menuEntries[menuEntries.length - 1];
		if (hoveredEntry == null)
		{
			return null;
		}

		if (isActorHoverAction(hoveredEntry.getType()))
		{
			return hoveredEntry.getActor();
		}
		return isGroundItemAction(hoveredEntry.getType())
			? groundItem(hoveredEntry, groundItems)
			: null;
	}

	public static Actor interactedActor(Client client, Actor explicitInteraction)
	{
		if (explicitInteraction != null)
		{
			return explicitInteraction;
		}

		Player localPlayer = client.getLocalPlayer();
		return localPlayer != null ? localPlayer.getInteracting() : null;
	}

	public static boolean isActorHoverAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}

		switch (action)
		{
			case ITEM_USE_ON_NPC:
			case WIDGET_TARGET_ON_NPC:
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case ITEM_USE_ON_PLAYER:
			case WIDGET_TARGET_ON_PLAYER:
			case PLAYER_FIRST_OPTION:
			case PLAYER_SECOND_OPTION:
			case PLAYER_THIRD_OPTION:
			case PLAYER_FOURTH_OPTION:
			case PLAYER_FIFTH_OPTION:
			case PLAYER_SIXTH_OPTION:
			case PLAYER_SEVENTH_OPTION:
			case PLAYER_EIGHTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	public static boolean isActorInteractionAction(MenuAction action)
	{
		return isActorHoverAction(action) || action == MenuAction.WORLD_ENTITY_FIRST_OPTION;
	}

	public static boolean isGroundItemAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}
		switch (action)
		{
			case ITEM_USE_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	public static TileItem groundItem(MenuEntry entry, Iterable<Map.Entry<TileItem, GroundItemBillboard>> groundItems)
	{
		if (entry == null || groundItems == null)
		{
			return null;
		}
		for (Map.Entry<TileItem, GroundItemBillboard> candidate : groundItems)
		{
			TileItem item = candidate.getKey();
			GroundItemBillboard billboard = candidate.getValue();
			if (item != null && billboard != null && item.getId() == entry.getIdentifier()
				&& billboard.localPoint.getSceneX() == entry.getParam0()
				&& billboard.localPoint.getSceneY() == entry.getParam1())
			{
				return item;
			}
		}
		return null;
	}

	private static MenuEntry hoveredMenuEntry(Client client, MenuEntry[] menuEntries)
	{
		int menuX = client.getMenuX();
		int menuY = client.getMenuY();
		int menuWidth = client.getMenuWidth();
		Point mouse = client.getMouseCanvasPosition();
		int row = mouse.getY() - menuY - 19;
		if (row < 0)
		{
			return menuEntries[menuEntries.length - 1];
		}

		row = (menuEntries.length - 1) - (row / 15);
		if (mouse.getX() > menuX && mouse.getX() < menuX + menuWidth && row >= 0 && row < menuEntries.length)
		{
			return menuEntries[row];
		}

		return menuEntries[menuEntries.length - 1];
	}
}
