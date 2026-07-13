package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardProjectileGeometry;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.DynamicObject;
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
import net.runelite.api.coords.LocalPoint;
import org.slf4j.Logger;

public final class BillboardClassificationDebug
{
	private final Client client;
	private final NpcSnapConfig config;
	private final Logger log;
	private final Map<Object, String> previousMessages = new IdentityHashMap<>();

	public BillboardClassificationDebug(Client client, NpcSnapConfig config, Logger log)
	{
		this.client = client;
		this.config = config;
		this.log = log;
	}

	public void clear()
	{
		previousMessages.clear();
	}

	public ClassifiedObjectType resolveObservedTileObjectType(ObservedTileObject observed)
	{
		if (observed == null)
		{
			return ClassifiedObjectType.UNKNOWN;
		}

		ObjectClassifier.ClassificationDecision decision = classifyObservedTileObjectDecision(observed);
		logObservedTileObjectClassification(observed, decision);
		ClassifiedObjectType classifiedType = decision.classification;
		if (classifiedType == ClassifiedObjectType.UNKNOWN)
		{
			return observed.classifiedType;
		}

		observed.classifiedType = classifiedType;
		return classifiedType;
	}

	public void logDecision(Object source, ObjectClassifier.ClassificationDecision decision)
	{
		logDecision(source, decision, null);
	}

	public void logDecision(Object source, ObjectClassifier.ClassificationDecision decision, String extraTypes)
	{
		if (!log.isDebugEnabled() || !config.debugLogClassifications() || source == null || decision == null)
		{
			return;
		}

		String message = classificationDebugMessage(source, decision, extraTypes);
		String previousMessage = previousMessages.put(source, message);
		if (message.equals(previousMessage))
		{
			return;
		}

		log.debug(message);
	}

	public String actorDebugKey(Actor actor)
	{
		return classificationDebugApiType(actor) + ":" + classificationDebugId(actor) + ":" + classificationDebugName(actor);
	}

	private ObjectClassifier.ClassificationDecision classifyObservedTileObjectDecision(ObservedTileObject observed)
	{
		if (observed == null || observed.tileObject == null)
		{
			return ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed == null || observed.tileObject == null");
		}

		boolean hasEffectLikePart = observedHasEffectLikePart(observed);
		TileObject tileObject = observed.tileObject;
		if (tileObject instanceof GameObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed GameObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.OBJECT, "observed GameObject has no effect-like part");
		}

		if (tileObject instanceof DecorativeObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed DecorativeObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed DecorativeObject has no effect-like part");
		}

		if (tileObject instanceof WallObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed WallObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed WallObject has no effect-like part");
		}

		if (tileObject instanceof GroundObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed GroundObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed GroundObject has no effect-like part");
		}

