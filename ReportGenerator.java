package com.animalvision.report;

import com.animalvision.analysis.ConcernLevel;
import com.animalvision.model.ChickenReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class ReportGenerator {
    private static final String DISCLAIMER = "AnimalVision does not diagnose pain, disease, anxiety, distress, or suffering. "
            + "It surfaces possible welfare-relevant movement and spacing patterns for human review.";

    public String generate(List<ChickenReport> reports) {
        StringBuilder builder = new StringBuilder();
        long concerningCount = reports.stream()
                .filter(report -> report.getConcernLevel() != ConcernLevel.LOW)
                .count();

        builder.append("AnimalVision Welfare-Review Signal Report\n");
        builder.append("=========================================\n\n");
        builder.append(DISCLAIMER).append("\n\n");
        builder.append("Concern-level birds: ").append(concerningCount).append("\n\n");

        if (concerningCount == 0) {
            builder.append("No tracked chicken objects exceeded the current Watch, Moderate, or High thresholds. ");
            builder.append("Human review is still recommended because video quality, camera position, and clip length can affect results.\n\n");
        }

        for (ChickenReport report : reports) {
            if (report.getConcernLevel() == ConcernLevel.LOW) {
                continue;
            }
            builder.append("Chicken #").append(report.getChickenId()).append("\n");
            builder.append("Concern level: ").append(displayLevel(report.getConcernLevel())).append("\n");
            builder.append("Activity: ").append(report.getActivityLevel()).append("\n");
            builder.append("Total movement: ").append(number(report.getTotalMovementPixels())).append(" pixels\n");
            builder.append("Movement vs flock average: ").append(number(report.getMovementVsFlockAveragePercent())).append("%\n");
            builder.append("Edge time: ").append(number(report.getEdgeTimePercent())).append("%\n");
            builder.append("Isolation time: ").append(number(report.getIsolationTimePercent())).append("%\n");
            builder.append("Crowding time: ").append(number(report.getCrowdingTimePercent())).append("%\n");
            builder.append("Repetitive pathing: ").append(report.isRepetitivePathing() ? "possible" : "not observed").append("\n");
            builder.append("Why flagged:\n");
            for (String reason : report.getReasons()) {
                builder.append("- ").append(reason).append("\n");
            }
            builder.append("\n");
        }

        builder.append("Recommended human review steps:\n");
        builder.append("- Rewatch the marked time ranges and compare each flagged object with surrounding birds.\n");
        builder.append("- Check for camera-angle artifacts, occlusion, lighting problems, and tracking ID switches.\n");
        builder.append("- Use trained welfare staff or veterinary guidance for any welfare decisions.\n\n");
        builder.append("Limitations:\n");
        builder.append("- YOLO may label chickens as generic birds and may miss partially visible birds.\n");
        builder.append("- Tracking IDs are approximate and may switch during heavy overlap.\n");
        builder.append("- These metrics may indicate patterns that warrant review; they are not welfare-state diagnoses.\n");

        return builder.toString();
    }

    public void save(String report, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, report);
    }

    private String displayLevel(ConcernLevel level) {
        return switch (level) {
            case LOW -> "Low";
            case WATCH -> "Watch";
            case MODERATE -> "Moderate";
            case HIGH -> "High";
        };
    }

    private String number(double value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
