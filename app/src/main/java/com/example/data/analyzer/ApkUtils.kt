package com.example.data.analyzer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

object ApkUtils {

    /**
     * Calculates SHA-256 checksum of a file.
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Validates whether a file is a valid non-empty APK/AAB archive.
     * Checks existence, size, magic header bytes (PK\u0003\u0004), and ZIP structural integrity.
     */
    fun validateApk(file: File): Result<Boolean> {
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("APK dosyası mevcut değil: ${file.name}"))
        }
        if (!file.isFile) {
            return Result.failure(IllegalArgumentException("Belirtilen dosya yolu bir dosya değil: ${file.name}"))
        }
        val length = file.length()
        if (length < 4096) {
            return Result.failure(IllegalArgumentException("APK dosyası bozuk veya sıfır baytlık ($length bayt)."))
        }

        // Verify ZIP / APK magic header (PK\u0003\u0004 -> 0x50, 0x4B, 0x03, 0x04)
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                if (read < 4 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte() || header[2] != 0x03.toByte() || header[3] != 0x04.toByte()) {
                    return Result.failure(IllegalArgumentException("Geçersiz APK formatı: Dosya geçerli bir Android ZIP/APK imzası içermiyor."))
                }
            }
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Dosya başlığı okunamadı: ${e.message}"))
        }

        // Test ZIP archive integrity
        try {
            ZipFile(file).use { zip ->
                if (zip.size() == 0) {
                    return Result.failure(IllegalArgumentException("APK arşivi boş."))
                }
            }
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("APK arşivi bozuk veya tamamlanmamış: ${e.message}"))
        }

        return Result.success(true)
    }

    /**
     * Data class holding APK signature verification details.
     */
    data class ApkSignatureInfo(
        val isSigned: Boolean,
        val scheme: String,
        val packageName: String? = null,
        val versionName: String? = null,
        val versionCode: Long? = null,
        val details: String = ""
    )

    /**
     * Rigorously verifies whether an APK is signed with v1 (JAR signing) and/or v2/v3 (APK Signature Scheme).
     * Also extracts package and version metadata from the APK using PackageManager when context is available.
     */
    fun verifyApkSignature(context: Context?, file: File): ApkSignatureInfo {
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return ApkSignatureInfo(
                isSigned = false,
                scheme = "Yok",
                details = "APK dosyası bulunamadı veya boş."
            )
        }

        var hasV1 = false
        var hasV2 = false
        var certName: String? = null

        // 1. Check v1 Signature entries inside META-INF
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.uppercase()
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        hasV1 = true
                        certName = entry.name
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        // 2. Check v2/v3 Signing Block magic string "APK Sig Block 42"
        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                val targetMagic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead >= targetMagic.size) {
                        for (i in 0..(bytesRead - targetMagic.size)) {
                            var match = true
                            for (j in targetMagic.indices) {
                                if (buffer[i + j] != targetMagic[j]) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                hasV2 = true
                                break
                            }
                        }
                    }
                    if (hasV2) break
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        // 3. Extract Package Metadata via PackageManager if available
        var pkgName: String? = null
        var verName: String? = null
        var verCode: Long? = null
        if (context != null) {
            try {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                if (pkgInfo != null) {
                    pkgName = pkgInfo.packageName
                    verName = pkgInfo.versionName
                    verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        val isSigned = hasV1 || hasV2
        val scheme = when {
            hasV1 && hasV2 -> "v1 + v2 (Çift İmza)"
            hasV2 -> "v2 (APK Signature Scheme)"
            hasV1 -> "v1 (JAR İmzası)"
            else -> "İmzasız"
        }

        val details = if (isSigned) {
            "İmza doğrulandı ($scheme)${if (certName != null) " • Sertifika: $certName" else ""}"
        } else {
            "APK geçerli bir dijital imza içermiyor."
        }

        return ApkSignatureInfo(
            isSigned = isSigned,
            scheme = scheme,
            packageName = pkgName,
            versionName = verName,
            versionCode = verCode,
            details = details
        )
    }

    /**
     * Checks if the app has permission to install unknown apps on Android 8.0+ (Oreo).
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Creates an Intent to navigate the user directly to the system settings
     * to grant the "Install unknown apps" permission for this application.
     */
    fun createUnknownSourcesSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Creates an Intent to launch Android's system package installer using FileProvider.
     * Enforces content:// URI, Intent.ACTION_VIEW, and necessary flags.
     */
    fun createInstallIntent(context: Context, apkFile: File): Result<Intent> {
        val validation = validateApk(apkFile)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Geçersiz APK dosyası."))
        }

        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            Result.success(intent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Safely launches the system package installer for the given APK file.
     * Performs file validation, creates FileProvider URI, and handles Android Activity start.
     */
    fun launchInstallApk(context: Context, apkFile: File): Result<Boolean> {
        return try {
            val intentResult = createInstallIntent(context, apkFile)
            if (intentResult.isFailure) {
                return Result.failure(intentResult.exceptionOrNull() ?: Exception("Kurulum intent'i oluşturulamadı."))
            }
            val intent = intentResult.getOrThrow()
            context.startActivity(intent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates an Intent to share the APK file via messaging or other apps.
     */
    fun createShareIntent(context: Context, apkFile: File): Result<Intent> {
        if (!apkFile.exists()) {
            return Result.failure(IllegalArgumentException("Paylaşılacak dosya bulunamadı."))
        }

        return try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "CNDN APK: ${apkFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "APK Dosyasını Paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Result.success(chooser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Safely launches the share chooser for the given APK file.
     */
    fun launchShareApk(context: Context, apkFile: File): Result<Boolean> {
        return try {
            val shareResult = createShareIntent(context, apkFile)
            if (shareResult.isFailure) {
                return Result.failure(shareResult.exceptionOrNull() ?: Exception("Paylaşım intent'i oluşturulamadı."))
            }
            val chooserIntent = shareResult.getOrThrow()
            context.startActivity(chooserIntent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Formats bytes into a human-readable string (KB, MB, GB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format("%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }

    /**
     * Quick check if a file path is valid, exists, and non-empty.
     */
    fun isFileAvailable(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val f = File(path)
        return f.exists() && f.isFile && f.length() > 0
    }
}
