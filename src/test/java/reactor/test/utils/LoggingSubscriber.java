package reactor.test.utils;

import java.lang.invoke.MethodHandles;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSubscriber<T> implements Subscriber<T>, Subscription {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String name;

  private Subscription subscription;

  public LoggingSubscriber(String name) {
    this.name = name;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    this.subscription = subscription;
    logger.info("{} onSubscribe:{}", name, subscription.toString());
  }

  @Override
  public void onNext(T t) {
    logger.info("{} onNext:{}", name, t.toString());
  }

  @Override
  public void onError(Throwable t) {
    logger.error("{} onError:{}", name, t.getMessage());
  }

  @Override
  public void onComplete() {
    logger.info("{} onComplete", name);
  }

  @Override
  public void request(long n) {
    subscription.request(n);
  }

  @Override
  public void cancel() {
    subscription.cancel();
  }

}
