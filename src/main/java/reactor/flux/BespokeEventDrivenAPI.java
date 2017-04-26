package reactor.flux;

import reactor.core.publisher.FluxSink;

public class BespokeEventDrivenAPI {

  private FluxSink<String> sink;

  public BespokeEventDrivenAPI() {
  }

  public BespokeEventDrivenAPI notify(FluxSink<String> sink) {
    this.setSink(sink);
    return this;
  }

  public void onMessage(String message) {
    sink.next(message);
  }

  public void setSink(FluxSink<String> sink) {
    this.sink = sink;
  }

}
