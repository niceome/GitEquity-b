package com.equicode.gitequity.equity.comparison;

import com.equicode.gitequity.equity.EquityCalculatorService;
import com.equicode.gitequity.equity.EquityResult;
import com.equicode.gitequity.equity.LegacyEquityCalculatorService;
import com.equicode.gitequity.equity.UserEquity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquityComparisonServiceTest {

    private static final String GAP_ALERT_MESSAGE =
            "산정 결과와 팀 인식 간 괴리가 큽니다. 비코드 기여 등록을 확인해보세요.";

    @Mock EquityCalculatorService equityCalculatorService;
    @Mock LegacyEquityCalculatorService legacyEquityCalculatorService;

    @InjectMocks
    EquityComparisonService service;

    private EquityResult result(String algorithmVersion, Object... userIdAndPercentagePairs) {
        List<UserEquity> equities = new java.util.ArrayList<>();
        for (int i = 0; i < userIdAndPercentagePairs.length; i += 3) {
            Long userId = (Long) userIdAndPercentagePairs[i];
            String username = (String) userIdAndPercentagePairs[i + 1];
            double pct = (Double) userIdAndPercentagePairs[i + 2];
            equities.add(new UserEquity(userId, username, pct, pct, Map.of()));
        }
        return new EquityResult(1L, equities, 100.0, LocalDateTime.now(), algorithmVersion);
    }

    @Test
    @DisplayName("legacy 대비 신규 순위 변동(rankChange = legacyRank - newRank)을 계산한다")
    void rankChange_isComputedFromLegacyAndNewRanks() {
        EquityResult legacy = result("1.0.0", 1L, "alice", 60.0, 2L, "bob", 40.0);
        EquityResult current = result("2.0.0", 2L, "bob", 60.0, 1L, "alice", 40.0);

        when(legacyEquityCalculatorService.calculate(1L)).thenReturn(legacy);
        when(equityCalculatorService.calculate(1L)).thenReturn(current);

        ComparisonReport report = service.compare(1L, null);

        assertThat(report.users()).hasSize(2);

        UserComparison bob = report.users().get(0);
        assertThat(bob.username()).isEqualTo("bob");
        assertThat(bob.legacyRank()).isEqualTo(2);
        assertThat(bob.newRank()).isEqualTo(1);
        assertThat(bob.rankChange()).isEqualTo(1);

        UserComparison alice = report.users().get(1);
        assertThat(alice.username()).isEqualTo("alice");
        assertThat(alice.legacyRank()).isEqualTo(1);
        assertThat(alice.newRank()).isEqualTo(2);
        assertThat(alice.rankChange()).isEqualTo(-1);

        assertThat(report.pearsonLegacyVsPeer()).isNull();
        assertThat(report.pearsonNewVsPeer()).isNull();
        assertThat(report.gapAlerts()).isEmpty();
    }

    @Test
    @DisplayName("한쪽 결과에만 존재하는 사용자는 다른 쪽 지분이 0%로 처리된다")
    void userOnlyInOneResult_treatedAsZeroPercentInOther() {
        EquityResult legacy = result("1.0.0", 1L, "alice", 100.0);
        EquityResult current = result("2.0.0", 1L, "alice", 60.0, 2L, "bob", 40.0);

        when(legacyEquityCalculatorService.calculate(1L)).thenReturn(legacy);
        when(equityCalculatorService.calculate(1L)).thenReturn(current);

        ComparisonReport report = service.compare(1L, null);

        UserComparison bob = report.users().stream()
                .filter(u -> u.username().equals("bob"))
                .findFirst().orElseThrow();

        assertThat(bob.legacyPercentage()).isEqualTo(0.0);
        assertThat(bob.legacyRank()).isEqualTo(2); // alice 100% > bob 0% → bob은 2위
    }

    @Test
    @DisplayName("동료 평가 점수와 공통 사용자가 2명 이상이면 Pearson 상관계수를 계산한다")
    void peerScores_computesPearsonCorrelations() {
        EquityResult legacy = result("1.0.0", 1L, "alice", 80.0, 2L, "bob", 20.0);
        EquityResult current = result("2.0.0", 1L, "alice", 80.0, 2L, "bob", 20.0);

        when(legacyEquityCalculatorService.calculate(1L)).thenReturn(legacy);
        when(equityCalculatorService.calculate(1L)).thenReturn(current);

        Map<Long, Double> peerScores = Map.of(1L, 100.0, 2L, 0.0);

        ComparisonReport report = service.compare(1L, peerScores);

        // legacy/new 모두 alice=80,bob=20 / peer=alice=100,bob=0 → 동일 방향 → 상관계수 1.0
        assertThat(report.pearsonLegacyVsPeer()).isCloseTo(1.0, within(0.0001));
        assertThat(report.pearsonNewVsPeer()).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("동료 평가 점수와 공통 사용자가 2명 미만이면 Pearson은 null이다")
    void peerScores_fewerThanTwoCommonUsers_pearsonIsNull() {
        EquityResult legacy = result("1.0.0", 1L, "alice", 60.0, 2L, "bob", 40.0);
        EquityResult current = result("2.0.0", 1L, "alice", 60.0, 2L, "bob", 40.0);

        when(legacyEquityCalculatorService.calculate(1L)).thenReturn(legacy);
        when(equityCalculatorService.calculate(1L)).thenReturn(current);

        ComparisonReport report = service.compare(1L, Map.of(1L, 50.0));

        assertThat(report.pearsonLegacyVsPeer()).isNull();
        assertThat(report.pearsonNewVsPeer()).isNull();
    }

    @Test
    @DisplayName("신규 순위와 동료 평가 순위 차이가 2 이상이면 괴리 알림이 생성된다")
    void gapAlert_whenRankDiffAtLeastTwo() {
        EquityResult legacy = result("1.0.0", 1L, "alice", 50.0, 2L, "bob", 30.0, 3L, "carol", 20.0);
        EquityResult current = result("2.0.0", 1L, "alice", 50.0, 2L, "bob", 30.0, 3L, "carol", 20.0);

        when(legacyEquityCalculatorService.calculate(1L)).thenReturn(legacy);
        when(equityCalculatorService.calculate(1L)).thenReturn(current);

        // newRanks: alice=1, bob=2, carol=3
        // peerRanks: carol=1(100), bob=2(50), alice=3(10)
        // diff(alice)=|1-3|=2, diff(bob)=|2-2|=0, diff(carol)=|3-1|=2
        Map<Long, Double> peerScores = Map.of(1L, 10.0, 2L, 50.0, 3L, 100.0);

        ComparisonReport report = service.compare(1L, peerScores);

        assertThat(report.gapAlerts()).hasSize(2);
        assertThat(report.gapAlerts()).extracting(GapAlert::username)
                .containsExactlyInAnyOrder("alice", "carol");
        assertThat(report.gapAlerts()).allSatisfy(alert ->
                assertThat(alert.message()).isEqualTo(GAP_ALERT_MESSAGE));
    }

    @Test
    @DisplayName("콘솔 테이블에는 사용자별 지분/순위, Pearson, 괴리 알림이 모두 포함된다")
    void toConsoleTable_includesAllSections() {
        ComparisonReport report = new ComparisonReport(
                1L,
                List.of(new UserComparison(1L, "alice", 60.0, 40.0, 1, 2, -1)),
                0.9876,
                0.5432,
                List.of(new GapAlert(1L, "alice", 2, 1, 1, GAP_ALERT_MESSAGE)),
                LocalDateTime.now());

        String table = service.toConsoleTable(report);

        assertThat(table).contains("alice");
        assertThat(table).contains("Pearson(legacy vs peer) = 0.9876");
        assertThat(table).contains("Pearson(new vs peer)    = 0.5432");
        assertThat(table).contains("gap alerts:");
        assertThat(table).contains(GAP_ALERT_MESSAGE);
    }
}
