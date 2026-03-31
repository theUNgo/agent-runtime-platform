package com.example.agentruntime.installation;

public record UserInstallationView(
        String itemId,
        String itemName,
        String itemType,
        String status,
        String provider,
        String metadataJson
) {
}
