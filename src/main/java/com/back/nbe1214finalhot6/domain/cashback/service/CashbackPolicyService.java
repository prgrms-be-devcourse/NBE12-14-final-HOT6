package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyCreateRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캐시백 정책 관리. */
@Service
@RequiredArgsConstructor
public class CashbackPolicyService {

  private final CashbackPolicyRepository policyRepository;
  private final Clock clock;

  /**
   * 정책을 등록한다. 기간이 기존 정책과 겹치면 거부한다.
   *
   * <p>겹침 검사는 조회 후 저장이라, 같은 순간에 두 운영자가 겹치는 정책을 등록하면 둘 다 통과할 수 있다. 운영자 단독 작업을 전제로 DB 제약은 두지 않는다.
   */
  @Transactional
  public PolicyResponse create(PolicyCreateRequest request) {
    CashbackPolicy policy =
        CashbackPolicy.create(
            request.rate(), request.startsAt(), request.endsAt(), LocalDateTime.now(clock));

    if (policyRepository.existsOverlapping(policy.getStartsAt(), policy.getEndsAt())) {
      throw new BusinessException(CashbackErrorCode.POLICY_PERIOD_OVERLAP);
    }
    return PolicyResponse.from(policyRepository.save(policy));
  }

  /** 시작이 늦은 순으로 돌려준다. */
  @Transactional(readOnly = true)
  public List<PolicyResponse> findAll() {
    return policyRepository.findAllByOrderByStartsAtDesc().stream()
        .map(PolicyResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PolicyResponse get(Long policyId) {
    return policyRepository
        .findById(policyId)
        .map(PolicyResponse::from)
        .orElseThrow(() -> new BusinessException(CashbackErrorCode.POLICY_NOT_FOUND));
  }

  /** 해당 시각에 유효한 정책. 적립 계산이 접수 시각의 요율을 구할 때 쓴다. */
  @Transactional(readOnly = true)
  public Optional<PolicyResponse> findEffective(LocalDateTime at) {
    return policyRepository.findEffective(at).stream().findFirst().map(PolicyResponse::from);
  }
}
