package com.back.nbe1214finalhot6.support.perf;

import com.back.nbe1214finalhot6.support.perf.RunResult.Latency;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 측정 1회(시나리오 1개 × 조건 1개 × 라운드 1개)를 JSON 파일로 남기고 콘솔에 한 줄 요약을 찍는다.
 *
 * <p>파일 위치: {@code <perf.outputDir>/<시나리오>/<실행일시>-<라벨>.json}. {@code perf.outputDir} 은 perfTest 작업이
 * {@code docs/perf/data} 로 넘긴다. 없으면(IDE 에서 바로 실행 등) {@code build/perf-results} 에 저장해 문서 폴더를 어지럽히지 않는다.
 *
 * <p>같은 조건으로 다시 재현할 수 있게 커밋 해시, 커밋하지 않은 수정 여부, PostgreSQL 버전, 커넥션 풀 크기, 실행 일시를 함께 남긴다.
 */
public final class PerfReport {

  private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  private PerfReport() {}

  /** 측정 환경. 측정 대상 코드·DB·풀을 식별한다. */
  public record Environment(
      String commit, boolean dirty, String postgresVersion, int poolSize, LocalDateTime executedAt) {}

  /** 지금 실행 중인 테스트 컨텍스트의 환경을 읽는다. */
  public static Environment environment(DataSource dataSource) {
    String version =
        new JdbcTemplate(dataSource).queryForObject("show server_version", String.class);
    int poolSize =
        dataSource instanceof HikariDataSource hikari ? hikari.getMaximumPoolSize() : -1;
    return new Environment(
        System.getProperty("perf.commit", "unknown"),
        Boolean.parseBoolean(System.getProperty("perf.dirty", "false")),
        version,
        poolSize,
        LocalDateTime.now());
  }

  /**
   * 측정 결과를 저장한다.
   *
   * @param scenario 시나리오 이름(폴더 이름이 된다. 예: S1-same-key)
   * @param label 이 실행을 구분하는 짧은 이름(예: t10-r3 = 스레드 10, 3라운드)
   * @param conditions 실행 조건(스레드 수, 라운드, 예열 여부 등)
   * @param checks 불변 조건 확인 결과(예: paymentRows=1, balanceOk=true)
   * @return 저장한 파일 경로
   */
  public static Path save(
      String scenario,
      String label,
      Map<String, ?> conditions,
      RunResult result,
      Map<String, ?> checks,
      Environment env) {
    return save(outputDir(), scenario, label, conditions, result, checks, env);
  }

  static Path save(
      Path baseDir,
      String scenario,
      String label,
      Map<String, ?> conditions,
      RunResult result,
      Map<String, ?> checks,
      Environment env) {
    Map<String, Object> doc = toDocument(scenario, label, conditions, result, checks, env);
    Path file = baseDir.resolve(scenario).resolve(FILE_TIME.format(env.executedAt()) + "-" + label + ".json");
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, Json.write(doc) + "\n", StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("측정 결과 저장 실패: " + file, e);
    }
    System.out.println(summary(scenario, label, result, checks));
    return file;
  }

  static Map<String, Object> toDocument(
      String scenario,
      String label,
      Map<String, ?> conditions,
      RunResult result,
      Map<String, ?> checks,
      Environment env) {
    Map<String, Object> environment = new LinkedHashMap<>();
    environment.put("commit", env.commit());
    environment.put("dirty", env.dirty());
    environment.put("postgresVersion", env.postgresVersion());
    environment.put("poolSize", env.poolSize());
    environment.put("executedAt", env.executedAt().toString());
    environment.put("javaVersion", System.getProperty("java.version"));
    environment.put("cpus", Runtime.getRuntime().availableProcessors());

    Map<String, Object> groups = new LinkedHashMap<>();
    for (String group : result.groups()) {
      Map<String, Object> g = new LinkedHashMap<>();
      g.put("counts", result.counts(group));
      g.put("latencyMs", latency(result.latency(group)));
      groups.put(group, g);
    }

    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("scenario", scenario);
    doc.put("label", label);
    doc.put("environment", environment);
    doc.put("conditions", conditions);
    doc.put("wallMs", result.wallMillis());
    doc.put("counts", result.counts());
    doc.put("latencyMs", latency(result.latency()));
    doc.put("groups", groups);
    doc.put("checks", checks);
    doc.put("exampleMessages", result.exampleMessages());
    return doc;
  }

  /** 콘솔용 한 줄 요약. */
  static String summary(String scenario, String label, RunResult result, Map<String, ?> checks) {
    Latency l = result.latency();
    return String.format(
        "[perf] %s %s | %s | p50=%.2fms p95=%.2fms max=%.2fms wall=%.2fms | checks=%s",
        scenario, label, result.counts(), l.p50Ms(), l.p95Ms(), l.maxMs(), result.wallMillis(), checks);
  }

  private static Map<String, Object> latency(Latency l) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("n", l.n());
    m.put("min", l.minMs());
    m.put("p50", l.p50Ms());
    m.put("p95", l.p95Ms());
    m.put("max", l.maxMs());
    return m;
  }

  private static Path outputDir() {
    String dir = System.getProperty("perf.outputDir");
    return dir == null || dir.isBlank() ? Path.of("build", "perf-results") : Path.of(dir);
  }

  /** 의존성 없이 쓰는 작은 JSON 직렬화기. Map·Collection·String·Number·Boolean·null 만 다룬다. */
  static final class Json {

    private Json() {}

    static String write(Object value) {
      StringBuilder sb = new StringBuilder();
      write(sb, value, 0);
      return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, int depth) {
      if (value == null) {
        sb.append("null");
      } else if (value instanceof String s) {
        string(sb, s);
      } else if (value instanceof Number || value instanceof Boolean) {
        sb.append(value);
      } else if (value instanceof Map<?, ?> map) {
        if (map.isEmpty()) {
          sb.append("{}");
          return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
          indent(sb, depth + 1);
          string(sb, String.valueOf(e.getKey()));
          sb.append(": ");
          write(sb, e.getValue(), depth + 1);
          sb.append(++i < map.size() ? ",\n" : "\n");
        }
        indent(sb, depth);
        sb.append('}');
      } else if (value instanceof Collection<?> list) {
        sb.append('[');
        int i = 0;
        for (Object item : list) {
          write(sb, item, depth + 1);
          if (++i < list.size()) {
            sb.append(", ");
          }
        }
        sb.append(']');
      } else {
        string(sb, value.toString());
      }
    }

    private static void string(StringBuilder sb, String s) {
      sb.append('"');
      for (char c : s.toCharArray()) {
        switch (c) {
          case '"' -> sb.append("\\\"");
          case '\\' -> sb.append("\\\\");
          case '\n' -> sb.append("\\n");
          case '\r' -> sb.append("\\r");
          case '\t' -> sb.append("\\t");
          default -> {
            if (c < 0x20) {
              sb.append(String.format("\\u%04x", (int) c));
            } else {
              sb.append(c);
            }
          }
        }
      }
      sb.append('"');
    }

    private static void indent(StringBuilder sb, int depth) {
      sb.append("  ".repeat(depth));
    }
  }
}
