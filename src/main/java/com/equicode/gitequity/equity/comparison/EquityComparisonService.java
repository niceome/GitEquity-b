package com.equicode.gitequity.equity.comparison;

import com.equicode.gitequity.equity.EquityCalculatorService;
import com.equicode.gitequity.equity.EquityResult;
import com.equicode.gitequity.equity.LegacyEquityCalculatorService;
import com.equicode.gitequity.equity.UserEquity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S10 — 개선 전(legacy, 1.0.0) vs 개선 후(신규, 2.0.0) 지분 비교 리포트
 *
 * - 같은 프로젝트에 대해 두 공식의 지분을 각각 산출하고 사용자별 순위 변동을 계산한다.
 * - 동료 평가 점수(peerScores, userId → score)가 주어지면 legacy/신규 지분과의
 *   Pearson 상관계수를 계산하고, 신규 알고리즘 순위와 동료 평가 순위 차이가
 *   2 이상인 사용자에 대해 괴리 알림을 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquityComparisonService {

    private static final int GAP_ALERT_THRESHOLD = 2;
    private static final String GAP_ALERT_MESSAGE =
            "산정 결과와 팀 인식 간 괴리가 큽니다. 비코드 기여 등록을 확인해보세요.";

    private final EquityCalculatorService equityCalculatorService;
    private final LegacyEquityCalculatorService legacyEquityCalculatorService;

    @Transactional(readOnly = true)
    public ComparisonReport compare(Long projectId, Map<Long, Double> peerScores) {
        Map<Long, Double> peers = peerScores == null ? Map.of() : peerScores;

        EquityResult legacy = legacyEquityCalculatorService.calculate(projectId);
        EquityResult current = equityCalculatorService.calculate(projectId);

        Map<Long, UserEquity> legacyByUser = toMap(legacy);
        Map<Long, UserEquity> newByUser = toMap(current);

        Set<Long> userIds = new LinkedHashSet<>();
        userIds.addAll(legacyByUser.keySet());
        userIds.addAll(newByUser.keySet());

        Map<Long, String> usernames = new LinkedHashMap<>();
        legacyByUser.forEach((id, e) -> usernames.put(id, e.username()));
        newByUser.forEach((id, e) -> usernames.put(id, e.username()));

        Map<Long, Double> legacyPct = new LinkedHashMap<>();
        Map<Long, Double> newPct = new LinkedHashMap<>();
        for (Long id : userIds) {
            legacyPct.put(id, legacyByUser.containsKey(id) ? legacyByUser.get(id).percentage() : 0.0);
            newPct.put(id, newByUser.containsKey(id) ? newByUser.get(id).percentage() : 0.0);
        }

        Map<Long, Integer> legacyRanks = rankByDesc(legacyPct);
        Map<Long, Integer> newRanks = rankByDesc(newPct);
        Map<Long, Integer> peerRanks = peers.isEmpty() ? Map.of() : rankByDesc(peers);

        List<UserComparison> comparisons = userIds.stream()
                .map(id -> new UserComparison(
                        id, usernames.get(id),
                        legacyPct.get(id), newPct.get(id),
                        legacyRanks.get(id), newRanks.get(id),
                        legacyRanks.get(id) - newRanks.get(id)))
                .sorted((a, b) -> Integer.compare(a.newRank(), b.newRank()))
                .toList();

        Double pearsonLegacyVsPeer = null;
        Double pearsonNewVsPeer = null;
        if (!peers.isEmpty()) {
            List<Long> common = userIds.stream().filter(peers::containsKey).toList();
            if (common.size() >= 2) {
                double[] legacyArr = common.stream().mapToDouble(legacyPct::get).toArray();
                double[] newArr = common.stream().mapToDouble(newPct::get).toArray();
                double[] peerArr = common.stream().mapToDouble(peers::get).toArray();
                pearsonLegacyVsPeer = PearsonCorrelation.compute(legacyArr, peerArr);
                pearsonNewVsPeer = PearsonCorrelation.compute(newArr, peerArr);
            }
        }

        List<GapAlert> gapAlerts = new ArrayList<>();
        for (Long id : userIds) {
            Integer peerRank = peerRanks.get(id);
            if (peerRank == null) continue;

            int algoRank = newRanks.get(id);
            int diff = Math.abs(algoRank - peerRank);
            if (diff >= GAP_ALERT_THRESHOLD) {
                gapAlerts.add(new GapAlert(id, usernames.get(id), algoRank, peerRank, diff, GAP_ALERT_MESSAGE));
            }
        }

        ComparisonReport report = new ComparisonReport(
                projectId, comparisons, pearsonLegacyVsPeer, pearsonNewVsPeer, gapAlerts, LocalDateTime.now());

        log.info("[EquityComparison] project={}\n{}", projectId, toConsoleTable(report));

        return report;
    }

    public String toConsoleTable(ComparisonReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-15s %10s %10s %8s %8s %6s%n",
                "user", "legacy%", "new%", "legRank", "newRank", "Δrank"));
        for (UserComparison c : report.users()) {
            sb.append(String.format("%-15s %10.2f %10.2f %8d %8d %+6d%n",
                    c.username(), c.legacyPercentage(), c.newPercentage(),
                    c.legacyRank(), c.newRank(), c.rankChange()));
        }
        if (report.pearsonLegacyVsPeer() != null) {
            sb.append(String.format("Pearson(legacy vs peer) = %.4f%n", report.pearsonLegacyVsPeer()));
        }
        if (report.pearsonNewVsPeer() != null) {
            sb.append(String.format("Pearson(new vs peer)    = %.4f%n", report.pearsonNewVsPeer()));
        }
        if (!report.gapAlerts().isEmpty()) {
            sb.append("gap alerts:\n");
            for (GapAlert g : report.gapAlerts()) {
                sb.append(String.format("  - %s (algoRank=%d, peerRank=%d, diff=%d): %s%n",
                        g.username(), g.algorithmRank(), g.peerRank(), g.rankDiff(), g.message()));
            }
        }
        return sb.toString();
    }

    private Map<Long, UserEquity> toMap(EquityResult result) {
        return result.equities().stream()
                .collect(Collectors.toMap(UserEquity::userId, e -> e, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, Integer> rankByDesc(Map<Long, Double> scores) {
        List<Long> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        Map<Long, Integer> ranks = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            ranks.put(sorted.get(i), i + 1);
        }
        return ranks;
    }
}
