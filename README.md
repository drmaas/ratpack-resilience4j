# MOVED

This project is now hosted by resilience4j. https://github.com/resilience4j/resilience4j

# ratpack-resilience4j
Ratpack support for resilience4j. https://github.com/resilience4j/resilience4j

[![Build Status](https://travis-ci.org/drmaas/ratpack-rx2.svg?branch=master)](https://travis-ci.org/drmaas/ratpack-resilience4j)

[![forthebadge](https://forthebadge.com/images/badges/uses-badges.svg)](https://forthebadge.com)

## Gradle
```
compile 'me.drmaas:ratpack-resilience4j:x.x.x'
```

## Maven
```
<dependency>
    <groupId>me.drmaas</groupId>
    <artifactId>ratpack-resilience4j</artifactId>
    <version>x.x.x/version>
</dependency>
```

## Examples

### Circuit break promises
```
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
      .failureRateThreshold(50)
      .waitDurationInOpenState(Duration.ofMillis(1000))
      .ringBufferSizeInHalfOpenState(2)
      .ringBufferSizeInClosedState(2)
      .build()
    CircuitBreaker breaker = CircuitBreaker.of("test", config)
    
    CircuitBreakerTransformer<String> transformer = CircuitBreakerTransformer.of(breaker).recover { t -> "bar" }
    
    def r = ExecHarness.yieldSingle {
      Blocking.<String>get { 
        throw new Exception("test")
      }.transform(transformer)
    }
    r.value == "bar"
    breaker.state == CircuitBreaker.State.CLOSED
    
    r = ExecHarness.yieldSingle {
      Blocking.<String>get { 
        throw e 
      }.transform(transformer)
    }
    r.value == "bar"
    breaker.state == CircuitBreaker.State.OPEN
```

### Retry promises
```
    RetryConfig config = RetryConfig.custom()
      .maxAttempts(3)
      .waitDuration(Duration.ofMillis(500))
      .build()
    Retry rate = Retry.of("test", config)
    RetryTransformer<String> transformer = RetryTransformer.of(rate).recover { t -> "bar" }
    AtomicInteger times = new AtomicInteger(0)

    when:
    def r = ExecHarness.yieldSingle {
      Blocking.<String>get { 
        times.getAndIncrement()
        throw new Exception("test")
      }.transform(transformer)
    }

    then:
    r.value == "bar"
    times.get() == 3
```

### Circuit breaker annotations
Simply set up the circuit breaker registry and register your circuit breakers before binding the registry.
The annotation will look up your circuit breaker by name.

The annotation is tested for Promise, Observable, Flowable, CompletionStage, and generic Object types.
```
ratpack {
  bindings {
    bindInstance(CircuitBreakerRegistry, someRegistry)
    bind(Something)
    module(ResilienceModule)
  } 
  handlers {
    get('promise') { Something something ->
      something.breakerPromise().then {
        render it
      }
    }
  }
}

class Something {

  @Breaker(name = "test", recovery = MyRecoveryFunction)
  Promise<String> breakerPromise() {
    Promise.async {
      it.error(new Exception("grrr"))
    }
  }
  
}

class MyRecoveryFunction implements RecoveryFunction<String> {
  @Override
  String apply(Throwable t) throws Exception {
    "recovered"
  }
}
```


