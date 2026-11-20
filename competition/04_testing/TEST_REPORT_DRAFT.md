# AgroVision Test Report Draft

> Draft status: evidence-backed sections updated for the 38-class competition model.  
> Updated: 2026-08-10  
> Final submission still needs APK device testing, demo screenshots/video, and field-shot validation if available.

## 1. Program Overview And Core Modules

AgroVision is an Android native application built with Kotlin, Jetpack Compose, Room, Hilt, CameraX, and ONNX Runtime. The local-first workflow is:

1. Capture or select a crop leaf image.
2. Run local ONNX classification.
3. Return Top-3 disease candidates.
4. Combine the result with local knowledge, plot records, weather/risk rules, and treatment history.
5. Save diagnosis and follow-up records locally for traceability.

Core code paths:

| Module | Path |
|---|---|
| ONNX classification | `app/src/main/java/com/agrovision/app/ml/OnnxDetector.kt` |
| Model routing | `app/src/main/java/com/agrovision/app/ml/ModelRouter.kt` |
| Detection repository | `app/src/main/java/com/agrovision/app/data/repository/DetectionRepository.kt` |
| Label catalog | `app/src/main/java/com/agrovision/app/domain/model/PestLabelCatalog.kt` |
| Local knowledge | `app/src/main/assets/encyclopedia.json` |

## 2. Data Description And Processing

The current competition model uses the PlantVillage color image dataset extracted from `D:\xiazai\archive.zip`.

| Item | Value |
|---|---|
| Source archive SHA-256 | `08BF3315BACB7D36F4E37AE47BDAD7562761C261FA2CB0B4BA76CC606C3B912F` |
| Extracted color dataset | `D:\xiazai\plantvillage_color_archive` |
| Split dataset | `D:\xiazai\plantvillage_color_split` |
| Classes | 38 |
| Total images | 54,305 |
| Split seed | `20260809` |
| Train / val / test | 37,997 / 8,129 / 8,179 |
| Split manifest | `competition/02_data/plantvillage_color_split_manifest.csv` |

Only the `color` variant was extracted and used. The split was generated with hardlinks to avoid unnecessary duplication while keeping train/val/test folders deterministic.

## 3. Model Selection And Fine-Tuning Strategy

The selected model is MobileNetV3-Small because it is compact and suitable for Android CPU inference. Training used a conservative two-stage transfer-learning strategy:

| Stage | Configuration |
|---|---|
| Stage 1 | ImageNet pretrained, backbone frozen, 224 input, 5 epochs |
| Stage 2 | Resume Stage 1 best, unfreeze all layers, LR `1e-4`, epochs 6-8 |

The model was exported to ONNX with input `[1,3,224,224]` and output `[1,38]`. The app uses the same ImageNet normalization and Top-3 softmax post-processing.

## 4. Overall Optimization Strategy

Implemented:

- Local ONNX Runtime inference without network dependency.
- 224x224 fixed input for stable mobile inference cost.
- ImageNet mean/std preprocessing aligned with training.
- Session locking around `session.run()` to avoid concurrent inference issues.
- Dynamic Top-K handling so label count and output dimension are checked safely.
- 38-class labels and model metadata synchronized in app assets.

Pending before final submission:

| Item | Status |
|---|---|
| APK build on clean machine | Pending |
| Real-device p50/p95 inference latency | Pending |
| Memory and repeated-inference stability | Pending |
| Demo video with final APK | Pending |
| Field-shot external validation | Pending if suitable data is available |

## 5. Evaluation Metrics And Results

Validation metrics during Stage 2:

| Epoch | Train Loss | Train Top-1 | Val Loss | Val Top-1 | Val Top-3 |
|---:|---:|---:|---:|---:|---:|
| 6 | 0.099654 | 0.968471 | 0.056715 | 0.981302 | 0.998155 |
| 7 | 0.050100 | 0.983709 | 0.043733 | 0.985853 | 0.999385 |
| 8 | 0.033803 | 0.988973 | 0.029732 | 0.990528 | 0.999754 |

Final test results:

| Metric | Value |
|---|---:|
| Test loss | 0.028356207834935074 |
| Test Top-1 Accuracy | 0.9918082895219464 |
| Test Top-3 Accuracy | 0.9995109426580266 |

Artifact hashes:

| File | SHA-256 |
|---|---|
| `best.onnx` | `41643BD279F3221B810B7500E8596D801ABA2084F93010C687D9F00109F38FCF` |
| `best.pt` | `42F7077B946117E11CA5F7141559E503D2DAA512CB420490F86D4B9F1E468945` |

## 6. Work Value And Innovation

AgroVision is not only a single-image classifier. It connects image diagnosis with plot records, local knowledge, weather/risk rules, treatment planning, and follow-up history. This makes the app closer to a farm-management workflow: recognition result, suggested action, treatment record, and later review can be kept in one local-first loop.

## 7. Other Notes

- The final bundled disease model is 38-class MobileNetV3-Small, not YOLO detection.
- The filename `yolo11n.onnx` remains only for compatibility with existing app code.
- Pest detection and field GIS/robot/OCR functions should not be overstated unless separately verified.
- Pesticide and treatment advice is auxiliary and must not replace local agricultural guidance or product labels.
- The current high accuracy is measured on PlantVillage color split; external real-field images are the main remaining risk.
