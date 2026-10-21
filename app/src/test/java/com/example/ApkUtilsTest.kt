package com.example

import com.example.data.analyzer.ApkUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test formatFileSize returns readable string`() {
        assertEquals("0 B", ApkUtils.formatFileSize(0))
        assertEquals("512.0 B", ApkUtils.formatFileSize(512))
        assertEquals("1.0 KB", ApkUtils.formatFileSize(1024))
        assertEquals("2.5 MB", ApkUtils.formatFileSize(2621440))
    }

    @Test
    fun `test isFileAvailable for non-existent and empty files`() {
        assertFalse(ApkUtils.isFileAvailable(null))
        assertFalse(ApkUtils.isFileAvailable(""))
        assertFalse(ApkUtils.isFileAvailable("/non/existent/path.apk"))

        val emptyFile = tempFolder.newFile("empty.apk")
        assertFalse(ApkUtils.isFileAvailable(emptyFile.absolutePath))

        emptyFile.writeBytes(ByteArray(100))
        assertTrue(ApkUtils.isFileAvailable(emptyFile.absolutePath))
    }

    @Test
    fun `test validateApk checks file size and zip integrity`() {
        val nonExistent = File(tempFolder.root, "does_not_exist.apk")
        val fail1 = ApkUtils.validateApk(nonExistent)
        assertTrue(fail1.isFailure)

        val smallFile = tempFolder.newFile("small.apk")
        smallFile.writeBytes(ByteArray(100))
        val fail2 = ApkUtils.validateApk(smallFile)
        assertTrue(fail2.isFailure)

        // Valid mock APK (valid ZIP structure >= 4096 bytes)
        val validMockApk = tempFolder.newFile("test_valid.apk")
        val randomBytes = ByteArray(10000).apply { java.util.Random(42).nextBytes(this) }
        ZipOutputStream(FileOutputStream(validMockApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(randomBytes)
            zos.closeEntry()
        }
        val pass = ApkUtils.validateApk(validMockApk)
        assertTrue("Expected success, but was failure: ${pass.exceptionOrNull()?.message}", pass.isSuccess)
    }

    @Test
    fun `test calculateSha256 produces valid 64-char hex string`() {
        val file = tempFolder.newFile("hash_test.apk")
        file.writeBytes("hello cndn apk".toByteArray())
        val hash = ApkUtils.calculateSha256(file)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun `test verifyApkSignature detects v1 signed APK`() {
        val signedApk = tempFolder.newFile("signed_v1.apk")
        ZipOutputStream(FileOutputStream(signedApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("manifest".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write("mock_signature".toByteArray())
            zos.closeEntry()
        }

        val sigInfo = ApkUtils.verifyApkSignature(null, signedApk)
        assertTrue("Must detect v1 signature", sigInfo.isSigned)
        assertTrue("Scheme should mention v1", sigInfo.scheme.contains("v1"))
    }

    @Test
    fun `test verifyApkSignature detects unsigned APK`() {
        val unsignedApk = tempFolder.newFile("unsigned.apk")
        ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write("dex".toByteArray())
            zos.closeEntry()
        }

        val sigInfo = ApkUtils.verifyApkSignature(null, unsignedApk)
        assertFalse("Unsigned APK should not be marked as signed", sigInfo.isSigned)
        assertEquals("İmzasız", sigInfo.scheme)
    }
}
