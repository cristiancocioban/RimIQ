# RimIQ MVP Architecture (MVVM)

## Layers
- **UI (Compose)**: `TrainingScreen` renders camera preview, skeleton overlay, and metrics pills.
- **ViewModel**: `TrainingViewModel` receives analyzed pose frames and publishes `TrainingUiState`.
- **Domain Engine**: `DrillEngine` runs drill state machine and metric extraction logic.
- **Data/Device**: `PoseAnalyzer` (ML Kit + CameraX) converts `ImageProxy` to `PoseFrame`.

## Runtime flow
1. CameraX uses **front camera** and sends frames to `PoseAnalyzer`.
2. `PoseAnalyzer` calls ML Kit Pose API with frame rotation and emits normalized landmarks.
3. `TrainingViewModel` forwards each frame to `DrillEngine`.
4. `DrillEngine` updates state (`READY` / `ACTIVE` / `SUMMARY`) and computes drill metrics.
5. Compose observes `StateFlow` and draws the overlay + metric pills in real time.

## Drill state machine
- `READY`: user not fully in frame.
- `ACTIVE`: required landmarks visible, drills tracked.
- `SUMMARY`: fixed results after ending session.
