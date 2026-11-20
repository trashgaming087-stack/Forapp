package com.example.engine

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

data class LoadedSigningMaterial(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String
)

data class GeneratedKeyResult(
    val keystorePath: String,
    val alias: String,
    val sha256Fingerprint: String
)

class KeystoreManager(private val context: Context) {
    private val bcProvider = BouncyCastleProvider()

    private fun keystoreDir(): File = File(context.filesDir, "keystores").apply { mkdirs() }

    fun createKeystore(
        alias: String,
        commonName: String,
        organization: String,
        validityYears: Int = 25,
        password: String = DEFAULT_STORE_PASS
    ): GeneratedKeyResult {
        val cleanAlias = alias.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
            .ifBlank { "forge_key_${System.currentTimeMillis()}" }
        val fileName = "${cleanAlias}_${System.currentTimeMillis()}.p12"
        val outFile = File(keystoreDir(), fileName)

        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val notAfter = Date(now + validityYears.toLong() * 365L * 24L * 60L * 60L * 1000L)
        val serial = BigInteger(64, SecureRandom()).abs().max(BigInteger.ONE)

        val subject = X500Name("CN=${sanitizeDn(commonName)}, O=${sanitizeDn(organization)}, C=US")
        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(bcProvider)
            .build(keyPair.private)
        val cert = JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certBuilder.build(signer))

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            cleanAlias,
            keyPair.private,
            password.toCharArray(),
            arrayOf(cert)
        )
        FileOutputStream(outFile).use { fos ->
            keyStore.store(fos, password.toCharArray())
        }

        return GeneratedKeyResult(
            keystorePath = outFile.absolutePath,
            alias = cleanAlias,
            sha256Fingerprint = computeFingerprint(cert, "SHA-256")
        )
    }

    fun loadSigningMaterial(
        keystorePath: String,
        alias: String,
        password: String = DEFAULT_STORE_PASS
    ): LoadedSigningMaterial {
        val ks = KeyStore.getInstance("PKCS12")
        FileInputStream(keystorePath).use { fis ->
            ks.load(fis, password.toCharArray())
        }
        val key = ks.getKey(alias, password.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(alias) as X509Certificate
        return LoadedSigningMaterial(
            privateKey = key,
            certificate = cert,
            sha256Fingerprint = computeFingerprint(cert, "SHA-256"),
            sha1Fingerprint = computeFingerprint(cert, "SHA-1")
        )
    }

    fun deleteKeystoreFile(keystorePath: String) {
        runCatching { File(keystorePath).delete() }
    }

    private fun sanitizeDn(value: String): String =
        value.replace(",", " ").replace("=", " ").trim().ifBlank { "APKForge" }

    companion object {
        const val DEFAULT_STORE_PASS = "apkforge_local"

        fun computeFingerprint(cert: X509Certificate, algorithm: String): String {
            val digest = MessageDigest.getInstance(algorithm).digest(cert.encoded)
            return digest.joinToString(":") { "%02X".format(it) }
        }
    }
}
