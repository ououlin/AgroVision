from __future__ import annotations

import argparse
import shutil
import zipfile
from pathlib import Path, PurePosixPath


def extract_class(archive: Path, output_root: Path, class_name: str) -> tuple[int, int, int]:
    prefix = f"plantvillage dataset/color/{class_name}/"
    copied = 0
    skipped = 0
    entries = 0

    output_root.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(archive) as source:
        for info in source.infolist():
            if info.is_dir() or not info.filename.startswith(prefix):
                continue

            relative = PurePosixPath(info.filename[len(prefix) :])
            if not relative.parts or any(part in ("", ".", "..") for part in relative.parts):
                raise ValueError(f"Unsafe archive path: {info.filename}")

            destination = output_root.joinpath(*relative.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            entries += 1

            if destination.is_file() and destination.stat().st_size == info.file_size:
                skipped += 1
                continue

            with source.open(info, "r") as input_stream, destination.open("wb") as output_stream:
                shutil.copyfileobj(input_stream, output_stream, length=1024 * 1024)
            copied += 1

    return entries, copied, skipped


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--class-name", required=True)
    args = parser.parse_args()

    entries, copied, skipped = extract_class(args.archive, args.output, args.class_name)
    print(
        f"class={args.class_name} entries={entries} copied={copied} skipped={skipped} "
        f"output={args.output}"
    )


if __name__ == "__main__":
    main()
