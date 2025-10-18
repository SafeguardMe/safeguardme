// VoiceDetectionComponents.kt - Advanced Voice Configuration UI Components
package com.safeguardme.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * ✅ VOICE KEYWORD SECTION - Keyword Management & Testing Interface
 *
 * Features:
 * - Current keyword display with verification status
 * - Quick edit/configure options
 * - Real-time voice detection testing
 * - Keyword quality assessment
 * - User guidance for optimal keywords
 */
@Composable
fun VoiceKeywordSection(
    currentKeyword: String?,
    onConfigureKeyword: () -> Unit,
    onTestVoiceDetection: () -> Unit,
    modifier: Modifier = Modifier,
    isDetectionActive: Boolean = false,
    keywordVerified: Boolean = false,
    lastTestResult: String? = null,
    isTestingInProgress: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                currentKeyword == null -> Color.Yellow.copy(alpha = 0.1f)
                keywordVerified -> Color.Green.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "🗣️ Voice Keyword Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Status indicator
                KeywordStatusIndicator(
                    hasKeyword = currentKeyword != null,
                    isVerified = keywordVerified,
                    isActive = isDetectionActive
                )
            }

            // Current keyword display
            CurrentKeywordDisplay(
                keyword = currentKeyword,
                isVerified = keywordVerified,
                isActive = isDetectionActive
            )

            // Keyword actions
            KeywordActionRow(
                hasKeyword = currentKeyword != null,
                onConfigureKeyword = onConfigureKeyword,
                onTestVoiceDetection = onTestVoiceDetection,
                isTestingInProgress = isTestingInProgress
            )

            // Test results
            if (lastTestResult != null) {
                TestResultDisplay(
                    result = lastTestResult,
                    isSuccess = lastTestResult.contains("success", ignoreCase = true)
                )
            }

            // Keyword quality guidance
            if (currentKeyword != null) {
                KeywordQualityGuidance(keyword = currentKeyword)
            } else {
                KeywordSetupGuidance()
            }
        }
    }
}

/**
 * ✅ VOICE SENSITIVITY SECTION - Precision Control Interface
 *
 * Features:
 * - Interactive sensitivity slider with real-time feedback
 * - Sensitivity level descriptions and recommendations
 * - Visual feedback for current setting
 * - Automatic optimization suggestions
 * - Performance impact indicators
 */
@Composable
fun VoiceSensitivitySection(
    sensitivity: Float,
    onUpdateSensitivity: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isDetectionActive: Boolean = false,
    recentFalsePositives: Int = 0,
    recentMissedDetections: Int = 0
) {
    var localSensitivity by remember(sensitivity) { mutableStateOf(sensitivity) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with current level
            SensitivityHeader(
                sensitivity = sensitivity,
                isActive = isDetectionActive
            )

            // Main sensitivity slider
            SensitivitySlider(
                value = localSensitivity,
                onValueChange = {
                    localSensitivity = it
                },
                onValueChangeFinished = {
                    onUpdateSensitivity(localSensitivity)
                }
            )

            // Sensitivity level description
            SensitivityDescription(sensitivity = localSensitivity)

            // Performance feedback
            if (isDetectionActive && (recentFalsePositives > 0 || recentMissedDetections > 0)) {
                PerformanceFeedback(
                    falsePositives = recentFalsePositives,
                    missedDetections = recentMissedDetections,
                    currentSensitivity = sensitivity,
                    onOptimize = { optimizedValue ->
                        localSensitivity = optimizedValue
                        onUpdateSensitivity(optimizedValue)
                    }
                )
            }

            // Advanced settings hint
            SensitivityAdvancedHint()
        }
    }
}

// ================================================
// Supporting Composables - Keyword Section
// ================================================

@Composable
private fun KeywordStatusIndicator(
    hasKeyword: Boolean,
    isVerified: Boolean,
    isActive: Boolean
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            !hasKeyword -> Color.Yellow
            !isVerified -> Color.Blue
            isActive -> Color.Green
            else -> Color.Blue
        },
        animationSpec = tween(300), label = "status_color"
    )

    val statusIcon = when {
        !hasKeyword -> Icons.Default.Warning
        !isVerified -> Icons.Default.Error
        isActive -> Icons.Default.CheckCircle
        else -> Icons.Default.Mic
    }

    Surface(
        color = statusColor,
        shape = CircleShape,
        modifier = Modifier.size(8.dp)
    ) {}
}

