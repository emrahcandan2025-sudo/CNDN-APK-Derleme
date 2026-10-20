package com.example

import com.example.data.analyzer.ZipProjectAnalyzer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test valid Android project ZIP is detected successfully`() {
        val zipBytes = createZipBytes(
            mapOf(
                "settings.gradle.kts" to "rootProject.name = \"TestApp\"\ninclude(\":app\")",
                "build.gradle.kts" to "plugins { id(\"com.android.application\") }",
                "app/build.gradle.kts" to "android { compileSdk = 34\n defaultConfig { applicationId = \"com.test.app\" } }",
                "app/src/main/AndroidManifest.xml" to "<manifest package=\"com.test.app\"><application><activity android:name=\".MainActivity\"/></application></manifest>",
                "gradlew" to "#!/bin/sh\nexec gradle \"$@\""
            )
        )

        val destDir = tempFolder.newFolder("extract_valid")
        val result = ZipProjectAnalyzer.extractAndAnalyze(
            inputStream = ByteArrayInputStream(zipBytes),
            destParentDir = destDir
        )

        assertTrue("Proje geçerli Android projesi olarak doğrulanmalı", result.isValidAndroidProject)
        assertNull(result.validationError)
        assertTrue(result.hasSettingsGradle)
        assertTrue(result.hasBuildGradle)
        assertTrue(result.hasManifest)
        assertTrue(result.hasGradleWrapper)
        assertEquals("com.test.app", result.applicationId)
    }

    @Test
    fun `test non-Android project ZIP is rejected with descriptive error`() {
        val zipBytes = createZipBytes(
            mapOf(
                "readme.txt" to "This is a random python project",
                "main.py" to "print('hello')",
                "requirements.txt" to "requests==2.28.1"
            )
        )

        val destDir = tempFolder.newFolder("extract_non_android")
        val result = ZipProjectAnalyzer.extractAndAnalyze(
            inputStream = ByteArrayInputStream(zipBytes),
            destParentDir = destDir
        )

        assertFalse("Android projesi içermeyen ZIP reddedilmeli", result.isValidAndroidProject)
        assertNotNull(result.validationError)
        assertTrue(result.validationError!!.contains("geçerli bir Android projesi içermiyor"))
    }

    @Test
    fun `test corrupted ZIP is rejected safely`() {
        val corruptedBytes = "This is not a valid zip byte sequence".toByteArray()
        val destDir = tempFolder.newFolder("extract_corrupt")

        val result = ZipProjectAnalyzer.extractAndAnalyze(
            inputStream = ByteArrayInputStream(corruptedBytes),
            destParentDir = destDir
        )

        assertFalse("Bozuk ZIP reddedilmeli", result.isValidAndroidProject)
        assertNotNull(result.validationError)
    }

    @Test
    fun `test malicious ZIP with Path Traversal (Zip Slip) is blocked`() {
        // Create an entry that attempts to escape using ../
        val zipBytes = createZipBytes(
            mapOf(
                "../../evil_exploit.sh" to "echo hacked",
                "settings.gradle" to "include ':app'"
            )
        )

        val destDir = tempFolder.newFolder("extract_zip_slip")
        val result = ZipProjectAnalyzer.extractAndAnalyze(
            inputStream = ByteArrayInputStream(zipBytes),
            destParentDir = destDir
        )

        assertFalse("Zip Slip içeren arşiv reddedilmeli", result.isValidAndroidProject)
        assertTrue("Şüpheli yol tespiti bayrağı aktif olmalı", result.containsSuspiciousPaths)
        assertTrue(result.validationError!!.contains("Tehlikeli dosya yolu") || result.validationError!!.contains("Zip Slip"))
    }

    @Test
    fun `test Gradle Wrapper auto-fix injector generates wrapper files`() {
        val projectFolder = tempFolder.newFolder("project_without_wrapper")
        File(projectFolder, "build.gradle.kts").writeText("plugins { id(\"com.android.application\") }")

        ZipProjectAnalyzer.injectGradleWrapper(projectFolder)

        val gradlew = File(projectFolder, "gradlew")
        val props = File(projectFolder, "gradle/wrapper/gradle-wrapper.properties")

        assertTrue("gradlew dosyası oluşturulmalı", gradlew.exists())
        assertTrue("gradle-wrapper.properties oluşturulmalı", props.exists())
        assertTrue(props.readText().contains("gradle-9.3.1"))
    }

    private fun createZipBytes(entries: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
