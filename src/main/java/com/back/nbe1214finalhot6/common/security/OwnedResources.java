package com.back.nbe1214finalhot6.common.security;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원의 지갑·가맹점 번호를 토큰의 회원 번호로 찾는다.
 *
 * <p>요청 본문·파라미터로 받은 walletId, merchantId 를 믿으면 남의 번호를 보내 남의 지갑으로 결제하거나 남의 가맹점을 고칠 수 있다. 번호를 요청에서 빼고
 * 컨트롤러가 이 도구로 찾으면 남의 번호를 보낼 방법 자체가 없어진다.
 *
 * <p>전제: 회원 1명 = 지갑 1개, 점주 1명 = 가맹점 1개. 내 지갑·가맹점이 없으면 404 로 응답한다. 번호를 요청으로 받지 않으므로 "남의 것"인 경우는 없고 "내 것이 없는"
 * 경우만 남는다.
 */
@Component
@RequiredArgsConstructor
public class OwnedResources {

  private final WalletRepository walletRepository;
  private final MerchantRepository merchantRepository;

  /** 내 지갑 번호. 없으면 WAL-001(404). */
  @Transactional(readOnly = true)
  public Long walletIdOf(AuthUser user) {
    return walletRepository
        .findByUserId(user.userId())
        .map(Wallet::getId)
        .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
  }

  /** 내 가맹점 번호. 점주당 가맹점은 하나(유일 제약)다. 없으면 MER-001(404). */
  @Transactional(readOnly = true)
  public Long merchantIdOf(AuthUser user) {
    return merchantRepository
        .findByOwnerId(user.userId())
        .map(Merchant::getId)
        .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }
}
