package ratpack.resilience4j

import io.github.robwin.ratelimiter.RateLimiter
import io.github.robwin.ratelimiter.RateLimiterConfig
import ratpack.exec.Blocking
import ratpack.test.exec.ExecHarness
import spock.lang.Specification

import java.time.Duration

class RatelimiterTransformerSpec extends Specification {

  def "can ratelimit promise then throw exception when limit is exceeded"() {
    given:
    RateLimiter rateLimiter = buildRatelimiter()
    RateLimiterTransformer<Integer> transformer = RateLimiterTransformer.of(rateLimiter)
    Set<Integer> values = [].toSet()
    Set<Integer> expected = (0..99).toSet()

    when:
    for (int i = 0 ; i <= 100; i++) {
      def r =  ExecHarness.yieldSingle {
        Blocking.<Integer> get {
          i
        }.transform(transformer)
      }
      if (r.success) values << r.value
    }

    then:
    values == expected
  }

  def "can ratelimit promise then throw exception when limit is exceeded then call onFailure"() {
    given:
    String failure = "failure"
    RateLimiter rateLimiter = buildRatelimiter()
    RateLimiterTransformer<Integer> transformer = RateLimiterTransformer.of(rateLimiter).recover { t -> failure }
    Set<Integer> values = [].toSet()
    Set<Integer> expected = (0..99).toSet()

    when:
    for (int i = 0 ; i <= 100; i++) {
      def r =  ExecHarness.yieldSingle {
        Blocking.<Integer> get {
          i
        }.transform(transformer)
      }
      if (r.success) values << r.value
    }

    then:
    values == expected << failure
  }

  // 100 events / s
  def buildRatelimiter() {
    RateLimiterConfig config = RateLimiterConfig.custom()
      .limitRefreshPeriod(Duration.ofSeconds(1))
      .limitForPeriod(100)
      .timeoutDuration(Duration.ofNanos(1))
      .build()
    RateLimiter.of("test", config)
  }
}
