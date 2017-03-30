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
import ratpack.resilience4j.CircuitBreak;
import ratpack.resilience4j.CircuitBreakerTransformer;
import ratpack.resilience4j.RecoveryFunction;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A {@link MethodInterceptor} to handle all methods annotated with {@link CircuitBreak}. It will
 * handle methods that return a Promise only. It will add a transform to the promise with the circuit breaker and
 * fallback found in the annotation.
 */
public class CircuitBreakerMethodInterceptor implements MethodInterceptor {

  private final Provider<CircuitBreakerRegistry> provider;

  @Inject
  public CircuitBreakerMethodInterceptor(Provider<CircuitBreakerRegistry> provider) {
    this.provider = provider;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    CircuitBreak annotation = invocation.getMethod().getAnnotation(CircuitBreak.class);
    CircuitBreaker breaker = provider.get().circuitBreaker(annotation.name());
    if (breaker == null) {
      return invocation.proceed();
    }
    RecoveryFunction<?> recoveryFunction = annotation.recovery().newInstance();
    Object result;
    StopWatch stopWatchOuter = StopWatch.start(breaker.getName());
    try {
      result = invocation.proceed();
    } catch (Exception e) {
      breaker.onError(stopWatchOuter.getProcessingDuration(), e);
      return recoveryFunction.apply((Throwable) e);
    }
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
    }
    return result;
  }

}
