// data/repositories/UserRepository.kt - FIXED with Safe Deserialization
package com.safeguardme.app.data.repositories

import android.content.ContentValues.TAG
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.snapshots
import com.safeguardme.app.data.models.SafetyStatus
import com.safeguardme.app.data.models.User
import com.safeguardme.app.utils.AppConstants.USERS_COLLECTION
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val usersCollection = firestore.collection("users")
    private val _currentUserFlow = MutableStateFlow<User?>(null)

    /**
     * ✅ CRITICAL FIX: Safe user deserialization that handles timestamp conversion issues
     */
    private fun safeDeserializeUser(documentData: Map<String, Any>?, documentId: String): User? {
        return try {
            if (documentData == null) {
                Log.w(TAG, "Document data is null for user: $documentId")
                return null
            }

            // ✅ NEW: Manual deserialization to handle timestamp issues
            val user = User(
                uid = documentData["uid"] as? String ?: documentId,
                email = documentData["email"] as? String ?: "",
                fullName = documentData["fullName"] as? String ?: "",
                phoneNumber = documentData["phoneNumber"] as? String ?: "",
                profilePhotoUrl = documentData["profilePhotoUrl"] as? String,
                isEmailVerified = documentData["isEmailVerified"] as? Boolean ?: false,
                emailVerified = documentData["emailVerified"] as? Boolean ?: false,
                lastLoginAt = documentData["lastLoginAt"] as? Long ?: 0L,
                isActive = documentData["isActive"] as? Boolean ?: true,
                valid = documentData["valid"] as? Boolean ?: true,

                triggerKeyword = documentData["triggerKeyword"] as? String,
                voiceAudioUrl = documentData["voiceAudioUrl"] as? String,
                transcriptionData = documentData["transcriptionData"] as? Map<String, Any>,
                triggerUpdatedAt = documentData["triggerUpdatedAt"] as? Long,
                triggerDeletedAt = documentData["triggerDeletedAt"] as? Long,
                audioFileSize = documentData["audioFileSize"] as? Long,
                profileSecurityLevel = documentData["profileSecurityLevel"] as? String,

                // ✅ CRITICAL: Safe timestamp handling
                createdAt = safeExtractTimestamp(documentData["createdAt"]),
                lastActiveAt = safeExtractTimestamp(documentData["lastActiveAt"]),

                // ✅ SAFE: Handle enum conversion
                safetyStatus = try {
                    val statusString = documentData["safetyStatus"] as? String
                    if (statusString != null) {
                        SafetyStatus.valueOf(statusString)
                    } else {
                        SafetyStatus.DISABLED
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid safety status, using default", e)
                    SafetyStatus.DISABLED
                },

                emergencyContacts = (documentData["emergencyContacts"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                isProfileComplete = documentData["isProfileComplete"] as? Boolean ?: false,
                hasCompletedOnboarding = documentData["hasCompletedOnboarding"] as? Boolean ?: false,

                // ✅ SAFE: Handle nested objects with defaults
                notificationPreferences = documentData["notificationPreferences"]?.let {
                    // Handle notification preferences safely
                    com.safeguardme.app.data.models.NotificationPreferences()
                } ?: com.safeguardme.app.data.models.NotificationPreferences(),

                emergencySettings = (documentData["emergencySettings"] as? Map<*, *>)?.let { map ->
                    com.safeguardme.app.data.models.EmergencySettings(
                        autoLocationSharing = map["autoLocationSharing"] as? Boolean ?: true,
                        emergencyCallEnabled = map["emergencyCallEnabled"] as? Boolean ?: true,
                        panicButtonEnabled = map["panicButtonEnabled"] as? Boolean ?: true,
                        voiceActivationEnabled = map["voiceActivationEnabled"] as? Boolean ?: false,
                        emergencyRecordingEnabled = map["emergencyRecordingEnabled"] as? Boolean ?: false,
                        automaticCheck = map["automaticCheck"] as? Boolean ?: true,
                        checkInFrequencyMinutes = (map["checkInFrequencyMinutes"] as? Number)?.toInt() ?: 60,
                        maxResponseTimeMinutes = (map["maxResponseTimeMinutes"] as? Number)?.toInt() ?: 5,
                        volumeGestureEnabled = map["volumeGestureEnabled"] as? Boolean ?: true,
                        shakeGestureEnabled = map["shakeGestureEnabled"] as? Boolean ?: true,
                        powerGestureEnabled = map["powerGestureEnabled"] as? Boolean ?: false
                    )
                } ?: com.safeguardme.app.data.models.EmergencySettings()
            )

            Log.d(TAG, "✅ Successfully deserialized user: ${user.email}")
            user

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to deserialize user document: $documentId", e)

            // ✅ FALLBACK: Return minimal user object to prevent total failure
            try {
                User(
                    uid = documentId,
                    email = documentData?.get("email") as? String ?: "",
                    fullName = documentData?.get("fullName") as? String ?: "",
                    createdAt = System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis(),
                    valid = true
                )
            } catch (fallbackError: Exception) {
                Log.e(TAG, "❌ Even fallback user creation failed", fallbackError)
                null
            }
        }
    }

    /**
     * ✅ NEW: Safe timestamp extraction that handles both Long and Timestamp
     */
    private fun safeExtractTimestamp(value: Any?): Any? {
        return when (value) {
            is Timestamp -> value
            is Long -> value
            is Number -> value.toLong()
            null -> System.currentTimeMillis() // Default to current time
            else -> {
                Log.w(TAG, "Unknown timestamp type: ${value::class.java}, using current time")
                System.currentTimeMillis()
            }
        }
    }

    /**
     * ✅ FIXED: Safe user profile creation with better error handling
     */
    private suspend fun createUserProfileFromAuth(firebaseUser: FirebaseUser): Result<User> = try {
        val user = User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            fullName = firebaseUser.displayName ?: "",
            phoneNumber = "", // Will be updated later
            profilePhotoUrl = firebaseUser.photoUrl?.toString(),
            isEmailVerified = firebaseUser.isEmailVerified,
            emailVerified = firebaseUser.isEmailVerified, // Sync both fields

            // Safety-specific defaults
            safetyStatus = SafetyStatus.DISABLED,
            emergencyContacts = emptyList(),
            triggerKeyword = "help me", // Default keyword

            // ✅ FIXED: Use consistent timestamp format
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis(),
            valid = true
        ).sanitized()

        // Use set() to guarantee creation
        usersCollection.document(user.uid)
            .set(user)
            .await()

        // ✅ IMPROVED: Safe document retrieval
        val createdDoc = usersCollection.document(user.uid).get().await()
        val createdUser = if (createdDoc.exists()) {
            safeDeserializeUser(createdDoc.data, createdDoc.id)
        } else {
            null
        }

        if (createdUser != null) {
            _currentUserFlow.value = createdUser
            Log.i("UserRepository", "✅ Created user profile for ${user.uid}")
            Result.success(createdUser)
        } else {
            throw Exception("Failed to retrieve created user profile")
        }
    } catch (e: Exception) {
        Log.e("UserRepository", "Failed to create user profile from auth", e)
        Result.failure(SecurityException("Failed to create user profile: ${e.message}"))
    }

    /**
     * ✅ CRITICAL FIX: Safe user profile retrieval with fallback handling
     */
    suspend fun ensureUserProfile(): Result<User> = try {
        val currentUser = auth.currentUser
            ?: return Result.failure(SecurityException("User must be authenticated"))

        val userId = currentUser.uid
        val email = currentUser.email ?: ""
        val displayName = currentUser.displayName ?: ""

        Log.d(TAG, "🔍 Ensuring user profile for: $email")

        // Check if profile exists
        val existingDoc = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .await()

        if (existingDoc.exists()) {
            // ✅ CRITICAL: Use safe deserialization
            val user = safeDeserializeUser(existingDoc.data, existingDoc.id)

            if (user != null) {
                Log.d(TAG, "✅ User profile found: ${user.email}")
                _currentUserFlow.value = user
                Result.success(user.copy(uid = userId))
            } else {
                Log.w(TAG, "⚠️ User document exists but deserialization failed, creating new profile")
                // Create new profile if deserialization failed
                createNewUserProfile(userId, email, displayName)
            }
        } else {
            Log.d(TAG, "🆕 User profile not found, creating new profile")
            // Create new profile
            createNewUserProfile(userId, email, displayName)
        }

    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to ensure user profile", e)

        // ✅ EMERGENCY FALLBACK: Try to create minimal profile
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.w(TAG, "🚨 Attempting emergency user profile creation")
                createNewUserProfile(currentUser.uid, currentUser.email ?: "", currentUser.displayName ?: "")
            } else {
                Result.failure(SecurityException("Failed to ensure user profile: ${e.message}"))
            }
        } catch (fallbackError: Exception) {
            Log.e(TAG, "❌ Emergency profile creation also failed", fallbackError)
            Result.failure(SecurityException("Failed to ensure user profile: ${e.message}"))
        }
    } as Result<User>

    /**
     * ✅ NEW: Helper to create new user profile safely
     */
    private suspend fun createNewUserProfile(userId: String, email: String, displayName: String): Result<User> = try {
        val newUser = User(
            uid = userId,
            email = email,
            fullName = displayName.ifEmpty { email.substringBefore("@") },
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis(),
            valid = true,
            isEmailVerified = auth.currentUser?.isEmailVerified ?: false,
            emailVerified = auth.currentUser?.isEmailVerified ?: false
        ).sanitized()

        // ✅ SAFE: Use merge to avoid overwriting existing data
        usersCollection.document(userId)
            .set(newUser, SetOptions.merge())
            .await()

        _currentUserFlow.value = newUser
        Log.i(TAG, "✅ Created new user profile for: $email")
        Result.success(newUser)

    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to create new user profile", e)
        Result.failure(SecurityException("Failed to create user profile: ${e.message}"))
    }

    fun observeVoiceTriggerData(): Flow<VoiceTriggerData?> {
        val currentUser = auth.currentUser
        return if (currentUser != null) {
            firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        val data = snapshot.data ?: emptyMap()
                        VoiceTriggerData(
                            keyword = data["triggerKeyword"] as? String,
                            voiceAudioUrl = data["voiceAudioUrl"] as? String,
                            transcriptionData = data["transcriptionData"] as? Map<String, Any>,
                            createdAt = data["createdAt"] as? Long,
                            updatedAt = data["triggerUpdatedAt"] as? Long,
                            audioFileSize = data["audioFileSize"] as? Long,
                            recordingDuration = data["recordingDuration"] as? Int
                        )
                    } else {
                        null
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error observing voice trigger data", e)
                    emit(null)
                }
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }

    suspend fun updateUserSettings(settings: Map<String, Any>): Result<Unit> = try {
        val currentUser = auth.currentUser
            ?: return Result.failure(SecurityException("User must be authenticated"))

        val userId = currentUser.uid

        Log.d(TAG, "Updating user settings")

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .set(settings, SetOptions.merge())
            .await()

        Log.i(TAG, "✅ User settings updated")
        Result.success(Unit)

    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to update user settings", e)
        Result.failure(SecurityException("Failed to update user settings: ${e.message}"))
    }

    suspend fun getVoiceTriggerData(): Result<VoiceTriggerData> = try {
        val currentUser = auth.currentUser
            ?: return Result.failure(SecurityException("User must be authenticated"))

        val userId = currentUser.uid

        val document = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .await()

        if (document.exists()) {
            val data = document.data ?: emptyMap()

            val voiceTriggerData = VoiceTriggerData(
                keyword = data["triggerKeyword"] as? String,
                voiceAudioUrl = data["voiceAudioUrl"] as? String,
                transcriptionData = data["transcriptionData"] as? Map<String, Any>,
                createdAt = data["createdAt"] as? Long,
                updatedAt = data["triggerUpdatedAt"] as? Long,
                audioFileSize = data["audioFileSize"] as? Long,
                recordingDuration = data["recordingDuration"] as? Int
            )

            Result.success(voiceTriggerData)
        } else {
            Result.success(VoiceTriggerData()) // Empty data
        }

    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to get voice trigger data", e)
        Result.failure(SecurityException("Failed to get voice trigger data: ${e.message}"))
    }

    suspend fun deleteVoiceTriggerData(): Result<Unit> = try {
        val currentUser = auth.currentUser
            ?: return Result.failure(SecurityException("User must be authenticated"))

        val userId = currentUser.uid

        Log.d(TAG, "Deleting voice trigger data")

        val updates = mapOf(
            "triggerKeyword" to null,
            "voiceAudioUrl" to null,
            "transcriptionData" to null,
            "triggerDeletedAt" to System.currentTimeMillis()
        )

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .set(updates, SetOptions.merge())
            .await()

        Log.i(TAG, "✅ Voice trigger data deleted")
        Result.success(Unit)

    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to delete voice trigger data", e)
        Result.failure(SecurityException("Failed to delete voice trigger data: ${e.message}"))
    }

    /**
     * ✅ FIXED: Safe user profile creation
     */
    suspend fun createUserProfile(user: User): Result<Unit> = try {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User must be authenticated" }

        val sanitizedUser = user.sanitized().copy(
            uid = currentUser.uid,
            email = currentUser.email ?: "",
            isEmailVerified = currentUser.isEmailVerified,
            emailVerified = currentUser.isEmailVerified, // Sync both fields
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis(),
            valid = true
        )

        require(sanitizedUser.isValid()) { "Invalid user data" }

        usersCollection.document(currentUser.uid)
            .set(sanitizedUser, SetOptions.merge())
            .await()

        // Update local cache
        _currentUserFlow.value = sanitizedUser
        Log.i(TAG, "✅ User profile created/updated successfully")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to create/update user profile", e)
        Result.failure(SecurityException("Failed to create user profile: ${e.message}"))
    }

    suspend fun updateUserProfile(updates: Map<String, Any>): Result<Unit> = try {
        val currentUser = auth.currentUser
            ?: return Result.failure(SecurityException("User must be authenticated"))

        val userId = currentUser.uid

        Log.d(TAG, "Updating user profile with ${updates.keys}")

        // ✅ ADD: Ensure lastActiveAt is always updated
        val safeUpdates = updates.toMutableMap().apply {
            put("lastActiveAt", System.currentTimeMillis())
        }

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .set(safeUpdates, SetOptions.merge())
            .await()

        Log.i(TAG, "✅ User profile updated successfully")
        Result.success(Unit)

    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to update user profile", e)
        Result.failure(SecurityException("Failed to update user profile: ${e.message}"))
    }

    /**
     * ✅ FIXED: Safe user profile retrieval
     */
    suspend fun getCurrentUserProfile(): Result<User?> = try {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User must be authenticated" }

        val document = usersCollection.document(currentUser.uid).get().await()
        val user = if (document.exists()) {
            safeDeserializeUser(document.data, document.id)
        } else {
            null
        }

        _currentUserFlow.value = user
        Result.success(user)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to get user profile", e)
        Result.failure(SecurityException("Failed to get user profile: ${e.message}"))
    }

    /**
     * ✅ FIXED: Safe user profile observation
     */
    fun observeCurrentUserProfile(): Flow<User?> {
        val currentUser = auth.currentUser
        return if (currentUser != null) {
            firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        safeDeserializeUser(snapshot.data, snapshot.id)?.copy(uid = currentUser.uid)
                    } else {
                        null
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Error observing user profile", e)
                    emit(null)
                }
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }

    suspend fun updateSafetyStatus(status: SafetyStatus): Result<Unit> = try {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User must be authenticated" }

        usersCollection.document(currentUser.uid)
            .set(mapOf(
                "safetyStatus" to status.name,
                "lastActiveAt" to System.currentTimeMillis()
            ), SetOptions.merge())
            .await()

        // Update local cache
        val currentUserData = _currentUserFlow.value
        if (currentUserData != null) {
            _currentUserFlow.value = currentUserData.copy(safetyStatus = status)
        }

        Log.i("UserRepository", "✅ Updated safety status to $status")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to update safety status", e)
        Result.failure(SecurityException("Failed to update safety status: ${e.message}"))
    }

    /**
     * ✅ FIXED: Safe trigger keyword update
     */
    suspend fun updateTriggerKeyword(
        keyword: String,
        voiceAudioUrl: String? = null,
        transcriptionData: Map<String, Any>? = null
    ): Result<Unit> = try {
        val currentUser = auth.currentUser
            ?: return Result.failure(SecurityException("User must be authenticated"))

        val userId = currentUser.uid

        Log.d(TAG, "Updating trigger keyword: $keyword")

        val updates = mutableMapOf<String, Any>(
            "triggerKeyword" to keyword,
            "triggerUpdatedAt" to System.currentTimeMillis(),
            "lastActiveAt" to System.currentTimeMillis()
        )

        // Add voice audio URL if provided
        voiceAudioUrl?.let {
            updates["voiceAudioUrl"] = it
        }

        // Add transcription data if provided
        transcriptionData?.let {
            updates["transcriptionData"] = it
        }

        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .set(updates, SetOptions.merge())
            .await()

        Log.i(TAG, "✅ Trigger keyword updated: $keyword")
        Result.success(Unit)

    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to update trigger keyword", e)
        Result.failure(SecurityException("Failed to update trigger keyword: ${e.message}"))
    }

    /**
     * ✅ FIXED: Safe field update helper
     */
    suspend fun updateUserField(field: String, value: Any): Result<Unit> = try {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User must be authenticated" }

        usersCollection.document(currentUser.uid)
            .set(mapOf(
                field to value,
                "lastActiveAt" to System.currentTimeMillis()
            ), SetOptions.merge())
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to update user field", e)
        Result.failure(SecurityException("Failed to update user field: ${e.message}"))
    }

    /**
     * Get current user as a Flow for reactive UI updates
     */
    fun getCurrentUser(): Flow<User?> = _currentUserFlow.asStateFlow()

    /**
     * ✅ FIXED: Safe user refresh
     */
    suspend fun refreshCurrentUser(): Result<Unit> = try {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User must be authenticated" }

        val document = usersCollection.document(currentUser.uid).get().await()
        val user = if (document.exists()) {
            safeDeserializeUser(document.data, document.id)
        } else {
            null
        }

        _currentUserFlow.value = user
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to refresh current user", e)
        Result.failure(SecurityException("Failed to refresh user: ${e.message}"))
    }

    /**
     * Update user safety status (alias for consistency)
     */
    suspend fun updateUserSafetyStatus(status: SafetyStatus): Result<Unit> {
        return updateSafetyStatus(status)
    }

    /**
     * ✅ FIXED: Safe cache initialization
     */
    suspend fun initializeUserCache(): Result<Unit> = try {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val document = usersCollection.document(currentUser.uid).get().await()
            val user = if (document.exists()) {
                safeDeserializeUser(document.data, document.id)
            } else {
                null
            }
            _currentUserFlow.value = user
        } else {
            _currentUserFlow.value = null
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to initialize user cache", e)
        Result.failure(SecurityException("Failed to initialize cache: ${e.message}"))
    }

    /**
     * Clear user cache (useful for sign-out)
     */
    fun clearUserCache() {
        _currentUserFlow.value = null
    }

    /**
     * Emergency delete user data (for emergency situations)
     */
    suspend fun emergencyDeleteUserData(): Result<Unit> = try {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User must be authenticated" }

        val batch = firestore.batch()

        // Delete user document
        batch.delete(usersCollection.document(currentUser.uid))

        // TODO: Delete subcollections (contacts, incidents) in production
        // This requires recursive delete function

        batch.commit().await()

        _currentUserFlow.value = null
        Log.w("UserRepository", "⚠️ Emergency deleted user data for ${currentUser.uid}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to delete user data", e)
        Result.failure(SecurityException("Failed to delete user data: ${e.message}"))
    }

    /**
     * ✅ FIXED: Safe emergency contacts update
     */
    suspend fun updateEmergencyContacts(contactIds: List<String>): Result<Unit> = try {
        val currentUser = auth.currentUser
        require(currentUser != null) { "User must be authenticated" }

        usersCollection.document(currentUser.uid)
            .set(mapOf(
                "emergencyContacts" to contactIds,
                "lastActiveAt" to System.currentTimeMillis()
            ), SetOptions.merge())
            .await()

        // Update local cache
        val currentUserData = _currentUserFlow.value
        if (currentUserData != null) {
            _currentUserFlow.value = currentUserData.copy(emergencyContacts = contactIds)
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("UserRepository", "❌ Failed to update emergency contacts", e)
        Result.failure(SecurityException("Failed to update emergency contacts: ${e.message}"))
    }

    suspend fun updateGestureSettings(
        volumeEnabled: Boolean,
        shakeEnabled: Boolean,
        powerEnabled: Boolean
    ): Result<Unit> = try {
        val currentUser = auth.currentUser ?: return Result.failure(SecurityException("User must be authenticated"))

        usersCollection.document(currentUser.uid)
            .set(
                mapOf(
                    "emergencySettings.volumeGestureEnabled" to volumeEnabled,
                    "emergencySettings.shakeGestureEnabled" to shakeEnabled,
                    "emergencySettings.powerGestureEnabled" to powerEnabled,
                    "lastActiveAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .await()

        _currentUserFlow.value = _currentUserFlow.value?.let { user ->
            user.copy(
                emergencySettings = user.emergencySettings.copy(
                    volumeGestureEnabled = volumeEnabled,
                    shakeGestureEnabled = shakeEnabled,
                    powerGestureEnabled = powerEnabled
                )
            )
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to update gesture settings", e)
        Result.failure(SecurityException("Failed to update gesture settings: ${e.message}"))
    }
}

data class VoiceTriggerData(
    val keyword: String? = null,
    val voiceAudioUrl: String? = null,
    val transcriptionData: Map<String, Any>? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val audioFileSize: Long? = null,
    val recordingDuration: Int? = null
) {
    fun hasKeyword(): Boolean = !keyword.isNullOrBlank()

    fun hasAudioSample(): Boolean = !voiceAudioUrl.isNullOrBlank()

    fun hasTranscription(): Boolean = transcriptionData != null

    fun getTranscribedText(): String? {
        return transcriptionData?.get("primaryText") as? String
    }

    fun getTranscriptionConfidence(): Float? {
        return transcriptionData?.get("confidence") as? Float
    }

    fun isKeywordVerified(): Boolean {
        val matchData = transcriptionData?.get("matchResult") as? Map<String, Any>
        return matchData?.get("isMatch") as? Boolean == true
    }

    fun getMatchConfidence(): Float? {
        val matchData = transcriptionData?.get("matchResult") as? Map<String, Any>
        return matchData?.get("confidence") as? Float
    }

    fun getAlternativeTranscriptions(): List<String>? {
        @Suppress("UNCHECKED_CAST")
        return transcriptionData?.get("alternativeTexts") as? List<String>
    }

    fun isComplete(): Boolean {
        return hasKeyword() && hasAudioSample()
    }

    fun getSummary(): String {
        return when {
            !hasKeyword() -> "No keyword set"
            !hasAudioSample() -> "Keyword set, no audio sample"
            !hasTranscription() -> "Audio recorded, not transcribed"
            isKeywordVerified() -> "Verified voice trigger: '$keyword'"
            else -> "Unverified voice trigger: '$keyword'"
        }
    }
}
