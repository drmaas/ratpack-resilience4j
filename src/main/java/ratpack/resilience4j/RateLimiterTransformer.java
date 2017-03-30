package ratpack.resilience4j;

import io.github.robwin.ratelimiter.RateLimiter;
import io.github.robwin.ratelimiter.RateLimiterConfig;
import io.github.robwin.ratelimiter.RequestNotPermitted;
import ratpack.exec.Downstream;
import ratpack.exec.Upstream;
import ratpack.func.Function;

import java.time.Duration;

public class RateLimiterTransformer<T> implements Function<Upstream<? extends T>, Upstream<T>> {

  private final RateLimiter rateLimiter;
  private Function<Throwable, ? extends T> recover;

  private RateLimiterTransformer(RateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  /**
   * Create a new transformer that can be applied to the {@link ratpack.exec.Promise#transform(Function)} method.
   * The Promised value will pass through the rateLimiter, potentially causing it to rateLimiter on error.
   *
   * @param rateLimiter the rateLimiter to use
   * @return
   */
  public static <T> RateLimiterTransformer<T> of(RateLimiter rateLimiter) {
    return new RateLimiterTransformer<>(rateLimiter);
  }

  /**
   * Set a recovery function that will execute when the rateLimiter limit is exceeded.
   *
   * @param recover the recovery function
   * @return
   */
  public RateLimiterTransformer<T> recover(Function<Throwable, ? extends T> recover) {
    this.recover = recover;
    return this;
  }

  @Override
  public Upstream<T> apply(Upstream<? extends T> upstream) throws Exception {
    return down -> {
      RateLimiterConfig rateLimiterConfig = rateLimiter.getRateLimiterConfig();
      Duration timeoutDuration = rateLimiterConfig.getTimeoutDuration();
      boolean permission = rateLimiter.getPermission(timeoutDuration);
      if (Thread.interrupted()) {
        throw new IllegalStateException("Thread was interrupted during permission wait");
      }
      if (!permission) {
        Throwable t = new RequestNotPermitted("Request not permitted for limiter: " + rateLimiter.getName());
        if (recover != null) {
          down.success(recover.apply(t));
        } else {
          down.error(t);
        }
      } else {
        upstream.connect(new Downstream<T>() {

          @Override
          public void success(T value) {
            down.success(value);
          }

          @Override
          public void error(Throwable throwable) {
            down.error(throwable);
          }

          @Override
          public void complete() {
            down.complete();
          }
        });
      }
    };
  }

}
