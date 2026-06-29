package com.equicode.gitequity.equity.quality;

import org.springframework.stereotype.Component;

/**
 * 리뷰 "개수"가 아닌 "실질성"을 평가하는 S5 점수 계산기.
 *
 * 근거: McIntosh et al. (2014) 리뷰 참여도 연구.
 *
 * 점수 = base(state) × substance(본문+인라인 코멘트 총 길이) + (코드블록 포함 시 0.3)
 *
 *  base: CHANGES_REQUESTED=1.0, COMMENTED=0.5, APPROVED=0.4, 그 외=0.2
 *  substance: 길이 &lt;20자 → 0.2배, &gt;200자 → 1.3배, 그 외 1.0배
 */
@Component
public class ReviewSubstanceScorer {

    private static final String CODE_BLOCK_MARKER = "```";
    private static final double CODE_BLOCK_BONUS = 0.3;

    private static final double BASE_CHANGES_REQUESTED = 1.0;
    private static final double BASE_COMMENTED = 0.5;
    private static final double BASE_APPROVED = 0.4;
    private static final double BASE_DEFAULT = 0.2;

    private static final int SHORT_THRESHOLD = 20;
    private static final int LONG_THRESHOLD = 200;
    private static final double SUBSTANCE_SHORT = 0.2;
    private static final double SUBSTANCE_LONG = 1.3;
    private static final double SUBSTANCE_DEFAULT = 1.0;

    /**
     * @param state        리뷰 상태 ("APPROVED" | "CHANGES_REQUESTED" | "COMMENTED" | 그 외)
     * @param combinedText 리뷰 본문 + 인라인 코멘트를 합친 텍스트
     * @return S5 점수
     */
    public double score(String state, String combinedText) {
        double base = baseScore(state);
        double substance = substanceMultiplier(combinedText);
        double score = base * substance;
        if (hasCodeBlock(combinedText)) {
            score += CODE_BLOCK_BONUS;
        }
        return score;
    }

    public boolean hasCodeBlock(String text) {
        return text != null && text.contains(CODE_BLOCK_MARKER);
    }

    private double baseScore(String state) {
        if (state == null) return BASE_DEFAULT;
        return switch (state) {
            case "CHANGES_REQUESTED" -> BASE_CHANGES_REQUESTED;
            case "COMMENTED" -> BASE_COMMENTED;
            case "APPROVED" -> BASE_APPROVED;
            default -> BASE_DEFAULT;
        };
    }

    private double substanceMultiplier(String text) {
        int length = text != null ? text.trim().length() : 0;
        if (length < SHORT_THRESHOLD) return SUBSTANCE_SHORT;
        if (length > LONG_THRESHOLD) return SUBSTANCE_LONG;
        return SUBSTANCE_DEFAULT;
    }
}
