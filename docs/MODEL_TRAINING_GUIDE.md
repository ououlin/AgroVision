# AgroVision AI — V2.0 模型训练与导出指南

> 本文档面向竞赛展示、课程答辩、教学演示和 V2.0 模型迭代场景，说明 Disease 分类头与 Pest 检测头的训练、导出和 App 集成方式。

---

## 0. V2.0 当前模型状态

AgroVision 2.0.0 的识别链路由 `ModelRouter` 统一调度：

| 模型头 | 代码入口 | 资产文件 | 当前状态 |
|---|---|---|---|
| Disease 分类头 | `OnnxDetector` | `app/src/main/assets/model/yolo11n.onnx` + `labels_onnx.txt` | 当前随包为 8 类试验模型，输入 224×224，输出 `[1,8]` Top-3 分类结果 |
| Pest 检测头 | `PestDetector` | `model/pest_detector.onnx` + `labels_pest.txt` + 可选 `pest_temperature.txt` | 代码已接入；资产缺失时自动降级为 Disease 单头 |
| TFLite 兜底 | `YoloDetector` | `model/yolo11n.tflite` + `labels_tflite.txt` | 兜底路径；当前仓库未随包 TFLite 模型时不会启用 |

> 注意：历史文件名 `yolo11n.onnx` 当前承载的是 ONNX 分类推理资产，训练/替换模型时务必确认输入输出维度与 `OnnxDetector` 或 `PestDetector` 对齐。不要把 YOLO 检测 ONNX 直接覆盖到分类头文件名下，否则 App 推理会维度不匹配。

---

## 一、背景说明

### 1.1 为什么需要自己训练？

YOLO11n 官方预训练模型是在 **MS COCO**（80类日常物体）上训练的，只认识人、汽车、狗、苹果等。用于农业病虫害场景时，模型无法识别叶片病斑、害虫等农业目标。

我们需要在 **PlantVillage** 等农业数据集上训练 Disease 分类头，并在 **IP102/自采害虫数据集** 上训练 Pest 检测头，让 V2.0 具备“病害分类 + 害虫检测”的双头能力。

### 1.2 数据集选择

| 数据集 | 类别数 | 图片数 | 推荐 |
|--------|--------|--------|------|
| PlantVillage | 38类 | 54,305张 | ✅ Disease 分类头首选，格式规范 |
| IP102 | 102类 | 75,000张 | ✅ Pest 检测/分类候选，虫害为主 |
| AI Challenger | 61类 | 47,000张 | 作物病害扩展，中文标签，国内场景 |

本项目 V2.0 推荐先稳定 **PlantVillage Disease 分类头**，再补齐 10 类高频害虫检测资产。

### 1.3 PlantVillage 包含的类别

| 编号 | 类别 | 编号 | 类别 |
|------|------|------|------|
| 0 | Apple_scab（苹果黑星病） | 19 | Pepper_bell_Bacterial_spot（辣椒细菌性斑点病） |
| 1 | Apple_Black_rot（苹果黑腐病） | 20 | Pepper_bell_healthy（辣椒健康） |
| 2 | Apple_Cedar_apple_rust（苹果锈病） | 21 | Potato_Early_blight（马铃薯早疫病） |
| 3 | Apple_healthy（苹果健康） | 22 | Potato_Late_blight（马铃薯晚疫病） |
| 4 | Blueberry_healthy（蓝莓健康） | 23 | Potato_healthy（马铃薯健康） |
| 5 | Cherry_healthy（樱桃健康） | 24 | Raspberry_healthy（树莓健康） |
| 6 | Cherry_Powdery_mildew（樱桃白粉病） | 25 | Soybean_healthy（大豆健康） |
| 7 | Corn_Cercospora_leaf_spot（玉米灰斑病） | 26 | Squash_Powdery_mildew（南瓜白粉病） |
| 8 | Corn_Common_rust（玉米锈病） | 27 | Strawberry_Leaf_scorch（草莓叶枯病） |
| 9 | Corn_Northern_Leaf_Blight（玉米大斑病） | 28 | Strawberry_healthy（草莓健康） |
| 10 | Corn_healthy（玉米健康） | 29 | Tomato_Bacterial_spot（番茄细菌性斑点病） |
| 11 | Grape_Black_rot（葡萄黑腐病） | 30 | Tomato_Early_blight（番茄早疫病） |
| 12 | Grape_Esca（葡萄埃斯卡病） | 31 | Tomato_Late_blight（番茄晚疫病） |
| 13 | Grape_Leaf_blight（葡萄叶斑病） | 32 | Tomato_Leaf_Mold（番茄叶霉病） |
| 14 | Grape_healthy（葡萄健康） | 33 | Tomato_Septoria（番茄斑枯病） |
| 15 | Orange_Huanglongbing（柑橘黄龙病） | 34 | Tomato_Spider_mites（番茄红蜘蛛） |
| 16 | Peach_Bacterial_spot（桃树细菌性斑点病） | 35 | Tomato_Target_Spot（番茄靶斑病） |
| 17 | Peach_healthy（桃树健康） | 36 | Tomato_Yellow_Leaf_Curl_Virus（番茄黄化曲叶病毒） |
| 18 | Pepper_bell_Bacterial_spot（辣椒斑点病） | 37 | Tomato_Mosaic_virus（番茄花叶病毒） |

