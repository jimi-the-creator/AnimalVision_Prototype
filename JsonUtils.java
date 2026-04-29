package com.animalvision.util;

import com.animalvision.model.ChickenReport;
import com.animalvision.model.Detection;
import com.animalvision.model.TrackedChicken;
import com.animalvision.model.VideoMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static DetectionData loadDetections(Path detectionsJson) throws IOException {
        JsonNode root = MAPPER.readTree(detectionsJson.toFile());
        VideoMetadata metadata = new VideoMetadata();
        JsonNode video = root.path("video");
        metadata.setPath(video.path("path").asText());
        metadata.setFps(video.path("fps").asDouble());
        metadata.setWidth(video.path("width").asInt());
        metadata.setHeight(video.path("height").asInt());
        metadata.setFramesProcessed(video.path("frames_processed").asInt());

        Map<Integer, List<Detection>> detectionsByFrame = new TreeMap<>();
        for (JsonNode frameNode : root.path("frames")) {
            int frame = frameNode.path("frame").asInt();
            double timeSeconds = frameNode.path("time_seconds").asDouble();
            List<Detection> detections = new ArrayList<>();
            for (JsonNode detectionNode : frameNode.path("detections")) {
                detections.add(new Detection(
                        frame,
                        timeSeconds,
                        detectionNode.path("label").asText(),
                        detectionNode.path("confidence").asDouble(),
                        detectionNode.path("x").asDouble(),
                        detectionNode.path("y").asDouble(),
                        detectionNode.path("width").asDouble(),
                        detectionNode.path("height").asDouble()
                ));
            }
            detectionsByFrame.put(frame, detections);
        }
        return new DetectionData(metadata, detectionsByFrame);
    }

    public static void writeTrackingCsv(List<TrackedChicken> tracks, Path csvPath) throws IOException {
        Files.createDirectories(csvPath.getParent());
        StringBuilder builder = new StringBuilder();
        builder.append("frame,timeSeconds,chickenId,confidence,x,y,width,height,centerX,centerY\n");
        for (TrackedChicken track : tracks) {
            for (Detection detection : track.getHistory()) {
                builder.append(detection.getFrame()).append(',')
                        .append(format(detection.getTimeSeconds())).append(',')
                        .append(track.getId()).append(',')
                        .append(format(detection.getConfidence())).append(',')
                        .append(format(detection.getX())).append(',')
                        .append(format(detection.getY())).append(',')
                        .append(format(detection.getWidth())).append(',')
                        .append(format(detection.getHeight())).append(',')
                        .append(format(detection.centerX())).append(',')
                        .append(format(detection.centerY())).append('\n');
            }
        }
        Files.writeString(csvPath, builder.toString());
    }

    public static void writeTrackedDetections(VideoMetadata metadata, List<TrackedChicken> tracks, Path outputPath)
            throws IOException {
        Files.createDirectories(outputPath.getParent());
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode video = root.putObject("video");
        video.put("path", metadata.getPath());
        video.put("fps", metadata.getFps());
        video.put("width", metadata.getWidth());
        video.put("height", metadata.getHeight());
        video.put("frames_processed", metadata.getFramesProcessed());

        Map<Integer, List<Detection>> byFrame = new TreeMap<>();
        for (TrackedChicken track : tracks) {
            for (Detection detection : track.getHistory()) {
                byFrame.computeIfAbsent(detection.getFrame(), ignored -> new ArrayList<>()).add(detection);
            }
        }

        ArrayNode frames = root.putArray("frames");
        for (Map.Entry<Integer, List<Detection>> entry : byFrame.entrySet()) {
            ObjectNode frameNode = frames.addObject();
            frameNode.put("frame", entry.getKey());
            double timeSeconds = entry.getValue().isEmpty() ? 0 : entry.getValue().get(0).getTimeSeconds();
            frameNode.put("time_seconds", timeSeconds);
            ArrayNode detections = frameNode.putArray("detections");
            for (Detection detection : entry.getValue()) {
                ObjectNode detectionNode = detections.addObject();
                detectionNode.put("chickenId", detection.getChickenId());
                detectionNode.put("label", detection.getLabel());
                detectionNode.put("confidence", detection.getConfidence());
                detectionNode.put("x", detection.getX());
                detectionNode.put("y", detection.getY());
                detectionNode.put("width", detection.getWidth());
                detectionNode.put("height", detection.getHeight());
                detectionNode.put("centerX", detection.centerX());
                detectionNode.put("centerY", detection.centerY());
            }
        }
        MAPPER.writeValue(outputPath.toFile(), root);
    }

    public static String writeMetrics(VideoMetadata metadata, List<ChickenReport> reports, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode video = root.putObject("video");
        video.put("path", metadata.getPath());
        video.put("fps", metadata.getFps());
        video.put("width", metadata.getWidth());
        video.put("height", metadata.getHeight());
        video.put("frames_processed", metadata.getFramesProcessed());

        root.put("product_claim",
                "AnimalVision surfaces possible welfare-relevant movement and spacing patterns for human review. It does not diagnose welfare state.");
        ArrayNode reportNodes = root.putArray("chickens");
        for (ChickenReport report : reports) {
            ObjectNode node = reportNodes.addObject();
            node.put("chickenId", report.getChickenId());
            node.put("activityLevel", report.getActivityLevel());
            node.put("totalMovementPixels", report.getTotalMovementPixels());
            node.put("movementVsFlockAveragePercent", report.getMovementVsFlockAveragePercent());
            node.put("edgeTimePercent", report.getEdgeTimePercent());
            node.put("isolationTimePercent", report.getIsolationTimePercent());
            node.put("crowdingTimePercent", report.getCrowdingTimePercent());
            node.put("repetitivePathing", report.isRepetitivePathing());
            node.put("concernScore", report.getConcernScore());
            node.put("concernLevel", report.getConcernLevel().name());
            ArrayNode reasons = node.putArray("reasons");
            report.getReasons().forEach(reasons::add);
        }
        MAPPER.writeValue(outputPath.toFile(), root);
        return MAPPER.writeValueAsString(root);
    }

    public static String toJsonForPrompt(String metricsJson) throws IOException {
        return MAPPER.writeValueAsString(MAPPER.readTree(metricsJson));
    }

    public static Map<String, Object> toDisplayMap(ChickenReport report) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "Chicken #" + report.getChickenId());
        row.put("activity", report.getActivityLevel());
        row.put("mainConcern", report.getReasons().isEmpty() ? "No standout metric" : report.getReasons().get(0));
        row.put("level", report.getConcernLevel().name());
        return row;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    public record DetectionData(VideoMetadata metadata, Map<Integer, List<Detection>> detectionsByFrame) {
    }
}
