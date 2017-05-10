package reactor.test.utils;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.BaseSubscriber;

/**
 * A project reactive subscriber that simply logs after waiting for the given time.
 * Use to test backpressure 
 * @author the-james-burton
 */
public class DelaySubscriber<T> extends BaseSubscriber<T> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String name;
  private final int delay;
  private final AtomicInteger count = new AtomicInteger(0);

  public DelaySubscriber(String name, int delay) {
    this.name = name;
    this.delay = delay;
  }

  @Override
  protected void hookOnSubscribe(Subscription subscription) {
    request(1);
  }

  @Override
  protected void hookOnNext(T value) {
    try {
      logger.info("{}:processing:{}", name, count.incrementAndGet());
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    logger.info("{}:requesting next", name);
    request(1);
  }

  @Override
  protected void hookOnComplete() {
    logger.info("{}:complete", name);
    super.hookOnComplete();
  }

  @Override
  protected void hookOnError(Throwable throwable) {
    logger.info("{}:error:{}", name, throwable.getMessage());
    super.hookOnError(throwable);
  }

}
