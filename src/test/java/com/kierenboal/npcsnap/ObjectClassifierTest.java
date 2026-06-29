package com.kierenboal.npcsnap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.DynamicObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Renderable;
import org.junit.Assert;
import org.junit.Test;

public class ObjectClassifierTest
{
	@Test
	public void dynamicGroundObjectIsNotAnEffect()
	{
		DynamicObject dynamicObject = proxy(DynamicObject.class);
		Map<String, Object> methods = new HashMap<>();
		methods.put("getRenderable", dynamicObject);
		GroundObject groundObject = proxy(GroundObject.class, methods);

		Assert.assertEquals(ClassifiedObjectType.UNKNOWN, ObjectClassifier.classifyTileObject(groundObject, null));
	}

	@Test
	public void graphicsGroundObjectIsAnEffect()
	{
		GraphicsObject graphicsObject = proxy(GraphicsObject.class);
		Map<String, Object> methods = new HashMap<>();
		methods.put("getRenderable", graphicsObject);
		GroundObject groundObject = proxy(GroundObject.class, methods);

		Assert.assertEquals(ClassifiedObjectType.EFFECT, ObjectClassifier.classifyTileObject(groundObject, null));
	}

	@Test
	public void dynamicRenderableIsStillAnObject()
	{
		Renderable dynamicObject = proxy(DynamicObject.class);

		Assert.assertEquals(ClassifiedObjectType.OBJECT, ObjectClassifier.classifyRenderable(dynamicObject));
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type)
	{
		return (T) Proxy.newProxyInstance(
			type.getClassLoader(),
			new Class<?>[] { type },
			new DefaultsInvocationHandler(new HashMap<>())
		);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Map<String, Object> methods)
	{
		return (T) Proxy.newProxyInstance(
			type.getClassLoader(),
			new Class<?>[] { type },
			new DefaultsInvocationHandler(methods)
		);
	}

	private static final class DefaultsInvocationHandler implements InvocationHandler
	{
		private final Map<String, Object> methods;

		private DefaultsInvocationHandler(Map<String, Object> methods)
		{
			this.methods = methods;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
		{
			String name = method.getName();
			if (methods.containsKey(name))
			{
				return methods.get(name);
			}

			if ("toString".equals(name))
			{
				return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
			}

			if ("hashCode".equals(name))
			{
				return System.identityHashCode(proxy);
			}

			if ("equals".equals(name))
			{
				return proxy == args[0];
			}

			Class<?> returnType = method.getReturnType();
			if (returnType == boolean.class)
			{
				return false;
			}

			if (returnType == byte.class)
			{
				return (byte) 0;
			}

			if (returnType == short.class)
			{
				return (short) 0;
			}

			if (returnType == int.class)
			{
				return 0;
			}

			if (returnType == long.class)
			{
				return 0L;
			}

			if (returnType == float.class)
			{
				return 0f;
			}

			if (returnType == double.class)
			{
				return 0d;
			}

			if (returnType == char.class)
			{
				return (char) 0;
			}

			return null;
		}
	}
}
