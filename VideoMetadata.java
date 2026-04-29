package com.animalvision.model;

public class VideoMetadata {
    private String path;
    private double fps;
    private int width;
    private int height;
    private int framesProcessed;

    public VideoMetadata() {
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public double getFps() {
        return fps;
    }

    public void setFps(double fps) {
        this.fps = fps;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getFramesProcessed() {
        return framesProcessed;
    }

    public void setFramesProcessed(int framesProcessed) {
        this.framesProcessed = framesProcessed;
    }
}
