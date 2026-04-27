package com.equicode.gitequity.project;

import com.equicode.gitequity.auth.UserPrincipal;
import com.equicode.gitequity.common.response.ApiResponse;
import com.equicode.gitequity.github.collector.CollectionResult;
import com.equicode.gitequity.project.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(projectService.listProjects(principal.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateProjectRequest request) {
        return ApiResponse.ok("project created", projectService.createProject(principal.getId(), request));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectDetailResponse> get(@PathVariable Long projectId) {
        return ApiResponse.ok(projectService.getProject(projectId));
    }

    @GetMapping("/{projectId}/contributions")
    public ApiResponse<List<ContributionResponse>> listContributions(@PathVariable Long projectId) {
        return ApiResponse.ok(projectService.listContributions(projectId));
    }

    @GetMapping("/{projectId}/members")
    public ApiResponse<List<ProjectMemberResponse>> getMembers(@PathVariable Long projectId) {
        return ApiResponse.ok(projectService.getMembers(projectId));
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectMemberResponse> invite(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody InviteMemberRequest request) {
        return ApiResponse.ok("member invited",
                projectService.inviteMember(projectId, principal.getId(), request.username()));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        projectService.removeMember(projectId, principal.getId(), userId);
        return ApiResponse.ok("member removed", null);
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        projectService.deleteProject(projectId, principal.getId());
        return ApiResponse.ok("project deleted", null);
    }

    @PostMapping("/{projectId}/collect")
    public ApiResponse<CollectionResult> collect(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("collection done",
                projectService.collectContributions(projectId, principal.getId()));
    }

    @PutMapping("/{projectId}/weight-config")
    public ApiResponse<ProjectDetailResponse> updateWeightConfig(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UpdateWeightConfigRequest request) {
        return ApiResponse.ok("weight config updated",
                projectService.updateWeightConfig(projectId, principal.getId(), request.weightConfig()));
    }
}
