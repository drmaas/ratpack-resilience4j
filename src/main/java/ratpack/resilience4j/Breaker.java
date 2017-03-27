package ratpack.resilience4j;

import ratpack.resilience4j.internal.DefaultRecoveryFunction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for marking a method of an annotated object as circuit breaker enabled.
 * <p/>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}Breaker(name = "myCircuitBreaker")
 *     public String fancyName(String name) {
 *         return "Sir Captain " + name;
 *     }
 * </code></pre>
 * <p/>
 * each time the {@code #fancyName(String)} method is invoked, the method's execution will pass through a
 * circuit breaker according to the given circuit breaker policy.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
public @interface Breaker {
  /**
   * @return The name of the circuit breaker. It will be looked up the circuit breaker registry.
   */
  String name() default "";

  /**
   * The Function class that returns a fallback value. The default is a noop.
   * @return
   */
  Class<? extends RecoveryFunction> recovery() default DefaultRecoveryFunction.class;

}
