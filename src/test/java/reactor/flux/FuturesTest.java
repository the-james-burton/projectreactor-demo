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

public class FuturesTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    logger.info("start:{}", name.getMethodName());
  }

  @Test
  public void testFuture() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c");

    Function<String, Callable<String>> task = (s) -> () -> {
      logger.info("appending:{}", s);
      return s.concat("z");
    };

    ExecutorService executor = new ForkJoinPool(3);

    List<Future<String>> futures = data.stream()
        .map(s -> task.apply(s))
        .map(c -> executor.submit(c))
        .collect(toList());

    futures.stream()
        .map(f -> Try.of(() -> f.get()).get())
        .forEach(s -> logger.info("done:{}", s));
  }

  @Test
  public void testCompletionService() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c");

    Function<String, Callable<String>> task = (s) -> () -> {
      logger.info("appending:{}", s);
      return s.concat("z");
    };

    ExecutorService executor = new ForkJoinPool(3);
    CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

    data.forEach(s -> completionService.submit(task.apply(s)));

    data.stream()
        .map(s -> Try.of(() -> completionService.take()).get())
        .forEach(t -> logger.info("done:{}", Try.of(() -> t.get()).get()));
  }

  @Test
  public void testCompletableFuture() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c");

    Function<String, Supplier<String>> task = (s) -> () -> {
      logger.info("appending:{}", s);
      return s.concat("z");
    };

    data.forEach(s -> CompletableFuture
        .supplyAsync(() -> task.apply(s))
        .thenAccept(t -> logger.info("done:{}", t.get())));
  }

}
