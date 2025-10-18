// data/repositories/SafetyEvidenceRepository.kt - Evidence Storage and Management
package com.safeguardme.app.data.repositories

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.safeguardme.app.data.models.EvidenceType
import com.safeguardme.app.data.models.EvidenceUploadStatus
import com.safeguardme.app.data.models.IncidentTimelineEntry
import com.safeguardme.app.data.models.SafetyEvidence
import com.safeguardme.app.data.models.SafetySession
import com.safeguardme.app.data.models.SessionStatus
import com.safeguardme.app.managers.EvidenceVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyEvidenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    private val storageRepository: StorageRepository,
    private val evidenceVault: EvidenceVault
) {
    companion object {
        private const val TAG = "SafetyEvidenceRepo"
        private const val EVIDENCE_COLLECTION = "safetyEvidence"
        private const val SESSIONS_COLLECTION = "safetySessions"
        private const val STORAGE_BUCKET_PATH = "safety_evidence"
        private const val MAX_UPLOAD_RETRY_ATTEMPTS = 3
    }

    /**
     * ✅ PRIVATE: Auth state validation helper (internal use only)
     */
    private suspend fun validateCurrentAuthState(): AuthValidationResult {
        return try {
            val currentUser = auth.currentUser
                ?: return AuthValidationResult.NotAuthenticated

            // Force token refresh to ensure validity
            val token = currentUser.getIdToken(true).await()

            if (token.token.isNullOrEmpty()) {
                return AuthValidationResult.InvalidToken
            }

            AuthValidationResult.Valid(currentUser.uid, token.token!!)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Auth validation failed", e)
            AuthValidationResult.ValidationFailed(e)
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingUploads = mutableSetOf<String>()

    /**
     * Save safety evidence locally and queue for upload
     */
    suspend fun saveEvidence(evidence: SafetyEvidence): Result<SafetyEvidence> {
        return try {
            Log.d(TAG, "💾 Saving evidence: ${evidence.type} for session ${evidence.sessionId}")

            // Save locally first
            val savedEvidence = saveEvidenceLocally(evidence)

            // Queue for upload to Firebase
            queueForUpload(savedEvidence)

            Log.i(TAG, "✅ Evidence saved: ${savedEvidence.id}")
            Result.success(savedEvidence)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save evidence", e)
            Result.failure(e)
        }
    }

    /**
     * Get all evidence for a session
     */
    suspend fun getEvidenceForSession(sessionId: String): Result<List<SafetyEvidence>> {
        return try {
            Log.d(TAG, "📂 Getting evidence for session: $sessionId")

            // Try to get from local storage first
            val localEvidence = getLocalEvidenceForSession(sessionId)

            // Also check Firebase for any evidence not yet synced locally
            val remoteEvidence = getRemoteEvidenceForSession(sessionId)

            // Merge and deduplicate
            val allEvidence = (localEvidence + remoteEvidence)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }

            Log.i(TAG, "📊 Found ${allEvidence.size} evidence items for session $sessionId")
            Result.success(allEvidence)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get evidence for session", e)
            Result.failure(e)
        }
    }

    /**
     * Upload pending evidence to Firebase
     */
    /**
     * Upload pending evidence to Firebase
     */
    suspend fun uploadPendingEvidence(): Result<Int> {
        return try {
            Log.d(TAG, "☁️ Uploading pending evidence...")

            // ✅ STEP 1: Validate current auth state
            val authValidation = validateCurrentAuthState()
            if (authValidation !is AuthValidationResult.Valid) {
                Log.w(TAG, "⚠️ Cannot process upload queue - auth not valid: $authValidation")
                return Result.failure(SecurityException("Auth not valid for upload queue processing"))
            }

            val pendingFiles = getPendingUploadFiles()
            var uploadedCount = 0

            pendingFiles.forEach { evidenceFile ->
                try {
                    // Re-validate auth for each upload (long-running operation)
                    val currentAuthValidation = validateCurrentAuthState()
                    if (currentAuthValidation is AuthValidationResult.Valid) {
                        val evidence = loadEvidenceFromFile(evidenceFile)
                        if (evidence != null && uploadEvidenceToFirebase(evidence)) {
                            markAsUploaded(evidenceFile)
                            uploadedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to upload evidence file: ${evidenceFile.name}", e)
                }
            }

            Log.i(TAG, "✅ Uploaded $uploadedCount evidence items")
            Result.success(uploadedCount)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload pending evidence", e)
            Result.failure(e)
        }
    }


    /**
     * Create session summary with all evidence
     */
    suspend fun createSessionSummary(sessionId: String): Result<SafetySession> {
        return try {
            Log.d(TAG, "📋 Creating session summary for: $sessionId")

            val evidence = getEvidenceForSession(sessionId).getOrElse { emptyList() }
            val user = userRepository.getCurrentUser().firstOrNull()

            val session = SafetySession(
                id = sessionId,
                userId = user?.uid ?: "unknown",
                startTime = evidence.minOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
                endTime = evidence.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
                evidenceCount = evidence.size,
                locationCount = evidence.count { it.type == EvidenceType.LOCATION },
                photoCount = evidence.count { it.type == EvidenceType.PHOTO },
                audioCount = evidence.count { it.type == EvidenceType.AUDIO },
                transcriptionCount = evidence.count { it.type == EvidenceType.TRANSCRIPTION },
                status = SessionStatus.COMPLETED,
                evidenceIds = evidence.map { it.id }
            )

            // Save session summary
            saveSessionSummary(session)

            Log.i(TAG, "✅ Session summary created: ${session.evidenceCount} items")
            Result.success(session)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create session summary", e)
            Result.failure(e)
        }
    }

    suspend fun updateSessionSummary(session: SafetySession): Result<Unit> {
        return runCatching {
            saveSessionSummary(session)
        }
    }

    suspend fun getIncidentTimeline(sessionId: String): Result<List<IncidentTimelineEntry>> {
        return try {
            val evidence = getEvidenceForSession(sessionId).getOrElse { emptyList() }
            val entries = evidence.sortedBy { it.timestamp }
                .map { it.toTimelineEntry() }
            Result.success(entries)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to build incident timeline", e)
            Result.failure(e)
        }
    }

    /**
     * Compress old evidence to save space
     */
    suspend fun compressOldEvidence(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L): Result<Long> {
        return try {
            Log.d(TAG, "🗜️ Compressing old evidence older than ${maxAgeMs / (24 * 60 * 60 * 1000)}days")

            val evidenceDir = getEvidenceDirectory()
            val currentTime = System.currentTimeMillis()
            var totalSaved = 0L

            evidenceDir.listFiles()?.forEach { sessionDir ->
                if (sessionDir.isDirectory() &&
                    (currentTime - sessionDir.lastModified()) > maxAgeMs) {

                    val compressed = compressSessionDirectory(sessionDir)
                    if (compressed != null) {
                        val originalSize = sessionDir.walkTopDown()
                            .filter { it.isFile() }
                            .map { it.length() }
                            .sum()

                        val compressedSize = compressed.length()
                        totalSaved += (originalSize - compressedSize)

                        // Delete original files after successful compression
                        sessionDir.deleteRecursively()

                        Log.d(TAG, "🗜️ Compressed session ${sessionDir.name}: " +
                                "${originalSize / 1024}KB → ${compressedSize / 1024}KB")
                    }
                }
            }

            Log.i(TAG, "✅ Compression complete. Saved ${totalSaved / 1024 / 1024}MB")
            Result.success(totalSaved)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to compress old evidence", e)
            Result.failure(e)
        }
    }

    /**
     * Get storage usage summary
     */
    fun getStorageUsage(): StorageUsage {
        return try {
            val evidenceDir = getEvidenceDirectory()

            var totalSize = 0L
            var photoSize = 0L
            var audioSize = 0L
            var sessionCount = 0

            evidenceDir.listFiles()?.forEach { sessionDir ->
                if (sessionDir.isDirectory()) {
                    sessionCount++
                    sessionDir.walkTopDown().filter { it.isFile() }.forEach { file ->
                        val size = file.length()
                        totalSize += size

                        when {
                            file.name.contains("photo") -> photoSize += size
                            file.name.contains("audio") -> audioSize += size
                        }
                    }
                }
            }

            StorageUsage(
                totalSizeBytes = totalSize,
                photoSizeBytes = photoSize,
                audioSizeBytes = audioSize,
                sessionCount = sessionCount,
                availableSpaceBytes = context.filesDir.freeSpace
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating storage usage", e)
            StorageUsage(0, 0, 0, 0, 0)
        }
    }

    /**
     * Delete evidence for a session (for privacy)
     */
    suspend fun deleteSessionEvidence(sessionId: String): Result<Unit> {
        return try {
            Log.w(TAG, "🗑️ Deleting evidence for session: $sessionId")

            // Delete local files
            val sessionDir = File(getEvidenceDirectory(), sessionId)
            if (sessionDir.exists()) {
                sessionDir.deleteRecursively()
            }

            // Delete from Firebase
            deleteSessionFromFirebase(sessionId)

            Log.i(TAG, "✅ Session evidence deleted: $sessionId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete session evidence", e)
            Result.failure(e)
        }
    }

    // Private methods

    private suspend fun saveEvidenceLocally(evidence: SafetyEvidence): SafetyEvidence {
        val evidenceDir = File(getEvidenceDirectory(), evidence.sessionId)
        if (!evidenceDir.exists()) {
            evidenceDir.mkdirs()
        }

        val evidenceFile = File(evidenceDir, "${evidence.id}.json")
        val evidenceWithLocalPath = evidence.copy(
            localPath = evidenceFile.absolutePath,
        )

        // Save evidence metadata as JSON
        evidenceVault.writeEncrypted(evidenceFile, evidenceWithLocalPath.toJson())

        return evidenceWithLocalPath
    }

    private fun queueForUpload(evidence: SafetyEvidence) {
        pendingUploads.add(evidence.id)

        // Start background upload with auth awareness
        repositoryScope.launch {
            try {
                if (isNetworkAvailable()) {
                    // Validate auth before attempting upload
                    val authValidation = validateCurrentAuthState()
                    if (authValidation is AuthValidationResult.Valid) {
                        val uploadSuccess = uploadEvidenceToFirebase(evidence)
                        if (uploadSuccess) {
                            pendingUploads.remove(evidence.id)
                            Log.d(TAG, "✅ Background upload successful: ${evidence.id}")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Auth not valid, keeping evidence queued: ${evidence.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Background upload failed for ${evidence.id}", e)
            }
        }
    }

    private suspend fun uploadEvidenceToFirebase(evidence: SafetyEvidence): Boolean {
        return try {
            var attempts = 0
            while (attempts < MAX_UPLOAD_RETRY_ATTEMPTS) {
                try {
                    // ✅ STEP 1: Validate auth before each attempt
                    val currentAuthValidation = validateCurrentAuthState()
                    if (currentAuthValidation !is AuthValidationResult.Valid) {
                        Log.w(TAG, "⚠️ Auth state invalid during upload attempts")
                        return false
                    }

                    // ✅ STEP 2: Upload file to storage if it has a file path
                    val uploadedEvidence = if (evidence.filePath != null) {
                        val file = File(evidence.filePath)
                        if (file.exists()) {
                            // ✅ USE: Enhanced StorageRepository with auth guard
                            val uploadResult = when (evidence.type) {
                                EvidenceType.PHOTO -> storageRepository.uploadEvidenceImage(
                                    imageUri = android.net.Uri.fromFile(file),
                                    incidentId = evidence.sessionId,
                                    contentType = "image/jpeg"
                                )
                                EvidenceType.AUDIO -> storageRepository.uploadAudioEvidence(
                                    uri = android.net.Uri.fromFile(file),
                                    contentType = "audio/mpeg",
                                    incidentId = evidence.sessionId
                                )
                                else -> Result.success("") // No file upload needed
                            }

                            if (uploadResult.isSuccess) {
                                evidence.copy(
                                    firebaseStorageUrl = uploadResult.getOrNull(),
                                    uploadStatus = EvidenceUploadStatus.COMPLETED,
                                    uploadedAt = System.currentTimeMillis(),
                                )
                            } else {
                                Log.e(TAG, "❌ File upload failed: ${uploadResult.exceptionOrNull()}")
                                throw uploadResult.exceptionOrNull() ?: Exception("Unknown upload error")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Evidence file not found: ${evidence.filePath}")
                            evidence.copy( uploadStatus = EvidenceUploadStatus.COMPLETED,)
                        }
                    } else {
                        evidence.copy( uploadStatus = EvidenceUploadStatus.COMPLETED,)
                    }

                    // ✅ STEP 3: Save evidence metadata to Firestore
                    val user = userRepository.getCurrentUser().firstOrNull()
                    if (user != null) {
                        firestore.collection("users")
                            .document(user.uid)
                            .collection(EVIDENCE_COLLECTION)
                            .document(evidence.id)
                            .set(uploadedEvidence.toFirestoreMap())
                            .await()
                    }

                    Log.d(TAG, "☁️ Evidence uploaded to Firebase: ${evidence.id}")
                    return true

                } catch (e: Exception) {
                    attempts++
                    Log.w(TAG, "⚠️ Upload attempt $attempts failed for ${evidence.id}: ${e.message}")

                    if (attempts >= MAX_UPLOAD_RETRY_ATTEMPTS) {
                        throw e
                    }

                    // Wait before retry
                    kotlinx.coroutines.delay(2000L * attempts)
                }
            }

            false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload evidence to Firebase: ${evidence.id}", e)
            false
        }
    }


    private fun getLocalEvidenceForSession(sessionId: String): List<SafetyEvidence> {
        return try {
            val sessionDir = File(getEvidenceDirectory(), sessionId)
            if (!sessionDir.exists()) return emptyList()

            sessionDir.listFiles { file -> file.name.endsWith(".json") }
                ?.mapNotNull { file ->
                    try {
                        loadEvidenceFromFile(file)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load evidence from ${file.name}", e)
                        null
                    }
                } ?: emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting local evidence", e)
            emptyList()
        }
    }

    private suspend fun getRemoteEvidenceForSession(sessionId: String): List<SafetyEvidence> {
        return try {
            val user = userRepository.getCurrentUser().firstOrNull() ?: return emptyList()

            val querySnapshot = firestore.collection("users")
                .document(user.uid)
                .collection(EVIDENCE_COLLECTION)
                .whereEqualTo("sessionId", sessionId)
                .get()
                .await()

            querySnapshot.documents.mapNotNull { document ->
                try {
                    SafetyEvidence.fromFirestoreMap(document.data ?: emptyMap(), document.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse remote evidence ${document.id}", e)
                    null
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting remote evidence", e)
            emptyList()
        }
    }

    private fun getPendingUploadFiles(): List<File> {
        return try {
            val evidenceDir = getEvidenceDirectory()
            evidenceDir.walkTopDown()
                .filter { it.isFile() && it.name.endsWith(".json") }
                .mapNotNull { file ->
                    val evidence = loadEvidenceFromFile(file)
                    if (evidence?.uploadStatus == EvidenceUploadStatus.PENDING) file else null
                }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting pending upload files", e)
            emptyList()
        }
    }

    private fun loadEvidenceFromFile(file: File): SafetyEvidence? {
        return try {
            val result = evidenceVault.readEncrypted(file)
            if (result.content == null) {
                Log.w(TAG, "Evidence file ${file.name} is empty or unreadable")
                return null
            }

            if (result.tampered) {
                Log.w(TAG, "⚠️ Integrity mismatch for ${file.name}")
            }

            val evidence = SafetyEvidence.fromJson(result.content)
            if (result.tampered) {
                pendingUploads.add(evidence.id)
                evidenceVault.clearTamperFlag(file.name)
            }
            evidence
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load evidence from ${file.name}", e)
            null
        }
    }

    private fun markAsUploaded(evidenceFile: File) {
        try {
            val evidence = loadEvidenceFromFile(evidenceFile)
            if (evidence != null) {
                val uploadedEvidence = evidence.copy(
                    uploadStatus = EvidenceUploadStatus.COMPLETED,
                    uploadedAt = System.currentTimeMillis(),
                )
                evidenceVault.writeEncrypted(evidenceFile, uploadedEvidence.toJson())
                pendingUploads.remove(evidence.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking evidence as uploaded", e)
        }
    }

    private suspend fun saveSessionSummary(session: SafetySession) {
        try {
            // Save locally
            val sessionFile = File(getEvidenceDirectory(), "${session.id}_summary.json")
            evidenceVault.writeEncrypted(sessionFile, session.toJson())

            // Upload to Firebase
            val user = userRepository.getCurrentUser().firstOrNull()
            if (user != null) {
                firestore.collection("users")
                    .document(user.uid)
                    .collection(SESSIONS_COLLECTION)
                    .document(session.id)
                    .set(session.toFirestoreMap())
                    .await()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving session summary", e)
        }
    }

    private fun compressSessionDirectory(sessionDir: File): File? {
        return try {
            val zipFile = File(sessionDir.parent, "${sessionDir.name}.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                sessionDir.walkTopDown().forEach { file ->
                    if (file.isFile()) {
                        val entryName = file.relativeTo(sessionDir).path
                        zipOut.putNextEntry(ZipEntry(entryName))

                        FileInputStream(file).use { input ->
                            input.copyTo(zipOut)
                        }

                        zipOut.closeEntry()
                    }
                }
            }

            zipFile

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error compressing session directory", e)
            null
        }
    }

    private suspend fun deleteSessionFromFirebase(sessionId: String) {
        try {
            val user = userRepository.getCurrentUser().firstOrNull() ?: return

            // Delete evidence documents
            val evidenceQuery = firestore.collection("users")
                .document(user.uid)
                .collection(EVIDENCE_COLLECTION)
                .whereEqualTo("sessionId", sessionId)
                .get()
                .await()

            evidenceQuery.documents.forEach { document ->
                document.reference.delete().await()
            }

            // Delete session summary
            firestore.collection("users")
                .document(user.uid)
                .collection(SESSIONS_COLLECTION)
                .document(sessionId)
                .delete()
                .await()

            Log.d(TAG, "🗑️ Session deleted from Firebase: $sessionId")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting session from Firebase", e)
        }
    }

    private fun getEvidenceDirectory(): File {
        val dir = File(context.filesDir, "safety_evidence")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun SafetyEvidence.toTimelineEntry(): IncidentTimelineEntry {
        val label = when (type) {
            EvidenceType.LOCATION -> "Location Update"
            EvidenceType.PHOTO -> "Photo Captured"
            EvidenceType.AUDIO -> "Audio Snippet"
            EvidenceType.VOICE_TRIGGER, EvidenceType.TRANSCRIPTION -> "Voice Trigger"
            EvidenceType.DISTRESS_DETECTION -> "Distress Keywords"
            EvidenceType.EMERGENCY_ESCALATION -> "Emergency Escalation"
            else -> "System Event"
        }

        val detail = buildString {
            if (description.isNotBlank()) {
                append(description.take(160))
            } else {
                when (type) {
                    EvidenceType.LOCATION -> append("Lat ${latitude}, Lon ${longitude}")
                    EvidenceType.VOICE_TRIGGER, EvidenceType.TRANSCRIPTION -> append(keyword ?: "Voice input")
                    EvidenceType.DISTRESS_DETECTION -> append(distressKeywords?.joinToString() ?: "Distress signal")
                    else -> append(metadata.toString())
                }
            }
        }

        return IncidentTimelineEntry(
            timestamp = timestamp,
            label = label,
            detail = detail,
            evidenceType = type
        )
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }
}

/**
 * Storage usage summary
 */
data class StorageUsage(
    val totalSizeBytes: Long,
    val photoSizeBytes: Long,
    val audioSizeBytes: Long,
    val sessionCount: Int,
    val availableSpaceBytes: Long
) {
    fun getTotalSizeMB(): Double = totalSizeBytes / 1024.0 / 1024.0
    fun getPhotoSizeMB(): Double = photoSizeBytes / 1024.0 / 1024.0
    fun getAudioSizeMB(): Double = audioSizeBytes / 1024.0 / 1024.0
    fun getAvailableSpaceMB(): Double = availableSpaceBytes / 1024.0 / 1024.0

    fun isStorageLow(): Boolean = availableSpaceBytes < (100 * 1024 * 1024) // Less than 100MB
}

private sealed class AuthState {
    object Unknown : AuthState()
    object NotAuthenticated : AuthState()
    data class PartiallyAuthenticated(val userId: String) : AuthState()
    data class Authenticated(val userId: String) : AuthState()
}




