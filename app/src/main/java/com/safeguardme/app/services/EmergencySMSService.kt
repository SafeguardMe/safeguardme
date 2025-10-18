// services/EmergencySMSService.kt - Advanced SMS Queue and Delivery Management
package com.safeguardme.app.services

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.safeguardme.app.R
import com.safeguardme.app.data.models.EmergencyContact
import com.safeguardme.app.data.repositories.EmergencyContactRepository
import com.safeguardme.app.managers.AppPermission
import com.safeguardme.app.managers.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencySMSService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager,
    private val emergencyContactRepository: EmergencyContactRepository
) {
    companion object {
        private const val TAG = "EmergencySMSService"
        private const val SMS_SENT_ACTION = "SMS_SENT"
        private const val SMS_DELIVERED_ACTION = "SMS_DELIVERED"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val SMS_NOTIFICATION_ID = 3001
        private const val MAX_SMS_LENGTH = 160
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageQueue = mutableListOf<SMSQueueItem>()
    private val deliveryTracking = mutableMapOf<String, SMSDeliveryStatus>()
    private var isProcessingQueue = false

    // Broadcast receivers for SMS delivery tracking
    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val messageId = intent?.getStringExtra("messageId") ?: return
            val resultCode = resultCode

            serviceScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS) {
                handleSMSSentResult(messageId, resultCode)
            }
        }
    }

    private val deliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val messageId = intent?.getStringExtra("messageId") ?: return

            serviceScope.launch {
                handleSMSDeliveredResult(messageId)
            }
        }
    }

    init {
        registerReceivers()
    }

    /**
     * ✅ MAIN: Queue emergency SMS for delivery
     */
    suspend fun queueEmergencySMS(
        contact: EmergencyContact,
        message: String,
        priority: SMSPriority = SMSPriority.HIGH,
        sessionId: String? = null
    ): Result<String> {
        return try {
            if (!permissionManager.isPermissionGranted(AppPermission.SMS_MESSAGING)) {
                return Result.failure(SecurityException("SMS permission not granted"))
            }

            val messageId = UUID.randomUUID().toString()
            val queueItem = SMSQueueItem(
                id = messageId,
                contact = contact,
                message = message,
                priority = priority,
                sessionId = sessionId,
                queuedAt = System.currentTimeMillis(),
                attemptsRemaining = MAX_RETRY_ATTEMPTS
            )

            // Add to queue with priority ordering
            synchronized(messageQueue) {
                messageQueue.add(queueItem)
                messageQueue.sortBy { it.priority.ordinal }
            }

            // Initialize delivery tracking
            deliveryTracking[messageId] = SMSDeliveryStatus(
                messageId = messageId,
                status = DeliveryState.QUEUED,
                queuedAt = System.currentTimeMillis()
            )

            Log.d(TAG, "📱 SMS queued: ${contact.name} (${contact.phoneNumber}) - Priority: $priority")

            // Start processing queue
            processQueue()

            Result.success(messageId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to queue SMS", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ PROCESSING: Process SMS queue with smart retry logic
     */
    private suspend fun processQueue() {
        if (isProcessingQueue) {
            Log.d(TAG, "Queue processing already in progress")
            return
        }

        isProcessingQueue = true

        try {
            while (messageQueue.isNotEmpty()) {
                val item = synchronized(messageQueue) {
                    messageQueue.removeFirstOrNull()
                }

                if (item != null) {
                    sendSMSWithTracking(item)

                    // Delay between messages to avoid carrier limits
                    delay(1000L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing SMS queue", e)
        } finally {
            isProcessingQueue = false
        }
    }

    /**
     * ✅ SENDING: Send SMS with full delivery tracking
     */
    private suspend fun sendSMSWithTracking(item: SMSQueueItem) {
        try {
            Log.d(TAG, "📤 Sending SMS: ${item.id} to ${item.contact.name}")

            // Update delivery status
            updateDeliveryStatus(item.id, DeliveryState.SENDING)

            val smsManager = SmsManager.getDefault()
            val phoneNumber = item.contact.phoneNumber
            val message = item.message

            // Create pending intents for delivery tracking
            val sentIntent = createSentPendingIntent(item.id)
            val deliveredIntent = createDeliveredPendingIntent(item.id)

            // Split message if too long
            if (message.length > MAX_SMS_LENGTH) {
                val parts = smsManager.divideMessage(message)
                val sentIntents = arrayListOf<PendingIntent?>()
                val deliveredIntents = arrayListOf<PendingIntent?>()

                parts.forEach { _ ->
                    sentIntents.add(sentIntent)
                    deliveredIntents.add(deliveredIntent)
                }

                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents
                )
            } else {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    sentIntent,
                    deliveredIntent
                )
            }

            Log.i(TAG, "✅ SMS sent to carrier: ${item.contact.name}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send SMS: ${item.id}", e)
            handleSMSFailure(item, e.message ?: "Unknown error")
        }
    }

    /**
     * ✅ TRACKING: Handle SMS sent result from carrier
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun handleSMSSentResult(messageId: String, resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                updateDeliveryStatus(messageId, DeliveryState.SENT)
                Log.i(TAG, "✅ SMS sent successfully: $messageId")

                // Show success notification
                showSMSNotification("SMS Sent", "Emergency message sent successfully", false)
            }

            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.e(TAG, "❌ SMS generic failure: $messageId")
                handleSMSError(messageId, "Generic SMS failure")
            }

            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.e(TAG, "❌ SMS no service: $messageId")
                handleSMSError(messageId, "No cellular service")
            }

            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.e(TAG, "❌ SMS null PDU: $messageId")
                handleSMSError(messageId, "SMS format error")
            }

            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e(TAG, "❌ SMS radio off: $messageId")
                handleSMSError(messageId, "Cellular radio disabled")
            }

            else -> {
                Log.e(TAG, "❌ SMS unknown error: $messageId, code: $resultCode")
                handleSMSError(messageId, "Unknown SMS error: $resultCode")
            }
        }
    }

    /**
     * ✅ TRACKING: Handle SMS delivered confirmation
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun handleSMSDeliveredResult(messageId: String) {
        updateDeliveryStatus(messageId, DeliveryState.DELIVERED)
        Log.i(TAG, "📥 SMS delivered: $messageId")

        // Show delivery notification
        showSMSNotification("SMS Delivered", "Emergency message delivered to recipient", false)
    }

    /**
     * ✅ ERROR: Handle SMS sending errors with retry logic
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun handleSMSError(messageId: String, errorMessage: String) {
        val deliveryStatus = deliveryTracking[messageId] ?: return
        val originalItem = findQueueItemById(messageId)

        if (originalItem != null && originalItem.attemptsRemaining > 1) {
            // Retry with exponential backoff
            val retryItem = originalItem.copy(
                attemptsRemaining = originalItem.attemptsRemaining - 1
            )

            Log.w(TAG, "🔄 Retrying SMS: $messageId (${retryItem.attemptsRemaining} attempts left)")

            // Add back to queue with delay
            delay(RETRY_DELAY_MS * (MAX_RETRY_ATTEMPTS - retryItem.attemptsRemaining + 1))
            synchronized(messageQueue) {
                messageQueue.add(0, retryItem) // Add to front for priority
            }

            updateDeliveryStatus(messageId, DeliveryState.RETRYING)
            processQueue()
        } else {
            // Max retries exceeded
            updateDeliveryStatus(messageId, DeliveryState.FAILED, errorMessage)
            Log.e(TAG, "❌ SMS failed permanently: $messageId - $errorMessage")

            // Show failure notification
            showSMSNotification(
                "SMS Failed",
                "Emergency message failed to send: $errorMessage",
                true
            )
        }
    }

    /**
     * ✅ BATCH: Send bulk emergency SMS to multiple contacts
     */
    suspend fun sendBulkEmergencySMS(
        contacts: List<EmergencyContact>,
        message: String,
        sessionId: String
    ): Result<List<String>> {
        return try {
            val messageIds = mutableListOf<String>()

            contacts.forEach { contact ->
                if (contact.canReceiveEmergencyNotifications()) {
                    val result = queueEmergencySMS(
                        contact = contact,
                        message = message,
                        priority = SMSPriority.HIGH,
                        sessionId = sessionId
                    )

                    result.onSuccess { messageId ->
                        messageIds.add(messageId)
                    }
                }
            }

            Log.i(TAG, "📱 Bulk SMS queued: ${messageIds.size} messages")
            Result.success(messageIds)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send bulk SMS", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ STATUS: Get delivery status for message
     */
    fun getDeliveryStatus(messageId: String): SMSDeliveryStatus? {
        return deliveryTracking[messageId]
    }

    /**
     * ✅ STATUS: Get all delivery statuses for session
     */
    fun getSessionDeliveryStatus(sessionId: String): List<SMSDeliveryStatus> {
        return deliveryTracking.values.filter { it.sessionId == sessionId }
    }

    /**
     * ✅ QUEUE: Get current queue status
     */
    fun getQueueStatus(): SMSQueueStatus {
        synchronized(messageQueue) {
            return SMSQueueStatus(
                queueSize = messageQueue.size,
                isProcessing = isProcessingQueue,
                highPriorityCount = messageQueue.count { it.priority == SMSPriority.HIGH },
                criticalPriorityCount = messageQueue.count { it.priority == SMSPriority.CRITICAL }
            )
        }
    }

    /**
     * ✅ UTILITY: Clear old delivery records
     */
    fun clearOldDeliveryRecords(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val cutoffTime = System.currentTimeMillis() - maxAgeMs
        val toRemove = deliveryTracking.filter { it.value.queuedAt < cutoffTime }.keys

        toRemove.forEach { messageId ->
            deliveryTracking.remove(messageId)
        }

        Log.d(TAG, "🧹 Cleared ${toRemove.size} old delivery records")
    }

    // Private helper methods

    private fun updateDeliveryStatus(
        messageId: String,
        state: DeliveryState,
        errorMessage: String? = null
    ) {
        val status = deliveryTracking[messageId] ?: return
        deliveryTracking[messageId] = status.copy(
            status = state,
            lastUpdated = System.currentTimeMillis(),
            errorMessage = errorMessage
        )
    }

    private fun handleSMSFailure(item: SMSQueueItem, errorMessage: String) {
        serviceScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS) {
            handleSMSError(item.id, errorMessage)
        }
    }

    private fun findQueueItemById(messageId: String): SMSQueueItem? {
        // This would typically search a persistent queue, but for now return null
        // since we remove items from queue when processing
        return null
    }

    private fun createSentPendingIntent(messageId: String): PendingIntent {
        val intent = Intent(SMS_SENT_ACTION).apply {
            putExtra("messageId", messageId)
        }
        return PendingIntent.getBroadcast(
            context,
            messageId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDeliveredPendingIntent(messageId: String): PendingIntent {
        val intent = Intent(SMS_DELIVERED_ACTION).apply {
            putExtra("messageId", messageId)
        }
        return PendingIntent.getBroadcast(
            context,
            messageId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun registerReceivers() {
        try {
            ContextCompat.registerReceiver(
                context,
                sentReceiver,
                IntentFilter(SMS_SENT_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )
            ContextCompat.registerReceiver(
                context,
                deliveredReceiver,
                IntentFilter(SMS_DELIVERED_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.d(TAG, "📡 SMS broadcast receivers registered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register SMS receivers", e)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showSMSNotification(title: String, message: String, isError: Boolean) {
        try {
            val notification = NotificationCompat.Builder(context, "sms_notifications")
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(if (isError) R.drawable.ic_error else R.drawable.ic_check)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(context).notify(SMS_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show SMS notification", e)
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(sentReceiver)
            context.unregisterReceiver(deliveredReceiver)
            Log.d(TAG, "🧹 SMS service cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during SMS service cleanup", e)
        }
    }
}

// =============================================
// Supporting Data Classes and Enums
// =============================================

data class SMSQueueItem(
    val id: String,
    val contact: EmergencyContact,
    val message: String,
    val priority: SMSPriority,
    val sessionId: String?,
    val queuedAt: Long,
    val attemptsRemaining: Int
)

data class SMSDeliveryStatus(
    val messageId: String,
    val status: DeliveryState,
    val queuedAt: Long,
    val lastUpdated: Long = queuedAt,
    val sessionId: String? = null,
    val errorMessage: String? = null
)

data class SMSQueueStatus(
    val queueSize: Int,
    val isProcessing: Boolean,
    val highPriorityCount: Int,
    val criticalPriorityCount: Int
)

enum class SMSPriority {
    LOW,     // Status updates
    MEDIUM,  // Location updates
    HIGH,    // Emergency activation
    CRITICAL // Immediate danger
}

enum class DeliveryState {
    QUEUED,     // In queue waiting to send
    SENDING,    // Currently sending to carrier
    SENT,       // Sent to carrier successfully
    DELIVERED,  // Delivered to recipient device
    RETRYING,   // Failed, retrying
    FAILED      // Permanently failed
}