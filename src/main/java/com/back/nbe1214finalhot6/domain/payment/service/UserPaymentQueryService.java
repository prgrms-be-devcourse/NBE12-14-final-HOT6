package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.dto.UserPaymentItem;
import com.back.nbe1214finalhot6.domain.payment.dto.UserPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자 결제 내역. 내 지갑의 결제를 최신순으로 읽기만 한다. 어느 지갑인지는 호출한 쪽(컨트롤러)이 토큰의 회원으로 정한다.
 *
 * <p>보여 주는 상태는 완료·취소뿐이다. 처리 중·실패 건은 완료 시각이 없어 내역에서 제외한다.
 */
@Service
@RequiredArgsConstructor
public class UserPaymentQueryService {

  static final int MAX_PAGE_SIZE = 100;

  //목록에 뜨는 상태 VISIBLE_STATUSES : 완료COMPLETED & 취소CANCELED
  private static final List<PaymentStatus> VISIBLE_STATUSES =
      List.of(PaymentStatus.COMPLETED, PaymentStatus.CANCELED);

  private final PaymentRepository paymentRepository;
  private final MerchantRepository merchantRepository;

  /**
   * 내 결제 목록을 돌려준다.
   *
   * @param status 완료(COMPLETED) 또는 취소(CANCELED)만 걸러 본다. 없으면 둘 다
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   * @throws BusinessException 완료·취소가 아닌 상태를 요청하면 COM-001
   */
  @Transactional(readOnly = true)
  public UserPaymentsResponse get(Long walletId, PaymentStatus status, int page, int size) {
    List<PaymentStatus> statuses = statuses(status);
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("completedAt"), Sort.Order.desc("id")));

    Page<Payment> payments =
        paymentRepository.findByWalletIdAndStatusIn(walletId, statuses, pageable);
    Map<Long, String> merchantNames = merchantNames(payments.getContent());

    return new UserPaymentsResponse(
        PageResponse.from(
            payments.map(p -> UserPaymentItem.of(p, merchantNames.get(p.getMerchantId())))));
  }

  private List<PaymentStatus> statuses(PaymentStatus status) {
    if (status == null) {
      return VISIBLE_STATUSES;
    }
    if (!VISIBLE_STATUSES.contains(status)) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    return List.of(status);
  }

  // 한 페이지의 가맹점명을 한 번에 가져온다 (건마다 조회하면 쿼리가 건수만큼 늘어난다)
  private Map<Long, String> merchantNames(List<Payment> payments) {
    List<Long> merchantIds = payments.stream().map(Payment::getMerchantId).distinct().toList();
    return merchantRepository.findAllById(merchantIds).stream()
        .collect(Collectors.toMap(Merchant::getId, Merchant::getName));
  }
}
