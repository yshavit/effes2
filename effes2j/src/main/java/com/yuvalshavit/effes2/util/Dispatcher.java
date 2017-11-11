package com.yuvalshavit.effes2.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A dispatcher for subclasses that are all enclosed within their parent.
 * @param <T> the parent type
 * @param <R> the dispatch result
 */
public abstract class Dispatcher<T,R> implements Function<T,R> {
  private static final Map<Class<?>,Map<Class<?>,BiFunction<?,?,?>>> cachedFunctions = new ConcurrentHashMap<>();

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Dispatched {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface SubclassesAreIn {
    Class<?>[] value();
  }

  /**
   * keys are T's subclasses; values are BiFunctions that take "this" and a T, and return an R
   */
  private final Map<Class<? extends T>,BiFunction<Dispatcher<T,R>,? super T,? extends R>> functions;
  private final BiFunction<? super T, ? super Exception, ? extends RuntimeException> exceptionNormalizer;

  protected Dispatcher(Class<T> parentClass, Class<R> resultClass, BiFunction<? super T,? super Exception,? extends RuntimeException> exceptionNormalizer) {
    this.exceptionNormalizer = exceptionNormalizer;
    @SuppressWarnings("unchecked")
    Map<Class<? extends T>,BiFunction<Dispatcher<T,R>,? super T,? extends R>> dispatchSafe =
      (Map<Class<? extends T>,BiFunction<Dispatcher<T,R>,? super T,? extends R>>) getOrCreate((Class<Dispatcher<?,?>>) getClass(), parentClass, resultClass);
    this.functions = dispatchSafe;
  }

  public final R apply(T element) {
    @SuppressWarnings("unchecked")
    Class<? extends T> argClass = (Class<? extends T>) element.getClass();
    BiFunction<Dispatcher<T,R>,? super T,? extends R> func = functions.get(argClass);
    if (func == null) {
      throw new RuntimeException("no dispatch for argument of type " + argClass);
    }
    return func.apply(this, element);
  }

  private static Map getOrCreate(Class<Dispatcher<?,?>> dispatcher, Class<?> argClass, Class<?> resultClass) {
    SubclassesAreIn lookInAnnotation = dispatcher.getAnnotation(SubclassesAreIn.class);
    Class<?>[] lookIn = lookInAnnotation == null
      ? new Class[] { argClass }
      : lookInAnnotation.value();
    return cachedFunctions.computeIfAbsent(dispatcher, d -> dispatch(d, lookIn, argClass, resultClass));
  }

  private static Map<Class<?>,BiFunction<?,?,?>> dispatch(Class<?> dispatcherClass, Class<?>[] search, Class<?> argClass, Class<?> resultClass) {
    // (1) Find all subClasses of argClass that are enclosed in it
    // (2) find all Dispatched methods, and make sure they're instance methods that take a strict subclass of argClass and return a resultClass
    // (3) create the map of Dispatched methods, making sure to check for dupes
    // (4) make sure there are no subclasses left over

    // 1
    Set<Class<?>> subclasses = Stream.of(search)
      .map(Class::getClasses)
      .flatMap(Stream::of)
      .filter(cls -> argClass != cls && argClass.isAssignableFrom(cls))
      .collect(Collectors.toSet());

    // 2 - 3
    Map<Class<?>,BiFunction<?,?,?>> results = new HashMap<>(subclasses.size());
    for (Method method : dispatcherClass.getDeclaredMethods()) {
      if (method.getAnnotation(Dispatched.class) == null) {
        continue;
      }
      Class<?>[] argTypes = method.getParameterTypes();
      if (argTypes.length != 1) {
        throw new RuntimeException("dispatched method must take exactly one argument: " + method);
      }
      Class<?> methodArgClass = argTypes[0];
      if (methodArgClass == argClass || !argClass.isAssignableFrom(methodArgClass)) {
        throw new RuntimeException("argument to dispatched method must be a strict subclass of " + argClass + ": " + method);
      }
      if (!returnTypeIsAcceptable(resultClass, method)) {
        throw new RuntimeException("return value must be a subclass of " + resultClass + ": " + method);
      }
      method.setAccessible(true);
      BiFunction<?,?,?> old = results.put(methodArgClass, (Dispatcher<Object,?> o, Object a) -> {
        try {
          return method.invoke(o, a);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
          Throwable cause = e.getCause();
          if (cause instanceof Exception) {
            throw o.exceptionNormalizer.apply(a, ((Exception) cause));
          } else if (cause instanceof Error) {
            throw (Error) cause;
          } else {
            throw new RuntimeException("unrecognized Throwable type", e);
          }
        }
      });
      if (old != null) {
        throw new RuntimeException(String.format("duplicate dispatched methods that take %s: %s and %s", methodArgClass, old, method));
      }
    }

    // 4
    subclasses.removeAll(results.keySet());
    if (!subclasses.isEmpty()) {
      // To make this easy, we'll prove the boilerplate code for ya.
      StringBuilder sb = new StringBuilder("missing dispatch method");
      if (subclasses.size() > 1) {
        sb.append('s');
      }
      sb.append(":\n\n");
      String resultType = resultClass.equals(Void.class) ? "void" : resultClass.getSimpleName();
      for (Class<?> subclass : subclasses) {
        sb.append("  @").append(Dispatched.class.getSimpleName()).append('\n');
        sb.append("  public ").append(resultType).append(" apply(").append(subclass.getSimpleName()).append(" input) {\n");
        sb.append("    throw new UnsupportedOperationException(); // TODO\n");
        sb.append("  }\n\n");
      }
      throw new RuntimeException(sb.toString());
    }
    return results;
  }

  private static boolean returnTypeIsAcceptable(Class<?> resultClass, Method method) {
    return resultClass.isAssignableFrom(method.getReturnType())
      || (resultClass.equals(Void.class) && method.getReturnType() == void.class);
  }

}
