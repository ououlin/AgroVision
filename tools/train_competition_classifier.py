from __future__ import annotations

import argparse
import csv
import json
import random
import time
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision import datasets, models, transforms


IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]


def set_seed(seed: int) -> None:
    random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def json_safe_args(args) -> dict:
    result = {}
    for key, value in vars(args).items():
        result[key] = str(value) if isinstance(value, Path) else value
    return result


def accuracy_topk(outputs: torch.Tensor, labels: torch.Tensor, topk: tuple[int, ...]) -> list[float]:
    max_k = max(topk)
    _, predictions = outputs.topk(max_k, dim=1)
    predictions = predictions.t()
    correct = predictions.eq(labels.reshape(1, -1).expand_as(predictions))
    scores: list[float] = []
    for k in topk:
        scores.append(correct[:k].reshape(-1).float().sum().item() / labels.size(0))
    return scores


def build_dataloaders(data_dir: Path, batch_size: int, workers: int, image_size: int):
    train_transform = transforms.Compose([
        transforms.RandomResizedCrop(image_size, scale=(0.75, 1.0)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(15),
        transforms.ColorJitter(brightness=0.15, contrast=0.15, saturation=0.15),
        transforms.ToTensor(),
        transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
    ])
    eval_transform = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(image_size),
        transforms.ToTensor(),
        transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD),
    ])

    train_ds = datasets.ImageFolder(str(data_dir / "train"), transform=train_transform)
    val_ds = datasets.ImageFolder(str(data_dir / "val"), transform=eval_transform)
    test_ds = datasets.ImageFolder(str(data_dir / "test"), transform=eval_transform)

    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True, num_workers=workers)
    val_loader = DataLoader(val_ds, batch_size=batch_size, shuffle=False, num_workers=workers)
    test_loader = DataLoader(test_ds, batch_size=batch_size, shuffle=False, num_workers=workers)
    return train_loader, val_loader, test_loader, train_ds.classes


def build_model(num_classes: int, pretrained: bool) -> nn.Module:
    weights = models.MobileNet_V3_Small_Weights.DEFAULT if pretrained else None
    model = models.mobilenet_v3_small(weights=weights)
    in_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(in_features, num_classes)
    return model


def freeze_backbone(model: nn.Module) -> nn.Module:
    for parameter in model.parameters():
        parameter.requires_grad = False
    for parameter in model.classifier[3].parameters():
        parameter.requires_grad = True
    return model


def run_epoch(model: nn.Module, loader: DataLoader, criterion, optimizer, device: torch.device, train: bool):
    model.train(train)
    total = 0
    loss_sum = 0.0
    top1_sum = 0.0
    top3_sum = 0.0

    context = torch.enable_grad() if train else torch.no_grad()
    with context:
        for images, labels in loader:
            images = images.to(device)
            labels = labels.to(device)

            if train:
                optimizer.zero_grad(set_to_none=True)

            outputs = model(images)
            loss = criterion(outputs, labels)

            if train:
                loss.backward()
                optimizer.step()

            batch = labels.size(0)
            top1, top3 = accuracy_topk(outputs, labels, (1, min(3, outputs.size(1))))
            total += batch
            loss_sum += loss.item() * batch
            top1_sum += top1 * batch
            top3_sum += top3 * batch

    return {
        "loss": loss_sum / total,
        "top1": top1_sum / total,
        "top3": top3_sum / total,
    }


