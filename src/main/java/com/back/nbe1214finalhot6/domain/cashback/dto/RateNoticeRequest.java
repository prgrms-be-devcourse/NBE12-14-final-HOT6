package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 요율을 인하하면서 함께 알리는 공지.
 *
 * @param title 제목(100자까지)
 * @param content 본문(500자까지)
 * @param showOnHome 이용자 홈에 띄울지
 * @param homeUntil 홈에 띄우는 마지막 날. showOnHome 이면 오늘과 적용일 이후여야 한다
 */
public record RateNoticeRequest(
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 500) String content,
    boolean showOnHome,
    LocalDate homeUntil) {}
