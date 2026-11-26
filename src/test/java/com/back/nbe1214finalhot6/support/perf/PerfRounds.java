package com.back.nbe1214finalhot6.support.perf;

import com.back.nbe1214finalhot6.support.perf.ConcurrentRunner.Task;
import com.back.nbe1214finalhot6.support.perf.PerfReport.Environment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 시나리오 하나를 "예열 1회 + 측정 N라운드" 로 돌리고 라운드마다 결과를 저장한다.
 *
 * <p>라운드마다 데이터를 새로 준비(시간 측정 밖)하고, 동시에 실행(측정)하고, DB 로 불변 조건을 확인한 뒤 저장한다. 첫 라운드는 커넥션 생성·JIT
 * 컴파일 같은 첫 실행 비용이 섞이므로 버린다. 불변 조건이 깨져도 결과는 먼저 저장하고, 깨진 내용을 모아 돌려준다(보호 장치를 뺀 실험에서도 수치를
 * 남기기 위해).
 */
public final class PerfRounds {

  private PerfRounds() {}

  /** 라운드 하나. 준비가 끝난 상태에서 실행할 작업과, 실행 뒤 확인할 불변 조건을 정한다. */
  public interface Round {

    List<Task> tasks();

    /** 실행 결과와 DB 를 보고 확인 값을 돌려준다. 반드시 {@code invariantsOk}(true/false) 를 넣는다. */
    Map<String, Object> check(RunResult result);

    static Round of(List<Task> tasks, Function<RunResult, Map<String, Object>> check) {
      return new Round() {
        @Override
        public List<Task> tasks() {
          return tasks;
        }

        @Override
        public Map<String, Object> check(RunResult result) {
          return check.apply(result);
        }
      };
    }
  }

  /** 확인 값 묶음. invariantsOk 를 맨 앞에 두고 (이름, 값) 쌍을 이어 붙인다. */
  public static Map<String, Object> checks(boolean ok, Object... pairs) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("invariantsOk", ok);
    for (int i = 0; i < pairs.length; i += 2) {
      m.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return m;
  }

  /**
   * @param scenario 시나리오 이름(실험이면 앞에 변형 이름이 붙는다)
   * @param label 조건 라벨(예: t10). 라운드 번호가 뒤에 붙는다
   * @param conditions 기록할 실행 조건
   * @param warmup 첫 라운드를 버릴지
   * @param prepare 라운드마다 데이터를 새로 만들고 Round 를 돌려준다
   * @return 불변 조건이 깨진 라운드 설명(없으면 빈 목록)
   */
  public static List<String> measure(
      String scenario,
      String label,
      Map<String, ?> conditions,
      int rounds,
      boolean warmup,
      Duration timeout,
      Environment env,
      Supplier<Round> prepare) {
    List<String> violations = new ArrayList<>();
    int total = rounds + (warmup ? 1 : 0);
    for (int i = 0; i < total; i++) {
      boolean isWarmup = warmup && i == 0;
      Round round = prepare.get();
      RunResult result = ConcurrentRunner.run(round.tasks(), timeout);
      Map<String, Object> checks = round.check(result);
      if (isWarmup) {
        continue;
      }
      int roundNo = warmup ? i : i + 1;
      Map<String, Object> cond = new LinkedHashMap<>(conditions);
      cond.put("round", roundNo);
      cond.put("rounds", rounds);
      PerfReport.save(
          scenario, label + "-r" + roundNo, cond, result, checks, withNow(env));
      if (!Boolean.TRUE.equals(checks.get("invariantsOk"))) {
        violations.add(scenario + " " + label + "-r" + roundNo + " " + checks);
      }
    }
    return violations;
  }

  // ---------- 실행 조건(perfTest 가 -P 값으로 넘긴다) ----------

  /** 스레드 수 목록. 기본 2,10,50. */
  public static int[] threads() {
    return Arrays.stream(System.getProperty("perf.threads", "2,10,50").split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .mapToInt(Integer::parseInt)
        .toArray();
  }

  /** 측정 라운드 수(예열 제외). 기본 5. */
  public static int rounds() {
    return Integer.parseInt(System.getProperty("perf.rounds", "5"));
  }

  /** 보호 장치를 뺀 실험 이름. 기준값 측정이면 빈 문자열. */
  public static String variant() {
    return System.getProperty("perf.variant", "").trim();
  }

  /** 실험이면 "A-<변형>-<시나리오>", 기준값이면 시나리오 이름 그대로. */
  public static String scenarioName(String base) {
    String v = variant();
    return v.isEmpty() ? base : "A-" + v + "-" + base;
  }

  private static Environment withNow(Environment env) {
    return new Environment(
        env.commit(), env.dirty(), env.postgresVersion(), env.poolSize(), java.time.LocalDateTime.now());
  }
}