---

## 二、环境准备

### 2.1 安装依赖

```bash
# 创建虚拟环境（推荐）
python -m venv agrovision_env
# Windows
agrovision_env\Scripts\activate
# macOS/Linux
source agrovision_env/bin/activate

# 安装 ultralytics
pip install ultralytics

# 可选：GPU加速
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
```

### 2.2 验证环境

```bash
python -c "from ultralytics import YOLO; print('YOLO OK')"
# 输出: YOLO OK

python -c "import torch; print('CUDA:', torch.cuda.is_available())"
# 输出: CUDA: True  (如果有GPU)
```

---

## 三、训练流程

### 3.1 下载数据集

**方法一：Kaggle CLI（推荐）**

```bash
pip install kaggle
kaggle datasets download emmarex/plantdisease -p plant_dataset --unzip
```

**方法二：直接下载**

访问 https://www.kaggle.com/datasets/emmarex/plantdisease → 下载 ZIP → 解压到 `plant_dataset/`

**方法三：百度网盘（国内更快）**

搜索 "PlantVillage 数据集 百度网盘"，将下载的文件放入 `plant_dataset/`。

### 3.2 验证数据集结构

```
plant_dataset/
└── PlantVillage/
    ├── Apple___Apple_scab/
    │   ├── 0001.jpg
    │   ├── 0002.jpg
    │   └── ...
    ├── Apple___Black_rot/
    ├── Corn___Common_rust/
    ├── Tomato___Early_blight/
    └── ... (共38个文件夹)
```

### 3.3 一键训练

```bash
cd AgroVision
python train_agro_model.py
```

这条命令会自动完成：**数据集检查 → 生成配置文件 → 训练 100 epoch → 导出 ONNX → 精度验证 → 复制到 App**

### 3.4 分步执行（如果想调整参数）

```bash
# 只下载数据
python train_agro_model.py --download

# 下载+训练，跳过导出（调试用）
python train_agro_model.py --skip-export

# 下载+训练+导出（完整）
python train_agro_model.py
```

### 3.5 手动训练（高级）

```bash
# 先生成配置
python train_agro_model.py --download

# 手动训练
yolo detect train \
  data=plant.yaml \
  model=yolo11n.pt \
  epochs=100 \
  imgsz=640 \
  batch=16 \
  lr0=0.01 \
  optimizer=AdamW \
  name=agrovision_custom
```

### 3.6 训练时间参考

| 硬件 | batch=16, epochs=100 |
|------|---------------------|
| NVIDIA RTX 3060（6GB） | ~2 小时 |
| NVIDIA RTX 4090 | ~30 分钟 |
| Apple M2 Pro | ~4 小时 |
| Intel i5 CPU | ~20 小时 |

---

