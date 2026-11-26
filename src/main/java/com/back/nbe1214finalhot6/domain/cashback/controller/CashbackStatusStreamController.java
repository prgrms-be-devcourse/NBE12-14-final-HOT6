package com.back.nbe1214finalhot6.domain.cashback.controller;

import com.back.nbe1214finalhot6.domain.cashback.service.CashbackStatusStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "캐시백 조회(이용자)", description = "내 월 적립 한도와 적립 내역 조회 API. 지갑은 토큰의 회원으로 찾는다")
@RestController
@RequestMapping("/api/cashback/status")
@RequiredArgsConstructor
public class CashbackStatusStreamController {

  private final CashbackStatusStreamService streamService;

  @Operation(
      summary = "적립 상태 실시간 수신(SSE)",
      description =
          "연결 직후, 적립이 중단된 직후, 20초마다 status 이벤트({\"paused\": true|false})를 보낸다. "
              + "5분이 지나면 연결을 끝내므로 다시 연결한다. Authorization 헤더가 필요하다")
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> stream() {
    return ResponseEntity.ok()
        // 중간 서버(Next 프록시의 압축, nginx 버퍼)가 이벤트를 모아 두지 않게 한다
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
        .header("X-Accel-Buffering", "no")
        .body(streamService.subscribe());
  }

  // 화면이 연결을 닫은 것은 오류가 아니다. 공통 처리기가 500 으로 기록하고 응답을 쓰려 하지 않게 여기서 끝낸다
  @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
  public void disconnected() {}
}
