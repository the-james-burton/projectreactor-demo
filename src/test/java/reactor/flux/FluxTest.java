package reactor.flux;

import static java.lang.String.*;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.publisher.WorkQueueProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.test.utils.DelaySubscriber;
import reactor.test.utils.LoggingSubscriber;
import reactor.test.utils.TestUtils;

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
  public void testJust() {
    Flux.<String> just("c", "a", "b")
        .subscribe(m -> logger.info(m.toString()));
  }

  @Test
  public void testCold() throws InterruptedException {
    Flux<Integer> flux = Flux.range(1, 10)
        .map(i -> i + TestUtils.randomInteger())
        .take(3);

    Thread.sleep(100);
    flux.subscribe(i -> logger.info(format("one:%s", i.toString())));
    Thread.sleep(100);
    flux.subscribe(i -> logger.info(format("two:%s", i.toString())));
  }

  @Test
  public void testHot() throws InterruptedException {
    UnicastProcessor<Integer> processor = UnicastProcessor.create();

    Flux<Integer> hot = processor.publish()
        .autoConnect()
        .log()
        .map(i -> i + TestUtils.randomInteger());

    processor.onNext(1);
    processor.onNext(2);

    Thread.sleep(100);
    hot.subscribe(i -> logger.info(format("one:%s", i.toString())));

    processor.onNext(3);
    processor.onNext(4);

    Thread.sleep(100);
    hot.subscribe(i -> logger.info(format("two:%s", i.toString())));

    processor.onNext(5);
    processor.onNext(6);

    Thread.sleep(100);
    processor.onComplete();
  }

  @Test
  public void testParallel() {
    Flux.range(1, 10)
        .parallel() // will do number of CPU cores
        .runOn(Schedulers.parallel())
        .subscribe(i -> logger.info(i.toString()));
  }

  @Test
  public void testChaining() {
    // this is wrong, since a flux is effectively immutable...
    Flux<String> flux1 = Flux.just("foo", "chain");
    flux1.map(secret -> secret.replaceAll(".", "*"));
    flux1.subscribe(s -> logger.info(format("one:%s", s)));

    // this is right - the .map() returns another flux which we repoint to...
    Flux<String> flux2 = Flux.just("foo", "chain");
    flux2 = flux2.map(secret -> secret.replaceAll(".", "*"));
    flux2.subscribe(s -> logger.info(format("two:%s", s)));

    // this is n alternative solution with a single chain...
    Flux.just("foo", "chain")
        .map(secret -> secret.replaceAll(".", "*"))
        .subscribe(s -> logger.info(format("two:%s", s)));
  }

  @Test
  public void testZip() {
    Flux<String> letters = Flux.just("a", "b", "c");
    Flux<Integer> numbers = Flux.just(1, 2, 3);

    numbers.zipWith(letters)
        .subscribe(t -> logger.info(format("%s,%s", t.getT1(), t.getT2())));
  }

  @Test
  public void testRequest() throws InterruptedException {
    LoggingSubscriber<Integer> subscriber = new LoggingSubscriber<>("John");
    Flux<Integer> flux = Flux.range(1, 10).log();
    flux.subscribe(subscriber);

    Thread.sleep(100);
    subscriber.request(1);
    subscriber.request(1);
    subscriber.request(1);

    Thread.sleep(100);
    subscriber.request(Long.MAX_VALUE);
  }

  @Test
  public void testWindow() throws Exception {
    Flux<Integer> flux = Flux.range(1, 10).log();

    flux
        .doOnNext(s -> logger.info("pre-window:{}", s))
        .window(3)
        .subscribe(s -> s.log().buffer().subscribe(t -> logger.info("post-window:{}", t)));
  }

  @Test
  public void testBuffer() throws Exception {
    Flux<Integer> flux = Flux.range(1, 10).log();

    flux
        .doOnNext(s -> logger.info("pre-buffer:{}", s))
        .buffer(3)
        .subscribe(t -> logger.info("post-buffer:{}", t));
  }

  @Test
  public void testGroupBy() {
    Flux<Integer> flux = Flux.range(1, 10).log();

    flux
        .groupBy(i -> i % 2)
        .subscribe(s -> s.buffer().subscribe(t -> logger.info("group:{}:{}", s.key(), t)));
  }

  @Test
  public void testSplit() throws InterruptedException {
    Flux<Integer> flux = Flux.range(1, 10).log();

    flux
        .flatMap(i -> Flux.just(i, i + 100))
        .subscribe(t -> logger.info("split:{}", t));

    // flux
    // .handle((i, sink) -> {
    // sink.next(i);
    // sink.next(i + 200);
    // })
    // .subscribe(t -> logger.info("split:{}", t));
  }

  @Test
  public void testSample() throws Exception {
    Flux<Long> flux = Flux.interval(Duration.ofMillis(10)).log();

    flux
        .sample(Duration.ofMillis(100))
        .subscribe(t -> logger.info("interval:{}", t));

    Thread.sleep(500);
  }

  @Test
  public void testParallelFlux() throws Exception {

    ParallelFlux<Integer> flux = Flux.range(0, 10)
        .parallel()
        .runOn(Schedulers.parallel()).log();

    // parallel flux DOES NOT make these subscribers work at the same time...
    flux.subscribe(i -> logger.info("one:{}", i));
    flux.subscribe(i -> logger.info("two:{}", i));

    Thread.sleep(1000);
  }

  @Test
  public void testConnectableFlux() throws Exception {
    Flux<Integer> flux = Flux.range(0, 5).log();

    ConnectableFlux<Integer> cflux = flux.publish();

    cflux.subscribe(i -> logger.info("one:{}", i));
    cflux.subscribe(i -> logger.info("two:{}", i));

    Thread.sleep(100);

    // it looks like it is working in parallel, but in fact, it is waiting for ALL subscribers to signal demand...
    cflux.connect();
  }

  @Test
  public void testBackpressureSerial() throws Exception {

    Flux<Integer> flux = Flux.range(0, 5).log();

    BaseSubscriber<Integer> slow = new DelaySubscriber<>("slow", 20);
    BaseSubscriber<Integer> fast = new DelaySubscriber<>("fast", 5);

    flux.subscribeWith(WorkQueueProcessor.create()).subscribe(slow);
    logger.info("slow done");
    flux.subscribeWith(fast);

    Thread.sleep(500);
  }

  @Test
  public void testConnectableFluxWithBackpressure() throws Exception {
    Flux<Integer> flux = Flux.range(0, 5).log();

    ConnectableFlux<Integer> cflux = flux.publish();

    BaseSubscriber<Integer> slow = new DelaySubscriber<>("slow", 20);
    BaseSubscriber<Integer> fast = new DelaySubscriber<>("fast", 5);

    cflux.subscribe(fast);
    cflux.subscribe(slow);

    Thread.sleep(100);
    cflux.connect();

    // notice how the fast waits for the slow - this is by design...
    Thread.sleep(500);

  }

  @Test
  public void testBackpressureHotStream() throws Exception {

    Flux<Long> flux = Flux.interval(Duration.ofMillis(10)).log();

    BaseSubscriber<Long> slow = new DelaySubscriber<>("slow", 20);
    BaseSubscriber<Long> fast = new DelaySubscriber<>("fast", 5);

    flux.subscribe(slow);
    flux.subscribe(fast);

    // this hot stream is interesting, since it does appear to let the subscribers work at the same time...
    Thread.sleep(500);
  }

  @Test
  public void testSubscribeWith() throws Exception {

    Flux<Integer> flux = Flux.range(0, 5).log();

    BaseSubscriber<Integer> slow = new DelaySubscriber<>("slow", 20);
    BaseSubscriber<Integer> fast = new DelaySubscriber<>("fast", 5);

    //
    flux.subscribeWith(WorkQueueProcessor.create("slow proc", 1)).subscribe(slow);
    flux.subscribeWith(WorkQueueProcessor.create("fast proc", 1)).subscribe(fast);

    Thread.sleep(500);
  }

  @Test
  public void testFlatMap() throws Exception {
    Flux<Integer> flux = Flux.range(0, 5).log();

    flux
        .flatMap(i -> Flux.just(i, i + 100))
        .subscribe(i -> logger.info(i.toString()));
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
  public void testGenerateBufferSort() {
    List<String> result = Flux.<String> just("c", "a", "b")
        .buffer(Duration.ofSeconds(2))
        .flatMap(l -> Flux.fromIterable(l).sort())
        .collectList().block();
    // .subscribe(m -> logger.info(m.toString()));

    logger.info(result.toString());
  }

  @Test
  public void testTransform() throws Exception {
    // reusable part os a flux processing chain...
    Function<Flux<Integer>, Flux<Integer>> gtFivePlusTen = f -> f
        .filter(i -> i > 5)
        .map(i -> i + 10);

    Flux<Integer> flux = Flux.range(1, 10);

    flux
        .transform(gtFivePlusTen)
        .subscribe(m -> logger.info(m.toString()));
  }

  @Test
  public void testCompose() throws Exception {

  }

}
