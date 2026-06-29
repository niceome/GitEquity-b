# 지분 산정 공식 결합, 비교 리포트, Co-author 분할, 알고리즘 버전 (S10/S11/S12/S16)

신규 지분 산정 공식(`EquityCalculatorService`, v2.0.0)이 PR/리뷰/코멘트 점수를
어떻게 결합하는지, 기존 공식(`LegacyEquityCalculatorService`, v1.0.0)과의
비교 리포트(S10), Co-authored-by 기반 점수 분할(S12), 알고리즘 버전 추적(S16)을
설명한다.

## 1. PR 점수 공식 (S11 포함)

`EquityCalculatorService.calculate(projectId)`는 `pr_contributions` 한 건당
다음 공식으로 점수를 계산한다.

```
prScore = netLines × density × prType.weight × dmmFactor(dmmComplexity) × mergedFactor
```

| 항목         | 의미                                              | 출처                          |
|-------------|---------------------------------------------------|-------------------------------|
| `netLines`  | 실질 변경 라인 수                                  | `DensityCalculator`            |
| `density`   | `netLines / gross 변경 라인 수`                    | `DensityCalculator`            |
| `prType.weight` | PR 유형 가중치 (FEAT=1.0, FIX=0.9, ... UNKNOWN=0.6) | `PrTypeClassifier` → `PrType`  |
| `dmmFactor` | `dmm == null → 1.0`, 아니면 `0.7 + 0.6×dmm`        | `DmmFactor` ([dmm-analysis.md](dmm-analysis.md)) |
| `mergedFactor` | 병합 PR=1.0, closed-unmerged PR=0.3 (S11)        | `EquityCalculatorService`      |

### S11 — closed-unmerged PR "탐색 기여"

리뷰가 1건 이상 달린 closed-unmerged PR만 `mergedFactor=0.3`으로 인정한다
(`reviewContributionRepository.findDistinctPrNumbersByProjectId`로 리뷰 존재
여부 확인). 리뷰가 없는 closed-unmerged PR은 점수에 전혀 반영되지 않는다.

탐색 기여 점수 합은 사용자의 병합 PR 점수 합의 `capRatio`(기본 15%)를 넘지
못하도록 `ExplorationCapCalculator`가 제한한다.

```
explorationCap = mergedScoreSum × capRatio   (기본 capRatio = 0.15)
최종 탐색 점수 = min(explorationRawSum, explorationCap)

사용자 PR 점수 합 = mergedScoreSum + 최종 탐색 점수
```

`capRatio`는 `equity.exploration.cap-ratio` 설정 키로 오버라이드 가능하다.

## 2. 사용자 총점 결합 (`EquityWeights`)

PR/리뷰/코멘트 세 차원의 점수 합을 각각 팀 내 최댓값으로 정규화한 뒤
가중합산한다.

```
norm(x) = max == 0 ? 0 : x / max

userTotal = w_pr × norm(PR점수합) + w_review × norm(리뷰점수합) + w_comment × norm(코멘트점수합)
```

기본 가중치 `[w_pr, w_review, w_comment] = [0.75, 0.15, 0.10]`이며,
`equity.weights.{pr,review,comment}` 설정 키로 오버라이드 가능하다 (추후 AHP
합의값으로 교체 예정).

- 리뷰점수합: `ReviewContributionRepository.sumScoreGroupByReviewer` (S5)
- 코멘트점수합: `CommunicationScoreService.calculate` (S6, cap 적용 후)

최종 지분율은 `userTotal`을 전체 사용자 합(`grandTotal`)에 대한 비율로
환산한 백분율(소수점 2자리)이다.

```
percentage = round(userTotal / grandTotal × 100, 2)
```

### 미등록 사용자 처리

