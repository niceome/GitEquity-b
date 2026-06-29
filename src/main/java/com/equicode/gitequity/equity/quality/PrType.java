package com.equicode.gitequity.equity.quality;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Levin & Yehudai (2017)의 커밋 분류 체계를 PR 단위로 적용한 타입.
 *
 * weight는 후속 단계(점수 결합 시)에 사용할 기본값이며,
 * 추후 프로젝트별 설정(weightConfig)으로 오버라이드할 수 있도록
 * enum 필드로 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum PrType {
    FEAT(1.0),
    FIX(0.9),
    REFACTOR(0.8),
    TEST(0.7),
    DOCS(0.4),
    CHORE(0.3),
    STYLE(0.2),
    UNKNOWN(0.6);

    private final double weight;
}
