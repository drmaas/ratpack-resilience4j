package ratpack.resilience4j;

import io.github.robwin.circuitbreaker.CircuitBreaker;
import io.github.robwin.circuitbreaker.CircuitBreakerOpenException;
import io.github.robwin.metrics.StopWatch;
import ratpack.exec.Downstream;
import ratpack.exec.Upstream;
import ratpack.func.Function;

public class CircuitBreakerTransformer<T> implements Function<Upstream<? extends T>, Upstream<T>> {

  private final CircuitBreaker circuitBreaker;
  private Function<Throwable, ? extends T> recoverer;

  private CircuitBreakerTransformer(CircuitBreaker circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  /**
   * Create a new transformer that can be applied to the {@link ratpack.exec.Promise#transform(Function)} method.
   * The Promised value will pass through the circuitbreaker, potentially causing it to open if the thresholds
   * for the circuit breaker are exceeded.
   *
   * @param circuitBreaker the circuit breaker to use
   * @return
   */
  public static <T> CircuitBreakerTransformer<T> of(CircuitBreaker circuitBreaker) {
    return new CircuitBreakerTransformer<>(circuitBreaker);
  }

  /**
   * Set a recovery function that will execute when the circuit breaker is open.
   *
   * @param recoverer the recovery function
   * @return
   */
  public CircuitBreakerTransformer<T> recover(Function<Throwable, ? extends T> recoverer) {
    this.recoverer = recoverer;
    return this;
  }

  @Override
  public Upstream<T> apply(Upstream<? extends T> upstream) throws Exception {
    return down -> {
      StopWatch stopWatch;
      if (circuitBreaker.isCallPermitted()) {
        stopWatch = StopWatch.start(circuitBreaker.getName());
        upstream.connect(new Downstream<T>() {

          @Override
          public void success(T value) {
            circuitBreaker.onSuccess(stopWatch.stop().getProcessingDuration());
            down.success(value);
          }

          @Override
          public void error(Throwable throwable) {
            circuitBreaker.onError(stopWatch.stop().getProcessingDuration(), throwable);
            try {
              if (recoverer != null) {
                down.success(recoverer.apply(throwable));
              } else {
                down.error(throwable);
              }
            } catch (Throwable t) {
              down.error(t);
            }
          }

          @Override
          public void complete() {
            down.complete();
          }
        });
      } else {
        Throwable t = new CircuitBreakerOpenException("CircuitBreaker ${circuitBreaker.name} is open");
        if (recoverer != null) {
          try {
            down.success(recoverer.apply(t));
          } catch (Throwable t2) {
            down.error(t2);
          }
        } else {
          down.error(t);
        }
      }
    };
  }

}
