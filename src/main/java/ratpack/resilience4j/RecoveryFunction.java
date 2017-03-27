package ratpack.resilience4j;

import ratpack.func.Function;

public interface RecoveryFunction<O> extends Function<Throwable, O> {
}
