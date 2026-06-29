package com.equicode.gitequity.claim;

import com.equicode.gitequity.claim.dto.ClaimRequest;
import com.equicode.gitequity.claim.dto.ClaimResponse;
import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.domain.ClaimStatus;
import com.equicode.gitequity.domain.NonCodeContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.repository.NonCodeContributionRepository;
import com.equicode.gitequity.repository.ProjectMemberRepository;
import com.equicode.gitequity.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final NonCodeContributionRepository nonCodeContributionRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    // ── 비코드 기여 인정 요청 등록 (프로젝트 멤버만 가능) ──────────────────────────

    @Transactional
    public ClaimResponse createClaim(Long projectId, Long claimerId, ClaimRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        User claimer = projectMemberRepository.findByProjectIdAndUserId(projectId, claimerId)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN))
                .getUser();

        if (claimerId.equals(request.beneficiaryId())) {
            throw new CustomException(ErrorCode.SELF_CLAIM_NOT_ALLOWED);
        }

        User beneficiary = projectMemberRepository.findByProjectIdAndUserId(projectId, request.beneficiaryId())
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_MEMBER_NOT_FOUND))
                .getUser();

        NonCodeContribution claim = NonCodeContribution.builder()
                .project(project)
                .claimer(claimer)
                .beneficiary(beneficiary)
                .type(request.type())
                .description(request.description())
                .occurredAt(request.occurredAt())
                .build();

        return ClaimResponse.from(nonCodeContributionRepository.save(claim));
    }

    // ── 내가 수혜자로서 확인해야 할 PENDING 요청 목록 ─────────────────────────────

    @Transactional(readOnly = true)
    public List<ClaimResponse> getPendingForMe(Long projectId, Long userId) {
        return nonCodeContributionRepository
                .findByProjectIdAndBeneficiaryIdAndStatus(projectId, userId, ClaimStatus.PENDING)
                .stream()
                .map(ClaimResponse::from)
                .toList();
    }

    // ── 확인 / 반려 (수혜자 본인만 가능) ─────────────────────────────────────────

    @Transactional
    public ClaimResponse confirmClaim(Long claimId, Long userId) {
        NonCodeContribution claim = getClaimForDecision(claimId, userId);
        claim.confirm();
        return ClaimResponse.from(claim);
    }

    @Transactional
    public ClaimResponse rejectClaim(Long claimId, Long userId) {
        NonCodeContribution claim = getClaimForDecision(claimId, userId);
        claim.reject();
        return ClaimResponse.from(claim);
    }

    private NonCodeContribution getClaimForDecision(Long claimId, Long userId) {
        NonCodeContribution claim = nonCodeContributionRepository.findById(claimId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLAIM_NOT_FOUND));

        if (!claim.getBeneficiary().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new CustomException(ErrorCode.CLAIM_ALREADY_PROCESSED);
        }

        return claim;
    }
}
