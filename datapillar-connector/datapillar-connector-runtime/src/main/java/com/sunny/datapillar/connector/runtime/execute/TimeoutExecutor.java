package com.sunny.datapillar.connector.runtime.execute;

import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Timeout executor used by kernel. */
public class TimeoutExecutor {

  private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

  public <T> T execute(Duration timeout, Supplier<T> action) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      return action.get();
    }

    Future<T> future = executorService.submit(action::get);
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timeoutException) {
      future.cancel(true);
      throw new ServiceUnavailableException(timeoutException, "Connector invocation timeout");
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new ServiceUnavailableException(
          interruptedException, "Connector invocation interrupted");
    } catch (ExecutionException executionException) {
      Throwable cause = executionException.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new ServiceUnavailableException(cause, "Connector invocation execution failed");
    }
  }

  @PreDestroy
  public void shutdown() {
    executorService.shutdownNow();
  }
}
