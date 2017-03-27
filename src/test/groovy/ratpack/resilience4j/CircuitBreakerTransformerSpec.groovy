package ratpack.resilience4j

import io.github.robwin.circuitbreaker.CircuitBreaker
import io.github.robwin.circuitbreaker.CircuitBreakerConfig
import ratpack.exec.Blocking
import ratpack.test.exec.ExecHarness
import spock.lang.Specification

import java.time.Duration

class CircuitBreakerTransformerSpec extends Specification {

  def "can circuit break promise to open state"() {
    given:
    CircuitBreaker breaker = buildBreaker()
    CircuitBreakerTransformer<String> transformer = CircuitBreakerTransformer.of(breaker)
    Exception e = new Exception("puke")

    when:
    def r = ExecHarness.yieldSingle {
      Blocking.<String>get { throw e }
        .transform(transformer)
    }

    then:
    r.value == null
    r.error
    r.throwable == e
    breaker.state == CircuitBreaker.State.CLOSED

    when:
    r = ExecHarness.yieldSingle {
      Blocking.<String>get { throw e }
        .transform(transformer)
    }

    then:
    r.value == null
    r.error
    r.throwable == e
    breaker.state == CircuitBreaker.State.OPEN
  }

  def "can circuit break promise to open state with recovery"() {
    given:
    CircuitBreaker breaker = buildBreaker()
    CircuitBreakerTransformer<String> transformer = CircuitBreakerTransformer.of(breaker).recover { t -> "bar" }
    Exception e = new Exception("puke")

    when:
    def r = ExecHarness.yieldSingle {
      Blocking.<String>get { throw e }
        .transform(transformer)
    }

    then:
    r.value == "bar"
    !r.error
    r.throwable == null
    breaker.state == CircuitBreaker.State.CLOSED

    when:
    r = ExecHarness.yieldSingle {
      Blocking.<String>get { throw e }
        .transform(transformer)
    }

    then:
    r.value == "bar"
    !r.error
    r.throwable == null
    breaker.state == CircuitBreaker.State.OPEN
  }

  def "can circuit break promise to from closed to open, then half open, then closed"() {
    given:
    CircuitBreaker breaker = buildBreaker()
    CircuitBreakerTransformer<String> transformer = CircuitBreakerTransformer.of(breaker)
    Exception e = new Exception("puke")

    when:
    def r = ExecHarness.yieldSingle {
      Blocking.<String>get { throw e }
        .transform(transformer)
    }

    then:
    r.value == null
    r.error
    r.throwable == e
    breaker.state == CircuitBreaker.State.CLOSED

    when:
    r = ExecHarness.yieldSingle {
      Blocking.<String>get { throw e }
        .transform(transformer)
    }

    then:
    r.value == null
    r.error
    r.throwable == e
    breaker.state == CircuitBreaker.State.OPEN

    when:
    sleep 1000
    r = ExecHarness.yieldSingle {
      Blocking.<String>get {"foo" }
        .transform(transformer)
    }

    then:
    r.value == "foo"
    !r.error
    r.throwable == null
    breaker.state == CircuitBreaker.State.HALF_OPEN

    when:
    r = ExecHarness.yieldSingle {
      Blocking.<String>get {"foo" }
        .transform(transformer)
    }

    then:
    r.value == "foo"
    !r.error
    r.throwable == null
    breaker.state == CircuitBreaker.State.CLOSED
  }

  def buildBreaker() {
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
      .failureRateThreshold(50)
      .waitDurationInOpenState(Duration.ofMillis(1000))
      .ringBufferSizeInHalfOpenState(2)
      .ringBufferSizeInClosedState(2)
      .build()
    CircuitBreaker.of("test", config)
  }

}
