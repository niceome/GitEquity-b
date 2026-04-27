package com.equicode.gitequity.repository;

import com.equicode.gitequity.domain.Signature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignatureRepository extends JpaRepository<Signature, Long> {

    List<Signature> findByContractId(Long contractId);

    Optional<Signature> findByContractIdAndUserId(Long contractId, Long userId);

    long countByContractId(Long contractId);

    // 서명 완료 건수: signed_at IS NOT NULL
    long countByContractIdAndSignedAtIsNotNull(Long contractId);
}
