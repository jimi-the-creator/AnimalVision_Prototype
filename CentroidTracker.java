package com.animalvision.tracking;

import com.animalvision.model.Detection;
import com.animalvision.model.TrackedChicken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class CentroidTracker {
    private final double maxDistance;
    private int nextId = 1;

    public CentroidTracker(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    public List<TrackedChicken> track(Map<Integer, List<Detection>> detectionsByFrame) {
        List<TrackedChicken> tracks = new ArrayList<>();
        Map<Integer, List<Detection>> orderedFrames = new TreeMap<>(detectionsByFrame);

        for (Map.Entry<Integer, List<Detection>> frameEntry : orderedFrames.entrySet()) {
            List<Detection> detections = frameEntry.getValue();
            Set<Integer> usedTrackIndexes = new HashSet<>();

            for (Detection detection : detections) {
                Candidate candidate = findNearestTrack(detection, tracks, usedTrackIndexes);
                if (candidate != null && candidate.distance <= maxDistance) {
                    tracks.get(candidate.trackIndex).addDetection(detection);
                    usedTrackIndexes.add(candidate.trackIndex);
                } else {
                    TrackedChicken trackedChicken = new TrackedChicken(nextId++, detection);
                    tracks.add(trackedChicken);
                    usedTrackIndexes.add(tracks.size() - 1);
                }
            }
        }

        tracks.sort(Comparator.comparingInt(TrackedChicken::getId));
        return tracks;
    }

    private Candidate findNearestTrack(Detection detection, List<TrackedChicken> tracks, Set<Integer> usedTrackIndexes) {
        Candidate best = null;
        for (int i = 0; i < tracks.size(); i++) {
            if (usedTrackIndexes.contains(i)) {
                continue;
            }
            Detection last = tracks.get(i).lastDetection();
            if (last == null) {
                continue;
            }
            double distance = distance(detection.centerX(), detection.centerY(), last.centerX(), last.centerY());
            if (best == null || distance < best.distance) {
                best = new Candidate(i, distance);
            }
        }
        return best;
    }

    private double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private record Candidate(int trackIndex, double distance) {
    }
}
