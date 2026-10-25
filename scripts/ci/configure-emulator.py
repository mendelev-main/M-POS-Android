#!/usr/bin/env python3
"""Configure only the disposable CI AVD, before boot, preserving tablet dp geometry."""
import argparse
import os
from pathlib import Path


def configure(path):
    if not path.is_file():
        raise FileNotFoundError(f"CI AVD config not found: {path}")
    values = dict(line.split("=", 1) for line in path.read_text().splitlines() if "=" in line)
    # Pixel C: 2560x1800 at 320dpi -> 1280x900 dp.
    # 1600x1125 at 200dpi keeps that geometry with 61% fewer rendered pixels.
    values.update({"hw.lcd.width": "1600", "hw.lcd.height": "1125", "hw.lcd.density": "200"})
    path.write_text("\n".join(f"{key}={value}" for key, value in values.items()) + "\n")
    print("CI AVD: 1600x1125/200dpi, same 1280x900dp tablet viewport")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--avd", required=True)
    args = parser.parse_args()
    if args.avd != "mpos-ci-api36":
        parser.error("Only the named disposable M-POS CI AVD may be configured")
    folder = Path(os.environ.get("ANDROID_AVD_HOME", str(Path.home() / ".android/avd")))
    configure(folder / f"{args.avd}.avd/config.ini")
