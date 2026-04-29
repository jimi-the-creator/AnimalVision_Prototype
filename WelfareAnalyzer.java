package com.animalvision.analysis;

import com.animalvision.model.ChickenReport;
import com.animalvision.model.Detection;
import com.animalvision.model.TrackedChicken;
import com.animalvision.model.VideoMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WelfareAnalyzer {
    public List<ChickenReport> analyze(List<TrackedChicken> tracks, VideoMetadata metadata) {
        Map<Integer, Double> movementById = new HashMap<>();
        Map<Integer, Map<Integer, Detection>> detectionsByChickenAndFrame = new HashMap<>();
        Map<Integer, List<Detection>> detectionsByFrame = new HashMap<>();

        for (TrackedChicken track : tracks) {
            movementById.put(track.getId(), totalMovement(track));
            Map<Integer, Detection> frameMap = new HashMap<>();
            for (Detection detection : track.getHistory()) {
                frameMap.put(detection.getFrame(), detection);
                detectionsByFrame.computeIfAbsent(detection.getFrame(), ignored -> new ArrayList<>()).add(detection);
            }
            detectionsByChickenAndFrame.put(track.getId(), frameMap);
        }

        double flockAverage = movementById.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        List<ChickenReport> reports = new ArrayList<>();

        for (TrackedChicken track : tracks) {
            ChickenReport report = new ChickenReport();
            double movement = movementById.getOrDefault(track.getId(), 0.0);
            double movementVsAverage = flockAverage == 0.0 ? 0.0 : ((movement - flockAverage) / flockAverage) * 100.0;
            double edgePercent = edgeTimePercent(track, metadata);
            double isolationPercent = isolationTimePercent(track, detectionsByFrame, metadata);
            double crowdingPercent = crowdingTimePercent(track, detectionsByFrame, metadata);
            boolean repetitivePathing = repetitivePathing(track, metadata);

            report.setChickenId(track.getId());
            report.setTotalMovementPixels(movement);
            report.setMovementVsFlockAveragePercent(movementVsAverage);
            report.setActivityLevel(activityLevel(movementVsAverage));
            report.setEdgeTimePercent(edgePercent);
            report.setIsolationTimePercent(isolationPercent);
            report.setCrowdingTimePercent(crowdingPercent);
            report.setRepetitivePathing(repetitivePathing);

            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (movementVsAverage <= -50.0 && track.getFramesTracked() >= 2) {
                score += 2;
                reasons.add("Moved " + percent(Math.abs(movementVsAverage)) + " less than the flock average");
            } else if (movementVsAverage >= 75.0) {
                score += 1;
                reasons.add("Moved " + percent(movementVsAverage) + " more than the flock average");
            }
            if (isolationPercent >= 50.0) {
                score += 2;
                reasons.add("Stayed far from the flock center for " + percent(isolationPercent) + " of tracked frames");
            }
            if (crowdingPercent >= 50.0) {
                score += 2;
                reasons.add("Was close to other tracked birds for " + percent(crowdingPercent) + " of tracked frames");
            }
            if (edgePercent >= 60.0) {
                score += 1;
                reasons.add("Spent " + percent(edgePercent) + " of tracked time near frame edges or corners");
            }
            if (repetitivePathing) {
                score += 2;
                reasons.add("Showed a repeated back-and-forth movement pattern across frame zones");
            }
            if (reasons.isEmpty()) {
                reasons.add("No standout movement or spacing pattern exceeded the current review thresholds");
            }

            report.setConcernScore(score);
            report.setConcernLevel(concernLevel(score));
            report.setReasons(reasons);
            reports.add(report);
        }
        return reports;
    }

    private double totalMovement(TrackedChicken track) {
        double total = 0.0;
        Detection previous = null;
        for (Detection detection : track.getHistory()) {
            if (previous != null) {
                total += distance(detection.centerX(), detection.centerY(), previous.centerX(), previous.centerY());
            }
            previous = detection;
        }
        return total;
    }

    private double edgeTimePercent(TrackedChicken track, VideoMetadata metadata) {
        if (track.getHistory().isEmpty()) {
            return 0.0;
        }
        double xMargin = metadata.getWidth() * 0.15;
        double yMargin = metadata.getHeight() * 0.15;
        long edgeCount = track.getHistory().stream()
                .filter(d -> d.centerX() <= xMargin
                        || d.centerX() >= metadata.getWidth() - xMargin
                        || d.centerY() <= yMargin
                        || d.centerY() >= metadata.getHeight() - yMargin)
                .count();
        return percentOf(edgeCount, track.getHistory().size());
    }

    private double isolationTimePercent(TrackedChicken track, Map<Integer, List<Detection>> detectionsByFrame,
                                        VideoMetadata metadata) {
        if (track.getHistory().isEmpty()) {
            return 0.0;
        }
        double threshold = Math.hypot(metadata.getWidth(), metadata.getHeight()) * 0.28;
        int isolated = 0;
        for (Detection detection : track.getHistory()) {
            List<Detection> visible = detectionsByFrame.getOrDefault(detection.getFrame(), List.of());
            if (visible.size() < 2) {
                continue;
            }
            double avgX = visible.stream().mapToDouble(Detection::centerX).average().orElse(detection.centerX());
            double avgY = visible.stream().mapToDouble(Detection::centerY).average().orElse(detection.centerY());
            if (distance(detection.centerX(), detection.centerY(), avgX, avgY) > threshold) {
                isolated++;
            }
        }
        return percentOf(isolated, track.getHistory().size());
    }

    private double crowdingTimePercent(TrackedChicken track, Map<Integer, List<Detection>> detectionsByFrame,
                                       VideoMetadata metadata) {
        if (track.getHistory().isEmpty()) {
            return 0.0;
        }
        double threshold = Math.min(metadata.getWidth(), metadata.getHeight()) * 0.12;
        int crowded = 0;
        for (Detection detection : track.getHistory()) {
            List<Detection> visible = detectionsByFrame.getOrDefault(detection.getFrame(), List.of());
            for (Detection other : visible) {
                if (other == detection || other.getChickenId() == detection.getChickenId()) {
                    continue;
                }
                if (distance(detection.centerX(), detection.centerY(), other.centerX(), other.centerY()) < threshold) {
                    crowded++;
                    break;
                }
            }
        }
        return percentOf(crowded, track.getHistory().size());
    }

    private boolean repetitivePathing(TrackedChicken track, VideoMetadata metadata) {
        if (track.getHistory().size() < 6 || metadata.getWidth() == 0 || metadata.getHeight() == 0) {
            return false;
        }
        List<Integer> zones = new ArrayList<>();
        Integer previousZone = null;
        for (Detection detection : track.getHistory()) {
            int col = Math.min(2, (int) (detection.centerX() / Math.max(1.0, metadata.getWidth() / 3.0)));
            int row = Math.min(2, (int) (detection.centerY() / Math.max(1.0, metadata.getHeight() / 3.0)));
            int zone = row * 3 + col;
            if (!Integer.valueOf(zone).equals(previousZone)) {
                zones.add(zone);
                previousZone = zone;
            }
        }
        if (zones.size() < 6) {
            return false;
        }
        Set<String> repeatedPairs = new HashSet<>();
        for (int i = 0; i <= zones.size() - 4; i++) {
            int a = zones.get(i);
            int b = zones.get(i + 1);
            if (a != b && zones.get(i + 2) == a && zones.get(i + 3) == b) {
                repeatedPairs.add(a + ":" + b);
            }
        }
        return !repeatedPairs.isEmpty();
    }

    private String activityLevel(double movementVsAverage) {
        if (movementVsAverage <= -50.0) {
            return "LOW";
        }
        if (movementVsAverage >= 75.0) {
            return "HIGH";
        }
        return "NORMAL";
    }

    private ConcernLevel concernLevel(int score) {
        if (score >= 6) {
            return ConcernLevel.HIGH;
        }
        if (score >= 4) {
            return ConcernLevel.MODERATE;
        }
        if (score >= 2) {
            return ConcernLevel.WATCH;
        }
        return ConcernLevel.LOW;
    }

    private double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double percentOf(double numerator, double denominator) {
        if (denominator == 0.0) {
            return 0.0;
        }
        return (numerator / denominator) * 100.0;
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.0f%%", value);
    }
}
