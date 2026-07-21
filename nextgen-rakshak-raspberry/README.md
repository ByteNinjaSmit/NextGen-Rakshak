# NextGen Rakshak — Raspberry Pi Fixed Camera Node (Optional)

**Status: not implemented yet. Optional extension — out of scope for the current
MVP.**

## Planned role
A Raspberry Pi 4 + Pi Camera at festival exit gates / choke points running the
same face-matching pipeline continuously:

1. Poll the police kiosk API for active alert embeddings (HTTP GET, ~30s).
2. Detect + embed every passing face (OpenCV / MobileNet-SSD + MobileFaceNet TFLite).
3. On a high-confidence match, POST an alert (image, confidence, camera id,
   timestamp) back to the kiosk.

## Planned stack
- Raspberry Pi OS Lite, Python 3
- `opencv-python`, `tflite-runtime`, `numpy`, `requests`, `picamera2`

Build the web portal + volunteer app first; add this node only if time allows.
