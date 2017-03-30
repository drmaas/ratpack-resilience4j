package ratpack.resilience4j.internal;

import com.google.inject.Provider;
import io.github.robwin.ratelimiter.RateLimiter;
import io.github.robwin.ratelimiter.RateLimiterConfig;
import io.github.robwin.ratelimiter.RateLimiterRegistry;
import io.github.robwin.ratelimiter.RequestNotPermitted;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import ratpack.exec.Promise;
import ratpack.resilience4j.RateLimit;
import ratpack.resilience4j.RateLimiterTransformer;
import ratpack.resilience4j.RecoveryFunction;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A {@link MethodInterceptor} to handle all methods annotated with {@link RateLimit}. It will
 * handle methods that return a Promise only. It will add a transform to the promise with the circuit breaker and
 * fallback found in the annotation.
 */
public class RateLimiterMethodInterceptor implements MethodInterceptor {

  private final Provider<RateLimiterRegistry> provider;

  @Inject
  public RateLimiterMethodInterceptor(Provider<RateLimiterRegistry> provider) {
    this.provider = provider;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    RateLimit annotation = invocation.getMethod().getAnnotation(RateLimit.class);
    RecoveryFunction<?> recoveryFunction = annotation.recovery().newInstance();
    RateLimiter rateLimiter = provider.get().rateLimiter(annotation.name());
    if (rateLimiter == null) {
      return invocation.proceed();
    }
    Object result;
    try {
      result = invocation.proceed();
    } catch (Exception e) {
      RateLimiterConfig rateLimiterConfig = rateLimiter.getRateLimiterConfig();
      Duration timeoutDuration = rateLimiterConfig.getTimeoutDuration();
      boolean permission = rateLimiter.getPermission(timeoutDuration);
      if (Thread.interrupted()) {
        throw new IllegalStateException("Thread was interrupted during permission wait");
      }
      if (!permission) {
        throw new RequestNotPermitted("Request not permitted for limiter: " + rateLimiter.getName());
      } else {
        throw e;
      }
    }
    if (result instanceof Promise<?>) {
      RateLimiterTransformer transformer = RateLimiterTransformer.of(rateLimiter);
      if (!annotation.recovery().isAssignableFrom(DefaultRecoveryFunction.class)) {
        transformer = transformer.recover(recoveryFunction);
      }
      result = ((Promise<?>) result).transform(transformer);
    } else if (result instanceof CompletionStage) {
      CompletionStage stage = (CompletionStage) result;
      RateLimiterConfig rateLimiterConfig = rateLimiter.getRateLimiterConfig();
      Duration timeoutDuration = rateLimiterConfig.getTimeoutDuration();
      boolean permission = rateLimiter.getPermission(timeoutDuration);
      if (permission) {
        return stage;
      } else {
        return CompletableFuture.supplyAsync(() -> {
          throw new RequestNotPermitted("Request not permitted for limiter: " + rateLimiter.getName());
        });
      }
    } else {
      RateLimiter.waitForPermission(rateLimiter);
    }
    return result;
  }

}
