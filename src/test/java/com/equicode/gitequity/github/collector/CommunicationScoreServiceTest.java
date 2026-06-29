package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.equity.quality.CommunicationCapCalculator;
import com.equicode.gitequity.repository.CommentContributionRepository;
import com.equicode.gitequity.repository.PrContributionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunicationScoreServiceTest {

    @Mock CommentContributionRepository commentContributionRepository;
    @Mock PrContributionRepository prContributionRepository;
    @Spy  CommunicationCapCalculator communicationCapCalculator = new CommunicationCapCalculator(0.1);

    @InjectMocks
    CommunicationScoreService service;

    @Test
    @DisplayName("raw 합이 cap(PR netLines 합의 10%) 이하인 사용자는 raw 그대로 반환된다")
    void rawBelowCap_returnsRaw() {
        when(commentContributionRepository.sumRawScoreGroupByAuthor(1L))
                .thenReturn(List.<Object[]>of(new Object[]{100L, 5.0}));
        when(prContributionRepository.sumNetLinesGroupByAuthor(1L))
                .thenReturn(List.<Object[]>of(new Object[]{100L, 100}));

        Map<Long, Double> result = service.calculate(1L);

        assertThat(result.get(100L)).isCloseTo(5.0, within(0.0001));
    }

    @Test
    @DisplayName("raw 합이 cap을 초과한 사용자는 cap으로 제한된다")
    void rawAboveCap_clampsToCap() {
        when(commentContributionRepository.sumRawScoreGroupByAuthor(1L))
                .thenReturn(List.<Object[]>of(new Object[]{200L, 50.0}));
        when(prContributionRepository.sumNetLinesGroupByAuthor(1L))
                .thenReturn(List.<Object[]>of(new Object[]{200L, 100}));

        Map<Long, Double> result = service.calculate(1L);

        // cap = 100 × 0.1 = 10.0
        assertThat(result.get(200L)).isCloseTo(10.0, within(0.0001));
    }

    @Test
    @DisplayName("PR 기여가 없는 사용자는 cap이 0이라 커뮤니케이션 점수도 0이 된다")
    void userWithNoPrContributions_yieldsZero() {
        when(commentContributionRepository.sumRawScoreGroupByAuthor(1L))
                .thenReturn(List.<Object[]>of(new Object[]{300L, 2.0}));
        when(prContributionRepository.sumNetLinesGroupByAuthor(1L))
                .thenReturn(List.of());

        Map<Long, Double> result = service.calculate(1L);

        assertThat(result.get(300L)).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("여러 사용자의 점수를 동시에 계산할 수 있다")
    void multipleUsers_calculatedIndependently() {
        when(commentContributionRepository.sumRawScoreGroupByAuthor(1L))
                .thenReturn(List.<Object[]>of(new Object[]{100L, 5.0}, new Object[]{200L, 50.0}));
        when(prContributionRepository.sumNetLinesGroupByAuthor(1L))
                .thenReturn(List.<Object[]>of(new Object[]{100L, 100}, new Object[]{200L, 100}));

        Map<Long, Double> result = service.calculate(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(100L)).isCloseTo(5.0, within(0.0001));
        assertThat(result.get(200L)).isCloseTo(10.0, within(0.0001));
    }
}
