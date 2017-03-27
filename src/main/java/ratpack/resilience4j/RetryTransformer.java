package ratpack.resilience4j;

import io.github.robwin.retry.Retry;
import ratpack.exec.Downstream;
import ratpack.exec.Upstream;
import ratpack.func.Function;

public class RetryTransformer<T> implements Function<Upstream<? extends T>, Upstream<T>> {

  private final Retry retry;
  private Function<Throwable, ? extends T> recoverer;

  private RetryTransformer(Retry retry) {
    this.retry = retry;
  }

  /**
   * Create a new transformer that can be applied to the {@link ratpack.exec.Promise#transform(Function)} method.
   * The Promised value will pass through the retry, potentially causing it to retry on error.
   *
   * @param retry the retry to use
   * @return
   */
  public static <T> RetryTransformer<T> of(Retry retry) {
    return new RetryTransformer<>(retry);
  }

  /**
   * Set a recovery function that will execute when the retry limit is exceeded.
   *
   * @param recoverer the recovery function
   * @return
   */
  public RetryTransformer<T> recover(Function<Throwable, ? extends T> recoverer) {
    this.recoverer = recoverer;
    return this;
  }

  @Override
  public Upstream<T> apply(Upstream<? extends T> upstream) throws Exception {
    return down -> {
      Downstream<T> downstream = new Downstream<T>() {

        @Override
        public void success(T value) {
          retry.onSuccess();
          down.success(value);
        }

        @Override
        public void error(Throwable throwable) {
          try {
            retry.onError((Exception) throwable);
            upstream.connect(this);
          } catch (Throwable t) {
            if (recoverer != null) {
              try {
                down.success(recoverer.apply(t));
              } catch (Throwable t2) {
                down.error(t2);
              }
            } else {
              down.error(t);
            }
          }
        }

        @Override
        public void complete() {
          down.complete();
        }
      };
      upstream.connect(downstream);
    };
  }

}
