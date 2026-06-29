package com.equicode.gitequity.equity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 사용자 총점 결합 가중치
 *
 * userTotal = w1×norm(PR점수합) + w2×norm(리뷰점수합) + w3×norm(코멘트점수합)
 *
 * 기본값 [0.50, 0.25, 0.25]. equity.weights.{pr,review,comment}로 오버라이드 가능.
 */
@Component
public class EquityWeights {

    private final double prWeight;
    private final double reviewWeight;
    private final double commentWeight;

    public EquityWeights(
            @Value("${equity.weights.pr:0.25}") double prWeight,
            @Value("${equity.weights.review:0.50}") double reviewWeight,
            @Value("${equity.weights.comment:0.25}") double commentWeight) {
        this.prWeight = prWeight;
        this.reviewWeight = reviewWeight;
        this.commentWeight = commentWeight;
    }

    public double[] defaults() {
        return new double[]{prWeight, reviewWeight, commentWeight};
    }
}
