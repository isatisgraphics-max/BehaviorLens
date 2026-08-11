# BehaviorLens

Android app for multimodal behavioral signal analysis using Kotlin, Jetpack Compose, MediaPipe and Room.

## Build

GitHub Actions builds the debug APK automatically on pushes to `main`.

> MediaPipe model files are required for live face/pose analysis:
> `app/src/main/assets/face_landmarker.task`
> `app/src/main/assets/pose_landmarker_lite.task`
