package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantUpdateRequest;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.Location;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.LocationChange;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class MerchantServiceTest {

  // 이미 저장된 데이터와 섞이지 않도록 검색 테스트에서만 쓰는 고유한 키워드
  private static final String KEYWORD = "zq9k";

  // 점주 한 명당 가맹점 하나이므로, 여러 가맹점을 등록하는 테스트는 점주 번호를 달리한다
  private static final Long OWNER_ID = 7_001L;
  private long nextOwnerId = 7_100L;

  @Autowired private MerchantService merchantService;
  @Autowired private MerchantRepository merchantRepository;
  @PersistenceContext private EntityManager entityManager;

  @Test
  @DisplayName("가맹점을 등록하면 요청한 값으로 저장된다")
  void 가맹점등록시요청값으로저장() {
    MerchantRegisterRequest request =
        new MerchantRegisterRequest("테스트 가맹점", "인천광역시 남동구 어딘가 1", null);

    MerchantRegisterResponse response = merchantService.register(OWNER_ID, request, null);

    Merchant saved = merchantRepository.findById(response.merchantId()).orElseThrow();
    assertThat(saved.getOwnerId()).isEqualTo(OWNER_ID);
    assertThat(saved.getName()).isEqualTo("테스트 가맹점");
    assertThat(saved.getAddress()).isEqualTo("인천광역시 남동구 어딘가 1");
  }

  @Test
  @DisplayName("증빙 서류를 첨부하면 적립 대상으로 저장된다")
  void 증빙서류첨부시적립대상() {
    MerchantRegisterRequest request =
        new MerchantRegisterRequest("서류 가맹점", "인천광역시 서구 어딘가 2", "revenue.pdf");

    MerchantRegisterResponse response = merchantService.register(OWNER_ID, request, null);

    assertThat(response.cashbackEligible()).isTrue();
    assertThat(merchantRepository.findById(response.merchantId()).orElseThrow().isCashbackEligible())
        .isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("증빙 서류가 없거나 비어 있으면 적립 대상에서 제외된다")
  void 증빙서류없으면적립제외(String proof) {
    MerchantRegisterRequest request =
        new MerchantRegisterRequest("서류없는 가맹점", "인천광역시 부평구 어딘가 3", proof);

    MerchantRegisterResponse response = merchantService.register(OWNER_ID, request, null);

    assertThat(response.cashbackEligible()).isFalse();
  }


  @Test
  @DisplayName("일부 필드만 수정하면 나머지는 기존 값을 유지한다")
  void 일부필드만수정() {
    long ownerId = nextOwnerId; // register() 가 이 번호로 등록한다
    Long id = register("원래 이름", "원래 주소", true);

    merchantService.update(id, new MerchantUpdateRequest("새 이름", null), LocationChange.keep());
    Merchant updated = reload(id);

    assertThat(updated.getName()).isEqualTo("새 이름");
    assertThat(updated.getAddress()).isEqualTo("원래 주소");
    assertThat(updated.isCashbackEligible()).isTrue();
    assertThat(updated.getOwnerId()).isEqualTo(ownerId);
  }

  @Test
  @DisplayName("좌표를 만든 주소와 저장된 주소가 같으면 좌표를 반영한다")
  void 같은_주소_기준이면_좌표_반영() {
    Long id = register("이름", "옛 주소", true);

    merchantService.update(
        id, new MerchantUpdateRequest(null, "새 주소"), LocationChange.set(new Location(37.5, 127.0), "새 주소"));

    assertThat(reload(id).getLatitude()).isEqualTo(37.5);
  }

  @Test
  @DisplayName("변환하는 사이 주소가 바뀌었으면 옛 주소의 좌표는 저장하지 않는다")
  void 주소가_바뀐_사이의_좌표는_버림() {
    Long id = register("이름", "바뀐 주소", true);

    // 옛 주소를 기준으로 만든 좌표를 가진 이름만 수정하는 요청이 늦게 도착한 경우
    merchantService.update(
        id, new MerchantUpdateRequest("새 이름", null), LocationChange.set(new Location(37.5, 127.0), "옛 주소"));

    Merchant saved = reload(id);
    assertThat(saved.getName()).isEqualTo("새 이름");
    assertThat(saved.getLatitude()).isNull();
  }

  @Test
  @DisplayName("모든 필드를 수정할 수 있다")
  void 모든필드수정() {
    Long id = register("원래 이름", "원래 주소", true);

    merchantService.update(id, new MerchantUpdateRequest("새 이름", "새 주소"), LocationChange.keep());
    Merchant updated = reload(id);

    assertThat(updated.getName()).isEqualTo("새 이름");
    assertThat(updated.getAddress()).isEqualTo("새 주소");
    assertThat(updated.isCashbackEligible()).isTrue();
  }

  @Test
  @DisplayName("가맹점을 조회하면 저장된 이름, 주소, 적립 대상 여부를 반환한다")
  void 가맹점조회() {
    Long id = register("조회 가게", "서울시 종로구", true);

    MerchantResponse result = merchantService.get(id);

    assertThat(result.merchantId()).isEqualTo(id);
    assertThat(result.name()).isEqualTo("조회 가게");
    assertThat(result.address()).isEqualTo("서울시 종로구");
    assertThat(result.cashbackEligible()).isTrue();
  }

  @Test
  @DisplayName("존재하지 않는 가맹점을 조회하면 예외가 발생한다")
  void 없는가맹점조회시예외() {
    assertThatThrownBy(() -> merchantService.get(Long.MAX_VALUE))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  @Test
  @DisplayName("존재하지 않는 가맹점을 수정하면 예외가 발생한다")
  void 없는가맹점수정시예외() {
    assertThatThrownBy(
            () -> merchantService.update(Long.MAX_VALUE, new MerchantUpdateRequest("이름", null), LocationChange.keep()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  @Test
  @DisplayName("이름에 키워드가 포함된 가맹점을 대소문자 구분 없이 검색한다")
  void 이름부분일치검색() {
    register("ZQ9K 카페", "인천광역시 남동구 1", true);
    register("행복 zq9k", "인천광역시 남동구 2", true);
    register("무관한 가게", "인천광역시 남동구 3", true);

    PageResponse<MerchantResponse> result = merchantService.search(KEYWORD, 0, 20);

    assertThat(result.content())
        .extracting(MerchantResponse::name)
        .containsExactlyInAnyOrder("ZQ9K 카페", "행복 zq9k");
    assertThat(result.totalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("검색 결과에 적립 대상 여부가 포함된다")
  void 검색결과에적립대상여부포함() {
    register(KEYWORD + " 적립", "인천광역시 남동구 1", true);
    register(KEYWORD + " 비적립", "인천광역시 남동구 2", false);

    PageResponse<MerchantResponse> result = merchantService.search(KEYWORD, 0, 20);

    assertThat(result.content())
        .extracting(MerchantResponse::name, MerchantResponse::cashbackEligible)
        .containsExactlyInAnyOrder(
            tuple(KEYWORD + " 적립", true), tuple(KEYWORD + " 비적립", false));
  }

  @Test
  @DisplayName("검색어 앞뒤 공백은 무시한다")
  void 검색어앞뒤공백무시() {
    register(KEYWORD + " 가게", "인천광역시 남동구 1", true);

    PageResponse<MerchantResponse> result = merchantService.search("  " + KEYWORD + "  ", 0, 20);

    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("검색어의 % 와 _ 는 와일드카드로 해석하지 않는다")
  void 검색어와일드카드는문자그대로비교() {
    register(KEYWORD + " 가게", "인천광역시 남동구 1", true);

    assertThat(merchantService.search(KEYWORD + "%", 0, 20).totalElements()).isZero();
    assertThat(merchantService.search("zq9_", 0, 20).totalElements()).isZero();
  }

  @Test
  @DisplayName("일치하는 가맹점이 없으면 빈 결과를 반환한다")
  void 검색결과없음() {
    PageResponse<MerchantResponse> result = merchantService.search(KEYWORD + "-nomatch", 0, 20);

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isZero();
    assertThat(result.totalPages()).isZero();
  }

  @Test
  @DisplayName("size 만큼 잘라서 페이지별로 반환한다")
  void 검색페이징() {
    register(KEYWORD + "-가", "인천광역시 남동구 1", true);
    register(KEYWORD + "-나", "인천광역시 남동구 2", true);
    register(KEYWORD + "-다", "인천광역시 남동구 3", true);

    PageResponse<MerchantResponse> first = merchantService.search(KEYWORD, 0, 2);
    PageResponse<MerchantResponse> second = merchantService.search(KEYWORD, 1, 2);

    assertThat(first.content()).extracting(MerchantResponse::name)
        .containsExactly(KEYWORD + "-가", KEYWORD + "-나");
    assertThat(second.content()).extracting(MerchantResponse::name)
        .containsExactly(KEYWORD + "-다");
    assertThat(first.totalElements()).isEqualTo(3);
    assertThat(first.totalPages()).isEqualTo(2);
    assertThat(first.page()).isZero();
    assertThat(second.page()).isEqualTo(1);
  }

  @Test
  @DisplayName("size 가 50을 넘으면 50으로 제한한다")
  void size상한50제한() {
    register(KEYWORD + " 가게", "인천광역시 남동구 1", true);

    PageResponse<MerchantResponse> result = merchantService.search(KEYWORD, 0, 1000);

    assertThat(result.size()).isEqualTo(50);
  }

  @Test
  @DisplayName("이름 오름차순, 같은 이름이면 ID 오름차순으로 정렬한다")
  void 이름순ID순정렬() {
    register(KEYWORD + "-나", "인천광역시 남동구 1", true);
    Long firstSameName = register(KEYWORD + "-가", "인천광역시 남동구 2", true);
    Long secondSameName = register(KEYWORD + "-가", "인천광역시 남동구 3", true);

    PageResponse<MerchantResponse> result = merchantService.search(KEYWORD, 0, 20);

    assertThat(result.content())
        .extracting(MerchantResponse::merchantId)
        .contains(firstSameName, secondSameName);
    assertThat(result.content())
        .extracting(MerchantResponse::name)
        .containsExactly(KEYWORD + "-가", KEYWORD + "-가", KEYWORD + "-나");
    assertThat(result.content().get(0).merchantId()).isEqualTo(firstSameName);
    assertThat(result.content().get(1).merchantId()).isEqualTo(secondSameName);
  }

  @Test
  @DisplayName("같은 점주가 두 번째 가맹점을 등록하면 MER-002 로 거절하고 첫 가맹점은 그대로다")
  void 점주당가맹점하나() {
    MerchantRegisterResponse first =
        merchantService.register(
            OWNER_ID, new MerchantRegisterRequest("첫 가맹점", "인천광역시 남동구 어딘가 1", null), null);

    assertThatThrownBy(
            () ->
                merchantService.register(
                    OWNER_ID, new MerchantRegisterRequest("둘째 가맹점", "인천광역시 남동구 어딘가 2", null), null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_ALREADY_REGISTERED));

    assertThat(merchantRepository.findByOwnerId(OWNER_ID))
        .hasValueSatisfying(m -> assertThat(m.getId()).isEqualTo(first.merchantId()));
  }

  @Test
  @DisplayName("점주가 다르면 각자 가맹점을 등록할 수 있다")
  void 점주가다르면각자등록() {
    Long a = merchantService.register(OWNER_ID, new MerchantRegisterRequest("가게 A", "인천광역시 서구 1", null), null).merchantId();
    Long b = merchantService.register(OWNER_ID + 1, new MerchantRegisterRequest("가게 B", "인천광역시 서구 2", null), null).merchantId();

    assertThat(merchantRepository.findByOwnerId(OWNER_ID)).hasValueSatisfying(m -> assertThat(m.getId()).isEqualTo(a));
    assertThat(merchantRepository.findByOwnerId(OWNER_ID + 1)).hasValueSatisfying(m -> assertThat(m.getId()).isEqualTo(b));
  }

  private Long register(String name, String address, boolean cashbackEligible) {
    return merchantService
        .register(nextOwnerId++, new MerchantRegisterRequest(name, address, cashbackEligible ? "revenue.pdf" : null), null)
        .merchantId();
  }

  // 변경 내용이 실제로 DB에 반영됐는지 보려고, 영속성 컨텍스트를 비우고 다시 조회한다.
  private Merchant reload(Long id) {
    entityManager.flush();
    entityManager.clear();
    return merchantRepository.findById(id).orElseThrow();
  }
}
