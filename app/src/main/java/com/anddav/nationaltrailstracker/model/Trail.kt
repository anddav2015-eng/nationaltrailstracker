package com.anddav.nationaltrailstracker.model

import kotlinx.serialization.Serializable

@Serializable
data class Trail(
    val id: String,
    val name: String,
    val route: String,
    val startLabel: String,
    val endLabel: String,
    val colour: String,
    val subtitle: String,
    val footer: String,
    val landmarks: List<Landmark>,
    val defaultStages: List<StageDef>,
    val gpxAsset: String? = null,
)

@Serializable
data class Landmark(
    val name: String,
    val milesFromStart: Double,
    val note: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class StageDef(
    val fromLandmark: String,
    val toLandmark: String,
    val note: String? = null,
)