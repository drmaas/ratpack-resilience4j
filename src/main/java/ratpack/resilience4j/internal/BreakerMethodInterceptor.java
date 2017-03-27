package ratpack.resilience4j.internal;

import com.google.inject.Provider;
import io.github.robwin.circuitbreaker.CircuitBreaker;
import io.github.robwin.circuitbreaker.CircuitBreakerRegistry;
import javaslang.control.Try;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import ratpack.exec.Promise;
import ratpack.resilience4j.Breaker;
import ratpack.resilience4j.CircuitBreakerTransformer;
import ratpack.resilience4j.RecoveryFunction;

import javax.inject.Inject;

/**
 * A {@link MethodInterceptor} to handle all methods annotated with {@link ratpack.resilience4j.Breaker}. It will
 * handle methods that return a Promise only. It will add a transform to the promise with the circuit breaker and
 * fallback found in the annotation.
 */
public class BreakerMethodInterceptor implements MethodInterceptor {

  private final Provider<CircuitBreakerRegistry> provider;

  @Inject
  public BreakerMethodInterceptor(Provider<CircuitBreakerRegistry> provider) {
    this.provider = provider;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Object result = invocation.proceed();
    Breaker annotation = invocation.getMethod().getAnnotation(Breaker.class);
    CircuitBreaker breaker = provider.get().circuitBreaker(annotation.name());
    if (breaker == null) {
      return result;
    }
    if (result instanceof Promise<?>) {
      CircuitBreakerTransformer transformer = CircuitBreakerTransformer.of(breaker);
      if (!annotation.recovery().isAssignableFrom(DefaultRecoveryFunction.class)) {
        transformer = transformer.recover(annotation.recovery().newInstance());
      }
      result = ((Promise<?>) result).transform(transformer);
    } else {
      Object supplied = result;
      Try.CheckedSupplier<Object> supplier = CircuitBreaker.decorateCheckedSupplier(breaker, () -> supplied);
      RecoveryFunction<?> function = annotation.recovery().newInstance();
      Try<Object> recovered = Try.of(supplier).recover(throwable -> {
        try {
          return function.apply(throwable);
        } catch (Exception e) {
          return null;
        }
      });
      return recovered.get();
    }
    return result;
  }

}
