package com.equicode.gitequity.claim;

import com.equicode.gitequity.claim.dto.ClaimRequest;
import com.equicode.gitequity.claim.dto.ClaimResponse;
import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.domain.*;
import com.equicode.gitequity.repository.NonCodeContributionRepository;
import com.equicode.gitequity.repository.ProjectMemberRepository;
import com.equicode.gitequity.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    private static final User ALICE = User.builder().id(1L).githubId(101L).username("alice").build();
    private static final User BOB   = User.builder().id(2L).githubId(102L).username("bob").build();

    private static final Project PROJECT = Project.builder()
            .id(1L).owner(ALICE).name("proj").repoOwner("org").repoName("repo").build();

    @Mock NonCodeContributionRepository nonCodeContributionRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    ClaimService service;

    private ProjectMember member(User user, MemberRole role) {
        return ProjectMember.builder().id(1L).project(PROJECT).user(user).role(role).joinedAt(LocalDateTime.now()).build();
    }

    private NonCodeContribution pendingClaim() {
        return NonCodeContribution.builder()
                .project(PROJECT).claimer(ALICE).beneficiary(BOB)
                .type(NonCodeContributionType.DESIGN).description("설계 논의")
                .occurredAt(LocalDate.now())
                .build();
    }

    @BeforeEach
    void stubProject() {
        lenient().when(projectRepository.findById(1L)).thenReturn(Optional.of(PROJECT));
    }

    // ── 등록 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("프로젝트 멤버는 다른 멤버를 수혜자로 claim을 등록할 수 있다")
    void createClaim_success() {
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 1L)).thenReturn(Optional.of(member(ALICE, MemberRole.OWNER)));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(BOB, MemberRole.MEMBER)));
        when(nonCodeContributionRepository.save(any(NonCodeContribution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClaimRequest request = new ClaimRequest(2L, NonCodeContributionType.MENTORING, "온보딩 멘토링", LocalDate.now());
        ClaimResponse response = service.createClaim(1L, 1L, request);

        assertThat(response.claimerId()).isEqualTo(1L);
        assertThat(response.beneficiaryId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(ClaimStatus.PENDING);
        assertThat(response.type()).isEqualTo(NonCodeContributionType.MENTORING);
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 claim을 등록할 수 없다 (403)")
    void createClaim_notMember_forbidden() {
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        ClaimRequest request = new ClaimRequest(2L, NonCodeContributionType.MENTORING, "desc", LocalDate.now());

        assertThatThrownBy(() -> service.createClaim(1L, 99L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode().getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("자기 자신을 수혜자로 지정하면 400을 반환한다 (자기 확인 금지)")
    void createClaim_selfBeneficiary_badRequest() {
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 1L)).thenReturn(Optional.of(member(ALICE, MemberRole.OWNER)));

        ClaimRequest request = new ClaimRequest(1L, NonCodeContributionType.MENTORING, "desc", LocalDate.now());

        assertThatThrownBy(() -> service.createClaim(1L, 1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(nonCodeContributionRepository, never()).save(any());
    }

    @Test
    @DisplayName("수혜자가 프로젝트 멤버가 아니면 404를 반환한다")
    void createClaim_beneficiaryNotMember_notFound() {
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 1L)).thenReturn(Optional.of(member(ALICE, MemberRole.OWNER)));
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        ClaimRequest request = new ClaimRequest(2L, NonCodeContributionType.MENTORING, "desc", LocalDate.now());

        assertThatThrownBy(() -> service.createClaim(1L, 1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode().getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── 대기 목록 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("내가 수혜자인 PENDING claim 목록을 조회한다")
    void getPendingForMe_returnsPendingClaims() {
        when(nonCodeContributionRepository.findByProjectIdAndBeneficiaryIdAndStatus(1L, 2L, ClaimStatus.PENDING))
                .thenReturn(List.of(pendingClaim()));

        List<ClaimResponse> result = service.getPendingForMe(1L, 2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).beneficiaryId()).isEqualTo(2L);
        assertThat(result.get(0).status()).isEqualTo(ClaimStatus.PENDING);
    }

    // ── 확인 / 반려 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("수혜자는 PENDING 상태의 claim을 확인(CONFIRMED) 처리할 수 있다")
    void confirmClaim_byBeneficiary_succeeds() {
        NonCodeContribution claim = pendingClaim();
        when(nonCodeContributionRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimResponse response = service.confirmClaim(10L, 2L);

        assertThat(response.status()).isEqualTo(ClaimStatus.CONFIRMED);
        assertThat(response.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("수혜자가 아닌 사용자가 confirm을 시도하면 403을 반환한다")
    void confirmClaim_byNonBeneficiary_forbidden() {
        NonCodeContribution claim = pendingClaim();
        when(nonCodeContributionRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.confirmClaim(10L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode().getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PENDING);
    }

    @Test
    @DisplayName("수혜자는 PENDING 상태의 claim을 반려(REJECTED) 처리할 수 있다")
    void rejectClaim_byBeneficiary_succeeds() {
        NonCodeContribution claim = pendingClaim();
        when(nonCodeContributionRepository.findById(10L)).thenReturn(Optional.of(claim));

        ClaimResponse response = service.rejectClaim(10L, 2L);

        assertThat(response.status()).isEqualTo(ClaimStatus.REJECTED);
    }

    @Test
    @DisplayName("수혜자가 아닌 사용자가 reject를 시도하면 403을 반환한다")
    void rejectClaim_byNonBeneficiary_forbidden() {
        NonCodeContribution claim = pendingClaim();
        when(nonCodeContributionRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.rejectClaim(10L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode().getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("이미 처리된 claim을 다시 확인하면 400을 반환한다")
    void confirmClaim_alreadyProcessed_badRequest() {
        NonCodeContribution claim = pendingClaim();
        claim.confirm();
        when(nonCodeContributionRepository.findById(10L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.confirmClaim(10L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode().getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("존재하지 않는 claim을 확인하면 404를 반환한다")
    void confirmClaim_notFound() {
        when(nonCodeContributionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmClaim(999L, 2L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode().getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
