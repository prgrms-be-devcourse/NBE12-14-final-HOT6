package com.back.nbe1214finalhot6.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 테스트 컨텍스트가 뜨기 전에 테스트 전용 DB(예: devpay_test)가 없으면 만든다.
 *
 * <p>테스트 프로필은 스키마를 매번 새로 만들기(create-drop) 때문에, 개발 DB 를 건드리지 않도록 DB 이름이 {@code
 * _test} 로 끝나는지 먼저 확인하고 아니면 중단한다.
 */
public class TestDatabaseInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Pattern JDBC_URL = Pattern.compile("^(jdbc:postgresql://[^/]+/)([^/?]+)(.*)$");
  private static final String ALREADY_EXISTS = "42P04";

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    ConfigurableEnvironment env = context.getEnvironment();
    String url = env.getRequiredProperty("spring.datasource.url");
    String user = env.getRequiredProperty("spring.datasource.username");
    String password = env.getProperty("spring.datasource.password", "");

    Matcher matcher = JDBC_URL.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalStateException("지원하지 않는 JDBC URL 입니다: " + url);
    }
    String dbName = matcher.group(2);
    if (!dbName.matches("[a-zA-Z0-9_]+_test")) {
      throw new IllegalStateException(
          "테스트 DB 이름은 _test 로 끝나야 합니다(개발 DB 보호). 현재: " + dbName);
    }
    createIfMissing(matcher.group(1) + "postgres" + matcher.group(3), user, password, dbName);
  }

  private static void createIfMissing(String adminUrl, String user, String password, String dbName) {
    try (Connection connection = DriverManager.getConnection(adminUrl, user, password)) {
      if (exists(connection, dbName)) {
        return;
      }
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE DATABASE \"" + dbName + "\"");
      }
    } catch (SQLException e) {
      // 다른 프로세스가 먼저 만든 경우는 무시한다.
      if (!ALREADY_EXISTS.equals(e.getSQLState())) {
        throw new IllegalStateException(
            "테스트 DB 를 준비하지 못했습니다. docker compose up -d 로 DB 가 떠 있는지 확인하세요", e);
      }
    }
  }

  private static boolean exists(Connection connection, String dbName) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
      statement.setString(1, dbName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }
}
