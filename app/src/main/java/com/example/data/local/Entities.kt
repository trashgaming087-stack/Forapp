package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val sourceType: String,
    val targetUrl: String,
    val htmlContent: String,
    val themeColorHex: String,
    val accentColorHex: String,
    val badgeLabel: String,
    val customIconUri: String?,
    val enableJavaScript: Boolean,
    val domStorageEnabled: Boolean,
    val fullscreen: Boolean,
    val pullToRefresh: Boolean,
    val selectedKeyId: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "builds")
data class BuildRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val sourceType: String,
    val apkFilePath: String,
    val fileSizeBytes: Long,
    val signingAlias: String,
    val certSha256: String,
    val durationMs: Long,
    val isVerified: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "signing_keys")
data class SigningKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alias: String,
    val commonName: String,
    val organization: String,
    val keystorePath: String,
    val sha256Fingerprint: String,
    val validityYears: Int,
    val isDefault: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
