package com.back.nbe1214finalhot6.support.perf;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 작업 N개를 동시에 출발시키고 결과와 소요 시간을 모은다. 성능 측정(@Tag("perf")) 전용.
 *
 * <p>모든 스레드가 준비(ready)된 뒤 한 번에 출발(go)시켜 실제 경합이 나게 한다. 작업마다 결과 라벨(성공은 작업이 돌려준 문자열, 업무 예외는 에러 코드,
 * 그 밖의 예외는 예외 클래스 이름)과 소요 시간(ns)을 남긴다. 전체 제한 시간을 넘긴 작업은 {@link #TIMEOUT} 으로 기록하고 중단시킨다.
 */
public final class ConcurrentRunner {

  public static final String OK = "OK";
  public static final String TIMEOUT = "TIMEOUT";

  private ConcurrentRunner() {}

  /**
   * 동시에 실행할 작업 하나.
   *
   * @param group 결과를 따로 집계할 묶음 이름(예: pay, read)
   * @param startDelayMillis 출발 신호 뒤 이만큼 기다렸다 시작한다. 시간 측정은 기다린 뒤부터다
   * @param body 성공하면 결과 라벨을 돌려준다(보통 {@link #OK})
   */
  public record Task(String group, long startDelayMillis, Callable<String> body) {

    public static Task of(String group, Callable<String> body) {
      return new Task(group, 0, body);
    }

    public static Task delayed(String group, long startDelayMillis, Callable<String> body) {
      return new Task(group, startDelayMillis, body);
    }
  }

  /** 작업 1건의 결과. message 는 예외일 때 첫 메시지(원인 파악용)다. */
  public record Sample(String group, String outcome, long nanos, String message) {}

  public static RunResult run(List<Task> tasks, Duration timeout) {
    int n = tasks.size();
    ExecutorService executor = Executors.newFixedThreadPool(n);
    CountDownLatch ready = new CountDownLatch(n);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<Sample>> futures = new ArrayList<>(n);
      for (Task task : tasks) {
        futures.add(executor.submit(() -> execute(task, ready, go)));
      }
      if (!ready.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("스레드 준비가 30초 안에 끝나지 않았습니다");
      }
      long wallStart = System.nanoTime();
      long deadline = wallStart + timeout.toNanos();
      go.countDown();

      List<Sample> samples = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        samples.add(await(futures.get(i), tasks.get(i), deadline, wallStart));
      }
      return new RunResult(samples, System.nanoTime() - wallStart);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("측정 중 인터럽트", e);
    } finally {
      executor.shutdownNow();
    }
  }

  private static Sample execute(Task task, CountDownLatch ready, CountDownLatch go)
      throws InterruptedException {
    ready.countDown();
    go.await();
    if (task.startDelayMillis() > 0) {
      Thread.sleep(task.startDelayMillis());
    }
    long start = System.nanoTime();
    try {
      String outcome = task.body().call();
      return new Sample(task.group(), outcome == null ? OK : outcome, System.nanoTime() - start, null);
    } catch (BusinessException e) {
      return new Sample(
          task.group(), e.getErrorCode().getCode(), System.nanoTime() - start, e.getMessage());
    } catch (Exception e) {
      return new Sample(
          task.group(), e.getClass().getSimpleName(), System.nanoTime() - start, rootMessage(e));
    }
  }

  private static Sample await(Future<Sample> future, Task task, long deadline, long wallStart)
      throws InterruptedException {
    try {
      long remaining = Math.max(0, deadline - System.nanoTime());
      return future.get(remaining, TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      return new Sample(task.group(), TIMEOUT, System.nanoTime() - wallStart, "전체 제한 시간 초과");
    } catch (ExecutionException e) {
      return new Sample(task.group(), "HARNESS_ERROR", 0, rootMessage(e));
    }
  }

  private static String rootMessage(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getClass().getSimpleName() + ": " + root.getMessage();
    return message.length() > 300 ? message.substring(0, 300) : message;
  }
}
