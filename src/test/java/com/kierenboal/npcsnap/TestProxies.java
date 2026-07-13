package com.kierenboal.npcsnap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class TestProxies
{
	private TestProxies()
	{
	}

	static MethodResult method(String name, Object result)
	{
		return new MethodResult(name, result);
	}

	static MethodResult methodSupplier(String name, Supplier<?> result)
	{
		return new MethodResult(name, result);
	}

	@SuppressWarnings("unchecked")
	static <T> T proxy(Class<T> type, MethodResult... methodResults)
	{
		Map<String, Object> methods = new HashMap<>();
		for (MethodResult methodResult : methodResults)
		{
			methods.put(methodResult.name, methodResult.result);
		}

		return (T) Proxy.newProxyInstance(
			type.getClassLoader(),
			new Class<?>[] { type },
			new DefaultsInvocationHandler(methods)
		);
	}

	static final class MethodResult
	{
		private final String name;
		private final Object result;

		private MethodResult(String name, Object result)
		{
			this.name = name;
			this.result = result;
		}
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
				Object result = methods.get(name);
				return result instanceof Supplier ? ((Supplier<?>) result).get() : result;
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

			if (returnType == double.class)
			{
				return 0d;
			}

			if (returnType == float.class)
			{
				return 0f;
			}

			if (returnType == char.class)
			{
				return (char) 0;
			}

			return null;
		}
	}
}
