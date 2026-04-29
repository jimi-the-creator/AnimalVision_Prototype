package com.animalvision;

import com.animalvision.analysis.ConcernLevel;
import com.animalvision.analysis.WelfareAnalyzer;
import com.animalvision.model.ChickenReport;
import com.animalvision.model.TrackedChicken;
import com.animalvision.report.OpenAIReportAgent;
import com.animalvision.report.ReportGenerator;
import com.animalvision.tracking.CentroidTracker;
import com.animalvision.util.JsonUtils;
import com.animalvision.util.ProcessRunner;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class AnimalVisionApp extends Application {
    public static final String DISCLAIMER = "AnimalVision does not diagnose pain, disease, anxiety, distress, or suffering. "
            + "It surfaces possible welfare-relevant movement and spacing patterns for human review.";

    private final Path projectRoot = Path.of("").toAbsolutePath();
    private final Path outputsDir = projectRoot.resolve("outputs");
    private final Path detectionsJson = outputsDir.resolve("detections.json");
    private final Path trackedJson = outputsDir.resolve("tracked_detections.json");
    private final Path trackingCsv = outputsDir.resolve("tracking_data.csv");
    private final Path metricsJson = outputsDir.resolve("metrics.json");
    private final Path welfareReport = outputsDir.resolve("welfare_report.txt");
    private final Path trackedVideo = outputsDir.resolve("annotated").resolve("animalvision_tracked.mp4");
    private final Path chickenCropsDir = outputsDir.resolve("chicken_crops");

    private Path selectedVideo;
    private Label statusLabel;
    private Label selectedVideoLabel;
    private Label videoCaptionLabel;
    private TextArea reportTextArea;
    private MediaView mediaView;
    private MediaPlayer mediaPlayer;
    private final ObservableList<ResultRow> rows = FXCollections.observableArrayList();

    @Override
    public void start(Stage stage) {
        stage.setTitle("AnimalVision");

        Label title = new Label("AnimalVision");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 700;");
        Label subtitle = new Label("AI-assisted poultry welfare signal scanner");
        subtitle.setStyle("-fx-font-size: 15px;");

        Button chooseVideoButton = new Button("Choose Video");
        chooseVideoButton.setOnAction(event -> chooseVideo(stage));
        Button demoVideoButton = new Button("Use Demo Video");
        demoVideoButton.setOnAction(event -> useDemoVideo());
        Button analyzeButton = new Button("Analyze");
        analyzeButton.setOnAction(event -> analyze());

        HBox controls = new HBox(8, chooseVideoButton, demoVideoButton, analyzeButton);
        selectedVideoLabel = new Label("No video selected");
        selectedVideoLabel.setWrapText(true);
        statusLabel = new Label("Ready");
        statusLabel.setWrapText(true);

        VBox header = new VBox(8, title, subtitle, controls, selectedVideoLabel, statusLabel);
        header.setPadding(new Insets(16));

        TableView<ResultRow> table = resultsTable();
        table.setPrefWidth(520);
        table.setMinWidth(430);
        reportTextArea = new TextArea();
        reportTextArea.setWrapText(true);
        reportTextArea.setEditable(false);
        reportTextArea.setPromptText("AnimalVision report will appear here after analysis.");
        VBox.setVgrow(reportTextArea, Priority.ALWAYS);

        Label disclaimer = new Label(DISCLAIMER);
        disclaimer.setWrapText(true);
        disclaimer.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        HBox exportButtons = new HBox(8,
                openButton("Open annotated video", trackedVideo),
                openButton("Open tracking CSV", trackingCsv),
                openButton("Open welfare report", welfareReport),
                openButton("Open metrics JSON", metricsJson)
        );

        VBox videoPanel = videoPanel();
        HBox reviewArea = new HBox(12, videoPanel, table);
        HBox.setHgrow(videoPanel, Priority.ALWAYS);
        HBox.setHgrow(table, Priority.ALWAYS);

        VBox center = new VBox(10, reviewArea, reportTextArea, exportButtons, disclaimer);
        center.setPadding(new Insets(0, 16, 16, 16));
        VBox.setVgrow(reviewArea, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(center);

        Scene scene = new Scene(root, 1180, 820);
        stage.setScene(scene);
        stage.show();
    }

    private VBox videoPanel() {
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.setFitWidth(620);
        mediaView.setFitHeight(360);

        Label placeholder = new Label("Choose a video to preview it here");
        placeholder.setStyle("-fx-text-fill: #666; -fx-font-size: 14px;");
        StackPane videoFrame = new StackPane(placeholder, mediaView);
        videoFrame.setMinHeight(360);
        videoFrame.setStyle("-fx-background-color: #111; -fx-border-color: #333;");

        videoCaptionLabel = new Label("Preview: no video loaded");
        videoCaptionLabel.setWrapText(true);
        Button playButton = new Button("Play");
        playButton.setOnAction(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.play();
            }
        });
        Button pauseButton = new Button("Pause");
        pauseButton.setOnAction(event -> {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
        });
        HBox playbackControls = new HBox(8, playButton, pauseButton, videoCaptionLabel);
        playbackControls.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(8, videoFrame, playbackControls);
        VBox.setVgrow(videoFrame, Priority.ALWAYS);
        return panel;
    }

    private TableView<ResultRow> resultsTable() {
        TableView<ResultRow> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(74);

        TableColumn<ResultRow, ResultRow> chicken = new TableColumn<>("Chicken Snapshot");
        chicken.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        chicken.setCellFactory(column -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            private final Label label = new Label();
            private final HBox content = new HBox(8, imageView, label);

            {
                imageView.setFitWidth(72);
                imageView.setFitHeight(54);
                imageView.setPreserveRatio(true);
                content.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(ResultRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                label.setText(item.id());
                if (Files.exists(item.thumbnailPath())) {
                    imageView.setImage(new Image(item.thumbnailPath().toUri().toString(), 72, 54, true, true));
                } else {
                    imageView.setImage(null);
                }
                setGraphic(content);
            }
        });
        TableColumn<ResultRow, String> activity = new TableColumn<>("Activity");
        activity.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().activity()));
        TableColumn<ResultRow, String> concern = new TableColumn<>("Main Concern");
        concern.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().mainConcern()));
        TableColumn<ResultRow, String> level = new TableColumn<>("Concern Level");
        level.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().level()));

        table.getColumns().add(chicken);
        table.getColumns().add(activity);
        table.getColumns().add(concern);
        table.getColumns().add(level);
        return table;
    }

    private void chooseVideo(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose chicken/poultry video");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video files", "*.mp4", "*.mov", "*.avi"));
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            selectedVideo = file.toPath();
            selectedVideoLabel.setText("Selected video: " + selectedVideo);
            statusLabel.setText("Video selected");
            loadVideo(selectedVideo, true, "Preview: selected source video");
        }
    }

    private void useDemoVideo() {
        Path demo = projectRoot.resolve("sample_videos").resolve("chicken_demo.mp4");
        selectedVideo = demo;
        selectedVideoLabel.setText("Selected video: " + selectedVideo);
        if (Files.exists(demo)) {
            statusLabel.setText("Demo video selected");
            loadVideo(selectedVideo, true, "Preview: demo source video");
        } else {
            statusLabel.setText("Demo path selected, but sample_videos/chicken_demo.mp4 is not present yet.");
        }
    }

    private void analyze() {
        if (selectedVideo == null) {
            statusLabel.setText("Choose a video first.");
            return;
        }
        rows.clear();
        reportTextArea.clear();

        Task<AnalysisResult> task = new Task<>() {
            @Override
            protected AnalysisResult call() throws Exception {
                Files.createDirectories(outputsDir.resolve("annotated"));
                updateMessage("Running YOLO detector...");
                ProcessRunner runner = new ProcessRunner();
                ProcessRunner.ProcessResult detectResult = runner.run(
                        projectRoot,
                        line -> updateMessage("YOLO: " + line),
                        "python3",
                        "python/detect.py",
                        selectedVideo.toString()
                );
                if (!detectResult.succeeded()) {
                    throw new IOException("Python detector failed with exit code " + detectResult.exitCode()
                            + "\n\nSTDOUT:\n" + detectResult.stdout()
                            + "\nSTDERR:\n" + detectResult.stderr());
                }

                updateMessage("Parsing detections...");
                JsonUtils.DetectionData detectionData = JsonUtils.loadDetections(detectionsJson);

                updateMessage("Assigning Chicken IDs...");
                List<TrackedChicken> tracks = new CentroidTracker(100.0).track(detectionData.detectionsByFrame());
                JsonUtils.writeTrackingCsv(tracks, trackingCsv);
                JsonUtils.writeTrackedDetections(detectionData.metadata(), tracks, trackedJson);

                updateMessage("Drawing tracked annotated video...");
                ProcessRunner.ProcessResult drawResult = runner.run(
                        projectRoot,
                        line -> updateMessage("Draw: " + line),
                        "python3",
                        "python/draw_tracked_video.py",
                        selectedVideo.toString(),
                        trackedJson.toString()
                );
                if (!drawResult.succeeded()) {
                    updateMessage("Tracked video drawing failed; continuing with metrics and report.");
                }

                updateMessage("Computing movement and spacing metrics...");
                List<ChickenReport> reports = new WelfareAnalyzer().analyze(tracks, detectionData.metadata());
                String metrics = JsonUtils.writeMetrics(detectionData.metadata(), reports, metricsJson);

                updateMessage("Generating local report...");
                ReportGenerator reportGenerator = new ReportGenerator();
                String localReport = reportGenerator.generate(reports);
                reportGenerator.save(localReport, welfareReport);

                updateMessage("Requesting cautious OpenAI report polish...");
                String finalReport = new OpenAIReportAgent().polishReport(metrics, localReport);
                reportGenerator.save(finalReport, welfareReport);

                return new AnalysisResult(reports, finalReport);
            }
        };

        task.messageProperty().addListener((observable, oldValue, newValue) -> statusLabel.setText(newValue));
        task.setOnSucceeded(event -> {
            AnalysisResult result = task.getValue();
            rows.setAll(result.reports().stream().map(this::toResultRow).toList());
            reportTextArea.setText(result.report());
            loadVideo(trackedVideo, true, "Preview: tracked annotated video");
            statusLabel.setText("Analysis complete. Outputs: " + trackedVideo + ", " + trackingCsv + ", "
                    + metricsJson + ", " + welfareReport);
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            statusLabel.setText("Analysis failed.");
            reportTextArea.setText(error == null ? "Unknown error" : error.getMessage());
        });

        Thread thread = new Thread(task, "animalvision-analysis");
        thread.setDaemon(true);
        thread.start();
    }

    private ResultRow toResultRow(ChickenReport report) {
        String mainConcern = report.getReasons().isEmpty() ? "No standout metric" : report.getReasons().get(0);
        return new ResultRow(
                "Chicken #" + report.getChickenId(),
                chickenCropsDir.resolve("chicken_" + report.getChickenId() + ".jpg"),
                report.getActivityLevel(),
                mainConcern,
                displayLevel(report.getConcernLevel()) + " (" + report.getConcernScore() + ")"
        );
    }

    private void loadVideo(Path path, boolean autoplay, String caption) {
        if (path == null || !Files.exists(path)) {
            videoCaptionLabel.setText("Preview unavailable: " + path);
            return;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        Media media = new Media(path.toUri().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        mediaView.setMediaPlayer(mediaPlayer);
        videoCaptionLabel.setText(caption);
        if (autoplay) {
            mediaPlayer.play();
        }
    }

    private Button openButton(String label, Path path) {
        Button button = new Button(label);
        button.setOnAction(event -> openFile(path));
        return button;
    }

    private void openFile(Path path) {
        try {
            if (!Files.exists(path)) {
                statusLabel.setText("File does not exist yet: " + path);
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                statusLabel.setText("Desktop file opening is not supported on this system.");
                return;
            }
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            statusLabel.setText("Could not open file: " + e.getMessage());
        }
    }

    private String displayLevel(ConcernLevel level) {
        String lower = level.name().toLowerCase(Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private record ResultRow(String id, Path thumbnailPath, String activity, String mainConcern, String level) {
    }

    private record AnalysisResult(List<ChickenReport> reports, String report) {
    }
}
