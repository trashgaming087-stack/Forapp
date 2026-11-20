package com.example.engine

import android.content.Context
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.example.data.model.ApkEntryGroup
import com.example.data.model.ApkInspectionReport
import com.example.data.model.BuildLogEntry
import com.example.data.model.ProjectDraft
import com.example.data.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

sealed interface ForgeProgressEvent {
    data class Stage(
        val stepIndex: Int,
        val totalSteps: Int,
        val stageTitle: String,
        val logEntry: BuildLogEntry
    ) : ForgeProgressEvent

    data class Complete(
        val outputFile: File,
        val fileSizeBytes: Long,
        val durationMs: Long,
        val certSha256: String,
        val isVerified: Boolean,
        val logs: List<BuildLogEntry>
    ) : ForgeProgressEvent

    data class Failure(
        val stageTitle: String,
        val message: String,
        val logs: List<BuildLogEntry>
    ) : ForgeProgressEvent
}

class ApkForgeEngine(
    private val context: Context,
    private val keystoreManager: KeystoreManager
) {
    private fun outputDir(): File = File(context.filesDir, "generated_apks").apply { mkdirs() }

    fun forgeApk(
        draft: ProjectDraft,
        keystorePath: String,
        signingAlias: String
    ): Flow<ForgeProgressEvent> = flow {
        val startMs = System.currentTimeMillis()
        val logs = mutableListOf<BuildLogEntry>()
        val totalSteps = 7
        var currentStage = "Validation"

        fun emitLog(step: Int, stage: String, msg: String, isSuccess: Boolean = false): ForgeProgressEvent.Stage {
            currentStage = stage
            val entry = BuildLogEntry(System.currentTimeMillis(), stage, msg, isSuccess = isSuccess)
            logs.add(entry)
            return ForgeProgressEvent.Stage(step, totalSteps, stage, entry)
        }

        try {
            // Step 1: Validate inputs
            emit(emitLog(1, "Validation", "Validating manifest config for ${draft.packageName}..."))
            require(draft.appName.trim().isNotEmpty()) { "App name cannot be blank." }
            require(PACKAGE_REGEX.matches(draft.packageName.trim())) {
                "Invalid package ID '${draft.packageName}'. Use reverse-domain format (e.g. com.studio.myapp)."
            }
            if (draft.sourceType == SourceType.URL) {
                val u = draft.targetUrl.trim()
                require(u.startsWith("http://") || u.startsWith("https://")) {
                    "Website URL must begin with http:// or https://"
                }
            } else {
                require(draft.htmlContent.trim().isNotEmpty()) {
                    "HTML bundle cannot be empty."
                }
            }

            // Step 2: Unpack base template APK
            emit(emitLog(2, "Template", "Loading precompiled Android runtime template (minSdk 24, targetSdk 35)..."))
            val templateEntries = LinkedHashMap<String, ApkZipInputEntry>()
            context.assets.open("base_template.apk").use { raw ->
                ZipInputStream(raw).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && !entry.name.startsWith("META-INF/")) {
                            val bytes = zis.readBytes()
                            val storeUncompressed = entry.name == "resources.arsc" ||
                                entry.name.endsWith(".png", ignoreCase = true)
                            templateEntries[entry.name] = ApkZipInputEntry(entry.name, bytes, storeUncompressed)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val manifestBytes = templateEntries["AndroidManifest.xml"]?.bytes
                ?: error("Template missing AndroidManifest.xml")

            // Step 3: Patch Binary AndroidManifest.xml (AXML)
            emit(emitLog(3, "Manifest AXML", "Patching binary string pool & attributes (${draft.packageName} v${draft.versionName})..."))
            val patchedManifest = AxmlEditor.patchManifest(
                axmlBytes = manifestBytes,
                newPackageName = draft.packageName.trim(),
                newAppName = draft.appName.trim(),
                newVersionCode = draft.versionCode.coerceAtLeast(1),
                newVersionName = draft.versionName.trim().ifBlank { "1.0.0" }
            )
            templateEntries["AndroidManifest.xml"] = ApkZipInputEntry(
                name = "AndroidManifest.xml",
                bytes = patchedManifest,
                storeUncompressed = false
            )

            // Step 4: Bundle runtime configuration & web payload
            emit(emitLog(4, "Assets", "Writing forge_config.json and web runtime payload..."))
            val configJson = JSONObject().apply {
                put("sourceType", if (draft.sourceType == SourceType.URL) "URL" else "HTML_BUNDLE")
                put("targetUrl", draft.targetUrl.trim())
                put("appName", draft.appName.trim())
                put("themeColorHex", draft.themeColorHex)
                put("enableJavaScript", draft.enableJavaScript)
                put("domStorageEnabled", draft.domStorageEnabled)
                put("fullscreen", draft.fullscreen)
                put("pullToRefresh", draft.pullToRefresh)
            }.toString()

            templateEntries["assets/forge_config.json"] = ApkZipInputEntry(
                name = "assets/forge_config.json",
                bytes = configJson.toByteArray(StandardCharsets.UTF_8),
                storeUncompressed = false
            )
            if (draft.sourceType != SourceType.URL) {
                templateEntries["assets/www/index.html"] = ApkZipInputEntry(
                    name = "assets/www/index.html",
                    bytes = draft.htmlContent.toByteArray(StandardCharsets.UTF_8),
                    storeUncompressed = false
                )
            }

            // Step 5: Render & replace launcher icon mipmaps
            emit(emitLog(5, "Icons", "Rendering 5-density launcher & round PNG mipmaps..."))
            val iconSet = IconRenderer.renderIconSet(
                context = context,
                customIconUri = draft.customIconUri,
                badgeLabel = draft.badgeLabel.ifBlank { draft.appName.take(2) },
                bgHex = draft.themeColorHex,
                accentHex = draft.accentColorHex
            )
            for (key in templateEntries.keys.toList()) {
                val density = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
                    .firstOrNull { key.contains("mipmap-$it") }
                if (density != null) {
                    when {
                        key.endsWith("/ic_launcher.png") -> {
                            val png = iconSet.squareByDensity[density]
                            if (png != null) {
                                templateEntries[key] = ApkZipInputEntry(key, png, storeUncompressed = true)
                            }
                        }
                        key.endsWith("/ic_launcher_round.png") -> {
                            val png = iconSet.roundByDensity[density]
                            if (png != null) {
                                templateEntries[key] = ApkZipInputEntry(key, png, storeUncompressed = true)
                            }
                        }
                    }
                }
            }

            // Step 6: 4-byte aligned ZIP packaging
            emit(emitLog(6, "ZipAlign", "Aligning resources.arsc & PNG entries on 4-byte boundaries..."))
            val safeFilePrefix = draft.appName.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "forged_app" }
            val timestamp = System.currentTimeMillis()
            val alignedTemp = File(context.cacheDir, "aligned_${safeFilePrefix}_$timestamp.apk")
            val signedOut = File(outputDir(), "${safeFilePrefix}_v${draft.versionName.trim()}_$timestamp.apk")

            ZipAligner.writeAlignedZip(templateEntries.values.toList(), alignedTemp)

            // Step 7: Sign with apksig (v1 + v2 + v3) & verify
            emit(emitLog(7, "Signing", "Signing APK with RSA-2048 '$signingAlias' (v1 + v2 + v3 schemes)..."))
            val material = keystoreManager.loadSigningMaterial(keystorePath, signingAlias)
            val signerConfig = ApkSigner.SignerConfig.Builder(
                signingAlias,
                material.privateKey,
                listOf(material.certificate)
            ).build()

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(alignedTemp)
                .setOutputApk(signedOut)
                .setCreatedBy("APKForge Studio")
                .setMinSdkVersion(24)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()

            alignedTemp.delete()

            val verifyResult = ApkVerifier.Builder(signedOut).build().verify()
            val durationMs = System.currentTimeMillis() - startMs
            val finalEntry = BuildLogEntry(
                timestampMs = System.currentTimeMillis(),
                stage = "Verified",
                message = if (verifyResult.isVerified) {
                    "APK verified (v1=${verifyResult.isVerifiedUsingV1Scheme}, v2=${verifyResult.isVerifiedUsingV2Scheme}, v3=${verifyResult.isVerifiedUsingV3Scheme}) in ${durationMs}ms."
                } else {
                    "Signed with ${verifyResult.errors.size} verification warning(s)."
                },
                isSuccess = verifyResult.isVerified
            )
            logs.add(finalEntry)

            emit(
                ForgeProgressEvent.Complete(
                    outputFile = signedOut,
                    fileSizeBytes = signedOut.length(),
                    durationMs = durationMs,
                    certSha256 = material.sha256Fingerprint,
                    isVerified = verifyResult.isVerified,
                    logs = logs.toList()
                )
            )
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            logs.add(BuildLogEntry(System.currentTimeMillis(), currentStage, msg, isError = true))
            emit(ForgeProgressEvent.Failure(currentStage, msg, logs.toList()))
        }
    }.flowOn(Dispatchers.IO)

    fun inspectApk(apkFile: File): ApkInspectionReport {
        require(apkFile.exists()) { "APK file no longer exists on disk." }

        val verifyResult = ApkVerifier.Builder(apkFile).build().verify()
        val cert = verifyResult.signerCertificates.firstOrNull()

        var manifestSummary = ManifestSummary("unknown", "unknown", "1.0", 1, 24, 35)
        val groupSizes = linkedMapOf<String, Pair<Int, Long>>()

        ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            if (manifestEntry != null) {
                zip.getInputStream(manifestEntry).use { stream ->
                    manifestSummary = AxmlEditor.parseManifestSummary(stream.readBytes())
                }
            }

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e: ZipEntry = entries.nextElement()
                if (e.isDirectory) continue
                val cat = when {
                    e.name.endsWith(".dex") -> "Dalvik Bytecode (.dex)"
                    e.name == "resources.arsc" -> "Compiled Resources (.arsc)"
                    e.name == "AndroidManifest.xml" -> "Binary Manifest"
                    e.name.startsWith("assets/") -> "Web & Config Assets"
                    e.name.startsWith("res/") -> "Drawables & Mipmaps"
                    e.name.startsWith("META-INF/") -> "v1 JAR Signatures"
                    else -> "Other Entries"
                }
                val prev = groupSizes[cat] ?: (0 to 0L)
                val size = if (e.size >= 0) e.size else 0L
                groupSizes[cat] = (prev.first + 1) to (prev.second + size)
            }
        }

        val aligned4 = checkResourcesAligned(apkFile)

        return ApkInspectionReport(
            fileName = apkFile.name,
            fileSizeBytes = apkFile.length(),
            packageName = manifestSummary.packageName,
            appLabel = manifestSummary.appLabel,
            versionName = manifestSummary.versionName,
            versionCode = manifestSummary.versionCode,
            minSdkVersion = manifestSummary.minSdkVersion,
            targetSdkVersion = manifestSummary.targetSdkVersion,
            isSignatureVerified = verifyResult.isVerified,
            v1Verified = verifyResult.isVerifiedUsingV1Scheme,
            v2Verified = verifyResult.isVerifiedUsingV2Scheme,
            v3Verified = verifyResult.isVerifiedUsingV3Scheme,
            resourcesAligned4Byte = aligned4,
            signerSubject = cert?.subjectX500Principal?.name ?: "No signer certificate",
            certSha256 = cert?.let { KeystoreManager.computeFingerprint(it, "SHA-256") } ?: "N/A",
            certSha1 = cert?.let { KeystoreManager.computeFingerprint(it, "SHA-1") } ?: "N/A",
            entryGroups = groupSizes.map { (k, v) -> ApkEntryGroup(k, v.first, v.second) }
                .sortedByDescending { it.totalBytes },
            verificationWarnings = verifyResult.warnings.map { it.toString() },
            verificationErrors = verifyResult.errors.map { it.toString() }
        )
    }

    private fun checkResourcesAligned(apkFile: File): Boolean {
        return runCatching {
            RandomAccessFile(apkFile, "r").use { raf ->
                var pos = 0L
                val fileLen = raf.length()
                val header = ByteArray(30)
                while (pos + 30 <= fileLen) {
                    raf.seek(pos)
                    raf.readFully(header)
                    val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    val sig = bb.getInt(0)
                    if (sig != 0x04034b50) break
                    val method = bb.getShort(8).toInt() and 0xFFFF
                    val compSize = bb.getInt(18).toLong() and 0xFFFFFFFFL
                    val nameLen = bb.getShort(26).toInt() and 0xFFFF
                    val extraLen = bb.getShort(28).toInt() and 0xFFFF
                    val nameBytes = ByteArray(nameLen)
                    raf.readFully(nameBytes)
                    val name = String(nameBytes, StandardCharsets.UTF_8)
                    val dataStart = pos + 30L + nameLen + extraLen
                    if (name == "resources.arsc") {
                        return@use method == 0 && (dataStart % 4L == 0L)
                    }
                    pos = dataStart + compSize
                }
                false
            }
        }.getOrDefault(false)
    }

    companion object {
        val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
    }
}