		return ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed tileObject wrapper not handled");
	}

	private boolean observedHasEffectLikePart(ObservedTileObject observed)
	{
		if (observed == null || observed.parts == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part == null || part.renderable == null)
			{
				continue;
			}

			if (ObjectClassifier.classifyRenderable(part.renderable) == ClassifiedObjectType.EFFECT)
			{
				return true;
			}
		}

		return false;
	}

	private String classificationDebugMessage(Object source, ObjectClassifier.ClassificationDecision decision, String extraTypes)
	{
		String type = classificationDebugApiType(source);
		String implType = source.getClass().getSimpleName();
		String id = classificationDebugId(source);
		String name = classificationDebugName(source);
		String position = classificationDebugPosition(source);
		String relatedTypes = extraTypes == null || extraTypes.isEmpty() ? "-" : extraTypes;
		return "Classification: Type: " + type
			+ ", Impl: " + implType
			+ ", ID: " + id
			+ ", Name: " + name
			+ ", Position: " + position
			+ ", Related: " + relatedTypes
			+ ", Reason: '" + decision.reason + "'"
			+ ", Classification: " + decision.classification;
	}

	private String classificationDebugApiType(Object source)
	{
		if (source instanceof NPC)
		{
			return "NPC";
		}

		if (source instanceof Player)
		{
			return "Player";
		}

		if (source instanceof Projectile)
		{
			return "Projectile";
		}

		if (source instanceof GraphicsObject)
		{
			return "GraphicsObject";
		}

		if (source instanceof ActorSpotAnim)
		{
			return "ActorSpotAnim";
		}

		if (source instanceof TileItem)
		{
			return "TileItem";
		}

		if (source instanceof GameObject)
		{
			return "GameObject";
		}

		if (source instanceof DecorativeObject)
		{
			return "DecorativeObject";
		}

		if (source instanceof WallObject)
		{
			return "WallObject";
		}

		if (source instanceof GroundObject)
		{
			return "GroundObject";
		}

		if (source instanceof TileObject)
		{
			return "TileObject";
		}

		if (source instanceof DynamicObject)
		{
			return "DynamicObject";
		}

		if (source instanceof Renderable)
		{
			return "Renderable";
		}

		return source.getClass().getSimpleName();
	}

	private String classificationDebugId(Object source)
	{
		if (source instanceof TileObject)
		{
			return Integer.toString(((TileObject) source).getId());
		}

		if (source instanceof NPC)
		{
			return Integer.toString(((NPC) source).getId());
		}

		if (source instanceof TileItem)
		{
			return Integer.toString(((TileItem) source).getId());
		}

		if (source instanceof GraphicsObject)
		{
			return Integer.toString(((GraphicsObject) source).getId());
		}

		if (source instanceof ActorSpotAnim)
		{
			return Integer.toString(((ActorSpotAnim) source).getId());
		}

		if (source instanceof Projectile)
		{
			return Integer.toString(((Projectile) source).getId());
		}

		if (source instanceof Player)
		{
			Player player = (Player) source;
			return player.getName() != null ? player.getName() : "player";
		}

		return "-";
	}

	private String classificationDebugName(Object source)
	{
		if (source instanceof Player)
		{
			String name = ((Player) source).getName();
			return name != null && !name.isEmpty() ? name : "-";
		}

		if (source instanceof NPC)
		{
			String name = ((NPC) source).getName();
			return name != null && !name.isEmpty() ? name : "-";
		}

		if (source instanceof TileObject)
		{
			if (!client.isClientThread())
			{
				return "-";
			}

			net.runelite.api.ObjectComposition objectDefinition = client.getObjectDefinition(((TileObject) source).getId());
			if (objectDefinition == null)
			{
				return "-";
			}

			String name = objectDefinition.getName();
			return name != null && !name.trim().isEmpty() ? name : "-";
		}

		return "-";
	}

	private String classificationDebugPosition(Object source)
	{
		if (source instanceof TileObject)
		{
			TileObject tileObject = (TileObject) source;
			LocalPoint localPoint = tileObject.getLocalLocation();
			String local = formatLocalPoint(localPoint);
			String world = tileObject.getWorldLocation() != null
				? tileObject.getWorldLocation().getX() + "," + tileObject.getWorldLocation().getY() + "," + tileObject.getPlane()
				: "-";
			return "world=" + world + " local=" + local;
		}

		if (source instanceof Actor)
		{
			Actor actor = (Actor) source;
			LocalPoint localPoint = actor.getLocalLocation();
			String local = formatLocalPoint(localPoint);
			String world = actor.getWorldLocation() != null
				? actor.getWorldLocation().getX() + "," + actor.getWorldLocation().getY() + "," + actor.getWorldView().getPlane()
				: "-";
			return "world=" + world + " local=" + local;
		}

		if (source instanceof GraphicsObject)
		{
			GraphicsObject graphicsObject = (GraphicsObject) source;
			return "level=" + graphicsObject.getLevel() + " local=" + formatLocalPoint(graphicsObject.getLocation());
		}

		if (source instanceof Projectile)
		{
			Projectile projectile = (Projectile) source;
			return "floor=" + projectile.getFloor() + " local=" + formatLocalPoint(BillboardProjectileGeometry.localPoint(projectile));
		}

		return "-";
	}

	private void logObservedTileObjectClassification(ObservedTileObject observed, ObjectClassifier.ClassificationDecision decision)
	{
		if (!log.isDebugEnabled() || observed == null || observed.tileObject == null || decision == null)
		{
			return;
		}

		if (decision.classification == observed.lastLoggedClassification
			&& Objects.equals(decision.reason, observed.lastLoggedClassificationReason))
		{
			return;
		}

		observed.lastLoggedClassification = decision.classification;
		observed.lastLoggedClassificationReason = decision.reason;

		TileObject tileObject = observed.tileObject;
		String renderableTypes = observedRenderableTypes(observed);
		logDecision(tileObject, decision, renderableTypes);
	}

	private String observedRenderableTypes(ObservedTileObject observed)
	{
		if (observed == null || observed.parts == null || observed.parts.isEmpty())
		{
			return "-";
		}

		StringBuilder builder = new StringBuilder();
		for (ObjectRenderablePart part : observed.parts)
		{
			if (part == null || part.renderable == null)
			{
				continue;
			}

			if (builder.length() > 0)
			{
				builder.append(", ");
			}

			builder.append(classificationDebugApiType(part.renderable))
				.append("(")
				.append(part.renderable.getClass().getSimpleName())
				.append(")");
		}

		return builder.length() > 0 ? builder.toString() : "-";
	}

	private static String formatLocalPoint(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return "-";
		}

		return localPoint.getX() + "," + localPoint.getY();
	}

}
