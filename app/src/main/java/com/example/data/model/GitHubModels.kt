package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long,
    val name: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "html_url") val htmlUrl: String?,
    @Json(name = "public_repos") val publicRepos: Int = 0,
    @Json(name = "total_private_repos") val totalPrivateRepos: Int? = 0,
    val email: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean,
    @Json(name = "html_url") val htmlUrl: String,
    val description: String?,
    val fork: Boolean = false,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String = "",
    val private: Boolean = true,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GitHubBranch(
    val name: String,
    val commit: BranchCommit
)

@JsonClass(generateAdapter = true)
data class BranchCommit(
    val sha: String,
    val url: String?
)

@JsonClass(generateAdapter = true)
data class GitRef(
    val ref: String,
    @Json(name = "node_id") val nodeId: String? = null,
    val url: String? = null,
    val `object`: GitObject
)

@JsonClass(generateAdapter = true)
data class GitObject(
    val sha: String,
    val type: String,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateTreeRequest(
    @Json(name = "base_tree") val baseTree: String? = null,
    val tree: List<TreeItem>
)

@JsonClass(generateAdapter = true)
data class TreeItem(
    val path: String,
    val mode: String = "100644", // 100644 for regular file, 100755 for executable like gradlew
    val type: String = "blob",
    val content: String? = null,
    val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class TreeResponse(
    val sha: String,
    val url: String? = null,
    val tree: List<TreeEntryResponse>? = null
)

@JsonClass(generateAdapter = true)
data class TreeEntryResponse(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Long? = null
)

@JsonClass(generateAdapter = true)
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>
)

@JsonClass(generateAdapter = true)
data class CommitResponse(
    val sha: String,
    val url: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = false
)

@JsonClass(generateAdapter = true)
data class WorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class WorkflowRunsResponse(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "workflow_runs") val workflowRuns: List<GitHubWorkflowRun>
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowRun(
    val id: Long,
    val name: String?,
    @Json(name = "head_branch") val headBranch: String?,
    @Json(name = "head_sha") val headSha: String?,
    val status: String, // "queued", "in_progress", "completed", "waiting"
    val conclusion: String?, // "success", "failure", "cancelled", "timed_out", null
    @Json(name = "html_url") val htmlUrl: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "run_attempt") val runAttempt: Int = 1,
    @Json(name = "event") val event: String? = null
)

@JsonClass(generateAdapter = true)
data class ArtifactsResponse(
    @Json(name = "total_count") val totalCount: Int,
    val artifacts: List<GitHubArtifact>
)

@JsonClass(generateAdapter = true)
data class GitHubArtifact(
    val id: Long,
    val name: String,
    @Json(name = "size_in_bytes") val sizeInBytes: Long,
    @Json(name = "archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkflowJobsResponse(
    @Json(name = "total_count") val totalCount: Int,
    val jobs: List<WorkflowJob>
)

@JsonClass(generateAdapter = true)
data class WorkflowJob(
    val id: Long,
    @Json(name = "run_id") val runId: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val steps: List<WorkflowJobStep>? = null
)

@JsonClass(generateAdapter = true)
data class WorkflowJobStep(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int
)
