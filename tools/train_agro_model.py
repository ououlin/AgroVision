import os, sys

# ============================================================
# AgroVision AI — 农业病虫害 YOLO 模型训练脚本
# 数据集：PlantVillage (38类植物病害)
# 输出：yolo11n_agro.onnx → 替换 assets/model/yolo11n.onnx
# ============================================================

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(PROJECT_DIR, "plant_dataset")

# ========== Step 1: 下载 PlantVillage 数据集 ==========
def download_plantvillage():
    """从 kaggle 或手动链接下载 PlantVillage 数据集"""
    import subprocess
    
    print("=" * 60)
    print("Step 1: 下载 PlantVillage 数据集")
    print("=" * 60)
    
    # 方式1：TensorFlow Datasets（需安装 tensorflow_datasets）
    print("尝试 TensorFlow Datasets 下载...")
    try:
        import tensorflow_datasets as tfds
        ds = tfds.load('plant_village', split='train', data_dir=DATA_DIR, download=True)
        print("✅ TFDS 下载完成")
        return
    except ImportError:
        print("tensorflow_datasets 未安装，跳过...")
    except Exception as e:
        print(f"TFDS 下载失败: {e}")
    
    # 方式2：GitHub 仓库
    print("\n尝试 GitHub 下载...")
    try:
        import requests, zipfile, io
        url = "https://github.com/spMohanty/PlantVillage-Dataset/archive/refs/heads/master.zip"
        print(f"下载: {url}")
        resp = requests.get(url, stream=True, timeout=600)
        z = zipfile.ZipFile(io.BytesIO(resp.content))
        z.extractall(DATA_DIR)
        print("✅ GitHub 下载完成")
        return
    except Exception as e:
        print(f"GitHub 下载失败: {e}")
    
    print("\n" + "=" * 60)
    print("⚠️  自动下载失败，请手动下载 PlantVillage 数据集")
    print("=" * 60)
    print("\n方式一：阿里云天池（国内推荐，速度快）")
    print("  https://tianchi.aliyun.com/dataset/160100")
    print("\n方式二：百度网盘搜索 'PlantVillage 数据集'")
    print("\n方式三：GitHub")
    print("  https://github.com/spMohanty/PlantVillage-Dataset")
    print("  下载 master.zip → 解压到:", DATA_DIR)
    print("\n方式四：Kaggle（需登录）")
    print("  https://www.kaggle.com/datasets/emmarex/plantdisease")
    print("\n解压后确保目录结构：")
    print(f"  {DATA_DIR}/PlantVillage/ 或 {DATA_DIR}/raw/")
    print("  每个子目录为一个类别，内含 .jpg 图片")
    sys.exit(1)

# ========== Step 2: 生成 YOLO 格式配置文件 ==========
def create_yaml():
    """生成 plant.yaml 配置文件"""
    import glob
    from pathlib import Path
    
    print("\n" + "=" * 60)
    print("Step 2: 生成 YOLO 配置文件")
    print("=" * 60)
    
    # 扫描 PlantVillage 目录获取类别
    plantvillage_dir = os.path.join(DATA_DIR, "PlantVillage")
    if not os.path.exists(plantvillage_dir):
        # 可能是 plantdisease 或其他命名
        for d in os.listdir(DATA_DIR):
            if os.path.isdir(os.path.join(DATA_DIR, d)):
                plantvillage_dir = os.path.join(DATA_DIR, d)
                break
    
    print(f"数据集路径: {plantvillage_dir}")
    
    classes = sorted([
        d for d in os.listdir(plantvillage_dir)
        if os.path.isdir(os.path.join(plantvillage_dir, d))
    ])
    
    print(f"检测到 {len(classes)} 个类别:")
    for i, c in enumerate(classes):
        print(f"  {i}: {c}")
    
    # 写入 plant.yaml
    yaml_content = f"""# AgroVision Plant Disease Detection Configuration
# 自动生成 — 请根据实际路径调整

path: {plantvillage_dir.replace(chr(92), '/')}
train: ./
val: ./

# 类别数
nc: {len(classes)}

# 类别名称
names:
"""
    for i, c in enumerate(classes):
        yaml_content += f"  {i}: {c}\n"
    
    yaml_path = os.path.join(PROJECT_DIR, "plant.yaml")
    with open(yaml_path, "w", encoding="utf-8") as f:
        f.write(yaml_content)
    
    print(f"\n✅ 配置文件已生成: {yaml_path}")
    
    # 生成 labels.txt
    labels_txt = "\n".join(classes)
    labels_path = os.path.join(
        PROJECT_DIR, "..", "app", "src", "main", "assets", "model", "labels.txt"
    )
    os.makedirs(os.path.dirname(labels_path), exist_ok=True)
    with open(labels_path, "w", encoding="utf-8") as f:
        f.write(labels_txt)
    print(f"✅ labels.txt 已更新 ({len(classes)} 类)")
    
    return yaml_path, len(classes)

