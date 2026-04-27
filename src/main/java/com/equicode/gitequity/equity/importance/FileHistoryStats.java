package com.equicode.gitequity.equity.importance;

/**
 * 파일의 변경 이력 통계 (HistoryBasedFileImportance 입력)
 *
 * @param changeCount        해당 파일이 포함된 커밋 수
 * @param contributorCount   해당 파일을 수정한 고유 기여자 수
 * @param reviewCommentCount 해당 파일 변경에 달린 리뷰 코멘트 수
 */
public record FileHistoryStats(
        int changeCount,
        int contributorCount,
        int reviewCommentCount
) {
    public static FileHistoryStats empty() {
        return new FileHistoryStats(0, 0, 0);
    }
}
