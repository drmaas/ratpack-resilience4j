package ratpack.resilience4j.internal;

import com.google.inject.Provider;
import io.github.robwin.circuitbreaker.CircuitBreakerRegistry;

import javax.inject.Inject;

public class CircuitBreakerProvider implements Provider<CircuitBreakerRegistry> {

  private final CircuitBreakerRegistry registry;

  @Inject
  public CircuitBreakerProvider(CircuitBreakerRegistry registry) {
    this.registry = registry;
  }

  @Override
  public CircuitBreakerRegistry get() {
    return registry;
  }

}
