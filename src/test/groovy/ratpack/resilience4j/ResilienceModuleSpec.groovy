package ratpack.resilience4j

import io.github.robwin.circuitbreaker.CircuitBreaker
import io.github.robwin.circuitbreaker.CircuitBreakerConfig
import io.github.robwin.circuitbreaker.CircuitBreakerRegistry
import ratpack.exec.Promise
import ratpack.test.embed.EmbeddedApp
import ratpack.test.http.TestHttpClient
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

@Unroll
class ResilienceModuleSpec extends Specification {

  @AutoCleanup
  EmbeddedApp app

  @Delegate
  TestHttpClient client

  def "test circuit break a method via annotation with fallback"() {
    given:
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(buildConfig())
    app = ratpack {
      bindings {
        bindInstance(CircuitBreakerRegistry, registry)
        bind(Something)
        module(ResilienceModule)
      }
      handlers {
        get('promise') { Something something ->
          something.breakerPromise().then {
            render it
          }
        }
        get('promiseBad') { Something something ->
          something.breakerPromiseBad().then {
            render it
          }
        }
        get('promiseRecover') { Something something ->
          something.breakerPromiseRecovery().then {
            render it
          }
        }
      }
    }
    client = testHttpClient(app)
    def breaker = registry.circuitBreaker(breakerName)

    when:
    def actual = get(path)

    then:
    actual.body.text == expectedText
    actual.statusCode == 200
    breaker.callPermitted
    breaker.state == CircuitBreaker.State.CLOSED

    when:
    get(badPath)
    actual = get(badPath)

    then:
    actual.statusCode == 500
    !breaker.callPermitted
    breaker.state == CircuitBreaker.State.OPEN

    when:
    get(recoverPath)
    actual = get(recoverPath)

    then:
    actual.body.text == "recovered"
    actual.statusCode == 200
    !breaker.callPermitted
    breaker.state == CircuitBreaker.State.OPEN

    where:
    path      | badPath      | recoverPath      | breakerName | expectedText
    'promise' | 'promiseBad' | 'promiseRecover' | 'test'      | 'breaker promise'
  }

  def buildConfig() {
    CircuitBreakerConfig.custom()
      .failureRateThreshold(50)
      .waitDurationInOpenState(Duration.ofMillis(1000))
      .ringBufferSizeInHalfOpenState(2)
      .ringBufferSizeInClosedState(2)
      .build()
  }

  static class Something {

    @Breaker(name = "test")
    Promise<String> breakerPromise() {
      Promise.async {
        it.success("breaker promise")
      }
    }

    @Breaker(name = "test")
    Promise<String> breakerPromiseBad() {
      Promise.async {
        it.error(new Exception("breaker promise bad"))
      }
    }

    @Breaker(name = "test", recovery = MyRecoveryFunction)
    Promise<String> breakerPromiseRecovery() {
      Promise.async {
        it.error(new Exception("breaker promise bad"))
      }
    }
  }

  static class MyRecoveryFunction implements RecoveryFunction<String> {
    @Override
    String apply(Throwable t) throws Exception {
      "recovered"
    }
  }

}
