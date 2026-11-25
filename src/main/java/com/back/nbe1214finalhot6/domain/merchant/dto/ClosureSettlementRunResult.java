package com.back.nbe1214finalhot6.domain.merchant.dto;

import java.util.List;

/**
 * 해지 정산 한 번 실행한 결과.
 *
 * @param closed 정산을 끝내고 해지 완료로 바꾼 가맹점 수
 * @param held 갚을 금액이 정산금보다 많아 해지 진행 중으로 남겨 둔 가맹점 수
 * @param waiting 신청일이 아직 지나지 않아 다음 실행으로 미룬 가맹점 수
 * @param dailyIncomplete 신청일까지의 일 정산이 아직 없거나 끝나지 않아 미룬 가맹점 수. 일 정산을 먼저 확인해야 한다
 * @param failed 처리 중 오류가 난 가맹점 수
 * @param heldMerchantIds 보류한 가맹점 번호. 운영자가 따로 확인해야 한다
 */
public record ClosureSettlementRunResult(
    int closed, int held, int waiting, int dailyIncomplete, int failed, List<Long> heldMerchantIds) {}
