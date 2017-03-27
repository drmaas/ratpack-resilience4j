package ratpack.resilience4j.internal;

import com.google.inject.Provider;
import io.github.robwin.circuitbreaker.CircuitBreaker;
import io.github.robwin.circuitbreaker.CircuitBreakerOpenException;
import io.github.robwin.circuitbreaker.CircuitBreakerRegistry;
import io.github.robwin.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.robwin.metrics.StopWatch;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import javaslang.control.Try;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import ratpack.exec.Promise;
import ratpack.resilience4j.Breaker;
import ratpack.resilience4j.CircuitBreakerTransformer;
import ratpack.resilience4j.RecoveryFunction;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    RecoveryFunction<?> recoveryFunction = annotation.recovery().newInstance();
    if (result instanceof Promise<?>) {
      CircuitBreakerTransformer transformer = CircuitBreakerTransformer.of(breaker);
      if (!annotation.recovery().isAssignableFrom(DefaultRecoveryFunction.class)) {
        transformer = transformer.recover(recoveryFunction);
      }
      result = ((Promise<?>) result).transform(transformer);
    } else if (result instanceof Observable) {
      CircuitBreakerOperator operator = CircuitBreakerOperator.of(breaker);
      result = ((Observable<?>) result).lift(operator).onErrorReturn(t -> recoveryFunction.apply((Throwable) t));
    } else if (result instanceof Flowable) {
      CircuitBreakerOperator operator = CircuitBreakerOperator.of(breaker);
      result = ((Flowable<?>) result).lift(operator).onErrorReturn(t -> recoveryFunction.apply((Throwable) t));
    } else if (result instanceof CompletionStage) {
      CompletionStage stage = (CompletionStage) result;
      StopWatch stopWatch;
      if (breaker.isCallPermitted()) {
        stopWatch = StopWatch.start(breaker.getName());
        return stage.handle((v, t) -> {
          Duration d = stopWatch.stop().getProcessingDuration();
          if (t != null) {
            breaker.onError(d, (Throwable) t);
            try {
              return recoveryFunction.apply((Throwable) t);
            } catch (Exception e) {
              return v;
            }
          } else if (v != null) {
            breaker.onSuccess(d);
          }
          return v;
        });
      } else {
        return CompletableFuture.supplyAsync(() -> {
          Throwable t = new CircuitBreakerOpenException("CircuitBreaker ${circuitBreaker.name} is open");
          try {
            return recoveryFunction.apply((Throwable) t);
          } catch (Throwable t2) {
            return null;
          }
        });
      }
    } else {
      Object supplied = result;
      Try.CheckedSupplier<Object> supplier = CircuitBreaker.decorateCheckedSupplier(breaker, () -> supplied);
      Try<Object> recovered = Try.of(supplier).recover(throwable -> {
        try {
          return recoveryFunction.apply(throwable);
        } catch (Exception e) {
          return null;
        }
      });
      return recovered.get();
    }
    return result;
  }

}
