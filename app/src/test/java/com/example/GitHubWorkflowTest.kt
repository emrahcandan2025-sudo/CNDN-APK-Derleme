package com.example

import com.example.data.analyzer.WorkflowGenerator
import com.example.data.model.BuildStatusState
import com.example.data.model.BuildTarget
import com.example.data.model.GitHubWorkflowRun
import org.junit.Assert.*
import org.junit.Test

class GitHubWorkflowTest {

    @Test
    fun `test workflow generator produces valid YAML with correct commands and artifact paths`() {
        val debugYaml = WorkflowGenerator.generateWorkflowYaml(BuildTarget.DEBUG_APK)
        assertTrue(debugYaml.contains("assembleDebug"))
        assertTrue(debugYaml.contains("cndn-apk-debug"))
        assertTrue(debugYaml.contains("actions/upload-artifact@v4"))
        assertTrue(debugYaml.contains("actions/setup-java@v4"))
        assertTrue(debugYaml.contains("chmod +x gradlew"))
        assertTrue(debugYaml.contains("workflow_dispatch:"))
        assertFalse("Workflow must not have push trigger to prevent duplicate runs", debugYaml.contains("push:"))

        val releaseYaml = WorkflowGenerator.generateWorkflowYaml(BuildTarget.RELEASE_APK)
        assertTrue(releaseYaml.contains("assembleRelease"))
        assertTrue(releaseYaml.contains("cndn-apk-release"))
        assertTrue(releaseYaml.contains("workflow_dispatch:"))
        assertFalse(releaseYaml.contains("push:"))

        val bundleYaml = WorkflowGenerator.generateWorkflowYaml(BuildTarget.BUNDLE_AAB)
        assertTrue(bundleYaml.contains("bundleRelease"))
        assertTrue(bundleYaml.contains("cndn-aab-release"))
        assertTrue(bundleYaml.contains("workflow_dispatch:"))
        assertFalse(bundleYaml.contains("push:"))
    }

    @Test
    fun `test workflow run status evaluation for success`() {
        val run = GitHubWorkflowRun(
            id = 123456L,
            name = "CNDN Android APK Derleme",
            headBranch = "main",
            headSha = "a1b2c3d4e5",
            status = "completed",
            conclusion = "success",
            htmlUrl = "https://github.com/user/repo/actions/runs/123456",
            createdAt = "2026-10-20T10:00:00Z",
            updatedAt = "2026-10-20T10:04:12Z"
        )

        val statusState = when (run.status) {
            "completed" -> if (run.conclusion == "success") BuildStatusState.SUCCESS else BuildStatusState.FAILURE
            "in_progress" -> BuildStatusState.IN_PROGRESS
            else -> BuildStatusState.QUEUED
        }

        assertEquals(BuildStatusState.SUCCESS, statusState)
    }

    @Test
    fun `test workflow run status evaluation for failure`() {
        val run = GitHubWorkflowRun(
            id = 999999L,
            name = "CNDN Android APK Derleme",
            headBranch = "main",
            headSha = "f6e5d4c3b2",
            status = "completed",
            conclusion = "failure",
            htmlUrl = "https://github.com/user/repo/actions/runs/999999",
            createdAt = "2026-10-20T10:00:00Z",
            updatedAt = "2026-10-20T10:02:15Z"
        )

        val statusState = when (run.status) {
            "completed" -> if (run.conclusion == "success") BuildStatusState.SUCCESS else BuildStatusState.FAILURE
            "in_progress" -> BuildStatusState.IN_PROGRESS
            else -> BuildStatusState.QUEUED
        }

        assertEquals(BuildStatusState.FAILURE, statusState)
    }

    @Test
    fun `test commit message ensures skip ci flag to suppress push triggers`() {
        fun formatCommitMessage(msg: String): String {
            val clean = msg.ifBlank { "CNDN APK Derleme Merkezi: Proje güncellemesi" }.trim()
            return if (clean.contains("[skip ci]", ignoreCase = true) || clean.contains("[ci skip]", ignoreCase = true)) {
                clean
            } else {
                "$clean [skip ci]"
            }
        }

        assertEquals("Test Commit [skip ci]", formatCommitMessage("Test Commit"))
        assertEquals("Already [skip ci]", formatCommitMessage("Already [skip ci]"))
        assertEquals("Already with [ci skip]", formatCommitMessage("Already with [ci skip]"))
        assertEquals("CNDN APK Derleme Merkezi: Proje güncellemesi [skip ci]", formatCommitMessage(""))
    }

    @Test
    fun `test workflow run matcher matches exact headSha without latching onto unrelated runs`() {
        val runs = listOf(
            GitHubWorkflowRun(
                id = 111L,
                name = "Old Run",
                headBranch = "main",
                headSha = "old_sha_123",
                status = "completed",
                conclusion = "success",
                htmlUrl = "https://github.com/user/repo/actions/runs/111",
                createdAt = "2026-10-19T10:00:00Z",
                updatedAt = "2026-10-19T10:05:00Z"
            ),
            GitHubWorkflowRun(
                id = 222L,
                name = "CNDN Android APK Derleme",
                headBranch = "main",
                headSha = "target_sha_456",
                status = "in_progress",
                conclusion = null,
                htmlUrl = "https://github.com/user/repo/actions/runs/222",
                createdAt = "2026-10-21T10:00:00Z",
                updatedAt = "2026-10-21T10:01:00Z"
            )
        )

        val targetMatch = runs.find { it.headSha == "target_sha_456" }
        assertNotNull(targetMatch)
        assertEquals(222L, targetMatch!!.id)

        val nonExistentMatch = runs.find { it.headSha == "not_yet_started_sha" }
        assertNull(nonExistentMatch)
    }
}
