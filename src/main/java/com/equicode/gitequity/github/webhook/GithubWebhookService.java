package com.equicode.gitequity.github.webhook;

import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.github.collector.ContributionCollectionService;
import com.equicode.gitequity.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubWebhookService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    private final ProjectRepository projectRepository;
    private final ContributionCollectionService contributionCollectionService;

    // ── HMAC 검증 ──────────────────────────────────────────────────────────────

    public boolean verifySignature(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Webhook: missing or malformed X-Hub-Signature-256 header");
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(UTF_8), HMAC_SHA256));
            String computed = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
            // constant-time 비교 (timing attack 방지)
            return MessageDigest.isEqual(computed.getBytes(UTF_8), signatureHeader.getBytes(UTF_8));
        } catch (Exception e) {
            log.error("Webhook: HMAC computation failed", e);
            return false;
        }
    }

    // ── 이벤트 라우팅 ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void handle(String event, WebhookPayload payload) {
        String repoOwner = payload.repoOwner();
        String repoName = payload.repoName();

        if (repoOwner == null || repoName == null) {
            log.warn("Webhook: repository info missing in payload");
            return;
        }

        boolean shouldCollect = switch (event) {
            case "push" -> true;
            case "pull_request" -> "closed".equals(payload.action()) && payload.isPrMerged();
            default -> false;
        };

        if (!shouldCollect) {
            log.debug("Webhook: event={} action={} skipped", event, payload.action());
            return;
        }

        Optional<Project> projectOpt = projectRepository.findByRepoOwnerAndRepoName(repoOwner, repoName);
        if (projectOpt.isEmpty()) {
            log.debug("Webhook: no matching project for {}/{}", repoOwner, repoName);
            return;
        }

        Project project = projectOpt.get();
        String token = project.getOwner().getAccessToken();
        if (token == null) {
            log.warn("Webhook: project owner has no access token, project={}", project.getId());
            return;
        }

        log.info("Webhook: triggering async collection event={} project={}/{}", event, repoOwner, repoName);
        contributionCollectionService.collectAllAsync(project, token);
    }
}
