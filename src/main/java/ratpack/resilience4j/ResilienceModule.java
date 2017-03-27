package ratpack.resilience4j;

import com.google.inject.matcher.Matchers;
import io.github.robwin.circuitbreaker.CircuitBreakerRegistry;
import ratpack.guice.ConfigurableModule;
import ratpack.resilience4j.internal.BreakerMethodInterceptor;

// TODO need to create a reporter that reports metrics to dropwizard every X seconds asynchronously.
public class ResilienceModule extends ConfigurableModule<ResilienceModule.ResilienceConfig> {

  @Override
  protected void configure() {
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(Breaker.class), injected(new BreakerMethodInterceptor(getProvider(CircuitBreakerRegistry.class))));
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
