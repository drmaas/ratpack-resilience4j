package ratpack.resilience4j;

import io.github.robwin.circuitbreaker.CircuitBreaker;
import io.github.robwin.retry.Retry;
import ratpack.exec.Promise;
import ratpack.func.Function;


public class RatpackResilience {

  public static <T> Promise<T> breaker(Promise<T> promise, CircuitBreaker circuitBreaker) {
    return promise.transform(CircuitBreakerTransformer.of(circuitBreaker));
  }

  @SuppressWarnings("unchecked")
  public static <T> Promise<T> breakerRecover(Promise<T> promise, CircuitBreaker circuitBreaker, Function<Throwable, ? extends T> recoverer) {
    return promise.transform((CircuitBreakerTransformer<T>)CircuitBreakerTransformer.of(circuitBreaker).recover(recoverer));
  }

  public static <T> Promise<T> retry(Promise<T> promise, Retry retry) {
    return promise.transform(RetryTransformer.of(retry));
  }

  @SuppressWarnings("unchecked")
  public static <T> Promise<T> retryRecover(Promise<T> promise, Retry retry, Function<Throwable, ? extends T> recoverer) {
    return promise.transform((RetryTransformer<T>)RetryTransformer.of(retry).recover(recoverer));
  }

}
