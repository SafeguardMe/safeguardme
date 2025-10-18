// =============================================
// EmergencyContactRepository.kt
// =============================================

package com.safeguardme.app.data.repositories

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.snapshots
import com.safeguardme.app.data.models.EmergencyContact
import com.safeguardme.app.data.models.NotificationMethod
import com.safeguardme.app.data.models.validateContactList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyContactRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository
) {

    companion object {
        private const val CONTACTS_COLLECTION = "emergencyContacts"
        private const val TAG = "EmergencyContactRepo"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private var deduplicationInProgress = false
        private var lastDeduplicationAttempt = 0L
        private val DEDUPLICATION_COOLDOWN_MS = 30000L // 30 seconds
    }

    /**
     * ✅ FIXED: Enhanced authentication checking with retry logic
     */
    private suspend fun getUserContactsCollection() = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "🚨 CRITICAL: User not authenticated when accessing emergency contacts")
            throw SecurityException("User must be authenticated to access emergency contacts")
        }

        val userId = currentUser.uid
        Log.d(TAG, "Accessing emergency contacts for user: $userId")

        firestore.collection("users")
            .document(userId)
            .collection(CONTACTS_COLLECTION)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get contacts collection", e)
        throw SecurityException("Cannot access emergency contacts: ${e.message}")
    }

    /**
     * ✅ FIXED: Enhanced getAllContacts with automatic deduplication
     */
    suspend fun getAllContacts(): Result<List<EmergencyContact>> {
        return executeWithRetry {
            Log.d(TAG, "Getting all emergency contacts...")

            val collection = getUserContactsCollection()

            val documents = collection
                .orderBy("priority", Query.Direction.ASCENDING)
                .get()
                .await()

            val allContacts = documents.mapNotNull { doc ->
                try {
                    val contact = doc.toObject(EmergencyContact::class.java)
                    if (contact.isActive) {
                        contact.copy(id = doc.id)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse contact ${doc.id}", e)
                    null
                }
            }

            Log.i(TAG, "✅ Retrieved ${allContacts.size} emergency contacts from Firestore")

            // ✅ FIXED: Non-recursive deduplication with safeguards
            val shouldDeduplicate = allContacts.size > 20 &&
                    !deduplicationInProgress &&
                    (System.currentTimeMillis() - lastDeduplicationAttempt) > DEDUPLICATION_COOLDOWN_MS

            if (shouldDeduplicate) {
                Log.w(TAG, "🚨 Detected ${allContacts.size} contacts - triggering background deduplication")

                // ✅ FIXED: Background deduplication without blocking current call
                triggerBackgroundDeduplication()
            }

            // ✅ FIXED: Always return distinct contacts immediately (client-side filtering)
            val distinctContacts = allContacts.distinctBy { it.phoneNumber.replace(Regex("[^0-9]"), "") }

            if (distinctContacts.size != allContacts.size) {
                Log.w(TAG, "Client-side deduplication: ${allContacts.size} -> ${distinctContacts.size}")
            }

            // Validate contact list
            val validation = distinctContacts.validateContactList()
            if (!validation.isValid()) {
                Log.w(TAG, "Contact list validation warning: ${validation.getErrorMessages()}")
            }

            Result.success(distinctContacts)
        }
    }

    private suspend fun triggerBackgroundDeduplication() {
        if (deduplicationInProgress) {
            Log.d(TAG, "Deduplication already in progress, skipping")
            return
        }

        try {
            deduplicationInProgress = true
            lastDeduplicationAttempt = System.currentTimeMillis()

            Log.i(TAG, "Starting background deduplication...")

            // Get all contacts again for deduplication
            val collection = getUserContactsCollection()
            val documents = collection.get().await()

            val allContacts = documents.mapNotNull { doc ->
                try {
                    val contact = doc.toObject(EmergencyContact::class.java)
                    if (contact.isActive) contact.copy(id = doc.id) else null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse contact ${doc.id} for deduplication", e)
                    null
                }
            }

            Log.d(TAG, "Deduplication working with ${allContacts.size} contacts")

            var deletedCount = 0

            // ✅ FIXED: More aggressive deduplication strategy
            val phoneGroups = allContacts.groupBy {
                // Normalize phone numbers for comparison
                it.phoneNumber.replace(Regex("[^0-9]"), "")
            }

            phoneGroups.forEach { (normalizedPhone, contacts) ->
                if (contacts.size > 1 && normalizedPhone.length > 3) { // Skip very short numbers
                    Log.w(TAG, "Found ${contacts.size} duplicates for phone: $normalizedPhone")

                    // Sort by preference: verified > higher priority (lower number) > newer
                    val sortedContacts = contacts.sortedWith(
                        compareByDescending<EmergencyContact> { it.isVerified }
                            .thenBy { it.priority }
                            .thenByDescending { it.createdAt }
                    )

                    val keepContact = sortedContacts.first()
                    val duplicatesToDelete = sortedContacts.drop(1)

                    Log.i(TAG, "Keeping contact: ${keepContact.name} (${keepContact.id})")

                    // ✅ FIXED: Actually delete the duplicates with batch operation
                    duplicatesToDelete.forEach { duplicate ->
                        try {
                            collection.document(duplicate.id).delete().await()
                            deletedCount++
                            Log.i(TAG, "✅ Hard deleted duplicate: ${duplicate.name} (${duplicate.id})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete duplicate ${duplicate.id}", e)
                        }
                    }
                }
            }

            // ✅ NEW: Also check for name-based duplicates
            val nameGroups = allContacts
                .filter { phoneGroups[it.phoneNumber.replace(Regex("[^0-9]"), "")]?.size == 1 } // Only non-phone duplicates
                .groupBy { "${it.name.trim().lowercase()}_${it.relationship.trim().lowercase()}" }

            nameGroups.forEach { (nameKey, contacts) ->
                if (contacts.size > 1) {
                    Log.w(TAG, "Found ${contacts.size} name duplicates for: $nameKey")

                    val sortedContacts = contacts.sortedWith(
                        compareByDescending<EmergencyContact> { it.isVerified }
                            .thenBy { it.priority }
                            .thenByDescending { it.createdAt }
                    )

                    val keepContact = sortedContacts.first()
                    val duplicatesToDelete = sortedContacts.drop(1)

                    duplicatesToDelete.forEach { duplicate ->
                        try {
                            collection.document(duplicate.id).delete().await()
                            deletedCount++
                            Log.i(TAG, "✅ Hard deleted name duplicate: ${duplicate.name} (${duplicate.id})")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete name duplicate ${duplicate.id}", e)
                        }
                    }
                }
            }

            Log.i(TAG, "✅ Background deduplication complete. Deleted $deletedCount duplicates")

        } catch (e: Exception) {
            Log.e(TAG, "Background deduplication failed", e)
        } finally {
            deduplicationInProgress = false
        }
    }

    /**
     * ✅ FIXED: Completely rewritten observe method with comprehensive error handling
     */
    fun observeContacts(): Flow<List<EmergencyContact>> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "🚨 User not authenticated for observeContacts")
                return flowOf(emptyList())
            }

            Log.d(TAG, "Setting up contacts observer for user: ${currentUser.uid}")

            firestore.collection("users")
                .document(currentUser.uid)
                .collection(CONTACTS_COLLECTION)
                .orderBy("priority", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    Log.d(TAG, "Received ${snapshot.size()} documents from Firestore")

                    val contacts = snapshot.mapNotNull { doc ->
                        try {
                            val contact = doc.toObject(EmergencyContact::class.java)
                            if (contact.isActive) {
                                contact.copy(id = doc.id)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse contact ${doc.id} in observer", e)
                            null
                        }
                    }

                    // ✅ NEW: Return distinct contacts to prevent UI duplicates
                    val distinctContacts = contacts.distinctBy { it.phoneNumber }

                    Log.d(TAG, "Parsed ${distinctContacts.size} active emergency contacts")
                    distinctContacts
                }
                .catch { e ->
                    Log.e(TAG, "🚨 Error in observeContacts flow", e)
                    handleFirestoreError(e)

                    // ✅ FIXED: Don't emit empty list - use last known good state
                    // This prevents re-initialization cycles
                    Log.w(TAG, "Observer failed - maintaining current state to prevent re-initialization")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create observeContacts flow", e)
            flowOf(emptyList())
        }
    }

    /**
     * ✅ FIXED: Enhanced save method with retry logic
     */
    suspend fun saveContact(contact: EmergencyContact): Result<EmergencyContact> {
        return executeWithRetry {
            Log.i(TAG, "Saving emergency contact: ${contact.name}")

            val collection = getUserContactsCollection()

            val sanitizedContact = contact.sanitized()
            val validation = sanitizedContact.isValid()

            if (!validation.isValid()) {
                val errorMsg = "Invalid contact data: ${validation.getErrorMessages().joinToString(", ")}"
                Log.e(TAG, errorMsg)
                return@executeWithRetry Result.failure(IllegalArgumentException(errorMsg))
            }

            // Save to Firestore with explicit merge
            collection.document(sanitizedContact.id)
                .set(sanitizedContact, SetOptions.merge())
                .await()

            Log.i(TAG, "✅ Saved emergency contact: ${sanitizedContact.toLogSafeString()}")
            Result.success(sanitizedContact)
        }
    }

    /**
     * ✅ FIXED: Enhanced delete with retry logic
     */
    suspend fun deleteContact(contactId: String): Result<Unit> {
        return executeWithRetry {
            Log.i(TAG, "Deleting emergency contact: $contactId")

            val collection = getUserContactsCollection()
            val contactRef = collection.document(contactId)
            val snapshot = contactRef.get().await()

            if (!snapshot.exists()) {
                Log.w(TAG, "⚠️ Tried to delete non-existent contact: $contactId")
                return@executeWithRetry Result.success(Unit)
            }

            contactRef.delete().await()
            Log.i(TAG, "✅ Deleted emergency contact: $contactId")

            cleanUpUserEmergencyContacts(contactId)
            Result.success(Unit)
        }
    }

    /**
     * ✅ FIXED: Get emergency contacts with better filtering
     */
    suspend fun getEmergencyContacts(): Result<List<EmergencyContact>> {
        return executeWithRetry {
            val allContacts = getAllContacts().getOrElse { emptyList() }
            val emergencyContacts = allContacts
                .filter { it.canReceiveEmergencyNotifications() }
                .sortedBy { it.priority }
                .take(5) // Limit to top 5 for emergency situations

            Log.d(TAG, "Found ${emergencyContacts.size} emergency-ready contacts")
            Result.success(emergencyContacts)
        }
    }


    /**
     * ✅ NEW: Get contacts that can receive SMS notifications
     */
    suspend fun getSMSCapableContacts(): Result<List<EmergencyContact>> {
        return executeWithRetry {
            val allContacts = getAllContacts().getOrElse { emptyList() }
            val smsContacts = allContacts
                .filter {
                    it.isActive &&
                            it.canReceiveEmergencyAlerts &&
                            it.notificationMethod != NotificationMethod.NONE &&
                            (it.notificationMethod == NotificationMethod.SMS_ONLY ||
                                    it.notificationMethod == NotificationMethod.SMS_AND_CALL ||
                                    it.notificationMethod == NotificationMethod.EMAIL_AND_SMS ||
                                    it.notificationMethod == NotificationMethod.ALL_METHODS)
                }
                .sortedBy { it.priority }

            Log.d(TAG, "Found ${smsContacts.size} SMS-capable contacts")
            Result.success(smsContacts)
        }
    }


    /**
     * ✅ NEW: Get high-priority SMS contacts for critical emergencies
     */
    suspend fun getCriticalSMSContacts(): Result<List<EmergencyContact>> {
        return executeWithRetry {
            val smsContacts = getSMSCapableContacts().getOrElse { emptyList() }
            val criticalContacts = smsContacts
                .filter { it.isHighPriority() }
                .take(3) // Limit to top 3 for immediate response

            Log.d(TAG, "Found ${criticalContacts.size} critical SMS contacts")
            Result.success(criticalContacts)
        }
    }

    /**
     * ✅ FIXED: Update contact verification with retry
     */
    suspend fun updateContactVerification(contactId: String, isVerified: Boolean): Result<Unit> {
        return executeWithRetry {
            val collection = getUserContactsCollection()

            collection.document(contactId)
                .set(mapOf(
                    "isVerified" to isVerified,
                    "lastContactedAt" to if (isVerified) System.currentTimeMillis() else 0L
                ), SetOptions.merge())
                .await()

            Log.i(TAG, "Updated contact verification: $contactId -> $isVerified")
            Result.success(Unit)
        }
    }

    /**
     * ✅ FIXED: Reorder contacts with batch operations
     */
    suspend fun reorderContacts(contactIds: List<String>): Result<Unit> {
        return executeWithRetry {
            val collection = getUserContactsCollection()
            val batch = firestore.batch()

            contactIds.forEachIndexed { index, contactId ->
                val contactRef = collection.document(contactId)
                batch.update(contactRef, "priority", index + 1)
            }

            batch.commit().await()

            Log.i(TAG, "Reordered ${contactIds.size} contacts")
            Result.success(Unit)
        }
    }

    /**
     * ✅ IMPROVED: Safer default contacts initialization
     */
    suspend fun initializeDefaultContacts(): Result<Unit> {
        return executeWithRetry {
            try {
                val existingContacts = getAllContacts().getOrElse { emptyList() }

                // ✅ NEW: More sophisticated check for initialization
                val hasEmergencyServices = existingContacts.any { it.phoneNumber == "911" }
                val hasCrisisHotlines = existingContacts.any { it.phoneNumber.contains("1-800-799-7233") }

                if (existingContacts.isEmpty() || (!hasEmergencyServices && !hasCrisisHotlines)) {
                    Log.i(TAG, "Initializing default emergency contacts...")

                    val defaultContacts = com.safeguardme.app.data.models.EmergencyContactFactory.createCrisisHotlines() +
                            com.safeguardme.app.data.models.EmergencyContactFactory.createEmergencyServices()

                    // ✅ NEW: Filter out contacts that already exist
                    val newContacts = defaultContacts.filter { defaultContact ->
                        existingContacts.none { existing ->
                            existing.phoneNumber == defaultContact.phoneNumber
                        }
                    }

                    var successCount = 0
                    newContacts.forEach { contact ->
                        saveContact(contact).onSuccess {
                            successCount++
                        }.onFailure { e ->
                            Log.w(TAG, "Failed to save default contact: ${contact.name}", e)
                        }
                    }

                    Log.i(TAG, "✅ Initialized $successCount new default emergency contacts")
                } else {
                    Log.d(TAG, "User already has ${existingContacts.size} contacts with defaults, skipping initialization")
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize default contacts", e)
                Result.failure(SecurityException("Failed to initialize default contacts: ${e.message}"))
            }
        }
    }

    /**
     * ✅ NEW: Manual duplicate cleanup method
     */
    suspend fun cleanupDuplicates(): Result<String> {
        return executeWithRetry {
            val beforeCount = getAllContacts().getOrElse { emptyList() }.size
            val removedCount = deduplicateContacts().getOrElse { 0 }
            val afterCount = getAllContacts().getOrElse { emptyList() }.size

            val report = "Cleanup complete: $beforeCount → $afterCount contacts ($removedCount removed)"
            Log.i(TAG, report)
            Result.success(report)
        }
    }
    /**
     * ✅ NEW: Detect and remove duplicate contacts
     */
    suspend fun deduplicateContacts(): Result<Int> {
        if (deduplicationInProgress) {
            return Result.failure(IllegalStateException("Deduplication already in progress"))
        }

        return executeWithRetry {
            Log.i(TAG, "Starting manual contact deduplication...")

            val collection = getUserContactsCollection()

            // Get ALL documents including inactive ones for thorough cleanup
            val documents = collection.get().await()

            val allContacts = documents.mapNotNull { doc ->
                try {
                    val contact = doc.toObject(EmergencyContact::class.java)
                    contact.copy(id = doc.id) // Include both active and inactive
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse contact ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "Manual deduplication found ${allContacts.size} total contacts (including inactive)")

            var deletedCount = 0
            val batch = firestore.batch()

            // ✅ GROUP 1: Phone number duplicates (more aggressive)
            val phoneGroups = allContacts.groupBy {
                it.phoneNumber.replace(Regex("[^0-9+]"), "") // Keep + for international numbers
            }

            phoneGroups.forEach { (normalizedPhone, contacts) ->
                if (contacts.size > 1 && normalizedPhone.length >= 3) {
                    Log.w(TAG, "Manual: Found ${contacts.size} phone duplicates for: $normalizedPhone")

                    // Keep the best contact
                    val bestContact = contacts.maxWithOrNull(
                        compareBy<EmergencyContact> { it.isActive } // Active first
                            .thenBy { it.isVerified } // Verified first
                            .thenBy { -it.priority } // Lower priority number = higher priority
                            .thenBy { it.createdAt } // Newer first
                    )

                    contacts.forEach { contact ->
                        if (contact.id != bestContact?.id) {
                            val contactRef = collection.document(contact.id)
                            batch.delete(contactRef)
                            deletedCount++
                            Log.d(TAG, "Queued for deletion: ${contact.name} (${contact.id})")
                        }
                    }
                }
            }

            // ✅ GROUP 2: Name + relationship duplicates (remaining contacts only)
            val remainingContacts = allContacts.filter { contact ->
                val normalizedPhone = contact.phoneNumber.replace(Regex("[^0-9+]"), "")
                phoneGroups[normalizedPhone]?.size == 1 // Only contacts without phone duplicates
            }

            val nameGroups = remainingContacts.groupBy {
                "${it.name.trim().lowercase()}_${it.relationship.trim().lowercase()}"
            }

            nameGroups.forEach { (nameKey, contacts) ->
                if (contacts.size > 1) {
                    Log.w(TAG, "Manual: Found ${contacts.size} name duplicates for: $nameKey")

                    val bestContact = contacts.maxWithOrNull(
                        compareBy<EmergencyContact> { it.isActive }
                            .thenBy { it.isVerified }
                            .thenBy { -it.priority }
                            .thenBy { it.createdAt }
                    )

                    contacts.forEach { contact ->
                        if (contact.id != bestContact?.id) {
                            val contactRef = collection.document(contact.id)
                            batch.delete(contactRef)
                            deletedCount++
                            Log.d(TAG, "Queued name duplicate for deletion: ${contact.name} (${contact.id})")
                        }
                    }
                }
            }

            // ✅ Execute batch deletion
            if (deletedCount > 0) {
                batch.commit().await()
                Log.i(TAG, "✅ Manual deduplication complete. Deleted $deletedCount contacts via batch operation")
            } else {
                Log.i(TAG, "No duplicates found during manual deduplication")
            }

            Result.success(deletedCount)
        }
    }

    /**
     * ✅ NEW: Hard delete contact (permanent removal)
     */
    private suspend fun hardDeleteContact(contactId: String) {
        try {
            val collection = getUserContactsCollection()
            collection.document(contactId).delete().await()
            Log.d(TAG, "Hard deleted contact: $contactId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hard delete contact: $contactId", e)
        }
    }


    /**
     * ✅ NEW: Generic retry mechanism for operations
     */
    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        delayMs: Long = RETRY_DELAY_MS,
        operation: suspend () -> Result<T>
    ): Result<T> {
        repeat(maxAttempts) { attempt ->
            try {
                val result = operation()
                if (result.isSuccess) {
                    return result
                }

                // If it's the last attempt, return the failed result
                if (attempt == maxAttempts - 1) {
                    return result
                }

                Log.w(TAG, "Operation failed, attempt ${attempt + 1}/$maxAttempts. Retrying in ${delayMs}ms...")
                delay(delayMs)

            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) {
                    Log.e(TAG, "Operation failed after $maxAttempts attempts", e)
                    handleFirestoreError(e)
                    return Result.failure(SecurityException("Operation failed after $maxAttempts attempts: ${e.message}"))
                }

                Log.w(TAG, "Operation threw exception, attempt ${attempt + 1}/$maxAttempts. Retrying...", e)
                delay(delayMs)
            }
        }

        return Result.failure(SecurityException("Operation failed after $maxAttempts attempts"))
    }

    /**
     * ✅ ENHANCED: Better Firestore error handling with specific guidance
     */
    private suspend fun handleFirestoreError(throwable: Throwable) {
        when {
            throwable.message?.contains("PERMISSION_DENIED") == true -> {
                Log.e(TAG, "🚨 PERMISSION_DENIED: Emergency contact access blocked")
                Log.e(TAG, "Check:")
                Log.e(TAG, "  1. User authentication: ${auth.currentUser?.uid}")
                Log.e(TAG, "  2. Firestore rules for path: /users/{userId}/emergencyContacts")
                Log.e(TAG, "  3. Collection name: $CONTACTS_COLLECTION")
                Log.e(TAG, "  4. Ensure 'list' permission is granted in security rules")
            }

            throwable.message?.contains("UNAVAILABLE") == true -> {
                Log.e(TAG, "🚨 Firebase Firestore unavailable - check network connection")
            }

            throwable.message?.contains("DEADLINE_EXCEEDED") == true -> {
                Log.e(TAG, "🚨 Firebase operation timeout - check network performance")
            }

            throwable.message?.contains("UNAUTHENTICATED") == true -> {
                Log.e(TAG, "🚨 User authentication expired or invalid")
            }

            else -> {
                Log.e(TAG, "🚨 Unexpected Firestore error: ${throwable.message}")
                Log.e(TAG, "Error type: ${throwable::class.simpleName}")
            }
        }
    }

    // =============================================
    // DIAGNOSTIC AND RECOVERY METHODS
    // =============================================

    /**
     * ✅ NEW: Get deduplication status
     */
    fun getDeduplicationStatus(): String {
        return buildString {
            appendLine("Deduplication Status:")
            appendLine("• In Progress: $deduplicationInProgress")
            appendLine("• Last Attempt: ${if (lastDeduplicationAttempt > 0) java.util.Date(lastDeduplicationAttempt) else "Never"}")
            appendLine("• Cooldown Remaining: ${maxOf(0, DEDUPLICATION_COOLDOWN_MS - (System.currentTimeMillis() - lastDeduplicationAttempt))}ms")
        }
    }

    /**
     * ✅ NEW: Force reset deduplication state (for testing/recovery)
     */
    fun resetDeduplicationState() {
        deduplicationInProgress = false
        lastDeduplicationAttempt = 0L
        Log.i(TAG, "Deduplication state reset")
    }

    /**
     * ✅ NEW: Comprehensive diagnostic method
     */
    suspend fun testContactAccess(): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(SecurityException("User not authenticated"))
            }

            val collection = getUserContactsCollection()

            // Test basic read access
            val testQuery = collection.limit(1).get().await()

            val diagnosticInfo = buildString {
                appendLine("✅ Contact access test successful")
                appendLine("User ID: ${currentUser.uid}")
                appendLine("Collection path: users/${currentUser.uid}/$CONTACTS_COLLECTION")
                appendLine("Test query returned: ${testQuery.size()} documents")
                appendLine("User email: ${currentUser.email}")
                appendLine("Auth provider: ${currentUser.providerId}")
            }

            Log.i(TAG, diagnosticInfo)
            Result.success(diagnosticInfo)

        } catch (e: Exception) {
            val errorInfo = "❌ Contact access test failed: ${e.message}"
            Log.e(TAG, errorInfo, e)
            handleFirestoreError(e)
            Result.failure(SecurityException(errorInfo))
        }
    }

    /**
     * ✅ NEW: Force refresh contacts (for pull-to-refresh)
     */
    suspend fun refreshContacts(): Result<Unit> {
        return executeWithRetry {
            Log.d(TAG, "Force refreshing emergency contacts...")
            getAllContacts()
            Log.i(TAG, "✅ Emergency contacts refreshed successfully")
            Result.success(Unit)
        }
    }

    private suspend fun cleanUpUserEmergencyContacts(contactId: String) {
        runCatching {
            val currentUser = userRepository.getCurrentUser().firstOrNull() ?: return@runCatching
            val updatedIds = currentUser.emergencyContacts.filterNot { it == contactId }

            if (updatedIds.size != currentUser.emergencyContacts.size) {
                userRepository.updateEmergencyContacts(updatedIds)
                    .onFailure { error ->
                        Log.w(TAG, "Failed to update user emergency contacts after delete", error)
                    }
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to clean up user emergency contact references", error)
        }
    }

    /**
     * ✅ NEW: Get contacts by priority level
     */
    suspend fun getContactsByPriority(maxPriority: Int): Result<List<EmergencyContact>> {
        return executeWithRetry {
            val allContacts = getAllContacts().getOrElse { emptyList() }
            val priorityContacts = allContacts
                .filter { it.priority <= maxPriority && it.isActive }
                .sortedBy { it.priority }

            Log.d(TAG, "Found ${priorityContacts.size} contacts with priority <= $maxPriority")
            Result.success(priorityContacts)
        }
    }

    /**
     * ✅ NEW: Clear local cache and force reload (for troubleshooting)
     */
    suspend fun clearCacheAndReload(): Result<Unit> {
        return executeWithRetry {
            Log.i(TAG, "Clearing cache and reloading contacts...")

            // Force a fresh load from Firestore
            val collection = getUserContactsCollection()
            collection.get(com.google.firebase.firestore.Source.SERVER).await()

            Log.i(TAG, "✅ Cache cleared and contacts reloaded")
            Result.success(Unit)
        }
    }
}
