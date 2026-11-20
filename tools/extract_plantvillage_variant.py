from __future__ import annotations

import argparse
from contextlib import nullcontext
import shutil
import zipfile
from pathlib import Path, PurePosixPath
from typing import TextIO


def write_log(log_file: TextIO | None, message: str) -> None:
    print(message, flush=True)
    if log_file is not None:
        log_file.write(message + "\n")
        log_file.flush()


def extract_variant(
    archive: Path,
    output_root: Path,
    variant: str,
    log_file: TextIO | None = None,
) -> tuple[int, int, int]:
    prefix = f"plantvillage dataset/{variant}/"
    entries = 0
    copied = 0
    skipped = 0
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

            if entries % 1000 == 0:
                write_log(log_file, f"entries={entries} copied={copied} skipped={skipped}")

    return entries, copied, skipped


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--variant", default="color")
    parser.add_argument("--log", type=Path)
    args = parser.parse_args()

    args.log.parent.mkdir(parents=True, exist_ok=True) if args.log else None
    with args.log.open("a", encoding="utf-8") if args.log else nullcontext() as log_file:
        entries, copied, skipped = extract_variant(args.archive, args.output, args.variant, log_file)
        write_log(
            log_file,
            f"variant={args.variant} entries={entries} copied={copied} "
            f"skipped={skipped} output={args.output}",
        )


if __name__ == "__main__":
    main()
