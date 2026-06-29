package com.equicode.gitequity.claim;

import com.equicode.gitequity.domain.*;
import com.equicode.gitequity.equity.EquityCalculatorService;
import com.equicode.gitequity.equity.EquityResult;
import com.equicode.gitequity.equity.UserEquity;
import com.equicode.gitequity.repository.NonCodeContributionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimAdjustmentServiceTest {

    private static final User ALICE = User.builder().id(1L).githubId(101L).username("alice").build();
    private static final User BOB   = User.builder().id(2L).githubId(102L).username("bob").build();
    private static final Project PROJECT = Project.builder()
            .id(1L).owner(ALICE).name("proj").repoOwner("org").repoName("repo").build();

    @Mock EquityCalculatorService equityCalculatorService;
    @Mock NonCodeContributionRepository nonCodeContributionRepository;
    @Spy  ClaimAdjustmentCalculator calculator = new ClaimAdjustmentCalculator(0.05);

    @InjectMocks
    ClaimAdjustmentService service;

    private EquityResult baseResult() {
        List<UserEquity> equities = List.of(
                new UserEquity(1L, "alice", 60.0, 60.0, Map.of()),
                new UserEquity(2L, "bob", 40.0, 40.0, Map.of())
        );
        return new EquityResult(1L, equities, 100.0, LocalDateTime.now(), "2.0.0");
    }

    private NonCodeContribution confirmedClaim(User beneficiary, NonCodeContributionType type) {
        return NonCodeContribution.builder()
                .project(PROJECT).claimer(BOB).beneficiary(beneficiary)
                .type(type).description("desc").occurredAt(LocalDate.now())
                .status(ClaimStatus.CONFIRMED)
                .build();
    }

    @Test
    @DisplayName("확정된 claim이 없으면 기본 지분이 그대로 반환된다")
    void noConfirmedClaims_returnsBaseUnchanged() {
        when(equityCalculatorService.calculate(1L)).thenReturn(baseResult());
        when(nonCodeContributionRepository.findByProjectIdAndStatus(1L, ClaimStatus.CONFIRMED)).thenReturn(List.of());

        EquityResult result = service.applyAdjustment(1L);

        assertThat(result.forUser(1L).percentage()).isCloseTo(60.0, within(0.01));
        assertThat(result.forUser(2L).percentage()).isCloseTo(40.0, within(0.01));
        assertThat(result.isSumValid()).isTrue();
    }

    @Test
    @DisplayName("확정된 claim 점수에 따라 보정되고 전체 합이 100%로 재정규화된다")
    void confirmedClaims_adjustAndRenormalize() {
        when(equityCalculatorService.calculate(1L)).thenReturn(baseResult());
        // alice 수혜 claim 1건 (weight=1.0) → multiplier = 1 + 1.0×0.05 = 1.05
        when(nonCodeContributionRepository.findByProjectIdAndStatus(1L, ClaimStatus.CONFIRMED))
                .thenReturn(List.of(confirmedClaim(ALICE, NonCodeContributionType.MENTORING)));

        EquityResult result = service.applyAdjustment(1L);

        // adjustedRaw: alice=60×1.05=63, bob=40×1.0=40, sum=103
        // alice% = 63/103×100 ≈ 61.17, bob% = 40/103×100 ≈ 38.83
        assertThat(result.forUser(1L).percentage()).isCloseTo(61.17, within(0.01));
        assertThat(result.forUser(2L).percentage()).isCloseTo(38.83, within(0.01));
        assertThat(result.isSumValid()).isTrue();
    }

    @Test
    @DisplayName("보정 배수는 ±10%로 클램프된 뒤 재정규화된다")
    void multiplier_clampedBeforeRenormalization() {
        when(equityCalculatorService.calculate(1L)).thenReturn(baseResult());
        // alice 수혜 claim 10건 (weight=1.0 each) → raw multiplier = 1 + 10×0.05 = 1.5 → 1.1로 클램프
        List<NonCodeContribution> claims = IntStream.range(0, 10)
                .mapToObj(i -> confirmedClaim(ALICE, NonCodeContributionType.MENTORING))
                .toList();
        when(nonCodeContributionRepository.findByProjectIdAndStatus(1L, ClaimStatus.CONFIRMED)).thenReturn(claims);

        EquityResult result = service.applyAdjustment(1L);

        // adjustedRaw: alice=60×1.1=66, bob=40×1.0=40, sum=106
        // alice% = 66/106×100 ≈ 62.26, bob% = 40/106×100 ≈ 37.74
        assertThat(result.forUser(1L).percentage()).isCloseTo(62.26, within(0.01));
        assertThat(result.forUser(2L).percentage()).isCloseTo(37.74, within(0.01));
        assertThat(result.isSumValid()).isTrue();
    }

    @Test
    @DisplayName("기여 데이터가 없으면(equities 비어있음) 그대로 반환한다")
    void emptyBase_returnsAsIs() {
        EquityResult empty = new EquityResult(1L, List.of(), 0.0, LocalDateTime.now(), "2.0.0");
        when(equityCalculatorService.calculate(1L)).thenReturn(empty);

        EquityResult result = service.applyAdjustment(1L);

        assertThat(result.equities()).isEmpty();
        verifyNoInteractions(nonCodeContributionRepository);
    }
}
