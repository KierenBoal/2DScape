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
import net.runelite.api.TileObject;
import net.runelite.api.Tile;
import net.runelite.api.GameObject;
import net.runelite.api.WorldView;
import net.runelite.api.Constants;

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
				&& billboard.localPoint.getSceneY() == entry.getParam1()
				&& billboard.localPoint.getWorldView() == entry.getWorldViewId())
			{
				return item;
			}
		}
		return null;
	}

	public static MenuEntry hoveredMenuEntry(Client client)
	{
		MenuEntry[] entries = client.getMenuEntries();
		return entries == null || entries.length == 0 ? null
			: client.isMenuOpen() ? hoveredMenuEntry(client, entries) : entries[entries.length - 1];
	}

	public static boolean isObjectAction(MenuAction action)
	{
		if (action == null) return false;
		switch (action)
		{
			case ITEM_USE_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	/** Resolve only the menu's tile, never scan the scene. */
	public static TileObject tileObject(Client client, MenuEntry entry)
	{
		if (entry == null || !isObjectAction(entry.getType())) return null;
		WorldView view = client.getWorldView(entry.getWorldViewId());
		if (view == null || view.getScene() == null) return null;
		int offset = view.isTopLevel()
			? (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2 : 0;
		int x = entry.getParam0() + offset, y = entry.getParam1() + offset;
		Tile[][][] tiles = view.getScene().getExtendedTiles();
		int plane = view.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null
			|| x < 0 || x >= tiles[plane].length || tiles[plane][x] == null
			|| y < 0 || y >= tiles[plane][x].length) return null;
		Tile tile = tiles[plane][x][y];
		if (tile == null) return null;
		int id = entry.getIdentifier();
		if (tile.getGameObjects() != null)
			for (GameObject object : tile.getGameObjects())
				if (object != null && object.getId() == id) return object;
		if (tile.getWallObject() != null && tile.getWallObject().getId() == id) return tile.getWallObject();
		if (tile.getDecorativeObject() != null && tile.getDecorativeObject().getId() == id) return tile.getDecorativeObject();
		return tile.getGroundObject() != null && tile.getGroundObject().getId() == id ? tile.getGroundObject() : null;
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
