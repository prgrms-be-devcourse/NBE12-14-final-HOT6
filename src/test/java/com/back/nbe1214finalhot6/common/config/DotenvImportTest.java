package com.back.nbe1214finalhot6.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** application.yaml 의 .env 읽기 설정이 의도대로 동작하는지 확인한다. */
class DotenvImportTest {

  // 설정 줄이 바뀌거나 지워지면 팀원의 로컬 실행 방법(.env)이 깨지므로 값을 고정해 둔다
  @Test
  @DisplayName("application.yaml 은 프로젝트 폴더의 .env 를 선택적으로(optional) 읽도록 설정되어 있다")
  void applicationYamlImportsDotenv() throws IOException {
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));

    assertThat(sources.get(0).getProperty("spring.config.import"))
        .isEqualTo("optional:file:.env[.properties]");
  }

  // 같은 import 문법으로 .env 의 KEY=값 을 읽어 ${JWT_SECRET} 자리에 들어가는지 확인(키 값에 = 가 있어도)
  @Test
  @DisplayName(".env 에 적은 JWT_SECRET 이 ${JWT_SECRET} 자리에 들어간다(base64 의 = 포함)")
  void dotenvValueResolvesPlaceholder(@TempDir Path dir) throws IOException {
    Path dotenv = dir.resolve(".env");
    Files.writeString(dotenv, "JWT_SECRET=abc+def/ghi==\n");

    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.import=optional:file:" + dotenv + "[.properties]",
            "jwt.secret=${JWT_SECRET}")
        .run(context -> assertThat(context.getEnvironment().getProperty("jwt.secret"))
            .isEqualTo("abc+def/ghi=="));
  }

  @Test
  @DisplayName(".env 파일이 없어도(optional) 설정을 읽는 데 실패하지 않는다")
  void missingDotenvIsIgnored(@TempDir Path dir) {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.import=optional:file:" + dir.resolve(".env") + "[.properties]")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
