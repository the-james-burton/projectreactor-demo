package reactor.flux;

import static java.util.stream.Collectors.*;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.control.Try;

/**
 * This class demonstrates the difference between Future, CompletionService and CompletableFuture.
 * @author the-james-burton
 */
public class FuturesTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    logger.info("start:{}", name.getMethodName());
  }

  /** With a Future, we must submit our tasks and then block on the completion  */
  @Test
  public void testFuture() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c", "d", "e", "f");

    Function<String, Callable<String>> task = (s) -> () -> {
      logger.info("appending:{}", s);
      return s.concat("z");
    };

    ExecutorService executor = new ForkJoinPool(3);

    List<Future<String>> futures = data.stream()
        .map(s -> task.apply(s))
        .map(c -> executor.submit(c))
        .collect(toList());

    // this is blocking..!
    futures.stream()
        .map(f -> Try.of(() -> f.get()).get())
        .forEach(s -> logger.info("done:{}", s));

    logger.info("blocked until all done");
  }

  /** A CompletionService hides the Future, but is still blocking */
  @Test
  public void testCompletionService() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c", "d", "e", "f");

    Function<String, Callable<String>> task = (s) -> () -> {
      logger.info("appending:{}", s);
      return s.concat("z");
    };

    ExecutorService executor = new ForkJoinPool(3);
    CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

    data.forEach(s -> completionService.submit(task.apply(s)));

    // this is blocking..!
    data.stream()
        .map(s -> Try.of(() -> completionService.take()).get())
        .forEach(t -> logger.info("done:{}", Try.of(() -> t.get()).get()));

    logger.info("blocked until all done");
  }

  /** The CompletableFuture */
  @Test
  public void testCompletableFuture() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c", "d", "e", "f");

    // notice how the otherwise identical task is now a Supplier, not a Callable..!
    Function<String, Supplier<String>> task = (s) -> () -> {
      logger.info("appending:{}", s);
      return s.concat("z");
    };

    // this is non-blocking, woo yay!
    List<CompletableFuture<Void>> futures = data.stream()
        .map(s -> CompletableFuture
            .supplyAsync(() -> task.apply(s))
            .thenAccept(t -> logger.info("done:{}:{}", s, t.get())))
        .collect(toList());

    logger.info("not blocked...");

    // we can wait until all completed...
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
        .thenRun(() -> logger.info("all done!"));

  }

}