def save_checkpoint(path: Path, model: nn.Module, classes: list[str], epoch: int, best_val_top1: float, args) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    torch.save({
        "epoch": epoch,
        "model_state": model.state_dict(),
        "classes": classes,
        "best_val_top1": best_val_top1,
        "args": json_safe_args(args),
    }, path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--workers", type=int, default=0)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--weight-decay", type=float, default=1e-4)
    parser.add_argument("--image-size", type=int, default=224)
    parser.add_argument("--seed", type=int, default=20260809)
    parser.add_argument("--pretrained", action="store_true")
    parser.add_argument("--freeze-backbone", action="store_true")
    parser.add_argument("--resume-checkpoint", type=Path)
    parser.add_argument("--early-stop-patience", type=int, default=0)
    parser.add_argument("--min-delta", type=float, default=0.0)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    set_seed(args.seed)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    metrics_path = args.output_dir / "metrics.csv"

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    train_loader, val_loader, test_loader, classes = build_dataloaders(
        args.data_dir,
        args.batch_size,
        args.workers,
        args.image_size,
    )
    model = build_model(len(classes), args.pretrained).to(device)
    start_epoch = 1
    best_val_top1 = 0.0
    if args.resume_checkpoint:
        checkpoint = torch.load(args.resume_checkpoint, map_location=device)
        model.load_state_dict(checkpoint["model_state"])
        start_epoch = int(checkpoint.get("epoch", 0)) + 1
        best_val_top1 = float(checkpoint.get("best_val_top1", 0.0))
        print(f"resumed_from={args.resume_checkpoint} start_epoch={start_epoch}", flush=True)
    if args.freeze_backbone:
        model = freeze_backbone(model)

    (args.output_dir / "labels.txt").write_text("\n".join(classes) + "\n", encoding="utf-8")
    (args.output_dir / "run_config.json").write_text(
        json.dumps({
            "data_dir": str(args.data_dir),
            "classes": classes,
            "class_count": len(classes),
            "train_count": len(train_loader.dataset),
            "val_count": len(val_loader.dataset),
            "test_count": len(test_loader.dataset),
            "device": str(device),
            "args": json_safe_args(args),
        }, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad), lr=args.lr, weight_decay=args.weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    if args.dry_run:
        images, labels = next(iter(train_loader))
        with torch.no_grad():
            outputs = model(images.to(device))
        print(
            f"dry_run_ok batch={tuple(images.shape)} labels={tuple(labels.shape)} "
            f"outputs={tuple(outputs.shape)}",
            flush=True,
        )
        return

    if args.resume_checkpoint and not (args.output_dir / "best.pt").exists():
        save_checkpoint(args.output_dir / "best.pt", model, classes, start_epoch - 1, best_val_top1, args)

    write_header = not metrics_path.exists()
    with metrics_path.open("a", newline="", encoding="utf-8") as metrics_file:
        writer = csv.DictWriter(
            metrics_file,
            fieldnames=[
                "epoch",
                "train_loss",
                "train_top1",
                "train_top3",
                "val_loss",
                "val_top1",
                "val_top3",
                "epoch_seconds",
            ],
        )
        if write_header:
            writer.writeheader()

        print(f"device={device} classes={len(classes)} train={len(train_loader.dataset)} val={len(val_loader.dataset)} test={len(test_loader.dataset)}", flush=True)
        stale_epochs = 0
        for epoch in range(start_epoch, args.epochs + 1):
            start = time.time()
            train_metrics = run_epoch(model, train_loader, criterion, optimizer, device, train=True)
            val_metrics = run_epoch(model, val_loader, criterion, optimizer, device, train=False)
            scheduler.step()
            elapsed = time.time() - start

            row = {
                "epoch": epoch,
                "train_loss": f"{train_metrics['loss']:.6f}",
                "train_top1": f"{train_metrics['top1']:.6f}",
                "train_top3": f"{train_metrics['top3']:.6f}",
                "val_loss": f"{val_metrics['loss']:.6f}",
                "val_top1": f"{val_metrics['top1']:.6f}",
                "val_top3": f"{val_metrics['top3']:.6f}",
                "epoch_seconds": f"{elapsed:.2f}",
            }
            writer.writerow(row)
            metrics_file.flush()
            print(json.dumps(row, ensure_ascii=False), flush=True)

            save_checkpoint(args.output_dir / "last.pt", model, classes, epoch, best_val_top1, args)
            if val_metrics["top1"] > best_val_top1 + args.min_delta:
                best_val_top1 = val_metrics["top1"]
                stale_epochs = 0
                save_checkpoint(args.output_dir / "best.pt", model, classes, epoch, best_val_top1, args)
            else:
                stale_epochs += 1

            if args.early_stop_patience > 0 and stale_epochs >= args.early_stop_patience:
                print(
                    f"early_stop epoch={epoch} stale_epochs={stale_epochs} "
                    f"best_val_top1={best_val_top1:.6f}",
                    flush=True,
                )
                break

        test_metrics = run_epoch(model, test_loader, criterion, optimizer, device, train=False)
        (args.output_dir / "test_metrics.json").write_text(
            json.dumps(test_metrics, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        print(json.dumps({"test": test_metrics}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
