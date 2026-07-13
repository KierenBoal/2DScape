package com.kierenboal.npcsnap;

import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;

final class BillboardSceneDrawCallbacks
{
	interface OverlayAccess
	{
		void noteSceneRenderable(Renderable renderable);

		void observeTileObject(TileObject tileObject);

		boolean shouldHideRenderable(Renderable renderable);

		boolean shouldHideTileObject(TileObject tileObject);
	}

	private final NpcSnapConfig config;
	private final OverlayAccess overlay;

	BillboardSceneDrawCallbacks(NpcSnapConfig config, OverlayAccess overlay)
	{
		this.config = config;
		this.overlay = overlay;
	}

	boolean addEntity(Renderable renderable, boolean drawingUi)
	{
		if (!isActiveSceneDraw(drawingUi))
		{
			return true;
		}

		overlay.noteSceneRenderable(renderable);
		// Actors must stay in this callback chain so RuneLite can build their
		// clickboxes and menu entries. Their visual suppression happens elsewhere.
		return ObjectClassifier.keepsActorInteraction(renderable)
			|| !overlay.shouldHideRenderable(renderable);
	}

	boolean draw(Renderable renderable, boolean drawingUi)
	{
		if (!isActiveSceneDraw(drawingUi))
		{
			return true;
		}

		overlay.noteSceneRenderable(renderable);
		return !overlay.shouldHideRenderable(renderable);
	}

	boolean drawObject(TileObject tileObject)
	{
		if (!config.enable2dBillboardSprites())
		{
			return true;
		}

		if (tileObject instanceof GameObject)
		{
			return shouldDrawObservedObject(tileObject, ((GameObject) tileObject).getRenderable());
		}

		if (tileObject instanceof GroundObject)
		{
			return shouldDrawObservedObject(tileObject, ((GroundObject) tileObject).getRenderable());
		}

		if (tileObject instanceof DecorativeObject)
		{
			if (observeAndShouldHide(tileObject))
			{
				return false;
			}
			DecorativeObject object = (DecorativeObject) tileObject;
			return shouldDrawAll(object.getRenderable(), object.getRenderable2());
		}

		if (tileObject instanceof WallObject)
		{
			if (observeAndShouldHide(tileObject))
			{
				return false;
			}
			WallObject object = (WallObject) tileObject;
			return shouldDrawAll(object.getRenderable1(), object.getRenderable2());
		}

		if (tileObject instanceof ItemLayer)
		{
			ItemLayer layer = (ItemLayer) tileObject;
			Renderable bottom = layer.getBottom();
			Renderable middle = layer.getMiddle();
			Renderable top = layer.getTop();
			overlay.noteSceneRenderable(bottom);
			overlay.noteSceneRenderable(middle);
			overlay.noteSceneRenderable(top);
			return shouldDrawAll(bottom, middle, top);
		}

		return true;
	}

	private boolean isActiveSceneDraw(boolean drawingUi)
	{
		return config.enable2dBillboardSprites() && !drawingUi;
	}

	private boolean shouldDrawObservedObject(TileObject tileObject, Renderable renderable)
	{
		return !observeAndShouldHide(tileObject) && shouldDraw(renderable);
	}

	private boolean observeAndShouldHide(TileObject tileObject)
	{
		if (!config.applyToObjects() && !config.applyToGraphicsObjects())
		{
			return false;
		}

		overlay.observeTileObject(tileObject);
		return overlay.shouldHideTileObject(tileObject);
	}

	private boolean shouldDrawAll(Renderable first, Renderable second)
	{
		return shouldDraw(first) && shouldDraw(second);
	}

	private boolean shouldDrawAll(Renderable first, Renderable second, Renderable third)
	{
		return shouldDraw(first) && shouldDraw(second) && shouldDraw(third);
	}

	private boolean shouldDraw(Renderable renderable)
	{
		return !overlay.shouldHideRenderable(renderable);
	}
}
