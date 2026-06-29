package com.kierenboal.npcsnap;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;

final class ObjectClassifier
{
	private ObjectClassifier()
	{
	}

	static ClassifiedObjectType classify(Object object, Client client)
	{
		return classifyDecision(object, client).classification;
	}

	static ClassificationDecision classifyDecision(Object object, Client client)
	{
		if (object == null)
		{
			return ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "object == null");
		}

		if (object instanceof NPC)
		{
			return ClassificationDecision.of(ClassifiedObjectType.NPC, "object instanceof NPC");
		}

		if (object instanceof Player)
		{
			return ClassificationDecision.of(ClassifiedObjectType.PLAYER, "object instanceof Player");
		}

		if (object instanceof Projectile)
		{
			return ClassificationDecision.of(ClassifiedObjectType.PROJECTILE, "object instanceof Projectile");
		}

		if (object instanceof GraphicsObject || object instanceof ActorSpotAnim)
		{
			return ClassificationDecision.of(ClassifiedObjectType.EFFECT, "object instanceof GraphicsObject || object instanceof ActorSpotAnim");
		}

		if (object instanceof TileItem)
		{
			return ClassificationDecision.of(ClassifiedObjectType.GROUND_ITEM, "object instanceof TileItem");
		}

		if (object instanceof TileObject)
		{
			return classifyTileObjectDecision((TileObject) object, client);
		}

		if (object instanceof Renderable)
		{
			return classifyRenderableDecision((Renderable) object);
		}

		return ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "object type not handled");
	}

	static ClassifiedObjectType classifyRenderable(Renderable renderable)
	{
		return classifyRenderableDecision(renderable).classification;
	}

	static ClassificationDecision classifyRenderableDecision(Renderable renderable)
	{
		if (renderable == null)
		{
			return ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "renderable == null");
		}

		if (renderable instanceof NPC)
		{
			return ClassificationDecision.of(ClassifiedObjectType.NPC, "renderable instanceof NPC");
		}

		if (renderable instanceof Player)
		{
			return ClassificationDecision.of(ClassifiedObjectType.PLAYER, "renderable instanceof Player");
		}

		if (renderable instanceof Projectile)
		{
			return ClassificationDecision.of(ClassifiedObjectType.PROJECTILE, "renderable instanceof Projectile");
		}

		if (renderable instanceof GraphicsObject || renderable instanceof ActorSpotAnim)
		{
			return ClassificationDecision.of(ClassifiedObjectType.EFFECT, "renderable instanceof GraphicsObject || renderable instanceof ActorSpotAnim");
		}

		if (renderable instanceof TileItem)
		{
			return ClassificationDecision.of(ClassifiedObjectType.GROUND_ITEM, "renderable instanceof TileItem");
		}

		if (renderable instanceof DynamicObject)
		{
			return ClassificationDecision.of(ClassifiedObjectType.OBJECT, "renderable instanceof DynamicObject");
		}

		if (renderable instanceof Actor)
		{
			return ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "renderable instanceof Actor");
		}

		return ClassificationDecision.of(ClassifiedObjectType.OBJECT, "renderable defaulted to OBJECT");
	}

	static ClassifiedObjectType classifyTileObject(TileObject tileObject, Client client)
	{
		return classifyTileObjectDecision(tileObject, client).classification;
	}

	static ClassificationDecision classifyTileObjectDecision(TileObject tileObject, Client client)
	{
		if (tileObject == null)
		{
			return ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "tileObject == null");
		}

		if (tileObject instanceof GameObject)
		{
			return hasEffectLikeRenderable(tileObject)
				? ClassificationDecision.of(ClassifiedObjectType.EFFECT, "tileObject instanceof GameObject && hasEffectLikeRenderable")
				: ClassificationDecision.of(ClassifiedObjectType.OBJECT, "tileObject instanceof GameObject && !hasEffectLikeRenderable");
		}

		if (tileObject instanceof DecorativeObject)
		{
			return ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "tileObject instanceof DecorativeObject && !hasEffectLikeRenderable");
			//return hasEffectLikeRenderable(tileObject) ? ClassificationDecision.of(ClassifiedObjectType.EFFECT, "tileObject instanceof DecorativeObject && hasEffectLikeRenderable") : ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "tileObject instanceof DecorativeObject && !hasEffectLikeRenderable");
		}

		if (tileObject instanceof WallObject)
		{
			return hasEffectLikeRenderable(tileObject)
				? ClassificationDecision.of(ClassifiedObjectType.EFFECT, "tileObject instanceof WallObject && hasEffectLikeRenderable")
				: ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "tileObject instanceof WallObject && !hasEffectLikeRenderable");
		}

		if (tileObject instanceof GroundObject)
		{
			return hasEffectLikeRenderable(tileObject)
				? ClassificationDecision.of(ClassifiedObjectType.EFFECT, "tileObject instanceof GroundObject && hasEffectLikeRenderable")
				: ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "tileObject instanceof GroundObject && !hasEffectLikeRenderable");
		}

		return ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "tileObject wrapper not handled");
	}

	static boolean isEnabled(ClassifiedObjectType classifiedType, NpcSnapConfig config)
	{
		if (classifiedType == null || config == null)
		{
			return false;
		}

		switch (classifiedType)
		{
			case NPC:
				return config.applyToNpcs();
			case PLAYER:
				return config.applyToPlayers();
			case EFFECT:
				return config.applyToGraphicsObjects();
			case PROJECTILE:
				return config.applyToProjectiles();
			case GROUND_ITEM:
				return config.applyToGroundItems();
			case OBJECT:
				return config.applyToObjects();
			default:
				return false;
		}
	}

	static int renderPriority(ClassifiedObjectType classifiedType)
	{
		if (classifiedType == null)
		{
			return -1;
		}

		switch (classifiedType)
		{
			case GROUND_ITEM:
				return 0;
			case NPC:
			case PLAYER:
				return 1;
			case EFFECT:
				return 2;
			default:
				return -1;
		}
	}

	static boolean keepsActorInteraction(Renderable renderable)
	{
		ClassifiedObjectType classifiedType = classifyRenderable(renderable);
		return classifiedType == ClassifiedObjectType.NPC || classifiedType == ClassifiedObjectType.PLAYER;
	}

	private static boolean hasEffectLikeRenderable(TileObject tileObject)
	{
		if (tileObject instanceof GameObject)
		{
			Renderable renderable = ((GameObject) tileObject).getRenderable();
			return renderable instanceof GraphicsObject || renderable instanceof ActorSpotAnim;
		}

		if (tileObject instanceof GroundObject)
		{
			return isEffectRenderable(((GroundObject) tileObject).getRenderable());
		}

		if (tileObject instanceof DecorativeObject)
		{
			DecorativeObject decorativeObject = (DecorativeObject) tileObject;
			return isEffectRenderable(decorativeObject.getRenderable())
				|| isEffectRenderable(decorativeObject.getRenderable2());
		}

		if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			return isEffectRenderable(wallObject.getRenderable1())
				|| isEffectRenderable(wallObject.getRenderable2());
		}

		return false;
	}

	private static boolean isEffectRenderable(Renderable renderable)
	{
		return renderable instanceof GraphicsObject
			|| renderable instanceof ActorSpotAnim;
	}

	static final class ClassificationDecision
	{
		final ClassifiedObjectType classification;
		final String reason;

		private ClassificationDecision(ClassifiedObjectType classification, String reason)
		{
			this.classification = classification;
			this.reason = reason;
		}

		static ClassificationDecision of(ClassifiedObjectType classification, String reason)
		{
			return new ClassificationDecision(classification, reason);
		}
	}
}
