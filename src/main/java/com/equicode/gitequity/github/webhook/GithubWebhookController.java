package com.equicode.gitequity.github.webhook;

import com.equicode.gitequity.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class GithubWebhookController {

    private final GithubWebhookService webhookService;
    private final ObjectMapper githubObjectMapper;  // SNAKE_CASE ObjectMapper (WebClientConfig)

    @PostMapping("/github")
    public ResponseEntity<ApiResponse<Void>> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "ping") String event,
            @RequestBody byte[] rawBody) {

        // ping 이벤트는 HMAC 검증 없이 즉시 200 응답
        if ("ping".equals(event)) {
            log.info("Webhook: ping received");
            return ResponseEntity.ok(ApiResponse.ok("pong", null));
        }

        // HMAC SHA-256 검증
        if (!webhookService.verifySignature(rawBody, signature)) {
            log.warn("Webhook: invalid signature for event={}", event);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail("invalid signature"));
        }

        // 페이로드 파싱 → 이벤트 처리 (비동기)
        try {
            WebhookPayload payload = githubObjectMapper.readValue(rawBody, WebhookPayload.class);
            webhookService.handle(event, payload);
        } catch (Exception e) {
            log.error("Webhook: failed to parse payload for event={}", event, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.fail("invalid payload"));
        }

        return ResponseEntity.ok(ApiResponse.ok("accepted", null));
    }
}
