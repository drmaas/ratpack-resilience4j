package ratpack.resilience4j;

import com.google.inject.matcher.Matchers;
import io.github.robwin.circuitbreaker.CircuitBreakerRegistry;
import io.github.robwin.ratelimiter.RateLimiterRegistry;
import ratpack.guice.ConfigurableModule;
import ratpack.resilience4j.internal.CircuitBreakerMethodInterceptor;
import ratpack.resilience4j.internal.RateLimiterMethodInterceptor;

public class ResilienceModule extends ConfigurableModule<ResilienceModule.ResilienceConfig> {

  @Override
  protected void configure() {
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(CircuitBreak.class), injected(new CircuitBreakerMethodInterceptor(getProvider(CircuitBreakerRegistry.class))));
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(RateLimit.class), injected(new RateLimiterMethodInterceptor(getProvider(RateLimiterRegistry.class))));
  }

  private <T> T injected(T instance) {
    requestInjection(instance);
    return instance;
  }

  public static class ResilienceConfig {
    boolean enableMetrics = false;

    public ResilienceConfig enableMetrics(boolean enableMetrics) {
      this.enableMetrics = enableMetrics;
      return this;
    }

  }
}
