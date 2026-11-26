package com.back.nbe1214finalhot6.support.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner.Sample;
import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner.Task;
import com.back.nbe1214finalhot6.support.perf.PerfReport.Environment;
import com.back.nbe1214finalhot6.support.perf.RunResult.Latency;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 측정 하네스 자체가 맞게 재는지 확인한다. DB·스프링 없이 가짜 작업으로 돌리며, 일반 테스트에 포함된다(1초 안팎).
 *
 * <p>하네스가 틀리면 모든 시나리오의 수치가 함께 틀리므로, 시나리오보다 먼저 이것부터 확인한다.
 */
class PerfHarnessTest {

  @Test
  @DisplayName("N개 작업이 실제로 동시에 실행된다 (N명이 모여야 열리는 장벽을 통과)")
  void 동시_출발() {
    int n = 20;
    CyclicBarrier barrier = new CyclicBarrier(n);
    List<Task> tasks =
        IntStream.range(0, n)
            .mapToObj(
                i ->
                    Task.of(
                        "t",
                        () -> {
                          barrier.await(2, TimeUnit.SECONDS); // 하나라도 늦게 출발하면 2초 뒤 깨진다
                          return ConcurrentRunner.OK;
                        }))
            .toList();

    RunResult result = ConcurrentRunner.run(tasks, Duration.ofSeconds(5));

    assertThat(result.counts()).containsExactly(Map.entry(ConcurrentRunner.OK, (long) n));
  }

  @Test
  @DisplayName("결과 라벨: 성공은 돌려준 값, 업무 예외는 에러 코드, 그 밖의 예외는 클래스 이름과 메시지")
  void 결과_라벨() {
    List<Task> tasks =
        List.of(
            Task.of("a", () -> ConcurrentRunner.OK),
            Task.of("a", () -> "CUSTOM"),
            Task.of(
                "b",
                () -> {
                  throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                }),
            Task.of(
                "b",
                () -> {
                  throw new IllegalStateException("터짐");
                }));

    RunResult result = ConcurrentRunner.run(tasks, Duration.ofSeconds(5));

    assertThat(result.counts("a")).containsOnlyKeys(ConcurrentRunner.OK, "CUSTOM");
    assertThat(result.counts("b"))
        .containsOnlyKeys(CommonErrorCode.INVALID_REQUEST.getCode(), "IllegalStateException");
    assertThat(result.exampleMessages().get("IllegalStateException")).contains("터짐");
    assertThat(result.groups()).containsExactly("a", "b");
  }

  @Test
  @DisplayName("전체 제한 시간을 넘긴 작업은 TIMEOUT 으로 기록하고 기다리지 않는다")
  void 제한_시간_초과() {
    List<Task> tasks =
        List.of(
            Task.of("fast", () -> ConcurrentRunner.OK),
            Task.of(
                "slow",
                () -> {
                  Thread.sleep(10_000);
                  return ConcurrentRunner.OK;
                }));

    long start = System.nanoTime();
    RunResult result = ConcurrentRunner.run(tasks, Duration.ofMillis(300));
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(result.counts("fast")).containsOnlyKeys(ConcurrentRunner.OK);
    assertThat(result.counts("slow")).containsOnlyKeys(ConcurrentRunner.TIMEOUT);
    assertThat(elapsedMs).isLessThan(3_000); // 10초짜리 작업을 끝까지 기다리지 않는다
  }

  @Test
  @DisplayName("늦게 출발한 작업의 소요 시간에는 기다린 시간이 들어가지 않는다")
  void 지연_출발() {
    List<Task> tasks = List.of(Task.delayed("late", 300, () -> ConcurrentRunner.OK));

    RunResult result = ConcurrentRunner.run(tasks, Duration.ofSeconds(5));

    assertThat(result.latency("late").maxMs()).isLessThan(100);
    assertThat(result.wallMillis()).isGreaterThanOrEqualTo(300);
  }

  @Test
  @DisplayName("백분위는 nearest-rank: 1~100ms 이면 p50=50, p95=95, max=100")
  void 백분위() {
    List<Sample> samples = new ArrayList<>();
    for (int ms = 100; ms >= 1; ms--) { // 순서가 섞여 있어도 정렬해서 계산하는지
      samples.add(new Sample("g", ConcurrentRunner.OK, ms * 1_000_000L, null));
    }
    samples.add(new Sample("other", "X", 7_000_000L, null));
    RunResult result = new RunResult(samples, 0);

    Latency g = result.latency("g");
    assertThat(g.n()).isEqualTo(100);
    assertThat(g.minMs()).isEqualTo(1.0);
    assertThat(g.p50Ms()).isEqualTo(50.0);
    assertThat(g.p95Ms()).isEqualTo(95.0);
    assertThat(g.maxMs()).isEqualTo(100.0);
    assertThat(result.latency().n()).isEqualTo(101);
    assertThat(result.latency("없음").n()).isZero();
  }

  @Test
  @DisplayName("보고서는 시나리오 폴더에 JSON 으로 저장되고 환경·조건·검증 결과를 담는다")
  void 보고서_저장(@TempDir Path dir) throws Exception {
    RunResult result =
        new RunResult(
            List.of(
                new Sample("pay", ConcurrentRunner.OK, 2_000_000L, null),
                new Sample("pay", "QR-003", 3_000_000L, "이미 \"사용\"된 QR\n")),
            5_000_000L);
    Environment env =
        new Environment("abc1234", true, "17.0", 10, LocalDateTime.of(2026, 11, 26, 13, 0));

    Path file =
        PerfReport.save(
            dir, "S0-demo", "t2-r1", Map.of("threads", 2), result, Map.of("paymentRows", 1), env);

    assertThat(file).isEqualTo(dir.resolve("S0-demo").resolve("20261126-130000-000-t2-r1.json"));
    String json = Files.readString(file);
    assertThat(json)
        .contains("\"commit\": \"abc1234\"")
        .contains("\"dirty\": true")
        .contains("\"poolSize\": 10")
        .contains("\"threads\": 2")
        .contains("\"paymentRows\": 1")
        .contains("\"QR-003\": 1")
        .contains("\"p95\": 3.0")
        .contains("\"wallMs\": 5.0")
        .contains("이미 \\\"사용\\\"된 QR\\n"); // 따옴표·줄바꿈 이스케이프
  }
}
