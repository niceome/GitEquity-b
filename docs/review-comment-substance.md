# 리뷰/코멘트 실질성 평가 가이드 (S5, S6)

리뷰와 코멘트를 "개수"가 아닌 "실질성"으로 평가하는 S5(리뷰 실질성)/S6(커뮤니케이션
기여) 점수 산출 로직을 설명한다.

- S5 근거: McIntosh et al. (2014) 리뷰 참여도 연구
- S6 근거: Hundhausen et al. (2023) 커뮤니케이션 기여 연구

## S5 — 리뷰 실질성 (`review_contributions`)

`ReviewSubstanceCollectorService`가 PR마다 다음을 수집한다.

- `GET /repos/{owner}/{repo}/pulls/{number}/reviews` — 리뷰 본문/상태
- `GET /repos/{owner}/{repo}/pulls/{number}/comments` — 인라인 코드 리뷰 코멘트
  (`pull_request_review_id`로 소속 리뷰에 합산)

### 점수 공식 (`ReviewSubstanceScorer`)

```
score = base(state) × substance(본문 + 인라인 코멘트 합산 길이)
        + (코드블록(```) 포함 시 +0.3)
```

| state             | base |
|-------------------|------|
| CHANGES_REQUESTED | 1.0  |
| COMMENTED         | 0.5  |
| APPROVED          | 0.4  |
| 그 외             | 0.2  |

| 합산 길이   | substance |
|------------|-----------|
| < 20자     | 0.2       |
| 20~200자   | 1.0       |
| > 200자    | 1.3       |

예: `"LGTM"` 한 줄 APPROVED → `0.4 × 0.2 = 0.08`
예: 200자 초과 + 코드블록 포함 CHANGES_REQUESTED → `1.0 × 1.3 + 0.3 = 1.6`

### 제외 대상

- 자기 PR에 단 리뷰 (`PR.user.id == review.user.id`)
- bot 계정 리뷰 (`GitHubUser.isBot()`)
- 이미 수집된 리뷰 (`review_contributions.review_id` 중복)

## S6 — 커뮤니케이션 기여 (`comment_contributions`)

`CommentCollectorService`가 저장소 전체 코멘트를 수집한다.

- `GET /repos/{owner}/{repo}/issues/comments` (페이지네이션) — 이슈/PR 본문 코멘트
  (PR도 issue 번호 namespace를 공유하므로 이 엔드포인트로 PR 코멘트도 함께 수집됨)
- `GET /repos/{owner}/{repo}/issues?state=all` — 이슈/PR 작성자 조회 (자가 코멘트
  필터링용)

### 점수 공식 (`CommentScorer`)

```
score = (길이 < 30자 → 0.1, 그 외 min(1.0, 길이/300))
        + (코드블록(```) 포함 시 +0.5)
```

### 제외 대상

- 본인이 연 이슈/PR에 단 코멘트 (`comment.user.id == issue.user.id`)
- bot 계정 코멘트
- 이미 수집된 코멘트 (`comment_contributions.comment_id` 중복)

### 사용자별 cap (`CommunicationCapCalculator` / `CommunicationScoreService`)

S6 raw 총점은 사용자별 "PR 점수 합"(병합 PR `net_lines` 합)의 일정 비율을
넘지 못하도록 제한한다.

```
cap = PR 점수 합 × capRatio   (기본 capRatio = 0.1, 즉 10%)
최종 S6 = min(raw 총점, cap)
```

`capRatio`는 `equity.communication.cap-ratio` 설정 키로 오버라이드 가능하다.
PR 기여가 없는 사용자는 cap이 0이 되어 S6도 0이 된다.

## DB 스키마

`ddl-auto: update`에 의해 애플리케이션 기동 시 자동 생성된다.

- `review_contributions`: `project_id`, `reviewer_github_id`, `pr_number`,
  `review_id`(unique), `state`, `content_length`, `has_code_block`, `score`,
  `submitted_at`
- `comment_contributions`: `project_id`, `author_github_id`, `issue_number`,
  `comment_id`(unique), `content_length`, `has_code_block`, `score`
  (raw, cap 적용 전), `created_at_github`

`reviewer_github_id`/`author_github_id`는 `User.githubId`와 매칭하여 점수
계산에 사용하는 raw GitHub 사용자 ID다 (수집 시점에 `User`로 등록되지
않았을 수 있음).

## 실행 (테스트 엔드포인트, Phase C 검증용)

```bash
# S5 리뷰 실질성 수집
curl -X POST "http://localhost:8080/api/github/test/review-substance/{projectId}" \
  -H "Authorization: Bearer {JWT}"

# S6 코멘트 실질성 raw 점수 수집
curl -X POST "http://localhost:8080/api/github/test/comments/{projectId}" \
  -H "Authorization: Bearer {JWT}"

# 사용자별 S6 최종 점수 (cap 적용 후)
curl "http://localhost:8080/api/github/test/communication-score/{projectId}"
```

## 결과 확인 (SQL)

```sql
SELECT reviewer_github_id, pr_number, state, content_length, has_code_block, score
FROM review_contributions
WHERE project_id = {projectId}
ORDER BY pr_number;

SELECT author_github_id, issue_number, content_length, has_code_block, score
FROM comment_contributions
WHERE project_id = {projectId}
ORDER BY issue_number;
```

확인 포인트:

- `"LGTM"` 류 한 줄 리뷰와 200자+코드블록 리뷰의 `score`가 유의미하게(예: 20배
  이상) 차이 나는지
- PR 작성자 본인의 리뷰/자가 이슈 코멘트가 두 테이블에 존재하지 않는지

## 자동화 테스트

- `ReviewSubstanceScorerTest`, `CommentScorerTest`, `CommunicationCapCalculatorTest`
  — 순수 점수 계산 로직 단위 테스트
- `ReviewSubstanceCollectorServiceTest` — 자기 PR 리뷰/봇 리뷰 제외, 인라인
  코멘트 합산, 중복 스킵
- `CommentCollectorServiceTest` — 자가 이슈 코멘트/봇 제외, 코드블록 보너스,
  중복 스킵
- `CommunicationScoreServiceTest` — cap 적용 시나리오 (raw < cap, raw > cap,
  PR 기여 없음)
