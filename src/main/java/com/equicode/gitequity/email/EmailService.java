package com.equicode.gitequity.email;

import com.equicode.gitequity.contract.dto.MemberSignatureStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:noreply@gitequity.com}")
    private String from;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── 서명 요청 이메일 ───────────────────────────────────────────────────────

    @Async("collectorExecutor")
    public void sendSignRequest(String toEmail, String recipientName,
                                 String projectName, Long contractId,
                                 List<MemberSignatureStatus> members) {
        try {
            Context ctx = new Context();
            ctx.setVariable("recipientName", recipientName);
            ctx.setVariable("projectName", projectName);
            ctx.setVariable("members", members);
            ctx.setVariable("signUrl", baseUrl + "/contracts/" + contractId + "/sign");
            ctx.setVariable("expiresAt", "7일");

            String html = templateEngine.process("email/sign-request", ctx);
            send(toEmail, "[GitEquity] " + projectName + " 지분 계약서 서명 요청", html);
        } catch (Exception e) {
            log.error("Failed to send sign-request email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── 계약 완료 이메일 ───────────────────────────────────────────────────────

    @Async("collectorExecutor")
    public void sendContractCompleted(String toEmail, String recipientName,
                                       String projectName, String pdfUrl,
                                       List<MemberSignatureStatus> members) {
        try {
            Context ctx = new Context();
            ctx.setVariable("recipientName", recipientName);
            ctx.setVariable("projectName", projectName);
            ctx.setVariable("members", members);
            ctx.setVariable("pdfUrl", pdfUrl);

            String html = templateEngine.process("email/contract-completed", ctx);
            send(toEmail, "[GitEquity] " + projectName + " 지분 계약 체결 완료 🎉", html);
        } catch (Exception e) {
            log.error("Failed to send completed email to {}: {}", toEmail, e.getMessage());
        }
    }

    private void send(String to, String subject, String html) throws MessagingException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        log.info("Email sent to={} subject={}", to, subject);
    }
}
