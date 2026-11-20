import os; os.chdir(r"C:\Users\24875\Desktop\农智眼\AgroVision\app\src\main\assets\model")
from ultralytics import YOLO
m = YOLO("yolo11n.pt")
m.export(format="onnx", imgsz=640, opset=11, simplify=True)
print("ONNX opset11 done")
