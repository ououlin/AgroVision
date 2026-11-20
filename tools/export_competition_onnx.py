from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch
import torch.nn as nn
from torchvision import models


def build_model(num_classes: int) -> nn.Module:
    model = models.mobilenet_v3_small(weights=None)
    in_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(in_features, num_classes)
    return model


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--image-size", type=int, default=160)
    args = parser.parse_args()

    checkpoint = torch.load(args.checkpoint, map_location="cpu")
    classes = checkpoint["classes"]
    model = build_model(len(classes))
    model.load_state_dict(checkpoint["model_state"])
    model.eval()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.randn(1, 3, args.image_size, args.image_size)
    torch.onnx.export(
        model,
        dummy,
        args.output,
        opset_version=11,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes=None,
        do_constant_folding=True,
    )

    import onnxruntime as ort

    session = ort.InferenceSession(str(args.output))
    output = session.run(None, {"input": dummy.numpy()})[0]
    meta = {
        "checkpoint": str(args.checkpoint),
        "onnx": str(args.output),
        "class_count": len(classes),
        "input_shape": list(dummy.shape),
        "output_shape": list(output.shape),
        "classes": classes,
    }
    args.output.with_suffix(".onnx.meta.json").write_text(
        json.dumps(meta, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    print(json.dumps(meta, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
