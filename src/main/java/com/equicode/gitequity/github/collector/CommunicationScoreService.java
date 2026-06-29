package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.equity.quality.CommunicationCapCalculator;
import com.equicode.gitequity.repository.CommentContributionRepository;
import com.equicode.gitequity.repository.PrContributionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S6(커뮤니케이션 기여) 사용자별 총점에 cap을 적용한 최종 점수를 계산한다.
 *
 * cap 기준 "PR 점수 합"은 해당 사용자의 병합 PR netLines 합("PR 최종 diff 실질 변경량")을 사용한다.
 */
@Service
@RequiredArgsConstructor
public class CommunicationScoreService {

    private final CommentContributionRepository commentContributionRepository;
    private final PrContributionRepository prContributionRepository;
    private final CommunicationCapCalculator communicationCapCalculator;

    @Transactional(readOnly = true)
    public Map<Long, Double> calculate(Long projectId) {
        Map<Long, Double> rawTotals = sumByAuthor(commentContributionRepository.sumRawScoreGroupByAuthor(projectId));
        Map<Long, Double> prScoreSums = sumByAuthor(prContributionRepository.sumNetLinesGroupByAuthor(projectId));

        Map<Long, Double> result = new LinkedHashMap<>();
        rawTotals.forEach((authorGithubId, raw) -> {
            double prScoreSum = prScoreSums.getOrDefault(authorGithubId, 0.0);
            result.put(authorGithubId, communicationCapCalculator.apply(raw, prScoreSum));
        });
        return result;
    }

    private Map<Long, Double> sumByAuthor(List<Object[]> rows) {
        Map<Long, Double> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], ((Number) row[1]).doubleValue());
        }
        return map;
    }
}
