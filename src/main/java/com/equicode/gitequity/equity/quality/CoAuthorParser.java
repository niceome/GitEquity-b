package com.equicode.gitequity.equity.quality;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * squash 커밋 메시지에서 PR 번호와 Co-authored-by 트레일러를 추출한다 (S12).
 *
 * - PR 번호: 메시지 첫 줄 끝의 "(#123)" — dmm_analyzer.py와 동일한 규약
 * - Co-authored-by: 메시지 본문 전체에서 "Co-authored-by: Name <email>" 형태를 모두 추출
 */
@Component
public class CoAuthorParser {

    private static final Pattern PR_NUMBER_PATTERN = Pattern.compile("\\(#(\\d+)\\)$");
    private static final Pattern CO_AUTHOR_PATTERN =
            Pattern.compile("Co-authored-by:\\s*(.+?)\\s*<(.+?)>", Pattern.MULTILINE);

    public Integer extractPrNumber(String commitMessage) {
        if (commitMessage == null) return null;

        String firstLine = commitMessage.split("\n", 2)[0].trim();
        Matcher matcher = PR_NUMBER_PATTERN.matcher(firstLine);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    public List<CoAuthor> extractCoAuthors(String commitMessage) {
        if (commitMessage == null) return List.of();

        List<CoAuthor> result = new ArrayList<>();
        Matcher matcher = CO_AUTHOR_PATTERN.matcher(commitMessage);
        while (matcher.find()) {
            result.add(new CoAuthor(matcher.group(1).trim(), matcher.group(2).trim()));
        }
        return result;
    }

    public record CoAuthor(String name, String email) {}
}
