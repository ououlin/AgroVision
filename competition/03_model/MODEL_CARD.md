# AgroVision Competition Model Card

> Model status: current competition model  
> Updated: 2026-08-10  
> App asset: `app/src/main/assets/model/yolo11n.onnx`  
> Historical note: the file name is kept for app compatibility, but the actual model is a MobileNetV3-Small image classifier.

## 1. Basic Information

| Item | Value |
|---|---|
| Architecture | MobileNetV3-Small |
| Task | Plant disease image classification |
| Classes | 38 PlantVillage color classes |
| Input | `input: [1, 3, 224, 224]`, float32 |
| Output | `output: [1, 38]`, logits |
| Preprocess | Resize 224x224, RGB, ImageNet mean/std normalize |
| Postprocess | Softmax, Top-3 |
| Training device | CPU |
| Framework | PyTorch 2.0.1+cpu, torchvision 0.15.2+cpu |
| ONNX asset size | 6,241,604 bytes |

## 2. Data

| Item | Value |
|---|---|
| Source archive | `D:\xiazai\archive.zip` |
| Source SHA-256 | `08BF3315BACB7D36F4E37AE47BDAD7562761C261FA2CB0B4BA76CC606C3B912F` |
| Extracted dataset | `D:\xiazai\plantvillage_color_archive` |
| Split dataset | `D:\xiazai\plantvillage_color_split` |
| Split seed | `20260809` |
| Split ratio | train/val/test = 70/15/15 |
| Total images | 54,305 |
| Train / val / test | 37,997 / 8,129 / 8,179 |
| Split manifest | `competition/02_data/plantvillage_color_split_manifest.csv` |

## 3. Training Strategy

| Stage | Setting | Result |
|---|---|---|
| Stage 1 | ImageNet pretrained, backbone frozen, input 224, 5 epochs | Produced warm-start checkpoint |
| Stage 2 | Resume Stage 1 best, full fine-tune, LR `1e-4`, epochs 6-8 | Final competition model |

Stage 2 validation metrics:

| Epoch | Train Top-1 | Val Top-1 | Val Top-3 |
|---:|---:|---:|---:|
| 6 | 0.968471 | 0.981302 | 0.998155 |
| 7 | 0.983709 | 0.985853 | 0.999385 |
| 8 | 0.988973 | 0.990528 | 0.999754 |

Final test metrics:

| Metric | Value |
|---|---:|
| Test loss | 0.028356207834935074 |
| Test Top-1 | 0.9918082895219464 |
| Test Top-3 | 0.9995109426580266 |

## 4. Artifacts

| Artifact | Path | SHA-256 |
|---|---|---|
| ONNX | `D:\xiazai\agrovision_competition\models\mobilenetv3_38class_stage2_224\best.onnx` | `41643BD279F3221B810B7500E8596D801ABA2084F93010C687D9F00109F38FCF` |
| PyTorch best | `D:\xiazai\agrovision_competition\models\mobilenetv3_38class_stage2_224\best.pt` | `42F7077B946117E11CA5F7141559E503D2DAA512CB420490F86D4B9F1E468945` |
| Labels | `D:\xiazai\agrovision_competition\models\mobilenetv3_38class_stage2_224\labels.txt` | `4CE16979CC3AB9054E3573B5B1A689A2C1FD54AFED7BDA67806A7C41837FA682` |
| Run config | `D:\xiazai\agrovision_competition\models\mobilenetv3_38class_stage2_224\run_config.json` | `C07334EE61F4E70E14F42E4F3B4A11059897003A66846650159965462F3A6D3E` |
| Test metrics | `D:\xiazai\agrovision_competition\models\mobilenetv3_38class_stage2_224\test_metrics.json` | `6EE75EFC629F88AC0C2C57F5CCE89EADA5AA48001695DF57793769BFF021341F` |

The app-bundled ONNX at `app/src/main/assets/model/yolo11n.onnx` has the same SHA-256 as the final ONNX above.

## 5. Known Limits

- The evaluation uses a deterministic PlantVillage color split, not an external field-shot test set.
- PlantVillage images are mostly controlled leaf images, so real farm performance still needs field validation.
- The model is a classifier, not an object detector; it returns class probabilities without bounding boxes.
- Further training for thousands of epochs is not recommended on this dataset because current validation/test scores are already saturated and overfitting risk is high.
