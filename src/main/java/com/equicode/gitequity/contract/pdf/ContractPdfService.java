package com.equicode.gitequity.contract.pdf;

import com.equicode.gitequity.contract.dto.ContractPdfData;
import com.equicode.gitequity.domain.ContractType;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class ContractPdfService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm");

    public byte[] generate(ContractPdfData data) {
        try {
            String html = buildHtml(data);
            return convertToPdf(html);
        } catch (Exception e) {
            log.error("PDF generation failed for contract={}: {}", data.contractId(), e.getMessage(), e);
            throw new RuntimeException("PDF 생성에 실패했습니다.", e);
        }
    }

    private String buildHtml(ContractPdfData data) throws IOException {
        boolean isFinal = data.contractType() == ContractType.FINAL;
        String template = loadTemplate();

        String contractTypeLabel = isFinal ? "최종 지분 확정 계약서" : "기여도 기반 지분 합의서";
        String contractSubtitle = isFinal
                ? "GitEquity · 기여도 기반 최종 지분 확정"
                : "GitEquity · 프로젝트 기여도 기반 지분 분배 합의";

        String tableHeader = isFinal
                ? """
                  <tr>
                    <th>멤버</th>
                    <th>최종 지분 (%)</th>
                    <th>기여 점수</th>
                    <th>커밋</th><th>PR</th><th>리뷰</th><th>이슈</th>
                  </tr>"""
                : """
                  <tr>
                    <th>참여자</th>
                    <th>서명 일시</th>
                  </tr>""";

        String memberRows = data.members().stream()
                .map(m -> isFinal ? buildFinalRow(m) : buildInitialRow(m))
                .reduce("", String::concat);

        String signBoxes = data.members().stream()
                .map(m -> {
                    if (isFinal && m.equity() != null) {
                        return """
                                <div class="sig-box">
                                  <div class="name">%s</div>
                                  <div class="pct">%.2f%%</div>
                                  <div class="pct-label">최종 지분</div>
                                  <div class="at">%s</div>
                                  <div class="ip">IP: %s</div>
                                </div>""".formatted(
                                m.username(), m.equity(),
                                m.signedAt() != null ? m.signedAt().format(FMT) : "미서명",
                                m.ipAddress() != null ? m.ipAddress() : "-");
                    } else {
                        return """
                                <div class="sig-box">
                                  <div class="name">%s</div>
                                  <div class="at">%s</div>
                                  <div class="ip">IP: %s</div>
                                </div>""".formatted(
                                m.username(),
                                m.signedAt() != null ? m.signedAt().format(FMT) : "미서명",
                                m.ipAddress() != null ? m.ipAddress() : "-");
                    }
                })
                .reduce("", String::concat);

        String clauses = isFinal ? buildFinalClauses() : buildInitialClauses();

        return template
                .replace("{{contractTypeLabel}}", contractTypeLabel)
                .replace("{{contractSubtitle}}", contractSubtitle)
                .replace("{{tableHeader}}", tableHeader)
                .replace("{{memberRows}}", memberRows)
                .replace("{{clauses}}", clauses)
                .replace("{{signatureBoxes}}", signBoxes)
                .replace("{{projectName}}", data.projectName())
                .replace("{{repoOwner}}", data.repoOwner())
                .replace("{{repoName}}", data.repoName())
                .replace("{{contractId}}", data.contractId().toString())
                .replace("{{createdAt}}", data.createdAt() != null ? data.createdAt().format(FMT) : "-")
                .replace("{{completedAt}}", data.completedAt() != null ? data.completedAt().format(FMT) : "-");
    }

    private String buildInitialRow(ContractPdfData.MemberPdfRow m) {
        return """
                <tr>
                  <td class="name">%s</td>
                  <td>%s</td>
                </tr>""".formatted(
                m.username(),
                m.signedAt() != null ? m.signedAt().format(FMT) : "미서명");
    }

    private String buildFinalRow(ContractPdfData.MemberPdfRow m) {
        double eq = m.equity() != null ? m.equity() : 0.0;
        return """
                <tr>
                  <td class="name">%s</td>
                  <td class="pct">%.2f%%</td>
                  <td>%.2f</td>
                  <td>%d</td><td>%d</td><td>%d</td><td>%d</td>
                </tr>""".formatted(
                m.username(), eq,
                m.rawScore(), m.commits(), m.prs(), m.reviews(), m.issues());
    }

    private String buildInitialClauses() {
        return """
                <p data-n="1">본 합의서는 GitEquity 플랫폼을 통해 프로젝트 참여자 전원이 기여도 기반 지분 산정 방식에 동의함을 확인한다.</p>
                <p data-n="2">각 참여자는 프로젝트 기간 동안의 GitHub 기여 데이터(커밋, PR, 리뷰, 이슈)를 기반으로 최종 지분이 산정됨에 동의한다.</p>
                <p data-n="3">지분은 사전에 목표치를 정하지 않으며, 프로젝트 종료 시점의 실제 기여도만을 기준으로 시스템이 자동 산정한다.</p>
                <p data-n="4">프로젝트 종료 시 GitEquity의 기여도 측정 결과를 최종 지분 기준으로 수용하는 데 모든 서명 참여자가 동의한다.</p>
                <p data-n="5">본 합의서는 전자서명이 완료된 시점부터 효력을 가지며, 서명 IP 주소가 기록된다.</p>
                """;
    }

    private String buildFinalClauses() {
        return """
                <p data-n="1">본 계약은 GitEquity 플랫폼이 수집한 GitHub 기여 데이터를 기반으로 산출한 최종 지분율을 공식화한다.</p>
                <p data-n="2">지분율은 커밋, PR, 리뷰, 이슈의 가중 합산 점수를 전체 합 대비 비율로 산출하며, 순환 복잡도(CCN) 및 파일 중요도 가중치를 반영한다.</p>
                <p data-n="3">위 표의 '최종 지분'이 확정 지분이며, 이후 수익 분배·주식 발행·정산 등의 기준으로 활용된다.</p>
                <p data-n="4">지분율은 프로젝트 기간 전체의 실제 기여 데이터에 의한 결과이며, 모든 서명 참여자는 이에 동의한다.</p>
                <p data-n="5">본 계약서는 전자서명이 완료된 시점부터 법적 효력을 가지며, 서명 IP 주소가 기록된다.</p>
                """;
    }

    private String loadTemplate() throws IOException {
        try (InputStream is = new ClassPathResource("templates/contract_template.html").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private byte[] convertToPdf(String html) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);

        ConverterProperties props = new ConverterProperties();
        DefaultFontProvider fontProvider = new DefaultFontProvider(true, true, false);

        ClassPathResource fontRes = new ClassPathResource("fonts/NotoSansKR-Regular.ttf");
        if (fontRes.exists()) {
            fontProvider.addFont(fontRes.getContentAsByteArray());
            log.debug("NotoSansKR font loaded");
        } else {
            log.warn("NotoSansKR-Regular.ttf not found — Korean may not render correctly");
        }

        props.setFontProvider(fontProvider);
        HtmlConverter.convertToPdf(html, writer, props);

        return baos.toByteArray();
    }
}