`pr_contributions.author_github_id` 등 raw GitHub 사용자 ID가 `users` 테이블에
없는 경우, 해당 기여자는 `equities` 결과에서 제외되지만 그 점수는 정규화
기준값(team max)에는 그대로 반영된다 — 즉 미등록 사용자의 대규모 기여가 있으면
등록된 사용자들의 `norm()` 값이 낮아질 수 있다.

## 3. S12 — Co-authored-by 기반 점수 분할

squash merge 커밋 메시지에 `Co-authored-by:` 트레일러가 있으면, PR 점수를
작성자(`author_github_id`)와 매칭된 공동 작성자들에게 균등 분할한다.

```
perRecipientScore = prScore / recipients.size()
recipients = {author_github_id} ∪ co_author_github_ids
```

### 수집 (`CoAuthorAttributionCollectorService`)

`GET /repos/{owner}/{repo}/commits`를 페이지네이션하며 각 커밋 메시지를 파싱한다
(`CoAuthorParser`).

1. 메시지 첫 줄 끝의 `(#PR번호)`로 `pr_contributions`와 매칭
   (`dmm_analyzer.py`와 동일 규약). 매칭되는 PR이 없으면 건너뜀.
2. 메시지 전체에서 `Co-authored-by: Name <email>` 트레일러를 모두 추출.
   트레일러가 없으면 건너뜀.
3. 각 트레일러의 이메일을 `User.email`과 매칭하여 `githubId`로 변환.
   - 매칭 실패 시 분할에서 제외하고 경고 로그만 남긴다:
     `[CoAuthor] project={} pr=#{} co-author email={} matched no registered user — split skipped for this co-author`
   - PR 작성자 본인의 이메일이 트레일러에 포함된 경우(자기 자신을 co-author로
     명시한 경우) 분할 대상에서 제외한다.
4. 매칭에 성공한 공동 작성자가 1명 이상이면 `pr_contributions.co_author_github_ids`
   (jsonb)를 갱신한다. 매칭된 공동 작성자가 없으면 아무것도 갱신하지 않는다.

### 실행 (테스트 엔드포인트)

```bash
curl -X POST "http://localhost:8080/api/github/test/co-authors/{projectId}" \
  -H "Authorization: Bearer {JWT}"
```

응답:

```json
{
  "message": "co-author attribution done: updated=3",
  "data": 3
}
```

## 4. S10 — Legacy vs 신규 비교 리포트

`EquityComparisonService.compare(projectId, peerScores)`는 같은 프로젝트에
대해 legacy(v1.0.0)와 신규(v2.0.0) 지분을 각각 산출하고 사용자별 순위 변동을
계산한다.

### 엔드포인트

```bash
curl -X POST "http://localhost:8080/api/projects/{projectId}/equity/comparison" \
  -H "Authorization: Bearer {JWT}" \
  -H "Content-Type: application/json" \
  -d '{"1": 45.0, "2": 35.0, "3": 20.0}'
```

`peerScores`(요청 바디, `Map<userId, score>`)는 동료 평가 점수가 있을 때만
전달하는 선택 항목이다. 바디를 생략하거나 `null`을 보내면 빈 맵으로 처리되어
Pearson 상관계수와 괴리 알림은 결과에 포함되지 않는다.

### 응답 구조 (`ComparisonResponse`)

```json
{
  "message": "3 users compared, 1 gap alerts",
  "data": {
    "report": {
      "projectId": 1,
      "users": [
        {
          "userId": 2, "username": "bob",
          "legacyPercentage": 30.0, "newPercentage": 45.0,
          "legacyRank": 2, "newRank": 1, "rankChange": 1
        },
        {
          "userId": 1, "username": "alice",
          "legacyPercentage": 50.0, "newPercentage": 35.0,
          "legacyRank": 1, "newRank": 2, "rankChange": -1
        }
      ],
      "pearsonLegacyVsPeer": 0.8123,
      "pearsonNewVsPeer": 0.9456,
      "gapAlerts": [
        {
          "userId": 3, "username": "carol",
          "algorithmRank": 3, "peerRank": 1, "rankDiff": 2,
          "message": "산정 결과와 팀 인식 간 괴리가 큽니다. 비코드 기여 등록을 확인해보세요."
        }
      ],
      "generatedAt": "2026-06-14T12:00:00"
    },
    "consoleTable": "user            legacy%       new%  legRank  newRank  Δrank\nbob                30.00      45.00        2        1     +1\nalice              50.00      35.00        1        2     -1\n..."
  }
}
```

