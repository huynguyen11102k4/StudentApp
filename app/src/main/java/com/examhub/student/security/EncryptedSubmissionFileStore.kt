package com.examhub.student.security

import android.content.Context
import java.io.File

class EncryptedSubmissionFileStore(
    context: Context,
    private val crypto: KeystoreCrypto
) {
    private val root = File(context.filesDir, "secure_submission_queue").apply { mkdirs() }

    fun write(clientSubmissionId: String, kind: String, bytes: ByteArray): String {
        val directory = File(root, clientSubmissionId).apply { mkdirs() }
        val file = File(directory, "$kind.enc")
        file.writeBytes(crypto.encrypt(bytes))
        return file.absolutePath
    }

    fun read(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { crypto.decrypt(file.readBytes()) }.getOrNull()
    }

    fun deleteSubmission(clientSubmissionId: String) {
        File(root, clientSubmissionId).deleteRecursively()
    }
}
