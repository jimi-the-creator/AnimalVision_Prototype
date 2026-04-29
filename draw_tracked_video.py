#!/usr/bin/env python3
import json
import sys
from collections import defaultdict
from pathlib import Path

try:
    import cv2
except ImportError as exc:
    print("Missing Python dependency. Install with: pip install opencv-python", file=sys.stderr)
    raise exc


COLORS = [
    (230, 74, 25),
    (46, 125, 50),
    (21, 101, 192),
    (142, 36, 170),
    (0, 137, 123),
    (245, 124, 0),
    (93, 64, 55),
    (84, 110, 122),
]


def main():
    if len(sys.argv) < 3:
        print(
            "Usage: python3 python/draw_tracked_video.py path/to/video.mp4 outputs/tracked_detections.json",
            file=sys.stderr,
        )
        return 2

    video_path = Path(sys.argv[1]).expanduser().resolve()
    tracked_json = Path(sys.argv[2]).expanduser().resolve()
    if not video_path.exists():
        print(f"Video not found: {video_path}", file=sys.stderr)
        return 2
    if not tracked_json.exists():
        print(f"Tracked detections JSON not found: {tracked_json}", file=sys.stderr)
        return 2

    project_root = Path(__file__).resolve().parents[1]
    output_path = project_root / "outputs" / "annotated" / "animalvision_tracked.mp4"
    crops_dir = project_root / "outputs" / "chicken_crops"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    crops_dir.mkdir(parents=True, exist_ok=True)

    data = json.loads(tracked_json.read_text(encoding="utf-8"))
    detections_by_frame = {}
    for frame in data.get("frames", []):
        detections_by_frame[int(frame["frame"])] = frame.get("detections", [])

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        print(f"Could not open video: {video_path}", file=sys.stderr)
        return 1

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    writer = cv2.VideoWriter(str(output_path), cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))

    trails = defaultdict(list)
    active_boxes = {}
    saved_crops = set()
    frame_index = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break

        for detection in detections_by_frame.get(frame_index, []):
            chicken_id = int(detection["chickenId"])
            x = int(detection["x"])
            y = int(detection["y"])
            w = int(detection["width"])
            h = int(detection["height"])
            center = (int(detection["centerX"]), int(detection["centerY"]))
            trails[chicken_id].append(center)
            trails[chicken_id] = trails[chicken_id][-40:]
            active_boxes[chicken_id] = {
                "x": x,
                "y": y,
                "w": w,
                "h": h,
                "last_frame": frame_index,
            }

            if chicken_id not in saved_crops:
                pad = 12
                x1 = max(0, x - pad)
                y1 = max(0, y - pad)
                x2 = min(width, x + w + pad)
                y2 = min(height, y + h + pad)
                crop = frame[y1:y2, x1:x2]
                if crop.size:
                    cv2.imwrite(str(crops_dir / f"chicken_{chicken_id}.jpg"), crop)
                    saved_crops.add(chicken_id)

        # YOLO detections are sampled every Nth frame. Keep the last known box visible
        # between detection frames so the review video does not blink on and off.
        stale_after_frames = int(max(8, fps * 1.5))
        for chicken_id in list(active_boxes.keys()):
            box = active_boxes[chicken_id]
            if frame_index - box["last_frame"] > stale_after_frames:
                del active_boxes[chicken_id]
                continue

            color = COLORS[(chicken_id - 1) % len(COLORS)]
            x = box["x"]
            y = box["y"]
            w = box["w"]
            h = box["h"]
            cv2.rectangle(frame, (x, y), (x + w, y + h), color, 2)
            cv2.putText(
                frame,
                f"Chicken #{chicken_id}",
                (x, max(22, y - 8)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.58,
                color,
                2,
                cv2.LINE_AA,
            )

        for chicken_id, points in trails.items():
            color = COLORS[(chicken_id - 1) % len(COLORS)]
            for i in range(1, len(points)):
                cv2.line(frame, points[i - 1], points[i], color, 2)

        writer.write(frame)
        frame_index += 1

    cap.release()
    writer.release()
    print(f"Wrote {output_path}")
    print(f"Wrote chicken crops to {crops_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