- `users`는 `newRank` 오름차순으로 정렬된다.
- `rankChange = legacyRank - newRank` (양수 = 신규 공식에서 순위 상승).
- 한쪽 결과에만 존재하는 사용자는 다른 쪽 지분을 0%로 처리한다.

### Pearson 상관계수

`peerScores`와 공통된 사용자가 2명 이상일 때만 계산한다 (`PearsonCorrelation`).
- `pearsonLegacyVsPeer`: legacy 지분율 vs 동료 평가 점수
- `pearsonNewVsPeer`: 신규 지분율 vs 동료 평가 점수

공통 사용자가 2명 미만이거나 한쪽의 분산이 0이면 `null`이다.

### 괴리 알림 (gap alerts)

신규 알고리즘 순위(`newRank`)와 동료 평가 순위(`peerRank`)의 차이가
2 이상인 사용자마다 알림을 생성한다.

```
rankDiff = |newRank - peerRank|
rankDiff >= 2 → GapAlert(message = "산정 결과와 팀 인식 간 괴리가 큽니다. 비코드 기여 등록을 확인해보세요.")
```

`consoleTable`은 동일한 정보를 고정폭 텍스트 표 + Pearson 값 + 괴리 알림
목록으로 포맷한 문자열이며, 서버 로그(`[EquityComparison] project={}...`)에도
동일하게 출력된다.

## 5. S16 — 알고리즘 버전 추적

- `EquityCalculatorService.ALGORITHM_VERSION` = `AlgorithmVersion.CURRENT` = `"2.0.0"`
- `LegacyEquityCalculatorService.ALGORITHM_VERSION` = `AlgorithmVersion.LEGACY` = `"1.0.0"`

두 값 모두 `EquityResult.algorithmVersion` 필드에 담겨 반환된다.

### 계약 서명 시 기록

`ContractService.sign(contractId, userId, ipAddress)`가 서명 시점에
`signatures.algorithm_version` 컬럼에 `EquityCalculatorService.ALGORITHM_VERSION`
(현재 `"2.0.0"`)을 기록한다 (`Signature.sign(ipAddress, algorithmVersion)`).
이를 통해 계약 체결 당시 어떤 산정 공식이 적용되었는지 추적할 수 있다.

## 자동화 테스트

- `EquityCalculatorServiceTest` — PR 점수 공식(netLines×density×prType.weight×
  dmmFactor×mergedFactor), S11 탐색 기여 cap, 정규화/가중합산, co-author 분할,
  미등록 사용자 처리, 빈 프로젝트/프로젝트 없음 예외, `algorithmVersion == "2.0.0"`
- `LegacyEquityCalculatorServiceTest` — 기존 commit/PR/review/issue 개수 기반
  공식 (rename 후에도 동일하게 동작)
- `ExplorationCapCalculatorTest`, `EquityWeightsTest` — S11 cap / 가중치 결합
  순수 로직
- `CoAuthorParserTest` — PR 번호/Co-authored-by 트레일러 파싱
- `CoAuthorAttributionCollectorServiceTest` — PR 매칭, 이메일 매칭/비매칭,
  자기 자신 co-author 제외, 트레일러·PR 번호 없음
- `PearsonCorrelationTest` — 상관계수 계산, 표본 부족/분산 0/길이 불일치 시 null
- `EquityComparisonServiceTest` — 순위 변동 계산, Pearson 상관계수, 괴리 알림,
  콘솔 테이블 포맷
