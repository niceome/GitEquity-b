package com.equicode.gitequity.github.dto;

import java.util.Base64;

// GET /repos/{owner}/{repo}/contents/{path} 응답
public record FileContentDto(
        String name,
        String path,
        String encoding,    // "base64"
        String content      // base64 인코딩된 파일 내용 (줄바꿈 포함)
) {
    public String decodeContent() {
        if (content == null) return "";
        if ("base64".equals(encoding)) {
            return new String(Base64.getDecoder().decode(content.replaceAll("\\s", "")));
        }
        return content;
    }
}
