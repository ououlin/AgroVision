import os
import sys
import json
import shutil
import time
from pathlib import Path

# 修复 Windows 控制台编码
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')

import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision import datasets, transforms, models

# ============================================================
# AgroVision AI — 农业病虫害分类模型训练
# 模型: MobileNetV3-Small (轻量, 适合移动端)
# 数据集: PlantVillage 子集 (8类, 7722张)
# 输出: yolo11n.onnx (分类模式, 输入 224x224, 输出 [1,8])
# ============================================================

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
DATASET_DIR = os.environ.get(
    "AGROVISION_DATASET",
    str(Path.home() / "agrovision_dataset"),
)

# 模型参数
IMGSZ = 224
BATCH = 64
EPOCHS = 50
LR = 0.001
DEVICE = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
NUM_WORKERS = 4

# ImageNet 归一化 (MobileNetV3 预训练使用)
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]


def get_classes():
    """获取类别列表"""
    train_dir = Path(DATASET_DIR) / "train"
    classes = sorted([d.name for d in train_dir.iterdir() if d.is_dir()])
    return classes


def get_dataloaders():
    """创建数据加载器"""
    print("加载数据集...")

    train_transform = transforms.Compose([
        transforms.RandomResizedCrop(IMGSZ, scale=(0.8, 1.0)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(15),
        transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2),
        transforms.ToTensor(),
        transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
    ])

    val_transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(IMGSZ),
        transforms.ToTensor(),
        transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
    ])

    train_ds = datasets.ImageFolder(
        str(Path(DATASET_DIR) / "train"), transform=train_transform
    )
    val_ds = datasets.ImageFolder(
        str(Path(DATASET_DIR) / "val"), transform=val_transform
    )

    train_loader = DataLoader(
        train_ds, batch_size=BATCH, shuffle=True, num_workers=NUM_WORKERS, pin_memory=True
    )
    val_loader = DataLoader(
        val_ds, batch_size=BATCH, shuffle=False, num_workers=NUM_WORKERS, pin_memory=True
    )

    print(f"  训练集: {len(train_ds)} 张")
    print(f"  验证集: {len(val_ds)} 张")
    print(f"  类别数: {len(train_ds.classes)}")
    for i, c in enumerate(train_ds.classes):
        print(f"    {i}: {c}")

    return train_loader, val_loader, train_ds.classes


def build_model(num_classes):
    """构建 MobileNetV3-Small 模型, 加载预训练权重"""
    print("\n构建模型: MobileNetV3-Small")
    # weights='DEFAULT' 会从 download.pytorch.org 下载预训练权重
    model = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.DEFAULT)

    # 替换分类头
    in_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(in_features, num_classes)
    model = model.to(DEVICE)

    total_params = sum(p.numel() for p in model.parameters())
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"  总参数: {total_params / 1e6:.1f}M")
    print(f"  可训练: {trainable_params / 1e6:.1f}M")
    print(f"  设备: {DEVICE}")

    return model


def train_one_epoch(model, loader, criterion, optimizer):
    """训练一个 epoch"""
    model.train()
    running_loss = 0.0
    correct = 0
    total = 0

    for images, labels in loader:
        images, labels = images.to(DEVICE), labels.to(DEVICE)

        optimizer.zero_grad()
        outputs = model(images)
        loss = criterion(outputs, labels)
        loss.backward()
        optimizer.step()

        running_loss += loss.item() * images.size(0)
        _, predicted = outputs.max(1)
        total += labels.size(0)
        correct += predicted.eq(labels).sum().item()

    avg_loss = running_loss / total
    acc = correct / total
    return avg_loss, acc


def validate(model, loader, criterion):
    """验证"""
    model.eval()
    running_loss = 0.0
    correct = 0
    total = 0

    with torch.no_grad():
        for images, labels in loader:
            images, labels = images.to(DEVICE), labels.to(DEVICE)
            outputs = model(images)
            loss = criterion(outputs, labels)

            running_loss += loss.item() * images.size(0)
            _, predicted = outputs.max(1)
            total += labels.size(0)
            correct += predicted.eq(labels).sum().item()

    avg_loss = running_loss / total
    acc = correct / total
    return avg_loss, acc