# ========== Step 3: 训练模型 ==========
def train_model(yaml_path, num_classes):
    from ultralytics import YOLO
    
    print("\n" + "=" * 60)
    print("Step 3: 训练 YOLO 模型")
    print(f"类别数: {num_classes}")
    print("=" * 60)
    
    # 加载 YOLO11n 预训练权重
    model = YOLO("yolo11n.pt")
    print("✅ 加载 yolo11n.pt 预训练权重")
    
    # 训练
    results = model.train(
        data=yaml_path,
        epochs=100,            # 训练轮数
        imgsz=640,             # 输入尺寸
        batch=16,              # 批量大小（根据显存调整）
        lr0=0.01,              # 初始学习率
        lrf=0.01,              # 最终学习率
        optimizer='AdamW',     # 优化器
        patience=20,           # 早停
        device='cpu',          # 用 CPU（有 GPU 改为 0）
        workers=4,             # 数据加载线程
        project=PROJECT_DIR,
        name='agrovision_train',
        exist_ok=True,
        verbose=True
    )
    
    print(f"\n✅ 训练完成!")
    print(f"最佳模型: {results.save_dir}")
    
    return os.path.join(results.save_dir, "weights", "best.pt")

# ========== Step 4: 导出 ONNX ==========
def export_onnx(best_pt):
    from ultralytics import YOLO
    
    print("\n" + "=" * 60)
    print("Step 4: 导出 ONNX 模型")
    print("=" * 60)
    
    model = YOLO(best_pt)
    onnx_path = model.export(format="onnx", imgsz=640, simplify=True, opset=11)
    
    print(f"✅ ONNX 模型已导出: {onnx_path}")
    
    # 复制到 App assets
    import shutil
    dest = os.path.join(
        PROJECT_DIR, "..", "app", "src", "main", "assets", "model", "yolo11n.onnx"
    )
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    shutil.copy(onnx_path, dest)
    print(f"✅ 已复制到 App: {dest}")
    
    return dest

# ========== Step 5: 验证 ==========
def validate(best_pt, yaml_path):
    from ultralytics import YOLO
    
    print("\n" + "=" * 60)
    print("Step 5: 验证模型精度")
    print("=" * 60)
    
    model = YOLO(best_pt)
    metrics = model.val(data=yaml_path)
    
    print(f"\n📊 验证结果:")
    print(f"  mAP@0.5 : {metrics.box.map50:.3f}")
    print(f"  mAP@0.5-0.95: {metrics.box.map:.3f}")
    print(f"  Precision: {metrics.box.p[0]:.3f}")
    print(f"  Recall: {metrics.box.r[0]:.3f}")
    
    return metrics

# ========== Main ==========
if __name__ == "__main__":
    print("=" * 60)
    print("🌱 AgroVision AI — 农业病虫害模型训练流水线")
    print("=" * 60)
    
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    
    # 如果没有数据集，自动下载
    if "--download" in sys.argv or not os.path.exists(os.path.join(PROJECT_DIR, "plant_dataset")):
        download_plantvillage()
    
    yaml_path, num_classes = create_yaml()
    best_pt = train_model(yaml_path, num_classes)
    
    if "--skip-export" not in sys.argv:
        export_onnx(best_pt)
    
    if "--skip-val" not in sys.argv:
        validate(best_pt, yaml_path)
    
    print("\n" + "=" * 60)
    print("🎉 全部完成！")
    print("=" * 60)
    print(f"ONNX 模型已更新到: app/src/main/assets/model/yolo11n.onnx")
    print(f"labels.txt 已更新（{num_classes} 类）")
    print("\n下一步: 在 Android Studio 中 Build → Run")
