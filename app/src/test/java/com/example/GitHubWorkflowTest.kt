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

        val releaseYaml = WorkflowGenerator.generateWorkflowYaml(BuildTarget.RELEASE_APK)
        assertTrue(releaseYaml.contains("assembleRelease"))
        assertTrue(releaseYaml.contains("cndn-apk-release"))

        val bundleYaml = WorkflowGenerator.generateWorkflowYaml(BuildTarget.BUNDLE_AAB)
        assertTrue(bundleYaml.contains("bundleRelease"))
        assertTrue(bundleYaml.contains("cndn-aab-release"))
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
}
