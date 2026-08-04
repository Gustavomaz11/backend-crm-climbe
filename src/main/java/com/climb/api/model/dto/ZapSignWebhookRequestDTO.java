package com.climb.api.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZapSignWebhookRequestDTO(
        @JsonProperty("event_type") String eventType,
        String token,
        String status,
        @JsonProperty("external_id") String externalId
) {
}
