package reactor.flux;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javaslang.control.Try;

public class FuturesTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void testFuture() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c");

    Callable<List<String>> task = () -> data.stream()
        .peek(s -> logger.info("appending:{}", s))
        .map(s -> s.concat("z"))
        .collect(Collectors.toList());

    ExecutorService executor = ForkJoinPool.commonPool();
    Future<List<String>> future = executor.submit(task);

    List<String> done = future.get();
    done.forEach(s -> logger.info("done:{}", s));
  }

  @Test
  public void testCompletionService() throws Exception {
    List<String> data = Lists.newArrayList("a", "b", "c");

    Function<String, Callable<String>> task = (s) -> () -> s.concat("z");

    ExecutorService executor = ForkJoinPool.commonPool();
    CompletionService<String> completionService = new ExecutorCompletionService<>(executor);

    data.forEach(s -> completionService.submit(task.apply(s)));

    data.stream()
        .map(s -> Try.of(() -> completionService.take()).get())
        .forEach(i -> logger.info("done:{}", Try.of(() -> i.get()).get()));
  }

  @Test
  public void testCompletableFuture() throws Exception {
  }

}
