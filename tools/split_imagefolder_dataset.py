from __future__ import annotations

import argparse
import csv
import os
import random
import shutil
from pathlib import Path


IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def link_or_copy(source: Path, destination: Path) -> str:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and destination.stat().st_size == source.stat().st_size:
        return "exists"
    if destination.exists():
        destination.unlink()
    try:
        os.link(source, destination)
        return "hardlink"
    except OSError:
        shutil.copy2(source, destination)
        return "copy"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--seed", type=int, default=20260809)
    parser.add_argument("--train-ratio", type=float, default=0.70)
    parser.add_argument("--val-ratio", type=float, default=0.15)
    args = parser.parse_args()

    if not args.source.is_dir():
        raise FileNotFoundError(args.source)
    if args.train_ratio <= 0 or args.val_ratio <= 0 or args.train_ratio + args.val_ratio >= 1:
        raise ValueError("Ratios must leave a positive test split.")

    classes = sorted(path for path in args.source.iterdir() if path.is_dir())
    args.output.mkdir(parents=True, exist_ok=True)
    args.manifest.parent.mkdir(parents=True, exist_ok=True)

    rows: list[dict[str, str]] = []
    method_counts: dict[str, int] = {}

    for class_dir in classes:
        images = sorted(
            path for path in class_dir.iterdir()
            if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES
        )
        rng = random.Random(f"{args.seed}:{class_dir.name}")
        rng.shuffle(images)

        train_end = int(len(images) * args.train_ratio)
        val_end = train_end + int(len(images) * args.val_ratio)
        split_map = (
            [("train", path) for path in images[:train_end]]
            + [("val", path) for path in images[train_end:val_end]]
            + [("test", path) for path in images[val_end:]]
        )

        for split, source in split_map:
            destination = args.output / split / class_dir.name / source.name
            method = link_or_copy(source, destination)
            method_counts[method] = method_counts.get(method, 0) + 1
            rows.append({
                "split": split,
                "class_name": class_dir.name,
                "filename": source.name,
                "source_path": str(source),
                "relative_path": str(destination.relative_to(args.output)),
                "link_method": method,
            })

    with args.manifest.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(
            output,
            fieldnames=["split", "class_name", "filename", "source_path", "relative_path", "link_method"],
        )
        writer.writeheader()
        writer.writerows(rows)

    print(f"classes={len(classes)}")
    print(f"rows={len(rows)}")
    for method, count in sorted(method_counts.items()):
        print(f"{method}={count}")
    print(f"output={args.output}")
    print(f"manifest={args.manifest}")


if __name__ == "__main__":
    main()
