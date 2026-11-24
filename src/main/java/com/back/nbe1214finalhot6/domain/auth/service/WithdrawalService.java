package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalPreviewResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalResult;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.RefundService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 실행 전에 환불 금액을 안내하고({@link #preview}), 실행하면({@link #settle}) 한 트랜잭션으로 아래를 처리한다.
 *
 * <ol>
 *   <li>지갑 행을 잠근다. 탈퇴 중에 들어온 결제·입금은 기다렸다가 탈퇴한 지갑이라 거절된다(WAL-002)
 *   <li>남은 데브머니를 모의계좌로 환불한다(환불 서비스 재사용, 원장 기록)
 *   <li>적립 예정(PENDING) 캐시백을 소멸시키고 확보한 예산을 되돌린다(건별 취소)
 *   <li>지갑과 회원을 탈퇴 상태로 바꾼다
 * </ol>
 *
 * <p>음수 캐시백은 정산하지 않고 감수한다(팀 합의). 데브머니로 상계하지 않고 계좌에서 충전하지도 않으며 음수가 남아 있어도 탈퇴할 수 있다. 양수 캐시백 자체의 소멸은 MVP
 * 이후(이슈 #27)다. 점주·운영자는 MVP 에서 탈퇴할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

  private final UserRepository userRepository;
  private final WalletRepository walletRepository;
  private final CashbackEarningRepository earningRepository;
  private final RefundService refundService;
  private final CashbackService cashbackService;
  private final Clock clock;

  /** 실행했을 때 일어날 일을 미리 보여 준다. 아무것도 바꾸지 않는다. */
  @Transactional(readOnly = true)
  public WithdrawalPreviewResponse preview(Long userId, Long walletId) {
    requireWithdrawable(userId);
    Wallet wallet =
        walletRepository
            .findById(walletId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    requireOwnActiveWallet(wallet, userId);

    List<CashbackEarning> pending = pendingEarnings(walletId);
    return new WithdrawalPreviewResponse(
        wallet.getBalance(), pending.size(), sum(pending), wallet.getCashbackBalance());
  }

  /**
   * 탈퇴를 실행한다.
   *
   * @param expectedRefundAmount 미리보기에서 확인한 환불 예정액. 실행 시점의 금액이 다르면 AUTH-008(409)로 거절해 다시 확인하게 한다
   * @param idempotencyKey 멱등키. 환불 서비스의 멱등키를 이 값으로 정해서, 같은 요청이 중복으로 환불되지 않게 한다
   * @throws BusinessException 점주·운영자(AUTH-007), 이미 탈퇴(AUTH-004 또는 WAL-002), 금액이 달라짐(AUTH-008)
   */
  @Transactional
  public WithdrawalResult settle(
      Long userId, Long walletId, long expectedRefundAmount, String idempotencyKey) {
    requireWithdrawable(userId);

    // 1. 지갑 행 잠금. 이 트랜잭션에서 지갑을 가장 먼저 읽는 곳이어야 최신 잔액을 본다
    Wallet wallet =
        walletRepository
            .findByIdForUpdate(walletId)
            .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    requireOwnActiveWallet(wallet, userId);

    // 2. 확인받은 금액과 지금 금액이 같은지(그 사이 결제·환불·충전이 있었으면 달라졌다)
    long refundAmount = wallet.getBalance();
    if (refundAmount != expectedRefundAmount) {
      throw new BusinessException(
          AuthErrorCode.WITHDRAW_AMOUNT_CHANGED,
          "환불 금액이 바뀌었습니다. 현재 환불 금액은 " + refundAmount + "원입니다. 다시 확인해 주세요");
    }

    // 3. 남은 데브머니를 모의계좌로 환불(0원이면 환불할 것이 없다)
    if (refundAmount > 0) {
      refundService.refund(walletId, refundAmount, refundKey(userId, idempotencyKey));
    }

    // 4. 적립 예정 캐시백 소멸. 취소는 PENDING 일 때만 처리되는 조건부 UPDATE 라서, 지급이 끝난(PAID) 건은 건드리지 않는다
    List<CashbackEarning> pending = pendingEarnings(walletId);
    for (CashbackEarning earning : pending) {
      cashbackService.cancel(earning.getPaymentId());
    }

    // 5. 지갑과 회원을 탈퇴로. 지갑은 잠근 상태에서 같은 객체를 바꾸고, 회원은 다른 요청의 변경을 덮어쓰지 않게 상태만 조건부로 바꾼다
    LocalDateTime now = LocalDateTime.now(clock);
    wallet.withdraw(now);
    int changed =
        userRepository.markWithdrawn(userId, now, UserStatus.ACTIVE, UserStatus.WITHDRAWN);
    if (changed != 1) {
      // 지갑을 잠근 상태라 동시에 다른 탈퇴가 끼어들 수 없다. 여기에 오면 이미 탈퇴한 것이므로 전부 되돌린다
      throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
    }

    return new WithdrawalResult(refundAmount, pending.size(), sum(pending), now);
  }

  // 활동 중인 일반 이용자(USER)만 탈퇴할 수 있다. 역할은 토큰이 아니라 DB의 현재 값으로 본다
  private void requireWithdrawable(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
    }
    if (user.getRole() != UserRole.USER) {
      throw new BusinessException(AuthErrorCode.WITHDRAW_NOT_ALLOWED);
    }
  }

  // 내 지갑이고 아직 탈퇴하지 않았는지
  private void requireOwnActiveWallet(Wallet wallet, Long userId) {
    if (!wallet.getUserId().equals(userId)) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
    if (wallet.isWithdrawn()) {
      throw new BusinessException(WalletErrorCode.WALLET_WITHDRAWN);
    }
  }

  private List<CashbackEarning> pendingEarnings(Long walletId) {
    return earningRepository
        .findByWalletIdAndStatus(walletId, EarningStatus.PENDING, Pageable.unpaged())
        .getContent();
  }

  private long sum(List<CashbackEarning> earnings) {
    return earnings.stream().mapToLong(CashbackEarning::getAmount).sum();
  }

  // 환불 서비스는 (지갑, 멱등키)로 중복을 막는다. 사용자가 환불 API 에 쓴 키와 겹치지 않게, 탈퇴 전용 키를 만든다
  private String refundKey(Long userId, String idempotencyKey) {
    return UUID.nameUUIDFromBytes(
            ("withdraw|" + userId + "|" + idempotencyKey).getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
