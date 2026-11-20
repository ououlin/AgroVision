import os, subprocess, sys
os.chdir(r"C:\Users\24875\Desktop\农智眼\AgroVision\app\src\main\assets\model")

print("Step 1: Export ONNX...")
from ultralytics import YOLO
m = YOLO("yolo11n.pt")
onnx_file = m.export(format="onnx", imgsz=640, simplify=True, opset=12)
print(f"ONNX: {onnx_file}")

print("Step 2: onnx2tf...")
# Use onnx2tf CLI - it's faster and handles errors better
result = subprocess.run([
    sys.executable, "-m", "onnx2tf",
    "-i", "yolo11n.onnx",
    "-o", "yolo11n_saved_model",
    "-osd",
    "-oiqt",
    "-b", "1"
], capture_output=True, text=True, timeout=600)

print(result.stdout[-500:] if len(result.stdout) > 500 else result.stdout)
if result.returncode != 0:
    print("STDERR:", result.stderr[-500:])
    # Try without optimizations
    print("\nRetry without optimizations...")
    result2 = subprocess.run([
        sys.executable, "-m", "onnx2tf",
        "-i", "yolo11n.onnx",
        "-o", "yolo11n_saved_model2",
        "--non_verbose"
    ], capture_output=True, text=True, timeout=600)
    if result2.returncode == 0:
        print("Simplified onnx2tf OK")
        
print("Step 3: Convert SavedModel to TFLite...")
try:
    import tensorflow as tf
    converter = tf.lite.TFLiteConverter.from_saved_model("yolo11n_saved_model")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]
    tflite_model = converter.convert()
    with open("yolo11n.tflite", "wb") as f:
        f.write(tflite_model)
    size = os.path.getsize("yolo11n.tflite")
    print(f"DONE! yolo11n.tflite: {size:,} bytes ({size/1024/1024:.1f} MB)")
except Exception as e:
    print(f"TFLite conversion failed: {e}")
    
# List final files
for f in sorted(os.listdir(".")):
    if os.path.isfile(f) and f.endswith((".tflite", ".onnx")):
        print(f"  {f}: {os.path.getsize(f):,} bytes")
