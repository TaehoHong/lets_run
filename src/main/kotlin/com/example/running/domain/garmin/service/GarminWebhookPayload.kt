package com.example.running.domain.garmin.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

internal object GarminWebhookPayload {
    val activityTypes = setOf("activities", "activityDetails", "manuallyUpdatedActivities")
    private val supported = activityTypes + setOf("deregistrations", "userPermissionsChange")

    fun parse(mapper: ObjectMapper, payload: String): JsonNode {
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
        return root
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
