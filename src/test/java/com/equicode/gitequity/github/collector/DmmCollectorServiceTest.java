package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.equity.dmm.CommitDmmResult;
import com.equicode.gitequity.equity.dmm.DmmAnalyzer;
import com.equicode.gitequity.repository.PrContributionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DmmCollectorServiceTest {

    private static final Project PROJECT = Project.builder()
            .id(1L).name("test-project").repoOwner("test-org").repoName("test-repo")
            .build();

    private static final String REPO_PATH = "/tmp/test-repo";

    @Mock DmmAnalyzer dmmAnalyzer;
    @Mock PrContributionRepository prContributionRepository;

    @InjectMocks
    DmmCollectorService service;

    @Test
    @DisplayName("pydriller가 없으면 분석을 시도하지 않고 0을 반환해야 한다")
    void collect_pydrillerUnavailable_returnsZero() {
        when(dmmAnalyzer.isAvailable()).thenReturn(false);

        int updated = service.collect(PROJECT, REPO_PATH);

        assertThat(updated).isEqualTo(0);
        verifyNoInteractions(prContributionRepository);
    }

    @Test
    @DisplayName("분석 프로세스가 실패하면 예외 없이 0을 반환해야 한다")
    void collect_analyzeThrows_returnsZeroWithoutCrashing() throws Exception {
        when(dmmAnalyzer.isAvailable()).thenReturn(true);
        when(dmmAnalyzer.analyze(REPO_PATH)).thenThrow(new IllegalStateException("python failed"));

        int updated = service.collect(PROJECT, REPO_PATH);

        assertThat(updated).isEqualTo(0);
        verifyNoInteractions(prContributionRepository);
    }

    @Test
    @DisplayName("PR 번호로 매칭되는 PrContribution의 dmm_complexity를 갱신해야 한다")
    void collect_matchingPr_updatesDmmComplexity() throws Exception {
        when(dmmAnalyzer.isAvailable()).thenReturn(true);
        when(dmmAnalyzer.analyze(REPO_PATH)).thenReturn(List.of(
                new CommitDmmResult("hash1", "feat: a (#1)", 1.0, 0.5, 0.0, 1)
        ));

        PrContribution pr1 = samplePr(1);
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 1)).thenReturn(Optional.of(pr1));

        int updated = service.collect(PROJECT, REPO_PATH);

        assertThat(updated).isEqualTo(1);
        assertThat(pr1.getDmmComplexity()).isCloseTo(0.5, within(0.0001));
    }

    @Test
    @DisplayName("PR 번호가 없는 커밋(prNumber=null)은 건너뛰어야 한다")
    void collect_commitWithoutPrNumber_isSkipped() throws Exception {
        when(dmmAnalyzer.isAvailable()).thenReturn(true);
        when(dmmAnalyzer.analyze(REPO_PATH)).thenReturn(List.of(
                new CommitDmmResult("hash1", "chore: misc", 1.0, 1.0, 1.0, null)
        ));

        int updated = service.collect(PROJECT, REPO_PATH);

        assertThat(updated).isEqualTo(0);
        verify(prContributionRepository, never()).findByProjectIdAndPrNumber(any(), any());
    }

    @Test
    @DisplayName("DMM 세 지표가 모두 null인 커밋은 건너뛰어야 한다")
    void collect_allDmmNull_isSkipped() throws Exception {
        when(dmmAnalyzer.isAvailable()).thenReturn(true);
        when(dmmAnalyzer.analyze(REPO_PATH)).thenReturn(List.of(
                new CommitDmmResult("hash1", "feat: a (#1)", null, null, null, 1)
        ));

        int updated = service.collect(PROJECT, REPO_PATH);

        assertThat(updated).isEqualTo(0);
        verify(prContributionRepository, never()).findByProjectIdAndPrNumber(any(), any());
    }

    @Test
    @DisplayName("PR 번호에 매칭되는 PrContribution이 없으면 건너뛰어야 한다")
    void collect_noMatchingPrContribution_isSkipped() throws Exception {
        when(dmmAnalyzer.isAvailable()).thenReturn(true);
        when(dmmAnalyzer.analyze(REPO_PATH)).thenReturn(List.of(
                new CommitDmmResult("hash1", "feat: a (#999)", 1.0, 1.0, 1.0, 999)
        ));
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 999)).thenReturn(Optional.empty());

        int updated = service.collect(PROJECT, REPO_PATH);

        assertThat(updated).isEqualTo(0);
    }

    @Test
    @DisplayName("여러 커밋 중 일부만 매칭되면 매칭된 수만큼만 반환해야 한다")
    void collect_mixedResults_returnsOnlyMatchedCount() throws Exception {
        when(dmmAnalyzer.isAvailable()).thenReturn(true);
        when(dmmAnalyzer.analyze(REPO_PATH)).thenReturn(List.of(
                new CommitDmmResult("hash1", "feat: a (#1)", 1.0, 1.0, 1.0, 1),
                new CommitDmmResult("hash2", "chore: b", 1.0, 1.0, 1.0, null),
                new CommitDmmResult("hash3", "fix: c (#3)", null, null, null, 3),
                new CommitDmmResult("hash4", "feat: d (#4)", 0.0, 0.0, 0.0, 4)
        ));

        PrContribution pr1 = samplePr(1);
        PrContribution pr4 = samplePr(4);
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 1)).thenReturn(Optional.of(pr1));
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 4)).thenReturn(Optional.of(pr4));

        int updated = service.collect(PROJECT, REPO_PATH);

        assertThat(updated).isEqualTo(2);
        assertThat(pr1.getDmmComplexity()).isCloseTo(1.0, within(0.0001));
        assertThat(pr4.getDmmComplexity()).isCloseTo(0.0, within(0.0001));
    }

    private PrContribution samplePr(int prNumber) {
        return PrContribution.builder()
                .project(PROJECT)
                .authorGithubId(100L)
                .prNumber(prNumber)
                .title("title-" + prNumber)
                .isMerged(true)
                .additions(10)
                .deletions(5)
                .netLines(10)
                .build();
    }
}
