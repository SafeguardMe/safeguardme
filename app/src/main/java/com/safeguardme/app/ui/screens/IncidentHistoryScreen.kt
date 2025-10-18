// ui/screens/IncidentHistoryScreen.kt
package com.safeguardme.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.safeguardme.app.data.models.Incident
import com.safeguardme.app.data.models.IncidentType
import com.safeguardme.app.data.models.SeverityLevel
import com.safeguardme.app.ui.viewmodels.IncidentFilter
import com.safeguardme.app.ui.viewmodels.IncidentHistoryViewModel
import com.safeguardme.app.utils.DateUtils
import java.util.Date


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class) // Opt-in is present
@Composable
fun IncidentHistoryScreen(
    navController: NavController,
    viewModel: IncidentHistoryViewModel = hiltViewModel()
) {
    val filteredIncidents by viewModel.filteredIncidents.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val filterCounts by viewModel.filterCounts.collectAsState()
    val totalIncidents by viewModel.totalIncidents.collectAsState()
    val criticalIncidents by viewModel.criticalIncidents.collectAsState()
    val timelineEntries by viewModel.timelineEntries.collectAsState()
    val selectedTimelineIncident by viewModel.selectedTimelineIncident.collectAsState()
    val isTimelineLoading by viewModel.isTimelineLoading.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<Incident?>(null) }
    var expandedIncident by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTimelineIncident) {
        if (selectedTimelineIncident == null) {
            expandedIncident = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Incidents",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Refresh button
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Statistics Overview
            item {
                StatisticsCard(
                    totalIncidents = totalIncidents,
                    criticalIncidents = criticalIncidents,
                    submittedCount = filterCounts[IncidentFilter.SUBMITTED] ?: 0,
                    notSubmittedCount = filterCounts[IncidentFilter.NOT_SUBMITTED] ?: 0
                )
            }

            // Filter Chips
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Filter Incidents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(IncidentFilter.values()) { filter ->
                            val count = filterCounts[filter] ?: 0
                            FilterChip(
                                onClick = { viewModel.setFilter(filter) },
                                label = {
                                    Text("${filter.displayName} ($count)")
                                },
                                selected = selectedFilter == filter,
                                leadingIcon = if (selectedFilter == filter) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Loading State
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading incidents...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Empty State
            if (filteredIncidents.isEmpty() && !isLoading) {
                item {
                    EmptyStateCard(
                        filter = selectedFilter,
                        onReportIncident = {
                            navController.navigate("incident_report")
                        }
                    )
                }
            }

            // Incidents List
            if (filteredIncidents.isNotEmpty()) {
                item {
                    Text(
                        text = "${filteredIncidents.size} incident${if (filteredIncidents.size != 1) "s" else ""} found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                items(
                    items = filteredIncidents,
                    key = { it.incidentId }
                ) { incident ->
                    val isExpanded = expandedIncident == incident.incidentId
                    val timelineForIncident = if (isExpanded && selectedTimelineIncident == incident.incidentId) {
                        timelineEntries
                    } else emptyList()

                    IncidentCard(
                        incident = incident,
                        onClick = {
                            // TODO: Navigate to incident detail screen
                        },
                        onDelete = { showDeleteDialog = incident },
                        onToggleTimeline = {
                            if (isExpanded) {
                                expandedIncident = null
                                viewModel.clearTimeline()
                            } else {
                                expandedIncident = incident.incidentId
                                viewModel.loadTimelineForIncident(incident.incidentId)
                            }
                        },
                        isTimelineExpanded = isExpanded,
                        isTimelineLoading = isTimelineLoading && selectedTimelineIncident == incident.incidentId,
                        timelineEntries = timelineForIncident,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            // Refreshing indicator
            if (isRefreshing) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(
                                text = "Refreshing...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Error Snackbar
        error?.let { errorMessage ->
            LaunchedEffect(errorMessage) {
                kotlinx.coroutines.delay(4000)
                viewModel.clearError()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
        }

        // Delete Confirmation Dialog
        showDeleteDialog?.let { incident ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Incident?") },
                text = {
                    Column {
                        Text("Are you sure you want to permanently delete this incident report?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ This action cannot be undone and may affect legal proceedings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteIncident(incident)
                            showDeleteDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun StatisticsCard(
    totalIncidents: Int,
    criticalIncidents: Int,
    submittedCount: Int,
    notSubmittedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Incident Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Total",
                    value = totalIncidents.toString(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatisticItem(
                    label = "Critical",
                    value = criticalIncidents.toString(),
                    color = if (criticalIncidents > 0) Color(0xFFE53E3E) else MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatisticItem(
                    label = "Submitted",
                    value = submittedCount.toString(),
                    color = if (submittedCount > 0) Color(0xFF38A169) else MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatisticItem(
                    label = "Pending",
                    value = notSubmittedCount.toString(),
                    color = if (notSubmittedCount > 0) Color(0xFFD69E2E) else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun EmptyStateCard(
    filter: IncidentFilter,
    onReportIncident: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = when (filter) {
                    IncidentFilter.ALL -> Icons.Default.EventNote
                    IncidentFilter.SUBMITTED -> Icons.Default.CheckCircle
                    IncidentFilter.NOT_SUBMITTED -> Icons.Default.PendingActions
                },
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = when (filter) {
                    IncidentFilter.ALL -> "No incidents reported"
                    IncidentFilter.SUBMITTED -> "No submitted incidents"
                    IncidentFilter.NOT_SUBMITTED -> "No pending incidents"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = when (filter) {
                    IncidentFilter.ALL -> "Your incident reports will appear here when you create them"
                    IncidentFilter.SUBMITTED -> "Incidents submitted to authorities will be shown here"
                    IncidentFilter.NOT_SUBMITTED -> "Incidents not yet submitted to authorities will be shown here"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (filter == IncidentFilter.ALL) {
                Button(
                    onClick = onReportIncident
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report First Incident")
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(
    incident: Incident,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleTimeline: () -> Unit,
    isTimelineExpanded: Boolean,
    isTimelineLoading: Boolean,
    timelineEntries: List<com.safeguardme.app.data.models.IncidentTimelineEntry>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DateUtils.formatDisplayDateTime(incident.date.toDate()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = DateUtils.getRelativeTimeString(incident.date.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeverityChip(severity = incident.severityLevel)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete incident",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Location
            if (incident.location.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = incident.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Description preview
            Text(
                text = incident.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Footer with type and submission status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IncidentTypeChip(type = incident.incidentType)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (incident.submittedToSAPS) {
                        SubmissionChip(text = "SAPS", isSubmitted = true)
                    }
                    if (incident.submittedToNGO) {
                        SubmissionChip(text = "NGO", isSubmitted = true)
                    }
                    if (!incident.submittedToSAPS && !incident.submittedToNGO) {
                        SubmissionChip(text = "Not Submitted", isSubmitted = false)
                    }
                }
            }

            // Evidence indicators
            if (incident.imageUrls.isNotEmpty() || incident.audioUrl.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (incident.imageUrls.isNotEmpty()) {
                        EvidenceChip(
                            icon = Icons.Default.Image,
                            text = "${incident.imageUrls.size} photo${if (incident.imageUrls.size != 1) "s" else ""}"
                        )
                    }
                    if (incident.audioUrl.isNotEmpty()) {
                        EvidenceChip(
                            icon = Icons.Default.AudioFile,
                            text = "Audio"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onToggleTimeline) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isTimelineExpanded) "Hide timeline" else "Replay timeline")
            }

            if (isTimelineExpanded) {
                if (isTimelineLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading timeline...")
                    }
                } else if (timelineEntries.isEmpty()) {
                    Text(
                        text = "No timeline data available yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    IncidentTimeline(entries = timelineEntries)
                }
            }
        }
    }
}

@Composable
private fun IncidentTimeline(entries: List<com.safeguardme.app.data.models.IncidentTimelineEntry>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.forEach { entry ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = DateUtils.formatDisplayTime(Date(entry.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TimelineMarker()
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineMarker() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
    )
}

@Composable
private fun SeverityChip(severity: SeverityLevel) {
    val (text, color) = when (severity) {
        SeverityLevel.LOW -> "Low" to Color(0xFF4CAF50)
        SeverityLevel.MEDIUM -> "Medium" to Color(0xFFFF9800)
        SeverityLevel.HIGH -> "High" to Color(0xFFF44336)
        SeverityLevel.CRITICAL -> "Critical" to Color(0xFF9C27B0)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun IncidentTypeChip(type: IncidentType) {
    val text = when (type) {
        IncidentType.PHYSICAL_VIOLENCE -> "Physical"
        IncidentType.EMOTIONAL_ABUSE -> "Emotional"
        IncidentType.SEXUAL_VIOLENCE -> "Sexual"
        IncidentType.ECONOMIC_ABUSE -> "Economic"
        IncidentType.STALKING -> "Stalking"
        IncidentType.HARASSMENT -> "Harassment"
        IncidentType.THREATS -> "Threats"
        IncidentType.OTHER -> "Other"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SubmissionChip(text: String, isSubmitted: Boolean) {
    val color = if (isSubmitted) Color(0xFF38A169) else Color(0xFFD69E2E)

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EvidenceChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// Extension property for filter display names
private val IncidentFilter.displayName: String
    get() = when (this) {
        IncidentFilter.ALL -> "All"
        IncidentFilter.SUBMITTED -> "Submitted"
        IncidentFilter.NOT_SUBMITTED -> "Pending"
    }
