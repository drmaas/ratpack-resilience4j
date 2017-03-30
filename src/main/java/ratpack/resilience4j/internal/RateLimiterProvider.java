package ratpack.resilience4j.internal;

import com.google.inject.Provider;
import io.github.robwin.ratelimiter.RateLimiterRegistry;

import javax.inject.Inject;

public class RateLimiterProvider implements Provider<RateLimiterRegistry> {

  private final RateLimiterRegistry registry;

  @Inject
  public RateLimiterProvider(RateLimiterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public RateLimiterRegistry get() {
    return registry;
  }

}
