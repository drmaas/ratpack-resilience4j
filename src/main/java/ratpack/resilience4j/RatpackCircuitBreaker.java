package ratpack.resilience4j;

import io.github.robwin.circuitbreaker.CircuitBreaker;
import ratpack.exec.Promise;
import ratpack.func.Function;


public class RatpackCircuitBreaker {

  public static <T> Promise<T> breaker(Promise<T> promise, CircuitBreaker circuitBreaker) {
    return promise.transform(CircuitBreakerTransformer.of(circuitBreaker));
  }

  @SuppressWarnings("unchecked")
  public static <T> Promise<T> breakerRecover(Promise<T> promise, CircuitBreaker circuitBreaker, Function<Throwable, ? extends T> recoverer) {
    return promise.transform((CircuitBreakerTransformer<T>)CircuitBreakerTransformer.of(circuitBreaker).recover(recoverer));
  }

}
