package reactor.flux;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;

public class FluxTest {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private BespokeEventDrivenAPI bespokeAPI = new BespokeEventDrivenAPI();

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    logger.info("start:{}", name.getMethodName());
  }

  @Test
  public void testCreate() throws Exception {
    Flux.<String> create(sink -> bespokeAPI.notify(sink))
        .subscribe(m -> logger.info(m.toString()));

    bespokeAPI.onMessage("z");
    bespokeAPI.onMessage("x");
    bespokeAPI.onMessage("y");

    Thread.sleep(1000);
  }

  @Test
  public void testCreateAndBufferSort() throws Exception {
    Flux.<String> create(sink -> bespokeAPI.notify(sink))
        .buffer(Duration.ofMillis(100))
        .flatMap(l -> Flux.fromIterable(l).sort())
        .subscribe(m -> logger.info(m.toString()));

    bespokeAPI.onMessage("z");
    bespokeAPI.onMessage("x");
    bespokeAPI.onMessage("y");

    Thread.sleep(150);

    bespokeAPI.onMessage("c");
    bespokeAPI.onMessage("a");
    bespokeAPI.onMessage("b");

    Thread.sleep(150);

  }

  @Test
  public void testJust() {
    Flux.<String> just("c", "a", "b")
        .subscribe(m -> logger.info(m.toString()));
  }

  @Test
  public void testGenerateBufferSort() {
    List<String> result = Flux.<String> just("c", "a", "b")
        .buffer(Duration.ofSeconds(2))
        .flatMap(l -> Flux.fromIterable(l).sort())
        .collectList().block();
    // .subscribe(m -> logger.info(m.toString()));

    logger.info(result.toString());
  }

}
