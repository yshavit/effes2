package com.yuvalshavit.effes2.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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

    // 1 - 3
    Set<Class<?>> subclasses = findSubclasses(argClass, search);
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

  private static Set<Class<?>> findSubclasses(Class<?> superClass, Class<?>... subclassesAreIn) {
    return Stream.of(subclassesAreIn)
        .map(Class::getClasses)
        .flatMap(Stream::of)
        .filter(cls -> superClass != cls && superClass.isAssignableFrom(cls))
        .collect(Collectors.toSet());
  }

  private static boolean returnTypeIsAcceptable(Class<?> resultClass, Method method) {
    return resultClass.isAssignableFrom(method.getReturnType())
      || (resultClass.equals(Void.class) && method.getReturnType() == void.class);
  }

  public static <I,R> AdHocDispatcher<I,R> dispatch(Class<I> inputBaseClass, Class<?> lookIn, Class<R> resultClass) {
    // We don't actually care about resultClass: it's just there to help Java's type inference. Still, I don't want to leak that to the API, so this trick lets
    // me suppress its unused-ness without putting the annotation up in the method signature.
    @SuppressWarnings("unused")
    Class<?> ignore = resultClass;
    return new AdHocDispatcher<>(inputBaseClass, lookIn);
  }


  public static <I,R> AdHocDispatcher<I,R> dispatch(Class<I> inputBaseClass, Class<R> resultClass) {
    Class<?> enclosingClass = inputBaseClass.getEnclosingClass();
    if (enclosingClass == null) {
      throw new IllegalArgumentException(String.format("%s has no enclosing class. Call the overload with lookIn", inputBaseClass.getSimpleName()));
    }
    return dispatch(inputBaseClass, enclosingClass, resultClass);
  }

  public static class AdHocDispatcher<I,R> {

    private final Map<Class<?>,Function<? super I, ? extends R>> handlers;
    private Supplier<? extends R> nullHandler;

    public AdHocDispatcher(Class<I> inputBaseClass, Class<?> lookIn) {
      Set<Class<?>> subclasses = findSubclasses(inputBaseClass, lookIn);
      handlers = new HashMap<>(subclasses.size());
      subclasses.forEach(c -> handlers.put(c, null));
    }

    public <S extends I> AdHocDispatcher<I,R> when(Class<S> subclass, Function<? super S, ? extends R> handler) {
      Objects.requireNonNull(subclass, "subclass can't be null");
      Objects.requireNonNull(handler, "handler can't be null");
      @SuppressWarnings("unchecked") // We'll always fetch the right handler, so this will be safe
      Function<? super I, ? extends R> handlerCast = (Function<? super I, ? extends R>) handler;
      Function<?,?> old = handlers.put(subclass, handlerCast);
      if (old != null) {
        throw new IllegalStateException("duplicate handler for " + subclass.getSimpleName());
      }
      return this;
    }

    public AdHocDispatcher<I,R> whenNull(Supplier<? extends R> nullHandler) {
      Objects.requireNonNull(nullHandler, "handler can't be null");
      this.nullHandler = nullHandler;
      return this;
    }

    public R on(I input) {
      validateAllSubclassesHandled();
      if (input == null) {
        return Objects.requireNonNull(nullHandler, "no null handler set").get();
      } else {
        return handlers.get(input.getClass()).apply(input);
      }
    }

    private void validateAllSubclassesHandled() {
      Set<String> unhandledClasses = handlers.entrySet().stream()
        .filter(e -> e.getValue() == null)
        .map(Map.Entry::getKey)
        .map(Class::getSimpleName)
        .collect(Collectors.toSet());
      if (!unhandledClasses.isEmpty()) {
        throw new IllegalStateException(String.format("unhandled subclass%s: %s", unhandledClasses.size() == 1 ? "" : "es", unhandledClasses));
      }
    }
  }

}
