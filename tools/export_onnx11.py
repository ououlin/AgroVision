from pathlib import Path
import os; os.chdir(str(Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "model"))
from ultralytics import YOLO
m = YOLO("yolo11n.pt")
m.export(format="onnx", imgsz=640, opset=11, simplify=True)
print("ONNX opset11 done")
