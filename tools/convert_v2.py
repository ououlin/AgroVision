from pathlib import Path
import os, sys, traceback
os.chdir(str(Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "model"))

log = open("convert_log.txt", "w", encoding="utf-8")
def log_print(*args):
    msg = " ".join(str(a) for a in args)
    print(msg)
    log.write(msg + "\n")
    log.flush()

log_print("Starting...")
sys.stdout.flush()

try:
    from ultralytics import YOLO
    log_print("Loading yolo11n.pt...")
    m = YOLO("yolo11n.pt")
    log_print("Model loaded. Exporting TFLite...")
    result = m.export(format="tflite", imgsz=640, verbose=True)
    log_print(f"Done! Result: {result}")
    
    for f in os.listdir("."):
        if "tflite" in f:
            s = os.path.getsize(f)
            log_print(f"  {f}: {s:,} bytes ({s/1024/1024:.1f} MB)")
except Exception as e:
    log_print(f"ERROR: {e}")
    traceback.print_exc(file=log)
    traceback.print_exc()

log.close()
print("See convert_log.txt")