def train_model():
    """完整训练流程"""
    train_loader, val_loader, classes = get_dataloaders()
    num_classes = len(classes)

    model = build_model(num_classes)

    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=LR, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS)

    best_acc = 0.0
    best_model_path = os.path.join(PROJECT_DIR, "agrovision_cls", "weights", "best.pt")
    os.makedirs(os.path.dirname(best_model_path), exist_ok=True)

    print(f"\n{'='*60}")
    print(f"开始训练: {EPOCHS} epochs, batch={BATCH}, lr={LR}")
    print(f"{'='*60}")
    print(f"{'Epoch':>6} {'Loss':>8} {'Acc':>8} {'ValLoss':>8} {'ValAcc':>8} {'Time':>7}")
    print(f"{'-'*60}")

    for epoch in range(1, EPOCHS + 1):
        start = time.time()

        train_loss, train_acc = train_one_epoch(model, train_loader, criterion, optimizer)
        val_loss, val_acc = validate(model, val_loader, criterion)
        scheduler.step()

        elapsed = time.time() - start
        marker = ""

        if val_acc > best_acc:
            best_acc = val_acc
            torch.save(model.state_dict(), best_model_path)
            marker = " *"

        print(f"{epoch:>6} {train_loss:>8.4f} {train_acc:>8.4f} {val_loss:>8.4f} {val_acc:>8.4f} {elapsed:>6.1f}s{marker}")

    print(f"\n训练完成! 最佳验证准确率: {best_acc:.4f}")
    print(f"最佳模型: {best_model_path}")

    return best_model_path, classes


def export_onnx(model_path, classes):
    """导出 ONNX 并复制到 App assets"""
    print(f"\n{'='*60}")
    print("导出 ONNX 模型")
    print(f"{'='*60}")

    num_classes = len(classes)
    model = models.mobilenet_v3_small(weights=None)
    model.classifier[3] = nn.Linear(model.classifier[3].in_features, num_classes)
    model.load_state_dict(torch.load(model_path, map_location='cpu'))
    model.eval()

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
        dynamic_axes=None,  # 固定 batch=1
        do_constant_folding=True,
    )

    print(f"ONNX 已导出: {onnx_path}")
    size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
    print(f"模型大小: {size_mb:.1f} MB")

    # 验证 ONNX 输出
    import onnxruntime as ort
    sess = ort.InferenceSession(onnx_path)
    test_input = dummy_input.numpy()
    test_output = sess.run(None, {'input': test_input})[0]
    print(f"输入形状: {test_input.shape}")
    print(f"输出形状: {test_output.shape}")
    print(f"输出示例: {test_output[0]}")

    # 复制到 App assets
    dest = os.path.join(
        PROJECT_DIR, "app", "src", "main", "assets", "model", "yolo11n.onnx"
    )
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    shutil.copy(onnx_path, dest)
    print(f"已复制到 App: {dest}")

    # 更新 labels.txt
    labels_path = os.path.join(
        PROJECT_DIR, "app", "src", "main", "assets", "model", "labels.txt"
    )
    with open(labels_path, "w", encoding="utf-8") as f:
        f.write("\n".join(classes))
    print(f"labels.txt 已更新 ({len(classes)} 类)")

    # 保存类别映射 JSON (供 Android 端使用)
    meta = {
        "model_type": "classification",
        "architecture": "MobileNetV3-Small",
        "input_size": IMGSZ,
        "num_classes": num_classes,
        "classes": classes,
        "mean": IMAGENET_MEAN,
        "std": IMAGENET_STD,
    }
    meta_path = os.path.join(
        PROJECT_DIR, "app", "src", "main", "assets", "model", "model_meta.json"
    )
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)
    print(f"model_meta.json 已保存")

    return dest


if __name__ == "__main__":
    print("=" * 60)
    print("AgroVision AI — PlantVillage 分类模型训练 (MobileNetV3)")
    print("=" * 60)

    os.chdir(PROJECT_DIR)

    best_model_path, classes = train_model()
    export_onnx(best_model_path, classes)

    print("\n" + "=" * 60)
    print("全部完成!")
    print("=" * 60)
    print("ONNX 模型: app/src/main/assets/model/yolo11n.onnx")
    print(f"标签文件:  app/src/main/assets/model/labels.txt ({len(classes)} 类)")
    print("元数据:    app/src/main/assets/model/model_meta.json")
    print("\n下一步: 修改 Android 推理代码适配分类模型, 然后 Build → Run")