@Composable
private fun CurrentKeywordDisplay(
    keyword: String?,
    isVerified: Boolean,
    isActive: Boolean
) {
    Surface(
        color = when {
            keyword == null -> Color.Yellow.copy(alpha = 0.2f)
            isActive -> Color.Green.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Keyword",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )

                KeywordVerificationBadge(
                    isVerified = isVerified,
                    hasKeyword = keyword != null
                )
            }

            if (keyword != null) {
                Text(
                    text = "\"$keyword\"",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = when {
                        isActive -> "🟢 Active - Listening for this keyword"
                        isVerified -> "✅ Verified - Ready to activate"
                        else -> "⚠️ Not verified - Test recommended"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No keyword set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Set up your voice trigger to enable hands-free emergency activation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeywordVerificationBadge(
    isVerified: Boolean,
    hasKeyword: Boolean
) {
    if (!hasKeyword) return

    Surface(
        color = if (isVerified) Color.Green else Color.Yellow,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White
            )

            Text(
                text = if (isVerified) "VERIFIED" else "UNVERIFIED",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KeywordActionRow(
    hasKeyword: Boolean,
    onConfigureKeyword: () -> Unit,
    onTestVoiceDetection: () -> Unit,
    isTestingInProgress: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onConfigureKeyword,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasKeyword) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (hasKeyword) Icons.Default.Edit else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (hasKeyword) "Change Keyword" else "Set Up Keyword")
        }

        if (hasKeyword) {
            OutlinedButton(
                onClick = onTestVoiceDetection,
                modifier = Modifier.weight(1f),
                enabled = !isTestingInProgress
            ) {
                if (isTestingInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isTestingInProgress) "Testing..." else "Test Detection")
            }
        }
    }
}

@Composable
private fun TestResultDisplay(
    result: String,
    isSuccess: Boolean
) {
    Surface(
        color = if (isSuccess) Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSuccess) Color.Green else Color.Red
            )

            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KeywordQualityGuidance(keyword: String) {
    val quality = assessKeywordQuality(keyword)

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "💡 Keyword Quality: ${quality.level}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = quality.color
            )

            quality.suggestions.forEach { suggestion ->
                Text(
                    text = "• $suggestion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeywordSetupGuidance() {
    Surface(
        color = Color.Blue.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "🎯 Choose an Effective Keyword",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Blue
            )

            listOf(
                "Use 2-4 syllables for best recognition",
                "Choose uncommon words to avoid false triggers",
                "Avoid names of people or common commands",
                "Test in noisy environments after setup"
            ).forEach { tip ->
                Text(
                    text = "• $tip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ================================================
// Supporting Composables - Sensitivity Section
// ================================================

@Composable
private fun SensitivityHeader(
    sensitivity: Float,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "🎚️ Detection Sensitivity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            color = if (isActive) Color.Green.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "${(sensitivity * 100).roundToInt()}%",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SensitivitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = "Less sensitive",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Less Sensitive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "More Sensitive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "More sensitive",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0.1f..1.0f,
            steps = 17, // 0.1 to 1.0 in 0.05 increments
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
private fun SensitivityDescription(sensitivity: Float) {
    val description = getSensitivityDescription(sensitivity)

    Surface(
        color = description.backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = description.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = description.titleColor
            )

            Text(
                text = description.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (description.recommendation != null) {
                Text(
                    text = "💡 ${description.recommendation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = description.titleColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PerformanceFeedback(
    falsePositives: Int,
    missedDetections: Int,
    currentSensitivity: Float,
    onOptimize: (Float) -> Unit
) {
    Surface(
        color = Color.Yellow.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📊 Performance Feedback",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Yellow
            )

            if (falsePositives > 0) {
                Text(
                    text = "⚠️ $falsePositives false alerts in last 24h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (missedDetections > 0) {
                Text(
                    text = "❌ $missedDetections missed detections reported",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val optimizedSensitivity = calculateOptimalSensitivity(
                currentSensitivity,
                falsePositives,
                missedDetections
            )

            if (optimizedSensitivity != currentSensitivity) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suggested: ${(optimizedSensitivity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    TextButton(
                        onClick = { onOptimize(optimizedSensitivity) }
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun SensitivityAdvancedHint() {
    Text(
        text = "💡 Tip: Start with 80% sensitivity and adjust based on your environment. Noisy areas may need lower sensitivity.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

// ================================================
// Helper Functions and Data Classes
// ================================================

private data class KeywordQuality(
    val level: String,
    val color: Color,
    val suggestions: List<String>
)

private fun assessKeywordQuality(keyword: String): KeywordQuality {
    val length = keyword.length
    val syllables = estimateSyllables(keyword)
    val commonWords = listOf("help", "emergency", "police", "fire", "medical", "urgent")
    val isCommon = commonWords.any { keyword.lowercase().contains(it) }

    return when {
        length < 3 -> KeywordQuality(
            level = "Poor",
            color = Color.Red,
            suggestions = listOf("Use longer words (3+ characters)", "Add more syllables for better recognition")
        )

        isCommon -> KeywordQuality(
            level = "Fair",
            color = Color.Yellow,
            suggestions = listOf("Consider more unique words", "Avoid common emergency terms", "Test for false positives")
        )

        syllables >= 2 && length >= 4 -> KeywordQuality(
            level = "Excellent",
            color = Color.Green,
            suggestions = listOf("Great choice for voice recognition", "Test in different environments")
        )

        else -> KeywordQuality(
            level = "Good",
            color = Color.Blue,
            suggestions = listOf("Consider adding syllables", "Test recognition accuracy")
        )
    }
}

private fun estimateSyllables(word: String): Int {
    // Simple syllable estimation based on vowel groups
    val vowels = "aeiouAEIOU"
    var count = 0
    var previousWasVowel = false

    for (char in word) {
        val isVowel = char in vowels
        if (isVowel && !previousWasVowel) {
            count++
        }
        previousWasVowel = isVowel
    }

    // Handle silent 'e'
    if (word.endsWith("e", ignoreCase = true) && count > 1) {
        count--
    }

    return maxOf(1, count) // Minimum 1 syllable
}

private data class SensitivityDescription(
    val title: String,
    val description: String,
    val recommendation: String?,
    val titleColor: Color,
    val backgroundColor: Color
)

private fun getSensitivityDescription(sensitivity: Float): SensitivityDescription {
    return when {
        sensitivity <= 0.3f -> SensitivityDescription(
            title = "Very Low Sensitivity",
            description = "Only responds to very clear, loud speech. Minimal false positives but may miss quiet emergency calls.",
            recommendation = "Increase if you're missing detections",
            titleColor = Color.Red,
            backgroundColor = Color.Red.copy(alpha = 0.1f)
        )

        sensitivity <= 0.5f -> SensitivityDescription(
            title = "Low Sensitivity",
            description = "Good for noisy environments. Reduces false alarms but requires clear speech.",
            recommendation = null,
            titleColor = Color.Yellow,
            backgroundColor = Color.Yellow.copy(alpha = 0.1f)
        )

        sensitivity <= 0.7f -> SensitivityDescription(
            title = "Medium Sensitivity",
            description = "Balanced detection suitable for most environments. Good mix of accuracy and reliability.",
            recommendation = "Recommended for most users",
            titleColor = Color.Blue,
            backgroundColor = Color.Blue.copy(alpha = 0.1f)
        )

        sensitivity <= 0.85f -> SensitivityDescription(
            title = "High Sensitivity",
            description = "Detects keywords even in background conversation. May trigger on similar-sounding words.",
            recommendation = "Monitor for false positives",
            titleColor = Color.Green,
            backgroundColor = Color.Green.copy(alpha = 0.1f)
        )

        else -> SensitivityDescription(
            title = "Maximum Sensitivity",
            description = "Extremely responsive but prone to false triggers. Best for quiet environments only.",
            recommendation = "Reduce if getting false alarms",
            titleColor = Color.Red,
            backgroundColor = Color.Red.copy(alpha = 0.1f)
        )
    }
}

private fun calculateOptimalSensitivity(
    current: Float,
    falsePositives: Int,
    missedDetections: Int
): Float {
    return when {
        falsePositives > 3 -> (current - 0.1f).coerceAtLeast(0.1f)
        missedDetections > 1 -> (current + 0.1f).coerceAtMost(1.0f)
        else -> current
    }
}