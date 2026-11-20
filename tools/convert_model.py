from pathlib import Path
import os, sys
os.chdir(str(Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "model"))
print("Step 1: Export ONNX from PT...")
sys.stdout.flush()

from ultralytics import YOLO
model = YOLO("yolo11n.pt")
onnx_path = model.export(format="onnx", imgsz=640, simplify=True)
print(f"ONNX saved: {onnx_path}")

# Step 2: ONNX -> TFLite using tf.lite
print("Step 2: Convert ONNX to TFLite...")
sys.stdout.flush()

try:
    import onnx
    from onnx_tf.backend import prepare
    onnx_model = onnx.load(onnx_path)
    tf_rep = prepare(onnx_model)
    tf_rep.export_graph("yolo11n_saved_model")
    print("SavedModel exported")
    
    import tensorflow as tf
    converter = tf.lite.TFLiteConverter.from_saved_model("yolo11n_saved_model")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    
    with open("yolo11n.tflite", "wb") as f:
        f.write(tflite_model)
    
    size = os.path.getsize("yolo11n.tflite")
    print(f"✅ TFLite saved: {size} bytes ({size/1024/1024:.1f} MB)")
except Exception as e1:
    print(f"onnx-tf method failed: {e1}")
    print("Trying direct tf.lite approach...")
    
    # Alternative: use the PT model weights directly
    # This is a simplified approach
    try:
        import tensorflow as tf
        import numpy as np
        
        # Create a minimal TFLite placeholder (640x640x3 input, 84x8400 output)
        print("Creating placeholder TFLite model...")
        
        # Use the onnx model to get input/output shapes
        import onnx
        onnx_m = onnx.load(onnx_path)
        for inp in onnx_m.graph.input:
            print(f"Input: {inp.name} shape={[d.dim_value for d in inp.type.tensor_type.shape.dim]}")
        for out in onnx_m.graph.output:
            print(f"Output: {out.name} shape={[d.dim_value for d in out.type.tensor_type.shape.dim]}")
        
        print("Cannot auto-convert. Please install onnx-tf: pip install onnx-tf")
        print("Or manually convert using: https://colab.research.google.com")
    except Exception as e2:
        print(f"Also failed: {e2}")
