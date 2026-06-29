package com.equicode.gitequity.equity.dmm;

import java.util.Optional;

/**
 * dmm_analyzer.py가 출력하는 JSON 배열의 단건 (커밋 = squash merge PR 1개).
 *
 * dmm_unit_size/complexity/interfacing은 해당 커밋에 측정 대상 파일이 없으면
 * pydriller가 None을 반환하며, 이는 null로 역직렬화된다.
 */
public record CommitDmmResult(
        String hash,
        String message,
        Double dmmUnitSize,
        Double dmmUnitComplexity,
        Double dmmUnitInterfacing,
        Integer prNumber
) {
    /** 세 DMM 지표 중 null이 아닌 값들의 평균. 모두 null이면 empty. */
    public Optional<Double> averageDmm() {
        double sum = 0;
        int count = 0;
        if (dmmUnitSize != null)         { sum += dmmUnitSize;         count++; }
        if (dmmUnitComplexity != null)   { sum += dmmUnitComplexity;   count++; }
        if (dmmUnitInterfacing != null)  { sum += dmmUnitInterfacing;  count++; }
        return count == 0 ? Optional.empty() : Optional.of(sum / count);
    }
}
