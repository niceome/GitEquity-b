package com.equicode.gitequity.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 계약에 대한 멤버별 전자서명
// signed_at이 null이면 미서명, not null이면 서명 완료
@Getter
@Entity
@Table(name = "signatures",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_signatures_contract_user",
                columnNames = {"contract_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 서명 시각 (null = 미서명)
    private LocalDateTime signedAt;

    // 서명 요청 IP (감사 추적용)
    private String ipAddress;

    public void sign(String ipAddress) {
        this.signedAt = LocalDateTime.now();
        this.ipAddress = ipAddress;
    }
}
