package com.equicode.gitequity.equity.quality;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Levin & Yehudai (2017)의 커밋 분류 체계를 PR 제목/라벨에 적용한 분류기.
 *
 * 분류 우선순위:
 *   1. GitHub 라벨에 타입명(또는 흔히 쓰이는 별칭)이 있으면 그것을 사용
 *   2. PR 제목의 Conventional Commits prefix (feat/fix/refactor/test/docs/chore/style)
 *   3. 제목의 키워드 휴리스틱 (영어/한국어)
 *   4. 모두 실패하면 UNKNOWN
 */
@Component
public class PrTypeClassifier {

    // 라벨 이름(소문자) → PrType. Conventional Commits 타입명 + 흔히 쓰이는 별칭
    private static final Map<String, PrType> LABEL_TYPE_MAP = Map.ofEntries(
            Map.entry("feat", PrType.FEAT),
            Map.entry("feature", PrType.FEAT),
            Map.entry("enhancement", PrType.FEAT),
            Map.entry("fix", PrType.FIX),
            Map.entry("bug", PrType.FIX),
            Map.entry("bugfix", PrType.FIX),
            Map.entry("refactor", PrType.REFACTOR),
            Map.entry("refactoring", PrType.REFACTOR),
            Map.entry("test", PrType.TEST),
            Map.entry("tests", PrType.TEST),
            Map.entry("docs", PrType.DOCS),
            Map.entry("documentation", PrType.DOCS),
            Map.entry("chore", PrType.CHORE),
            Map.entry("style", PrType.STYLE)
    );

    // 제목 시작부의 Conventional Commits prefix: "feat:", "fix(scope):", "Refactor ..."
    private static final Pattern CONVENTIONAL_PREFIX =
            Pattern.compile("^(feat|fix|refactor|test|docs|chore|style)[(:\\s]", Pattern.CASE_INSENSITIVE);

    private static final List<String> FEAT_KEYWORDS     = List.of("add", "implement", "구현", "추가");
    private static final List<String> FIX_KEYWORDS      = List.of("fix", "resolve", "수정", "버그");
    private static final List<String> STYLE_KEYWORDS    = List.of("rename", "format", "포맷");
    private static final List<String> REFACTOR_KEYWORDS = List.of("refactor", "리팩토링");

    public PrType classify(List<String> labels, String title) {
        PrType byLabel = classifyByLabel(labels);
        if (byLabel != null) return byLabel;

        PrType byPrefix = classifyByConventionalPrefix(title);
        if (byPrefix != null) return byPrefix;

        PrType byKeyword = classifyByKeyword(title);
        if (byKeyword != null) return byKeyword;

        return PrType.UNKNOWN;
    }

    private PrType classifyByLabel(List<String> labels) {
        if (labels == null) return null;
        for (String label : labels) {
            if (label == null) continue;
            PrType type = LABEL_TYPE_MAP.get(label.toLowerCase());
            if (type != null) return type;
        }
        return null;
    }

    private PrType classifyByConventionalPrefix(String title) {
        if (title == null) return null;
        var matcher = CONVENTIONAL_PREFIX.matcher(title.trim());
        if (!matcher.find()) return null;
        return PrType.valueOf(matcher.group(1).toUpperCase());
    }

    private PrType classifyByKeyword(String title) {
        if (title == null) return null;
        String lower = title.toLowerCase();

        if (containsAny(lower, FEAT_KEYWORDS))     return PrType.FEAT;
        if (containsAny(lower, FIX_KEYWORDS))      return PrType.FIX;
        if (containsAny(lower, STYLE_KEYWORDS))    return PrType.STYLE;
        if (containsAny(lower, REFACTOR_KEYWORDS)) return PrType.REFACTOR;

        return null;
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }
}
