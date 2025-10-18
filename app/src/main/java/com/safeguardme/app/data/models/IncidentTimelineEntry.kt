package com.safeguardme.app.data.models

data class IncidentTimelineEntry(
    val timestamp: Long,
    val label: String,
    val detail: String,
    val evidenceType: EvidenceType
)
