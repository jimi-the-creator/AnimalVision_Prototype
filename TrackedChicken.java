package com.animalvision.model;

import java.util.ArrayList;
import java.util.List;

public class TrackedChicken {
    private final int id;
    private final ArrayList<Detection> history = new ArrayList<>();
    private int lastSeenFrame;

    public TrackedChicken(int id, Detection firstDetection) {
        this.id = id;
        addDetection(firstDetection);
    }

    public void addDetection(Detection detection) {
        detection.setChickenId(id);
        history.add(detection);
        lastSeenFrame = detection.getFrame();
    }

    public Detection lastDetection() {
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    public List<double[]> getPathCenters() {
        List<double[]> centers = new ArrayList<>();
        for (Detection detection : history) {
            centers.add(new double[]{detection.centerX(), detection.centerY()});
        }
        return centers;
    }

    public int getId() {
        return id;
    }

    public ArrayList<Detection> getHistory() {
        return history;
    }

    public int getLastSeenFrame() {
        return lastSeenFrame;
    }

    public int getFramesTracked() {
        return history.size();
    }
}
