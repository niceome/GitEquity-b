package com.equicode.gitequity.equity.quality;

import org.springframework.stereotype.Component;

/**
 * 이슈/PR 코멘트 "개수"가 아닌 "실질성"을 평가하는 S6(커뮤니케이션 기여) 점수 계산기.
 *
 * 근거: Hundhausen et al. (2023) 커뮤니케이션 기여 연구.
 *
 * 점수 = (길이 &lt;30자 → 0.1, 그 외 min(1.0, 길이/300)) + (코드블록 포함 시 0.5)
 */
@Component
public class CommentScorer {

    private static final String CODE_BLOCK_MARKER = "```";
    private static final double CODE_BLOCK_BONUS = 0.5;

    private static final int SHORT_THRESHOLD = 30;
    private static final double SHORT_SCORE = 0.1;
    private static final double LENGTH_DIVISOR = 300.0;
    private static final double MAX_LENGTH_SCORE = 1.0;

    public double score(String body) {
        int length = body != null ? body.trim().length() : 0;
        double score = length < SHORT_THRESHOLD
                ? SHORT_SCORE
                : Math.min(MAX_LENGTH_SCORE, length / LENGTH_DIVISOR);

        if (hasCodeBlock(body)) {
            score += CODE_BLOCK_BONUS;
        }
        return score;
    }

    public boolean hasCodeBlock(String text) {
        return text != null && text.contains(CODE_BLOCK_MARKER);
    }
}
