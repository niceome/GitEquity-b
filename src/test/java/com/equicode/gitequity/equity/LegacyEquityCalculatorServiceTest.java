package com.equicode.gitequity.equity;

import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.fixture.ContributionFixture;
import com.equicode.gitequity.repository.ContributionRepository;
import com.equicode.gitequity.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.equicode.gitequity.fixture.ContributionFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyEquityCalculatorServiceTest {

    @Mock  ContributionRepository contributionRepository;
    @Mock  ProjectRepository      projectRepository;
    @Spy   TypeScoreCalculator    typeScoreCalculator;

    @InjectMocks
    LegacyEquityCalculatorService service;

    @BeforeEach
    void stubProject() {
        lenient().when(projectRepository.findById(1L)).thenReturn(Optional.of(PROJECT));
    }

    // ── 1. 정상 흐름 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("5명 기여 데이터 → 지분율 합이 100%이어야 한다")
    void calculate_fiveUsers_sumTo100Percent() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.isSumValid()).isTrue();
        assertThat(result.equities()).hasSize(5);
    }

    @Test
    @DisplayName("Review 위주 Carol이 1위이어야 한다")
    void calculate_carolIsFirst() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.equities().get(0).username()).isEqualTo("carol");
    }

    @Test
    @DisplayName("순위: Carol > Eve > Dave > Bob > Alice")
    void calculate_correctRanking() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);
        List<String> ranked = result.equities().stream().map(UserEquity::username).toList();

        assertThat(ranked).containsExactly("carol", "eve", "dave", "bob", "alice");
    }

    @Test
    @DisplayName("Alice 지분율은 약 3.23%이어야 한다")
    void calculate_alicePercentage() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);
        UserEquity alice = result.forUser(ALICE.getId());

        // Alice score=1.5, total=46.5 → 1.5/46.5 * 100 ≈ 3.23%
        assertThat(alice.percentage()).isCloseTo(3.23, within(0.1));
    }

    @Test
    @DisplayName("totalRawScore는 46.5이어야 한다")
    void calculate_totalRawScore() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.totalRawScore()).isCloseTo(TOTAL_EXPECTED, within(0.001));
    }

    // ── 2. 커스텀 가중치 반영 ─────────────────────────────────────────────────

    @Test
    @DisplayName("weightConfig PR 변경은 효과가 없어야 한다 (PR은 집계 대상 제외, Carol 1위 유지)")
    void calculate_customWeight_pr_hasNoEffect_carolFirst() {
        var projectWithCustomWeight = com.equicode.gitequity.domain.Project.builder()
                .id(1L).name("test").repoOwner("org").repoName("repo")
                .owner(ALICE).weightConfig(Map.of("PR", 100.0))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectWithCustomWeight));
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.equities().get(0).username()).isEqualTo("carol");
    }

    // ── 3. 엣지 케이스 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("기여 데이터 없으면 빈 equities를 반환해야 한다")
    void calculate_noContributions_emptyResult() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(List.of());

        EquityResult result = service.calculate(1L);

        assertThat(result.equities()).isEmpty();
        assertThat(result.totalRawScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("프로젝트가 없으면 CustomException이 발생해야 한다")
    void calculate_projectNotFound_throwsException() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(99L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("단독 기여자는 지분 100%이어야 한다")
    void calculate_singleContributor_100Percent() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(aliceContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.equities()).hasSize(1);
        assertThat(result.equities().get(0).percentage()).isCloseTo(100.0, within(0.01));
    }
}
