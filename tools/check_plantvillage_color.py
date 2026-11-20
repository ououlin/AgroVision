from __future__ import annotations

from collections import Counter
from pathlib import Path
import zipfile


ZIP_PATH = Path(r"E:\nong\_dataset_work\archive.zip")
OUTPUT_ROOT = Path(r"E:\nong\AgroVision\competition\02_data\datasets\plantvillage_color_archive")


def main() -> None:
    expected: Counter[str] = Counter()
    prefix = "plantvillage dataset/color/"
    with zipfile.ZipFile(ZIP_PATH) as archive:
        for info in archive.infolist():
            if info.is_dir() or not info.filename.startswith(prefix):
                continue
            class_name = info.filename[len(prefix) :].split("/")[0]
            expected[class_name] += 1

    actual = {
        directory.name: sum(1 for path in directory.iterdir() if path.is_file())
        for directory in OUTPUT_ROOT.iterdir()
        if directory.is_dir()
    } if OUTPUT_ROOT.exists() else {}

    for class_name in sorted(expected):
        expected_count = expected[class_name]
        actual_count = actual.get(class_name, 0)
        status = "OK" if actual_count == expected_count else "MISSING"
        print(f"{status}\t{class_name}\t{actual_count}\t{expected_count}\t{expected_count - actual_count}")

    print(
        f"TOTAL\tactual={sum(actual.values())}\texpected={sum(expected.values())}"
        f"\tmissing={sum(expected.values()) - sum(actual.values())}"
    )


if __name__ == "__main__":
    main()
