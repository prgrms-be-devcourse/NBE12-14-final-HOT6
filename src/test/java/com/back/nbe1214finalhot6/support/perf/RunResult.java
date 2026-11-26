package com.back.nbe1214finalhot6.support.perf;

import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner.Sample;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@link ConcurrentRunner} 실행 1회의 결과. 결과별 건수, 지연 백분위(ms), 전체 소요 시간을 계산한다. */
public record RunResult(List<Sample> samples, long wallNanos) {

  /** 결과 라벨별 건수(라벨 이름순). group 이 null 이면 전체. */
  public Map<String, Long> counts(String group) {
    Map<String, Long> counts = new TreeMap<>();
    for (Sample s : of(group)) {
      counts.merge(s.outcome(), 1L, Long::sum);
    }
    return counts;
  }

  public Map<String, Long> counts() {
    return counts(null);
  }

  public long count(String outcome) {
    return samples.stream().filter(s -> s.outcome().equals(outcome)).count();
  }

  public List<String> groups() {
    return samples.stream().map(Sample::group).distinct().sorted().toList();
  }

  /** 지연 통계(ms). p50·p95 는 nearest-rank 방식이다. */
  public Latency latency(String group) {
    long[] sorted = of(group).stream().mapToLong(Sample::nanos).sorted().toArray();
    if (sorted.length == 0) {
      return new Latency(0, 0, 0, 0, 0);
    }
    return new Latency(
        sorted.length,
        ms(sorted[0]),
        ms(percentile(sorted, 50)),
        ms(percentile(sorted, 95)),
        ms(sorted[sorted.length - 1]));
  }

  public Latency latency() {
    return latency(null);
  }

  public double wallMillis() {
    return ms(wallNanos);
  }

  /** 결과 라벨별 예외 메시지 예시 1건(원인 파악용). */
  public Map<String, String> exampleMessages() {
    Map<String, String> examples = new LinkedHashMap<>();
    for (Sample s : samples) {
      if (s.message() != null) {
        examples.putIfAbsent(s.outcome(), s.message());
      }
    }
    return examples;
  }

  private List<Sample> of(String group) {
    return group == null ? samples : samples.stream().filter(s -> s.group().equals(group)).toList();
  }

  private static long percentile(long[] sorted, int p) {
    int rank = (int) Math.ceil(p / 100.0 * sorted.length);
    return sorted[Math.max(0, rank - 1)];
  }

  static double ms(long nanos) {
    return Math.round(nanos / 10_000.0) / 100.0; // 소수 둘째 자리
  }

  public record Latency(int n, double minMs, double p50Ms, double p95Ms, double maxMs) {}
}
