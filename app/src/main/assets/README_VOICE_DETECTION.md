# SafePasig.AI - AI/ML Features Setup

## Overview

This app uses several AI/ML technologies for safety detection:

1. **TensorFlow Lite** - On-device AI inference for audio classification
2. **Mel-Spectrograms** - Audio feature extraction for CNN
3. **Signal Vector Magnitude (SVM)** - Fall detection algorithm
4. **DBSCAN Clustering** - Danger zone analysis and heatmaps

---

## 1. Voice Detection (Audio Classification)

### Current Status
The app uses **volume-based detection** as a fallback when no TFLite model is present.
This detects sustained loud sounds (like shouting for help) but cannot recognize specific words.

### To Enable AI-Powered Voice Detection ("Saklolo"/"Tulong")

#### Option 1: Use Google's Teachable Machine (Recommended for beginners)
1. Go to https://teachablemachine.withgoogle.com/train/audio
2. Create audio classes:
   - Class 1: "saklolo" - Record multiple samples of people saying "Saklolo"
   - Class 2: "tulong" - Record multiple samples of people saying "Tulong"
   - Class 3: "background" - Record ambient noise, silence, normal conversation
3. Train the model
4. Export as **TensorFlow Lite** format
5. Rename the downloaded model to `soundclassifier.tflite`
6. Place it in this folder (`app/src/main/assets/`)
7. Optionally create `labels.txt` with one label per line
8. Rebuild the app

#### Option 2: Custom CNN Model with Mel-Spectrograms
For better accuracy, train a custom CNN model:

1. **Collect Data**: Gather audio samples (WAV, 16kHz mono)
   - 100+ samples of "Saklolo"
   - 100+ samples of "Tulong"
   - 200+ samples of background noise

2. **Feature Extraction**: Use the MelSpectrogram class:
   ```kotlin
   val melSpec = MelSpectrogram(sampleRate = 16000, nMels = 40)
   val features = melSpec.compute(audioSamples)
   val normalized = melSpec.toNormalizedImage(features)
   ```

3. **Train CNN Model** (Python/TensorFlow):
   ```python
   model = tf.keras.Sequential([
       tf.keras.layers.Conv2D(32, (3,3), activation='relu', input_shape=(time, 40, 1)),
       tf.keras.layers.MaxPooling2D((2,2)),
       tf.keras.layers.Conv2D(64, (3,3), activation='relu'),
       tf.keras.layers.MaxPooling2D((2,2)),
       tf.keras.layers.Flatten(),
       tf.keras.layers.Dense(64, activation='relu'),
       tf.keras.layers.Dense(3, activation='softmax')  # 3 classes
   ])
   ```

4. **Convert to TFLite**:
   ```python
   converter = tf.lite.TFLiteConverter.from_keras_model(model)
   tflite_model = converter.convert()
   open('soundclassifier.tflite', 'wb').write(tflite_model)
   ```

5. Place `soundclassifier.tflite` in this folder

### Model Requirements
- Format: TensorFlow Lite (.tflite)
- Filename: `soundclassifier.tflite` (exact name required)
- Labels must contain "saklolo" or "tulong" (case-insensitive)

---

## 2. Fall Detection (Signal Vector Magnitude)

The app uses an advanced SVM algorithm that detects falls through 3 phases:

### How It Works
1. **Free-Fall Detection**: Phone experiences near-weightlessness (< 3 m/s²)
2. **Impact Detection**: High G-force spike (> 20 m/s²)  
3. **Stillness Detection**: Lack of movement after impact (~9.8 m/s² with low variance)

Only when all 3 phases complete in sequence does an alert trigger.

### Tuning Parameters (in SignalVectorMagnitude.kt)
```kotlin
FREE_FALL_THRESHOLD = 3.0f      // Adjust if too sensitive/insensitive
IMPACT_THRESHOLD = 20.0f        // Higher = fewer false positives
STILLNESS_WINDOW = 1500L        // Time to confirm stillness (ms)
```

---

## 3. Danger Zone Analysis (DBSCAN)

The app uses DBSCAN clustering to identify dangerous areas:

### How It Works
1. SOS events are recorded with GPS coordinates
2. DBSCAN groups nearby incidents into clusters
3. Risk scores are calculated based on:
   - Number of incidents
   - Recency of incidents
   - Severity of incidents

### Viewing Danger Zones
```kotlin
// Get clusters
val clusters = dangerZoneRepository.getDangerClusters()

// Check if location is dangerous
val (isDangerous, nearestCluster) = dangerZoneRepository.checkDangerZone(lat, lng)

// Get heatmap data
val heatmapPoints = dangerZoneRepository.getHeatmapPoints()
```

### Tuning Parameters (in DBSCAN.kt)
```kotlin
eps = 0.0005           // ~50 meters cluster radius
minPoints = 3          // Minimum incidents to form a zone
```

---

## File Structure

```
app/src/main/
├── assets/
│   ├── soundclassifier.tflite   # (You add this)
│   ├── labels.txt               # (Optional)
│   └── README_VOICE_DETECTION.md
└── java/com/capstone/safepasigai/
    └── ml/
        ├── MelSpectrogram.kt      # Audio feature extraction
        ├── SignalVectorMagnitude.kt # Fall detection algorithm
        ├── DBSCAN.kt              # Spatial clustering
        └── AudioClassifierCNN.kt   # TFLite wrapper
```

---

## Testing

### Fall Detection
1. Start Smart Escort mode
2. Hold phone and simulate a fall (drop onto soft surface)
3. Remain still for 1.5+ seconds
4. SOS should trigger

### Voice Detection (with model)
1. Start Smart Escort mode
2. Say "Saklolo" or "Tulong" loudly
3. SOS should trigger if confidence >= 70%

### Volume Detection (fallback)
1. Start Smart Escort mode
2. Shout loudly for 1.5+ seconds
3. "LOUD DISTRESS SOUND" alert should trigger

