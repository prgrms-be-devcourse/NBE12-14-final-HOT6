package com.back.nbe1214finalhot6.support.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * perfTest 작업 연결 확인. @Tag("perf") 라서 일반 test 에서는 빠지고 perfTest 에서만 돈다.
 *
 * <p>perfTest 가 결과 폴더·커밋 해시를 넘기는지 확인한다. 파일은 남기지 않는다.
 */
@Tag("perf")
class PerfTaskWiringTest {

  @Test
  @DisplayName("perfTest 는 결과 폴더(docs/perf/data)와 커밋 해시를 넘긴다")
  void 작업_설정() {
    assertThat(System.getProperty("perf.outputDir")).endsWith("docs/perf/data");
    assertThat(System.getProperty("perf.commit")).isNotBlank().isNotEqualTo("unknown");
    assertThat(System.getProperty("perf.dirty")).isIn("true", "false");
  }
}
