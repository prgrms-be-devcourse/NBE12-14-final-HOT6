package com.back.nbe1214finalhot6.domain.reconciliation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대사 한 번의 결과. 실행할 때마다 한 건씩 쌓고 수정하지 않는다. */
@Entity
@Table(
    name = "reconciliation_result",
    indexes = @Index(name = "idx_reconciliation_result_target_date", columnList = "target_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationResult {

  private static final int MAX_REFS_LENGTH = 2000;
  private static final int MAX_MESSAGE_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReconciliationType type;

  @Column(name = "target_date", nullable = false)
  private LocalDate targetDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReconciliationStatus status;

  @Column(name = "checked_count", nullable = false)
  private long checkedCount;

  @Column(name = "mismatch_count", nullable = false)
  private long mismatchCount;

  @Column(name = "mismatch_refs", length = MAX_REFS_LENGTH)
  private String mismatchRefs;

  @Column(name = "error_message", length = MAX_MESSAGE_LENGTH)
  private String errorMessage;

  @Column(name = "executed_at", nullable = false)
  private LocalDateTime executedAt;

  private ReconciliationResult(
      ReconciliationType type,
      LocalDate targetDate,
      ReconciliationStatus status,
      long checkedCount,
      List<String> mismatches,
      String errorMessage,
      LocalDateTime now) {
    this.type = type;
    this.targetDate = targetDate;
    this.status = status;
    this.checkedCount = checkedCount;
    this.mismatchCount = mismatches.size();
    this.mismatchRefs =
        mismatches.isEmpty() ? null : truncate(String.join(",", mismatches), MAX_REFS_LENGTH);
    this.errorMessage = errorMessage == null ? null : truncate(errorMessage, MAX_MESSAGE_LENGTH);
    this.executedAt = now;
  }

  public static ReconciliationResult of(
      ReconciliationType type,
      LocalDate targetDate,
      long checkedCount,
      List<String> mismatches,
      LocalDateTime now) {
    ReconciliationStatus status =
        mismatches.isEmpty() ? ReconciliationStatus.MATCHED : ReconciliationStatus.MISMATCHED;
    return new ReconciliationResult(type, targetDate, status, checkedCount, mismatches, null, now);
  }

  public static ReconciliationResult error(
      ReconciliationType type, LocalDate targetDate, String message, LocalDateTime now) {
    return new ReconciliationResult(
        type, targetDate, ReconciliationStatus.ERROR, 0, List.of(), message, now);
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }
}
