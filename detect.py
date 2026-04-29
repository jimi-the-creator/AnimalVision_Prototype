#!/usr/bin/env python3
import json
import sys
from pathlib import Path

try:
    import cv2
    from ultralytics import YOLO
except ImportError as exc:
    print(
        "Missing Python dependency. Install with: pip install ultralytics opencv-python",
        file=sys.stderr,
    )
    raise exc


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 python/detect.py path/to/video.mp4 [frame_stride]", file=sys.stderr)
        return 2

    video_path = Path(sys.argv[1]).expanduser().resolve()
    frame_stride = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    if not video_path.exists():
        print(f"Video not found: {video_path}", file=sys.stderr)
        return 2

    project_root = Path(__file__).resolve().parents[1]
    outputs_dir = project_root / "outputs"
    annotated_dir = outputs_dir / "annotated"
    outputs_dir.mkdir(parents=True, exist_ok=True)
    annotated_dir.mkdir(parents=True, exist_ok=True)

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        print(f"Could not open video: {video_path}", file=sys.stderr)
        return 1

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    detected_video_path = annotated_dir / "detected_video.mp4"
    writer = cv2.VideoWriter(str(detected_video_path), fourcc, fps, (width, height))

    print("Loading YOLO model yolov8n.pt...")
    model = YOLO("yolov8n.pt")
    names = model.names
    frames = []
    frame_index = 0
    frames_processed = 0

    while True:
        ok, frame = cap.read()
        if not ok:
            break

        frame_detections = []
        if frame_index % frame_stride == 0:
            results = model(frame, verbose=False)
            for result in results:
                for box in result.boxes:
                    cls_id = int(box.cls[0].item())
                    label = names.get(cls_id, str(cls_id))
                    confidence = float(box.conf[0].item())
                    if label != "bird":
                        continue
                    x1, y1, x2, y2 = [float(value) for value in box.xyxy[0].tolist()]
                    detection = {
                        "label": "bird",
                        "confidence": round(confidence, 4),
                        "x": round(x1, 2),
                        "y": round(y1, 2),
                        "width": round(x2 - x1, 2),
                        "height": round(y2 - y1, 2),
                    }
                    frame_detections.append(detection)

                    cv2.rectangle(frame, (int(x1), int(y1)), (int(x2), int(y2)), (44, 180, 80), 2)
                    cv2.putText(
                        frame,
                        f"bird {confidence:.2f}",
                        (int(x1), max(20, int(y1) - 8)),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        (44, 180, 80),
                        2,
                        cv2.LINE_AA,
                    )

            frames.append(
                {
                    "frame": frame_index,
                    "time_seconds": round(frame_index / fps, 4),
                    "detections": frame_detections,
                }
            )
            frames_processed += 1

        writer.write(frame)
        frame_index += 1

    cap.release()
    writer.release()

    output = {
        "video": {
            "path": str(video_path),
            "fps": fps,
            "width": width,
            "height": height,
            "frames_processed": frames_processed,
        },
        "frames": frames,
    }
    detections_path = outputs_dir / "detections.json"
    detections_path.write_text(json.dumps(output, indent=2), encoding="utf-8")
    print(f"Wrote {detections_path}")
    print(f"Wrote {detected_video_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
