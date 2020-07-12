package com.yuvalshavit.javavariance;

public final class Main {

  public interface Producer<T> {
    T get();
  }

  public interface Consumer<T> {
    void put(T elem);
  }

  public interface Predicate<T> {
    boolean apply(T elem);
  }

  public <T> void consumeOne(Producer<? extends T> producer,
                             Consumer<? super T> consumer,
                             Predicate<? super T> predicate)
  {
    T e = producer.get();
    if (predicate.apply(e)) {
      consumer.put(e);
    }
  }

  public void run() {
    Producer<Integer> producer = null;
    Consumer<Object> consumer = null;
    Predicate<Integer> predicate = null;
    consumeOne(producer, consumer, predicate);
  }
}
