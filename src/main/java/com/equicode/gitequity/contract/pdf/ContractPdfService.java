package com.equicode.gitequity.contract.pdf;

import com.equicode.gitequity.contract.dto.ContractPdfData;
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

/**
 * iText7 html2pdf를 사용하여 계약서 PDF를 생성한다.
 *
 * 한글 폰트 설정:
 *   fonts/NotoSansKR-Regular.ttf 를 classpath에 두면 자동 로딩된다.
 *   없을 경우 iText 기본 폰트를 사용 (한글 깨짐 가능).
 *
 *   다운로드: https://fonts.google.com/noto/specimen/Noto+Sans+KR
 *   위치: src/main/resources/fonts/NotoSansKR-Regular.ttf
 */
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

    // ── HTML 템플릿 렌더링 ─────────────────────────────────────────────────────

    private String buildHtml(ContractPdfData data) throws IOException {
        String template = loadTemplate();

        String memberRows = data.members().stream()
                .map(m -> """
                        <tr>
                          <td class="name">%s</td>
                          <td class="pct">%.2f%%</td>
                          <td>%.2f</td>
                          <td>%d</td><td>%d</td><td>%d</td><td>%d</td>
                        </tr>""".formatted(
                        m.username(), m.percentage(), m.rawScore(),
                        m.commits(), m.prs(), m.reviews(), m.issues()))
                .reduce("", String::concat);

        String signBoxes = data.members().stream()
                .map(m -> """
                        <div class="sig-box">
                          <div class="name">%s</div>
                          <div class="pct">%.2f%%</div>
                          <div class="at">%s</div>
                          <div class="ip">IP: %s</div>
                        </div>""".formatted(
                        m.username(), m.percentage(),
                        m.signedAt() != null ? m.signedAt().format(FMT) : "미서명",
                        m.ipAddress() != null ? m.ipAddress() : "-"))
                .reduce("", String::concat);

        return template
                .replace("{{projectName}}",  data.projectName())
                .replace("{{repoOwner}}",     data.repoOwner())
                .replace("{{repoName}}",      data.repoName())
                .replace("{{contractId}}",    data.contractId().toString())
                .replace("{{createdAt}}",     data.createdAt() != null ? data.createdAt().format(FMT) : "-")
                .replace("{{completedAt}}",   data.completedAt() != null ? data.completedAt().format(FMT) : "-")
                .replace("{{memberRows}}",    memberRows)
                .replace("{{signatureBoxes}}", signBoxes);
    }

    private String loadTemplate() throws IOException {
        try (InputStream is = new ClassPathResource("templates/contract_template.html").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ── html2pdf 변환 ─────────────────────────────────────────────────────────

    private byte[] convertToPdf(String html) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);

        ConverterProperties props = new ConverterProperties();
        DefaultFontProvider fontProvider = new DefaultFontProvider(true, true, false);

        // 한글 폰트 등록 (파일이 없으면 skip)
        ClassPathResource fontRes = new ClassPathResource("fonts/NotoSansKR-Regular.ttf");
        if (fontRes.exists()) {
            fontProvider.addFont(fontRes.getContentAsByteArray());
            log.debug("NotoSansKR font loaded");
        } else {
            log.warn("NotoSansKR-Regular.ttf not found — Korean may not render. " +
                     "Download from https://fonts.google.com/noto/specimen/Noto+Sans+KR");
        }

        props.setFontProvider(fontProvider);
        HtmlConverter.convertToPdf(html, writer, props);

        return baos.toByteArray();
    }
}
