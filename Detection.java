package com.animalvision.model;

public class Detection {
    private int frame;
    private double timeSeconds;
    private String label;
    private double confidence;
    private double x;
    private double y;
    private double width;
    private double height;
    private int chickenId;

    public Detection() {
    }

    public Detection(int frame, double timeSeconds, String label, double confidence,
                     double x, double y, double width, double height) {
        this.frame = frame;
        this.timeSeconds = timeSeconds;
        this.label = label;
        this.confidence = confidence;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double centerX() {
        return x + width / 2.0;
    }

    public double centerY() {
        return y + height / 2.0;
    }

    public int getFrame() {
        return frame;
    }

    public void setFrame(int frame) {
        this.frame = frame;
    }

    public double getTimeSeconds() {
        return timeSeconds;
    }

    public void setTimeSeconds(double timeSeconds) {
        this.timeSeconds = timeSeconds;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public int getChickenId() {
        return chickenId;
    }

    public void setChickenId(int chickenId) {
        this.chickenId = chickenId;
    }
}
