package ratpack.resilience4j.internal;

import ratpack.resilience4j.RecoveryFunction;

public class DefaultRecoveryFunction<O> implements RecoveryFunction<O> {
  @Override
  public O apply(Throwable e) throws Exception {
    return null;
  }
}
