package com.example.running.domain.garmin.service

import com.example.running.domain.garmin.service.dto.GarminWebhook
import com.example.running.domain.garmin.service.dto.GarminSample
import com.example.running.exception.GarminRequestException
import com.example.running.exception.RunningValidationException
import com.example.running.exception.RunningValidationReason
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper

internal object GarminWebhookPayload {
    private val activityTypes = setOf("activities", "activityDetails", "manuallyUpdatedActivities")
    private val supported = activityTypes + setOf("deregistrations", "userPermissionsChange")
    private val gpsFields = setOf(
        GarminSample::latitudeInDegree.name,
        GarminSample::longitudeInDegree.name,
        GarminSample::speedMetersPerSecond.name,
        GarminSample::elevationInMeters.name,
    )

    fun parse(mapper: ObjectMapper, payload: String): GarminWebhook {
        val root = parseEnvelope(mapper, payload)
        val entries = root.fields().asSequence().toList()
        entries.forEach { entry ->
            entry.value.forEach { event ->
                require(event.isObject) { "Invalid Garmin event" }
                val userId = event.path("userId")
                require(userId.isTextual && userId.asText().isNotBlank() && userId.asText().length <= 255) {
                    "Invalid Garmin event"
                }
                val samples = event.path("samples")
                require(samples.isMissingNode || (samples.isArray && samples.size() <= GarminInputLimits.MAX_SAMPLES)) {
                    "Invalid Garmin samples"
                }
            }
        }
        val config = mapper.deserializationConfig
            .without(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        return try {
            mapper.readerFor(GarminWebhook::class.java).with(config).readValue(root)
        } catch (error: JsonProcessingException) {
            if (error is JsonMappingException && error.path.any { it.fieldName in gpsFields }) {
                throw RunningValidationException(RunningValidationReason.RUNNING_INVALID_GPS)
            }
            throw GarminRequestException(400)
        }
    }

    fun split(mapper: ObjectMapper, payload: String): List<String> =
        parseEnvelope(mapper, payload)
            .fields()
            .asSequence()
            .flatMap { entry ->
                entry.value.map { event ->
                    val envelope = mapper.createObjectNode()
                    envelope.putArray(entry.key).add(sorted(mapper, event))
                    mapper.writeValueAsString(envelope)
                }
            }
            .toList()

    private fun parseEnvelope(mapper: ObjectMapper, payload: String): JsonNode {
        val root = mapper.readTree(payload)
        require(root != null && root.isObject) { "Invalid Garmin payload" }
        val entries = root.fields().asSequence().toList()
        require(
            entries.isNotEmpty() && entries.all { it.key in supported && it.value.isArray } &&
                entries.sumOf { it.value.size() } <= 100
        ) { "Invalid Garmin events" }
        return root
    }

    private fun sorted(mapper: ObjectMapper, node: JsonNode): JsonNode = when {
        node.isObject -> mapper.createObjectNode().also { result ->
            node.fields().asSequence().toList().sortedBy { it.key }.forEach { entry ->
                result.set<JsonNode>(entry.key, sorted(mapper, entry.value))
            }
        }
        node.isArray -> mapper.createArrayNode().also { result ->
            node.forEach { result.add(sorted(mapper, it)) }
        }
        else -> node
    }
}