## 四、导出 ONNX 模型

### 4.0 导出目标选择

- 替换 Disease 分类头：导出为 `[1, 3, 224, 224] → [1, N]` 的分类 ONNX，并复制为 `app/src/main/assets/model/yolo11n.onnx`，同步更新 `labels_onnx.txt`。
- 发布 Pest 检测头：导出为 `[1, 3, 640, 640] → [1, 4+NC, 8400]` 的 YOLO11 ONNX，并复制为 `app/src/main/assets/model/pest_detector.onnx`，同步新增 `labels_pest.txt`。
- 发布 TFLite 兜底：导出 `yolo11n.tflite` 后放入 `assets/model/`，但需确认 `YoloDetector` 的输出解析和类别数与模型一致。

### 4.1 自动导出

训练完成自动导出，或者手动：

```bash
python train_agro_model.py --skip-download
```

### 4.2 手动导出

```bash
yolo export model=agrovision_train/weights/best.pt format=onnx imgsz=640 opset=11 simplify=True
```

### 4.3 替代 App 中的模型

导出后按模型头复制到对应资产位置。Disease 分类头复制到 `app/src/main/assets/model/yolo11n.onnx`；Pest 检测头复制到 `app/src/main/assets/model/pest_detector.onnx`。

同时更新对应标签文件：Disease 用 `labels_onnx.txt`，TFLite 用 `labels_tflite.txt`，Pest 用 `labels_pest.txt`。

### 4.4 重新编译 APK

```
在 Android Studio 中: Build → Build Bundle(s)/APK(s) → Build APK(s)
或命令行:
./gradlew :app:assembleDebug
```

---

## 五、训练结果解读

### 5.1 Disease 分类头指标

当前随包模型是 8 类分类试验模型，不应使用目标检测的 mAP 描述其效果。PlantVillage 38 类或加入背景后的 39 类版本必须重新训练、导出并完成独立测试后才能对外宣称。

| 指标 | 含义 | 报告要求 |
|------|------|----------|
| **Top-1 Accuracy** | 第一候选分类正确的比例 | 同时报告样本数和 95% 置信区间 |
| **Top-3 Accuracy** | 正确分类落在前三候选中的比例 | 与 App 的 Top-3 输出一致 |
| **Macro-F1** | 各类别 F1 的算术平均 | 避免大类别掩盖小类别 |
| **Per-class Precision / Recall** | 每一类的查准率和召回率 | 附混淆矩阵，说明薄弱类别 |
| **ECE** | 置信度与真实正确率的校准误差 | 对比校准前后，并说明低置信度策略 |

### 5.2 Pest 检测头指标

只有实际发布 `pest_detector.onnx` 并按目标检测任务评估后，才报告以下指标：

| 指标 | 含义 | 报告要求 |
|------|------|----------|
| **mAP@0.5** | IoU=0.5 时的平均精度 | 附各类别 AP |
| **mAP@0.5:0.95** | 多个 IoU 阈值上的平均精度 | 作为主检测指标 |
| **Precision / Recall** | 检测查准率与召回率 | 固定置信度和 NMS 阈值 |

### 5.3 证据规则

- 训练、验证、测试集必须按文件清单固化，检查重复图与数据泄漏。
- 每次训练保存配置、随机种子、依赖版本、日志、最佳权重、混淆矩阵和指标原始文件。
- App 端另测 p50/p95 推理时延、峰值内存、连续运行稳定性和离线成功率。
- 预期值、教程示例和历史口径都不能作为实测结果。未找到对应原始产物的数字统一写“待测试”。

---

## 六、测试环节

### 6.1 快速测试（Python 测试）

```bash
# 用图片测试训练好的模型
yolo predict model=agrovision_train/weights/best.pt source=test_leaf.jpg imgsz=640
```

### 6.2 App 内测试

1. 将 `yolo11n.onnx` 和 `labels.txt` 放入 `assets/model/`
2. 构建 APK → 安装到手机
3. 打开 **AI识别** → 拍摄或上传植物叶片图片
4. 应显示：**病害名称 + 置信度 + 危险等级**

