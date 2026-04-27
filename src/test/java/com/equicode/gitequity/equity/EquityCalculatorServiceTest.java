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
class EquityCalculatorServiceTest {

    @Mock  ContributionRepository contributionRepository;
    @Mock  ProjectRepository      projectRepository;
    @Spy   TypeScoreCalculator    typeScoreCalculator;

    @InjectMocks
    EquityCalculatorService service;

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
    @DisplayName("PR 위주 Alice가 1위이어야 한다")
    void calculate_aliceIsFirst() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.equities().get(0).username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("순위: Alice > Bob > Dave > Carol > Eve")
    void calculate_correctRanking() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);
        List<String> ranked = result.equities().stream().map(UserEquity::username).toList();

        assertThat(ranked).containsExactly("alice", "bob", "dave", "carol", "eve");
    }

    @Test
    @DisplayName("Alice 지분율은 약 28.85%이어야 한다")
    void calculate_alicePercentage() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);
        UserEquity alice = result.forUser(ALICE.getId());

        // Alice score=66.5, total=230.5 → 66.5/230.5 * 100 ≈ 28.85%
        assertThat(alice.percentage()).isCloseTo(28.85, within(0.1));
    }

    @Test
    @DisplayName("totalRawScore는 230.5이어야 한다")
    void calculate_totalRawScore() {
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.totalRawScore()).isCloseTo(TOTAL_EXPECTED, within(0.001));
    }

    // ── 2. 커스텀 가중치 반영 ─────────────────────────────────────────────────

    @Test
    @DisplayName("weightConfig PR=0 시 Commit 위주 Bob이 1위가 되어야 한다")
    void calculate_customWeight_prZero_bobFirst() {
        var projectWithCustomWeight = com.equicode.gitequity.domain.Project.builder()
                .id(1L).name("test").repoOwner("org").repoName("repo")
                .owner(ALICE).weightConfig(Map.of("PR", 0.0))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectWithCustomWeight));
        when(contributionRepository.findByProjectId(1L)).thenReturn(allContributions());

        EquityResult result = service.calculate(1L);

        assertThat(result.equities().get(0).username()).isEqualTo("bob");
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
