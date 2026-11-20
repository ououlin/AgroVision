# AgroVision Data Provenance

> Updated: 2026-08-11  
> Purpose: competition evidence, model reproducibility, privacy review, and release audit.

## 1. Data Use Principles

1. Only evidence-backed data may be described as used in final competition materials.
2. Every dataset needs a source, path, license or reuse note, acquisition date when known, processing steps, and hash evidence.
3. User-generated images and farm records stay local by default and must be desensitized before export.
4. Public internet APIs and open research datasets must not be described as government open data unless their publisher and license prove it.
5. Large datasets are stored on `D:\xiazai`; the repository keeps manifests, scripts, and documentation only.

## 2. Current Data Assets

| Asset | Type | Status | Current Use | Evidence |
|---|---|---|---|---|
| PlantVillage color 38-class dataset | Open research image dataset | Archived and split | Competition model training | `D:\xiazai\archive.zip`, `D:\xiazai\plantvillage_color_archive`, `D:\xiazai\plantvillage_color_split` |
| PlantVillage 8-class historical subset | Open research image subset | Historical only | Old pilot model evidence | Original reproducible split is missing |
| Local encyclopedia | Knowledge data | 74 entries; per-entry source audit pending | Local diagnosis/RAG context | `app/src/main/assets/encyclopedia.json` |
| Encyclopedia sample images | Derived demonstration assets | 26 accurately mapped PlantVillage samples | Disease comparison in encyclopedia UI | `app/src/main/assets/encyclopedia/`, `EncyclopediaImageCatalog.kt` |
| User farm records | User-generated data | Local-first | Personalization and traceability | Room entities |
| QWeather API | Third-party API | Optional | Weather context | Service terms need final review |
| wttr.in | Public internet service | Optional fallback | Weather context | Terms/SLA need final review |
| Government/public agricultural data | To select | Pending | Competition data fusion evidence | Must be selected and archived before final submission |
| Pesticide registration public information | To select | Pending | Treatment safety validation | Access and reuse terms must be checked |

## 3. PlantVillage Color Archive

| Item | Value |
|---|---|
| Source archive | `D:\xiazai\archive.zip` |
| Archive SHA-256 | `08BF3315BACB7D36F4E37AE47BDAD7562761C261FA2CB0B4BA76CC606C3B912F` |
| Extracted variant | `color` only |
| Extracted dataset | `D:\xiazai\plantvillage_color_archive` |
| Total images | 54,305 |
| Classes | 38 |
| Split output | `D:\xiazai\plantvillage_color_split` |
| Split seed | `20260809` |
| Train / val / test | 37,997 / 8,129 / 8,179 |
| Manifest | `competition/02_data/plantvillage_color_split_manifest.csv` |
| Link method | hardlink |

The current model card and test report must cite this dataset as the training/evaluation source. Because PlantVillage-style images are mostly controlled leaf images, materials should clearly state that field-shot external validation remains pending.

## 4. Reproducibility Evidence

| Evidence | Location |
|---|---|
| Extraction script | `tools/extract_plantvillage_variant.py` |
| Split script | `tools/split_imagefolder_dataset.py` |
| Training script | `tools/train_competition_classifier.py` |
| ONNX export script | `tools/export_competition_onnx.py` |
| Final model directory | `D:\xiazai\agrovision_competition\models\mobilenetv3_38class_stage2_224` |
| App bundled model | `app/src/main/assets/model/yolo11n.onnx` |
| Current demo APK | `D:\xiazai\agrovision_competition\submission_materials\20260811_pesticide_center\AgroVision-2.0.0-debug.apk` |

Final ONNX SHA-256:

```text
41643BD279F3221B810B7500E8596D801ABA2084F93010C687D9F00109F38FCF
```

Current demonstration APK SHA-256:

```text
8CF551F3CCA6E1B1B11516B57460C58F7FB409CE0C355FF222263D40D586FE86
```

The APK is signed with the Android Debug certificate using APK Signature Scheme v2. It is suitable for device testing and competition demonstration, not for application-store release.

## 5. Submission Checklist

- Keep dataset files out of Git; submit manifests and evidence paths instead.
- Verify `competition/02_data/DATASET_MANIFEST.csv` matches the final model card.
- Keep model hash, labels, metadata, and app asset synchronized.
- Android `compileDebugKotlin` and `assembleDebug` passed on 2026-08-11; real-device inference and performance tests remain pending.
- Add public/government data source evidence only after the exact source and license are confirmed.