### 6.3 验证清单

- [ ] 下载 PlantVillage 数据集（54K+ 图片）
- [ ] 训练完成，mAP@0.5 > 0.8
- [ ] Disease ONNX 输入/输出与 `OnnxDetector` 匹配
- [ ] Pest ONNX 输入/输出与 `PestDetector` 匹配（如果发布害虫头）
- [ ] ONNX 模型 < 15MB（单头）或符合包体预算（双头）
- [ ] `labels_onnx.txt` / `labels_pest.txt` 与模型类别顺序一致
- [ ] APK 编译成功
- [ ] 拍照识别测试：取一片病叶 → 正确识别病害
- [ ] 图片上传测试：选择叶片图片 → 正确识别

---

## 七、常见问题

### Q1: 下载数据集很慢怎么办？
A: 用百度网盘搜索 "PlantVillage 数据集"，国内下载更快。或直接用 `kaggle CLI` + 代理。

### Q2: 训练到一半报 Out of Memory？
A: 减小 `batch` 参数，在 `train_agro_model.py` 中改为 `batch=8` 或 `batch=4`。

### Q3: ONNX 模型太大（>30MB）？
A: 训练时使用 `yolo11n`（不要用 yolo11x/yolo11l），导出时不加 `half=True`（保持 float32）。

### Q4: 我想要更多类别怎么办？
A: Disease 分类扩展可引入 AI Challenger、PlantDoc 等数据集；害虫识别建议先训练独立 Pest 检测头，不要直接混入 PlantVillage 分类头，以免分类/检测任务混淆。

### Q5: 训练完在手机上推理很慢？
A: 使用 `yolo11n` 是最轻量版本，640×640 输入。如果还慢，可以导出 `int8` 量化模型（需额外配置）。

---

## 八、文件清单

训练完成后，项目新增/更新的文件：

```
AgroVision/
├── tools/train_agro_model.py    # 一键训练脚本（可复用）
├── tools/plant.yaml             # YOLO 训练配置（自动生成）
├── tools/plant_dataset/         # PlantVillage 数据集（不提交Git）
├── agrovision_train/            # 训练产物（模型+日志+图表）
│   ├── weights/best.pt          # 最佳权重
│   ├── results.csv              # 训练指标CSV
│   └── confusion_matrix.png     # 混淆矩阵
├── MODEL_TRAINING_GUIDE.md      # 本文档
├── app/src/main/assets/model/
│   ├── yolo11n.onnx             # Disease 分类头（历史文件名保留）
│   ├── labels_onnx.txt          # Disease 标签
│   ├── pest_detector.onnx       # Pest 检测头（可选，缺失则降级）
│   └── labels_pest.txt          # Pest 标签（可选）
```

---

## 九、进阶：提高精度

训练完基础版本后，可以通过以下方法进一步提升：

```bash
# 1. 数据增强（在 plant.yaml 中添加）
flipud: 0.5      # 垂直翻转
hsv_h: 0.015     # 色相变化
hsv_s: 0.7       # 饱和度变化
scale: 0.5       # 缩放

# 2. 更长的训练
epochs=200, patience=30

# 3. 更大的模型（但APK会更大）
model=yolo11s.pt  # 替代 yolo11n.pt

# 4. Mixup + Mosaic（默认已开启）
```

---

## 十、竞赛/答辩证据模板

> “Disease 分类头使用【数据集名称与版本】，经过去重后共【N】张图片、【C】类，按【划分策略】生成独立测试集。模型为【架构】，训练配置和随机种子见【产物路径】。在【设备与系统版本】上，Top-1 Accuracy 为【实测值】、Macro-F1 为【实测值】、p95 推理时延为【实测值】。对应原始日志、混淆矩阵、模型 SHA-256 和测试脚本位于【证据路径】。”

以上占位符必须来自可复现测试产物。当前仓库尚未保存完整训练运行记录和正式独立测试报告，因此不得把教程中的示例数字写入比赛材料。

---

*最后更新：2026-08-05 | AgroVision V2.0*
