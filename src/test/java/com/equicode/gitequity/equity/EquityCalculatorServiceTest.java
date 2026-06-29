package com.equicode.gitequity.equity;

import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.equity.dmm.DmmFactor;
import com.equicode.gitequity.github.collector.CommunicationScoreService;
import com.equicode.gitequity.repository.PrContributionRepository;
import com.equicode.gitequity.repository.ProjectRepository;
import com.equicode.gitequity.repository.ReviewContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquityCalculatorServiceTest {

    private static final User ALICE = User.builder().id(1L).githubId(101L).username("alice").build();
    private static final User BOB   = User.builder().id(2L).githubId(102L).username("bob").build();
    private static final User CAROL = User.builder().id(3L).githubId(103L).username("carol").build();
    private static final User DAVE  = User.builder().id(4L).githubId(104L).username("dave").build();

    @Mock PrContributionRepository prContributionRepository;
    @Mock ReviewContributionRepository reviewContributionRepository;
    @Mock CommunicationScoreService communicationScoreService;
    @Mock UserRepository userRepository;
    @Mock ProjectRepository projectRepository;
    @Spy  DmmFactor dmmFactor = new DmmFactor(0.7, 0.6);
    @Spy  ExplorationCapCalculator explorationCapCalculator = new ExplorationCapCalculator(0.15);
    @Spy  EquityWeights equityWeights = new EquityWeights(0.25, 0.50, 0.25);

    @InjectMocks
    EquityCalculatorService service;

    @BeforeEach
    void stubDefaults() {
        lenient().when(projectRepository.existsById(1L)).thenReturn(true);
        lenient().when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of());
        lenient().when(reviewContributionRepository.sumScoreGroupByReviewer(1L)).thenReturn(List.of());
        lenient().when(reviewContributionRepository.findDistinctPrNumbersByProjectId(1L)).thenReturn(List.of());
        lenient().when(communicationScoreService.calculate(1L)).thenReturn(Map.of());
    }

    private PrContribution pr(Long authorGithubId, Integer prNumber, int netLines, double density,
                               String prType, boolean merged, Double dmm, List<Long> coAuthors) {
        return PrContribution.builder()
                .authorGithubId(authorGithubId)
                .prNumber(prNumber)
                .title("title")
                .isMerged(merged)
                .additions(netLines)
                .deletions(0)
                .netLines(netLines)
                .density(density)
                .prType(prType)
                .dmmComplexity(dmm)
                .coAuthorGithubIds(coAuthors)
                .build();
    }

    // ── 1. PR 점수 공식 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("merged PR 1건의 점수 = netLines × density × prType.weight × dmmFactor × 1.0")
    void singleMergedPr_basicFormula() {
        when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of(
                pr(101L, 1, 100, 0.5, "FEAT", true, null, null)
        ));
        when(userRepository.findByGithubId(101L)).thenReturn(Optional.of(ALICE));

        EquityResult result = service.calculate(1L);

        UserEquity alice = result.forUser(1L);
        // prScore = 100 × 0.5 × 1.0(FEAT) × 1.0(dmm null) × 1.0(merged) = 50.0
        assertThat(alice.byType().get(ContributionType.PR)).isCloseTo(50.0, within(0.0001));
        // 단독 기여자 → norm(PR)=1.0, userTotal = 0.50, percentage = 100%
        assertThat(alice.percentage()).isCloseTo(100.0, within(0.01));
        assertThat(result.algorithmVersion()).isEqualTo("3.0.0");
        assertThat(EquityCalculatorService.ALGORITHM_VERSION).isEqualTo("3.0.0");
    }

    @Test
    @DisplayName("dmm 점수가 있으면 dmmFactor가 PR 점수에 곱해진다")
    void dmmComplexity_appliesFactor() {
        when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of(
                pr(101L, 1, 100, 1.0, "FEAT", true, 1.0, null)
        ));
        when(userRepository.findByGithubId(101L)).thenReturn(Optional.of(ALICE));

        EquityResult result = service.calculate(1L);

        UserEquity alice = result.forUser(1L);
        // dmmFactor(1.0) = 0.7 + 0.6×1.0 = 1.3 → prScore = 100 × 1.0 × 1.0 × 1.3 × 1.0 = 130.0
        assertThat(alice.byType().get(ContributionType.PR)).isCloseTo(130.0, within(0.0001));
    }

    // ── 2. 사용자 총점 정규화 (w = 0.75/0.15/0.10) ────────────────────────────

    @Test
    @DisplayName("PR/리뷰/코멘트 점수를 각각 팀 최대값으로 정규화하여 가중합산한다")
    void userTotal_normalizedAcrossDimensions() {
        when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of(
                pr(101L, 1, 100, 1.0, "FEAT", true, null, null), // alice prScore=100
                pr(102L, 2, 50, 1.0, "FEAT", true, null, null)   // bob   prScore=50
        ));
        when(reviewContributionRepository.sumScoreGroupByReviewer(1L))
                .thenReturn(List.<Object[]>of(new Object[]{102L, 10.0})); // bob review=10
        when(communicationScoreService.calculate(1L))
                .thenReturn(Map.of(101L, 5.0)); // alice comment=5
        when(userRepository.findByGithubId(101L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findByGithubId(102L)).thenReturn(Optional.of(BOB));

        EquityResult result = service.calculate(1L);

        // alice: normPr=100/100=1.0, normReview=0/10=0, normComment=5/5=1.0
        //        → total = 0.25×1.0 + 0.50×0 + 0.25×1.0 = 0.50
        // bob:   normPr=50/100=0.5, normReview=10/10=1.0, normComment=0/5=0
        //        → total = 0.25×0.5 + 0.50×1.0 + 0.25×0 = 0.625
        // grandTotal = 1.125 → alice% = 44.44, bob% = 55.56
        assertThat(result.forUser(1L).percentage()).isCloseTo(44.44, within(0.01));
        assertThat(result.forUser(2L).percentage()).isCloseTo(55.56, within(0.01));
        assertThat(result.isSumValid()).isTrue();
        assertThat(result.equities().get(0).username()).isEqualTo("alice");
    }

    // ── 3. closed-unmerged PR "탐색 기여" (S11) ───────────────────────────────

    @Test
    @DisplayName("리뷰가 달린 unmerged PR은 0.3 가중치로 인정되고, merged 점수합의 15%로 cap된다")
    void unmergedPrWithReview_cappedAtFifteenPercentOfMerged() {
        when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of(
                pr(103L, 1, 100, 1.0, "FEAT", true, null, null),  // merged: prScore=100
                pr(103L, 50, 100, 1.0, "FEAT", false, null, null) // unmerged: rawScore=100×0.3=30
        ));
        when(reviewContributionRepository.findDistinctPrNumbersByProjectId(1L)).thenReturn(List.of(50));
        when(userRepository.findByGithubId(103L)).thenReturn(Optional.of(CAROL));

        EquityResult result = service.calculate(1L);

        UserEquity carol = result.forUser(3L);
        // cap = 100 × 0.15 = 15 < 30 → capped to 15 → prScoreSum = 100 + 15 = 115
        assertThat(carol.byType().get(ContributionType.PR)).isCloseTo(115.0, within(0.0001));
    }

    @Test
    @DisplayName("리뷰가 없는 unmerged PR은 점수에 반영되지 않는다")
    void unmergedPrWithoutReview_isIgnored() {
        when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of(
                pr(104L, 1, 100, 1.0, "FEAT", true, null, null),   // merged: prScore=100
                pr(104L, 60, 100, 1.0, "FEAT", false, null, null)  // unmerged, 리뷰 없음 → 미반영
        ));
        when(reviewContributionRepository.findDistinctPrNumbersByProjectId(1L)).thenReturn(List.of());
        when(userRepository.findByGithubId(104L)).thenReturn(Optional.of(DAVE));

        EquityResult result = service.calculate(1L);

        UserEquity dave = result.forUser(4L);
        assertThat(dave.byType().get(ContributionType.PR)).isCloseTo(100.0, within(0.0001));
    }

    // ── 4. Co-authored-by 균등 분할 (S12) ─────────────────────────────────────

    @Test
    @DisplayName("co-author가 있으면 PR 점수를 작성자+공동작성자 균등 분할한다")
    void coAuthoredPr_splitsScoreEvenly() {
        when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of(
                pr(101L, 1, 100, 1.0, "FEAT", true, null, List.of(102L))
        ));
        when(userRepository.findByGithubId(101L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findByGithubId(102L)).thenReturn(Optional.of(BOB));

        EquityResult result = service.calculate(1L);

        // prScore = 100 → 작성자(alice)+공동작성자(bob) 균등 분할 → 각 50.0
        assertThat(result.forUser(1L).byType().get(ContributionType.PR)).isCloseTo(50.0, within(0.0001));
        assertThat(result.forUser(2L).byType().get(ContributionType.PR)).isCloseTo(50.0, within(0.0001));
        // 두 사용자 모두 동일한 점수 → 지분도 50:50
        assertThat(result.forUser(1L).percentage()).isCloseTo(50.0, within(0.01));
        assertThat(result.forUser(2L).percentage()).isCloseTo(50.0, within(0.01));
    }

    // ── 5. 미등록 사용자 처리 ─────────────────────────────────────────────────

    @Test
    @DisplayName("User로 등록되지 않은 githubId는 결과에서 제외되지만 정규화 기준(team max)에는 반영된다")
    void unregisteredGithubId_excludedButAffectsNormalization() {
        when(prContributionRepository.findByProjectId(1L)).thenReturn(List.of(
                pr(101L, 1, 100, 1.0, "FEAT", true, null, null), // alice prScore=100
                pr(999L, 2, 200, 1.0, "FEAT", true, null, null)  // 미등록 githubId prScore=200
        ));
        when(userRepository.findByGithubId(101L)).thenReturn(Optional.of(ALICE));
        when(userRepository.findByGithubId(999L)).thenReturn(Optional.empty());

        EquityResult result = service.calculate(1L);

        assertThat(result.equities()).hasSize(1);
        // maxPr=200(미등록 사용자 포함) → alice normPr=100/200=0.5 → total=0.75×0.5=0.375
        // 등록된 사용자가 alice뿐이므로 grandTotal=0.375 → alice%=100%
        assertThat(result.forUser(1L).percentage()).isCloseTo(100.0, within(0.01));
    }

    // ── 6. 엣지 케이스 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("기여 데이터가 전혀 없으면 빈 equities를 반환한다")
    void noContributions_emptyResult() {
        EquityResult result = service.calculate(1L);

        assertThat(result.equities()).isEmpty();
        assertThat(result.totalRawScore()).isEqualTo(0.0);
        assertThat(result.algorithmVersion()).isEqualTo("3.0.0");
    }

    @Test
    @DisplayName("프로젝트가 없으면 CustomException이 발생한다")
    void projectNotFound_throwsException() {
        when(projectRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.calculate(99L))
                .isInstanceOf(CustomException.class);
    }
}
