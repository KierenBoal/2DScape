package com.kierenboal.npcsnap.targeting;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Point;

public final class BillboardHoverInteractionResolver
{
	private BillboardHoverInteractionResolver()
	{
	}

	public static Actor hoveredActor(Client client)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries == null || menuEntries.length == 0)
		{
			return null;
		}

		MenuEntry hoveredEntry = client.isMenuOpen()
			? hoveredMenuEntry(client, menuEntries)
			: menuEntries[menuEntries.length - 1];
		if (hoveredEntry == null || !isActorHoverAction(hoveredEntry.getType()))
		{
			return null;
		}

		return hoveredEntry.getActor();
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
