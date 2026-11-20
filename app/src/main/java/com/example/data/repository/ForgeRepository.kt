package com.example.data.repository

import android.content.Context
import com.example.data.local.BuildRecordEntity
import com.example.data.local.ForgeDatabase
import com.example.data.local.ProjectConfigEntity
import com.example.data.local.SigningKeyEntity
import com.example.data.model.ApkInspectionReport
import com.example.data.model.ProjectDraft
import com.example.data.model.SourceType
import com.example.engine.ApkForgeEngine
import com.example.engine.ForgeProgressEvent
import com.example.engine.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ForgeRepository(private val context: Context) {
    private val db = ForgeDatabase.getInstance(context)
    private val projectDao = db.projectDao()
    private val buildDao = db.buildDao()
    private val keyDao = db.signingKeyDao()

    private val keystoreManager = KeystoreManager(context)
    private val forgeEngine = ApkForgeEngine(context, keystoreManager)
    private val initMutex = Mutex()

    val projects: Flow<List<ProjectConfigEntity>> = projectDao.observeAll()
    val builds: Flow<List<BuildRecordEntity>> = buildDao.observeAll()
    val signingKeys: Flow<List<SigningKeyEntity>> = keyDao.observeAll()

    suspend fun ensureDefaultSigningKey(): SigningKeyEntity = withContext(Dispatchers.IO) {
        initMutex.withLock {
            val existing = keyDao.getAllOnce()
            val validExisting = existing.firstOrNull { File(it.keystorePath).exists() }
            if (validExisting != null) return@withLock validExisting

            val generated = keystoreManager.createKeystore(
                alias = "studio_debug",
                commonName = "APKForge Studio Debug",
                organization = "APKForge Mobile",
                validityYears = 25
            )
            val entity = SigningKeyEntity(
                alias = generated.alias,
                commonName = "APKForge Studio Debug",
                organization = "APKForge Mobile",
                keystorePath = generated.keystorePath,
                sha256Fingerprint = generated.sha256Fingerprint,
                validityYears = 25,
                isDefault = true
            )
            val id = keyDao.insert(entity)
            entity.copy(id = id)
        }
    }

    suspend fun saveProjectDraft(draft: ProjectDraft): Long = withContext(Dispatchers.IO) {
        val entity = ProjectConfigEntity(
            id = draft.id,
            appName = draft.appName.trim(),
            packageName = draft.packageName.trim(),
            versionName = draft.versionName.trim(),
            versionCode = draft.versionCode,
            sourceType = draft.sourceType.name,
            targetUrl = draft.targetUrl.trim(),
            htmlContent = draft.htmlContent,
            themeColorHex = draft.themeColorHex,
            accentColorHex = draft.accentColorHex,
            badgeLabel = draft.badgeLabel,
            customIconUri = draft.customIconUri,
            enableJavaScript = draft.enableJavaScript,
            domStorageEnabled = draft.domStorageEnabled,
            fullscreen = draft.fullscreen,
            pullToRefresh = draft.pullToRefresh,
            selectedKeyId = draft.selectedKeyId,
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsert(entity)
    }

    suspend fun getProjectDraft(projectId: Long): ProjectDraft? = withContext(Dispatchers.IO) {
        projectDao.getById(projectId)?.toDraft()
    }

    suspend fun resolveSigningKey(keyId: Long): SigningKeyEntity = withContext(Dispatchers.IO) {
        val requested = if (keyId > 0L) keyDao.getById(keyId) else null
        if (requested != null && File(requested.keystorePath).exists()) {
            requested
        } else {
            ensureDefaultSigningKey()
        }
    }

    fun forgeApk(draft: ProjectDraft, signingKey: SigningKeyEntity): Flow<ForgeProgressEvent> {
        return forgeEngine.forgeApk(
            draft = draft,
            keystorePath = signingKey.keystorePath,
            signingAlias = signingKey.alias
        )
    }

    suspend fun recordBuild(
        projectId: Long,
        draft: ProjectDraft,
        signingKey: SigningKeyEntity,
        event: ForgeProgressEvent.Complete
    ): Long = withContext(Dispatchers.IO) {
        buildDao.insert(
            BuildRecordEntity(
                projectId = projectId,
                appName = draft.appName.trim(),
                packageName = draft.packageName.trim(),
                versionName = draft.versionName.trim(),
                versionCode = draft.versionCode,
                sourceType = draft.sourceType.name,
                apkFilePath = event.outputFile.absolutePath,
                fileSizeBytes = event.fileSizeBytes,
                signingAlias = signingKey.alias,
                certSha256 = event.certSha256,
                durationMs = event.durationMs,
                isVerified = event.isVerified
            )
        )
    }

    suspend fun getBuildById(buildId: Long): BuildRecordEntity? = withContext(Dispatchers.IO) {
        buildDao.getById(buildId)
    }

    suspend fun deleteBuild(build: BuildRecordEntity) = withContext(Dispatchers.IO) {
        runCatching { File(build.apkFilePath).delete() }
        buildDao.deleteById(build.id)
    }

    suspend fun createSigningKey(
        alias: String,
        commonName: String,
        organization: String,
        validityYears: Int
    ): SigningKeyEntity = withContext(Dispatchers.IO) {
        val result = keystoreManager.createKeystore(
            alias = alias,
            commonName = commonName,
            organization = organization,
            validityYears = validityYears
        )
        val entity = SigningKeyEntity(
            alias = result.alias,
            commonName = commonName.trim().ifBlank { "APKForge Developer" },
            organization = organization.trim().ifBlank { "Independent" },
            keystorePath = result.keystorePath,
            sha256Fingerprint = result.sha256Fingerprint,
            validityYears = validityYears.coerceIn(1, 50),
            isDefault = false
        )
        val id = keyDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun deleteSigningKey(key: SigningKeyEntity) = withContext(Dispatchers.IO) {
        if (key.isDefault) return@withContext
        keystoreManager.deleteKeystoreFile(key.keystorePath)
        keyDao.deleteById(key.id)
    }

    suspend fun inspectApk(apkPath: String): ApkInspectionReport = withContext(Dispatchers.IO) {
        forgeEngine.inspectApk(File(apkPath))
    }

    private fun ProjectConfigEntity.toDraft(): ProjectDraft = ProjectDraft(
        id = id,
        appName = appName,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        sourceType = runCatching { SourceType.valueOf(sourceType) }.getOrDefault(SourceType.TEMPLATE),
        targetUrl = targetUrl,
        htmlContent = htmlContent,
        themeColorHex = themeColorHex,
        accentColorHex = accentColorHex,
        badgeLabel = badgeLabel,
        customIconUri = customIconUri,
        enableJavaScript = enableJavaScript,
        domStorageEnabled = domStorageEnabled,
        fullscreen = fullscreen,
        pullToRefresh = pullToRefresh,
        selectedKeyId = selectedKeyId
    )
}
