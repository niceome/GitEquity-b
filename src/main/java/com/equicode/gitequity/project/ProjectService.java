package com.equicode.gitequity.project;

import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.domain.*;
import com.equicode.gitequity.github.collector.CollectionResult;
import com.equicode.gitequity.github.collector.ContributionCollectionService;
import com.equicode.gitequity.project.dto.*;
import com.equicode.gitequity.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository        projectRepository;
    private final ProjectMemberRepository  projectMemberRepository;
    private final UserRepository           userRepository;
    private final ContributionRepository   contributionRepository;
    private final EquitySnapshotRepository snapshotRepository;
    private final ContractRepository       contractRepository;
    private final SignatureRepository      signatureRepository;
    private final com.equicode.gitequity.github.GithubApiClient githubApiClient;
    private final ContributionCollectionService contributionCollectionService;

    // ── 프로젝트 목록 (참여한 모든 프로젝트) ──────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(Long userId) {
        List<ProjectMember> memberships = projectMemberRepository.findByUserId(userId);
        return memberships.stream()
                .map(m -> {
                    int count = projectMemberRepository.findByProjectId(m.getProject().getId()).size();
                    return ProjectResponse.from(m.getProject(), count);
                })
                .toList();
    }

    // ── 프로젝트 생성 ─────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse createProject(Long ownerId, CreateProjectRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (projectRepository.findByRepoOwnerAndRepoName(req.repoOwner(), req.repoName()).isPresent()) {
            throw new CustomException(ErrorCode.PROJECT_ALREADY_EXISTS);
        }

        // GitHub 레포 실재 여부 확인
        if (!githubApiClient.repositoryExists(req.repoOwner(), req.repoName(), owner.getAccessToken())) {
            throw new CustomException(ErrorCode.REPO_NOT_FOUND);
        }

        String repoUrl = "https://github.com/" + req.repoOwner() + "/" + req.repoName();
        Project project = Project.builder()
                .owner(owner)
                .name(req.name())
                .repoOwner(req.repoOwner())
                .repoName(req.repoName())
                .repoUrl(repoUrl)
                .weightConfig(Map.of(
                        "COMMIT", 1.0,
                        "PULL_REQUEST", 3.0,
                        "REVIEW", 2.0,
                        "ISSUE", 1.0))
                .build();
        project = projectRepository.save(project);

        ProjectMember ownerMember = ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(MemberRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();
        projectMemberRepository.save(ownerMember);

        return ProjectResponse.from(project, 1);
    }

    // ── 프로젝트 상세 조회 ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        List<ProjectMemberResponse> members = projectMemberRepository.findByProjectId(projectId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();

        return ProjectDetailResponse.from(project, members);
    }

    // ── 기여 이력 조회 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContributionResponse> listContributions(Long projectId) {
        return contributionRepository.findByProjectId(projectId).stream()
                .map(ContributionResponse::from)
                .toList();
    }

    // ── 멤버 목록 조회 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(Long projectId) {
        return projectMemberRepository.findByProjectId(projectId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    // ── 멤버 초대 ─────────────────────────────────────────────────────────────

    @Transactional
    public ProjectMemberResponse inviteMember(Long projectId, Long requestUserId, String username) {
        requireOwner(projectId, requestUserId);

        User invitee = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, invitee.getId())) {
            throw new CustomException(ErrorCode.PROJECT_ALREADY_EXISTS);
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(invitee)
                .role(MemberRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        return ProjectMemberResponse.from(projectMemberRepository.save(member));
    }

    // ── 멤버 제거 ─────────────────────────────────────────────────────────────

    @Transactional
    public void removeMember(Long projectId, Long requestUserId, Long targetUserId) {
        requireOwner(projectId, requestUserId);

        ProjectMember target = projectMemberRepository
                .findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        if (target.getRole() == MemberRole.OWNER) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        projectMemberRepository.delete(target);
    }

    // ── 가중치 설정 ───────────────────────────────────────────────────────────

    @Transactional
    public ProjectDetailResponse updateWeightConfig(Long projectId, Long requestUserId,
                                                     Map<String, Double> weightConfig) {
        requireOwner(projectId, requestUserId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        project.updateWeightConfig(weightConfig);

        List<ProjectMemberResponse> members = projectMemberRepository.findByProjectId(projectId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();

        return ProjectDetailResponse.from(project, members);
    }

    // ── 프로젝트 삭제 ─────────────────────────────────────────────────────────

    @Transactional
    public void deleteProject(Long projectId, Long requestUserId) {
        requireOwner(projectId, requestUserId);

        // 1. Signature 삭제 (Contract → Signature 순)
        List<Long> contractIds = contractRepository.findByProjectId(projectId)
                .stream().map(Contract::getId).collect(Collectors.toList());
        contractIds.forEach(cid -> signatureRepository.deleteAll(signatureRepository.findByContractId(cid)));

        // 2. EquitySnapshot 삭제
        snapshotRepository.deleteAll(snapshotRepository.findByProjectIdOrderBySnapshotAtDesc(projectId));

        // 3. Contribution 삭제
        contributionRepository.deleteAll(contributionRepository.findByProjectId(projectId));

        // 4. Contract 삭제
        contractRepository.deleteAll(contractRepository.findByProjectId(projectId));

        // 5. ProjectMember 삭제
        projectMemberRepository.deleteAll(projectMemberRepository.findByProjectId(projectId));

        // 6. Project 삭제
        projectRepository.deleteById(projectId);
    }

    // ── 데이터 수집 ───────────────────────────────────────────────────────────

    @Transactional
    public CollectionResult collectContributions(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getAccessToken() == null) {
            throw new CustomException(ErrorCode.GITHUB_API_ERROR);
        }

        return contributionCollectionService.collectAll(project, user.getAccessToken());
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private void requireOwner(Long projectId, Long userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .filter(m -> m.getRole() == MemberRole.OWNER)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));
    }
}
