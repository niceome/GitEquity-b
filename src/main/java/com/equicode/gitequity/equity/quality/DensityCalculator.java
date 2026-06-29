package com.equicode.gitequity.equity.quality;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Hönel et al. (2020)의 source code density를 unified diff patch 텍스트로부터 계산한다.
 *
 * density = netLines(실질 변경 라인 수) / grossLines(+/- 변경 라인 총 수)
 *
 * 다음을 "비실질(non-substantive)" 라인으로 분류하여 netLines에서 제외한다:
 *   - 공백/빈 라인
 *   - 주석 전용 라인 (//, #, 블록 주석 시작/끝/연속 기호, &lt;!-- 로 시작
 *     — Java/JS/TS/Python/HTML 커버)
 *   - import / package / using / Python "from ... import" 문
 *   - 괄호·세미콜론만 있는 라인 ([{}();]+)
 *
 * 이 계산은 "라인 단위 근사(line-level approximation)"이다. 각 변경 라인을
 * 그 라인의 시작 문자만으로 독립적으로 판정하며, 여러 줄에 걸친 블록 주석
 * 내부에 실제로 포함되어 있는지를 추적하지 않는다 — 의도된 설계다.
 * 예를 들어 블록 주석 중간에 위치한 일반 텍스트 라인(주석 기호 없이 시작)은
 * 실질 라인으로 집계될 수 있다.
 */
@Component
public class DensityCalculator {

    private static final Pattern COMMENT_LINE = Pattern.compile("^(//|#|/\\*|\\*/?|<!--)");

    private static final Pattern IMPORT_LINE =
            Pattern.compile("^(import\\s|package\\s|using\\s|from\\s+\\S+\\s+import\\s)");

    private static final Pattern PUNCTUATION_ONLY = Pattern.compile("^[{}();]+$");

    /**
     * 단일 파일의 patch 텍스트를 분석한다.
     *
     * @param patch unified diff 텍스트 (nullable — GitHub가 patch를 제공하지 않는 경우)
     * @return netLines/grossLines 집계 결과 (patch가 null/blank면 zero)
     */
    public DensityResult analyze(String patch) {
        if (patch == null || patch.isBlank()) return DensityResult.zero();

        int gross = 0;
        int net = 0;

        for (String line : patch.split("\n", -1)) {
            if (line.startsWith("+++") || line.startsWith("---")) continue;
            if (line.isEmpty()) continue;

            char marker = line.charAt(0);
            if (marker != '+' && marker != '-') continue;

            gross++;

            if (!isNonSubstantive(line.substring(1).trim())) {
                net++;
            }
        }

        return new DensityResult(net, gross);
    }

    private boolean isNonSubstantive(String content) {
        if (content.isEmpty()) return true;
        if (COMMENT_LINE.matcher(content).find()) return true;
        if (IMPORT_LINE.matcher(content).find()) return true;
        if (PUNCTUATION_ONLY.matcher(content).matches()) return true;
        return false;
    }
}
