package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import java.util.Map;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;

import static com.kierenboal.npcsnap.targeting.BillboardHoverInteractionResolver.*;

public final class BillboardInteractionState
{
	public Object hovered, interacted;
	public boolean hoverAttack, attacked;
	private int clickTick = -1;
	private int clickCycle = -1;
	private Renderable priority;

	public void click(Client client, MenuEntry entry, boolean itemOp, Iterable<Map.Entry<TileItem, GroundItemBillboard>> items)
	{
		MenuAction action = entry.getType();
		Object target;
		if (isActorInteractionAction(action)) target = entry.getActor();
		else if (isObjectAction(action)) target = tileObject(client, entry);
		else if (isGroundItemAction(action)) target = groundItem(entry, items);
		else
		{
			if (action == MenuAction.WALK || action == MenuAction.WIDGET_TARGET_ON_WIDGET || itemOp) clear();
			return;
		}
		select(target, client.getTickCount(), client.getGameCycle(), attack(client, action));
	}

	void select(Object target, int tick, int cycle, boolean attack)
	{
		interacted = target;
		clickTick = tick;
		clickCycle = cycle;
		attacked = attack;
	}

	private static boolean attack(Client client, MenuAction action)
	{
		return action == MenuAction.NPC_SECOND_OPTION || action == MenuAction.WIDGET_TARGET_ON_NPC
			&& client.getSelectedWidget() != null
			&& WidgetUtil.componentToInterface(client.getSelectedWidget().getId()) == InterfaceID.MAGIC_SPELLBOOK;
	}

	public void update(Client client, Iterable<Map.Entry<TileItem, GroundItemBillboard>> items)
	{
		MenuEntry entry = hoveredMenuEntry(client);
		hoverAttack = entry != null && attack(client, entry.getType());
		hovered = hoveredTarget(entry, items);
		if (hovered == null) hovered = tileObject(client, entry);
		priority = interacted instanceof Renderable ? (Renderable) interacted
			: hovered instanceof Renderable ? (Renderable) hovered : null;
	}

	public Renderable priorityTarget() { return priority; }
	public int clickAge(int cycle) { return clickCycle < 0 ? 10 : cycle - clickCycle; }

	public void onLocalPlayerInteractionChanged(Actor target, int tick)
	{
		if (tick <= clickTick) return;
		interacted = target;
		attacked = target != null && target.getCombatLevel() > 0;
	}

	public void clearOnArrival(Client client)
	{
		if (!(interacted instanceof Actor) && client.getTickCount() > clickTick
			&& client.getLocalDestinationLocation() == null) interacted = null;
	}

	public void clearIfMatches(Object target)
	{
		if (target == interacted) interacted = null;
		if (target == hovered) hovered = null;
	}

	public void unloadWorldView(WorldView view)
	{
		if (interacted instanceof TileObject && ((TileObject) interacted).getWorldView() == view) interacted = null;
	}

	public void clear()
	{
		hovered = interacted = priority = null;
		clickTick = clickCycle = -1;
		attacked = hoverAttack = false;
	}
}
