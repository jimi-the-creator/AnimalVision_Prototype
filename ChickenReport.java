package com.animalvision.model;

import com.animalvision.analysis.ConcernLevel;

import java.util.ArrayList;
import java.util.List;

public class ChickenReport {
    private int chickenId;
    private String activityLevel;
    private double totalMovementPixels;
    private double movementVsFlockAveragePercent;
    private double edgeTimePercent;
    private double isolationTimePercent;
    private double crowdingTimePercent;
    private boolean repetitivePathing;
    private int concernScore;
    private ConcernLevel concernLevel;
    private List<String> reasons = new ArrayList<>();

    public int getChickenId() {
        return chickenId;
    }

    public void setChickenId(int chickenId) {
        this.chickenId = chickenId;
    }

    public String getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(String activityLevel) {
        this.activityLevel = activityLevel;
    }

    public double getTotalMovementPixels() {
        return totalMovementPixels;
    }

    public void setTotalMovementPixels(double totalMovementPixels) {
        this.totalMovementPixels = totalMovementPixels;
    }

    public double getMovementVsFlockAveragePercent() {
        return movementVsFlockAveragePercent;
    }

    public void setMovementVsFlockAveragePercent(double movementVsFlockAveragePercent) {
        this.movementVsFlockAveragePercent = movementVsFlockAveragePercent;
    }

    public double getEdgeTimePercent() {
        return edgeTimePercent;
    }

    public void setEdgeTimePercent(double edgeTimePercent) {
        this.edgeTimePercent = edgeTimePercent;
    }

    public double getIsolationTimePercent() {
        return isolationTimePercent;
    }

    public void setIsolationTimePercent(double isolationTimePercent) {
        this.isolationTimePercent = isolationTimePercent;
    }

    public double getCrowdingTimePercent() {
        return crowdingTimePercent;
    }

    public void setCrowdingTimePercent(double crowdingTimePercent) {
        this.crowdingTimePercent = crowdingTimePercent;
    }

    public boolean isRepetitivePathing() {
        return repetitivePathing;
    }

    public void setRepetitivePathing(boolean repetitivePathing) {
        this.repetitivePathing = repetitivePathing;
    }

    public int getConcernScore() {
        return concernScore;
    }

    public void setConcernScore(int concernScore) {
        this.concernScore = concernScore;
    }

    public ConcernLevel getConcernLevel() {
        return concernLevel;
    }

    public void setConcernLevel(ConcernLevel concernLevel) {
        this.concernLevel = concernLevel;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
