package com.animalvision.report;

import com.animalvision.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OpenAIReportAgent {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public String polishReport(String metricsJson, String fallbackReport) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackReport + "\n\nOpenAI polish skipped: OPENAI_API_KEY is not set.\n";
        }

        try {
            String prompt = """
                    You are AnimalVision, an animal welfare observation assistant.

                    Generate a cautious per-object welfare review report from the provided metrics.

                    Rules:
                    - Do not diagnose pain, anxiety, disease, distress, or suffering.
                    - Use only the provided metrics.
                    - Use cautious language: possible, may indicate, warrants review.
                    - Report each concerning chicken object separately.
                    - Include recommended human review steps.
                    - Include limitations.
                    - Keep the report clear and serious.

                    Input:
                    %s
                    """.formatted(JsonUtils.toJsonForPrompt(metricsJson));

            ObjectNode requestJson = MAPPER.createObjectNode();
            requestJson.put("model", "gpt-5.4-mini");
            ArrayNode input = requestJson.putArray("input");
            ObjectNode message = input.addObject();
            message.put("role", "user");
            ArrayNode content = message.putArray("content");
            ObjectNode text = content.addObject();
            text.put("type", "input_text");
            text.put("text", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(requestJson)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallbackReport + "\n\nOpenAI polish skipped: API returned HTTP "
                        + response.statusCode() + ".\n" + response.body() + "\n";
            }
            String textOutput = extractText(response.body());
            if (textOutput.isBlank()) {
                return fallbackReport + "\n\nOpenAI polish skipped: API response did not include report text.\n";
            }
            return textOutput;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return fallbackReport + "\n\nOpenAI polish skipped: " + e.getMessage() + "\n";
        }
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText();
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            for (JsonNode content : item.path("content")) {
                if (content.path("text").isTextual()) {
                    builder.append(content.path("text").asText()).append("\n");
                }
            }
        }
        return builder.toString().trim();
    }
}
