import os
import sys
import json
import shutil
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import torch
import torch.nn as nn
from torchvision import models

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
DATASET_DIR = r"D:\agrovision_dataset"
IMGSZ = 224
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]

# 获取类别
train_dir = Path(DATASET_DIR) / "train"
classes = sorted([d.name for d in train_dir.iterdir() if d.is_dir()])
num_classes = len(classes)
print(f"类别数: {num_classes}")
for i, c in enumerate(classes):
    print(f"  {i}: {c}")

# 加载模型
model_path = os.path.join(PROJECT_DIR, "agrovision_cls", "weights", "best.pt")
print(f"\n加载权重: {model_path}")

model = models.mobilenet_v3_small(weights=None)
model.classifier[3] = nn.Linear(model.classifier[3].in_features, num_classes)
model.load_state_dict(torch.load(model_path, map_location='cpu'))
model.eval()
print("模型加载成功")

# 导出 ONNX
onnx_path = os.path.join(PROJECT_DIR, "agrovision_cls", "yolo11n.onnx")
dummy_input = torch.randn(1, 3, IMGSZ, IMGSZ)

torch.onnx.export(
    model,
    dummy_input,
    onnx_path,
    opset_version=11,
    input_names=['input'],
    output_names=['output'],
    dynamic_axes=None,
    do_constant_folding=True,
)

size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
print(f"\nONNX 已导出: {onnx_path} ({size_mb:.1f} MB)")

# 验证 ONNX
import onnxruntime as ort
sess = ort.InferenceSession(onnx_path)
test_output = sess.run(None, {'input': dummy_input.numpy()})[0]
print(f"输入形状: [1, 3, {IMGSZ}, {IMGSZ}]")
print(f"输出形状: {test_output.shape}")
print(f"输出示例(softmax前): {test_output[0]}")

# 复制到 App assets
dest = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "model", "yolo11n.onnx")
os.makedirs(os.path.dirname(dest), exist_ok=True)
shutil.copy(onnx_path, dest)
print(f"\n已复制到 App: {dest}")

# 更新 labels.txt
labels_path = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "model", "labels.txt")
with open(labels_path, "w", encoding="utf-8") as f:
    f.write("\n".join(classes))
print(f"labels.txt 已更新 ({num_classes} 类)")

# 保存元数据
meta = {
    "model_type": "classification",
    "architecture": "MobileNetV3-Small",
    "input_size": IMGSZ,
    "num_classes": num_classes,
    "classes": classes,
    "mean": IMAGENET_MEAN,
    "std": IMAGENET_STD,
}
meta_path = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "model", "model_meta.json")
with open(meta_path, "w", encoding="utf-8") as f:
    json.dump(meta, f, indent=2, ensure_ascii=False)
print(f"model_meta.json 已保存")

print("\n完成! 下一步: 修改 Android 推理代码适配分类模型")
