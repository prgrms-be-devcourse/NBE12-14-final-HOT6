package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 공지 수정 요청. 네 항목을 모두 보낸다.
 *
 * @param title 제목(100자까지)
 * @param content 본문(500자까지)
 * @param showOnHome 이용자 홈에 띄울지
 * @param homeUntil 홈에 띄우는 마지막 날. 새로 띄우거나 날짜를 바꿀 때는 오늘 이후여야 한다
 */
public record NoticeUpdateRequest(
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 500) String content,
    boolean showOnHome,
    LocalDate homeUntil) {}
