#!/usr/bin/env python3
"""Install the bounded GM runtime bridge v2 fragment into df_game_r.js."""

import argparse
import datetime as dt
import pathlib
import re
import shutil
import sys
import tempfile
from typing import Optional


BEGIN_MARKER = "// BEGIN DNF GM RUNTIME BRIDGE V2 (managed)"
END_MARKER = "// END DNF GM RUNTIME BRIDGE V2 (managed)"


def _strip_managed_block(source: str) -> str:
    pattern = re.compile(
        r"\n?" + re.escape(BEGIN_MARKER) + r"\n.*?\n" +
        re.escape(END_MARKER) + r"\n?",
        re.DOTALL,
    )
    matches = list(pattern.finditer(source))
    if len(matches) > 1:
        raise ValueError("multiple managed v2 bridge blocks found")
    return pattern.sub("\n", source)


def _switch_lifecycle(source: str) -> str:
    start_v1 = re.compile(r"(?m)^(\s*)gmRuntimeBridgeStart\(\);(\s*(?://.*)?)$")
    stop_v1 = re.compile(r"(?m)^(\s*)gmRuntimeBridgeStop\(\);(\s*(?://.*)?)$")
    source, start_count = start_v1.subn(r"\1gmRuntimeBridgeV2Start();\2", source)
    source, stop_count = stop_v1.subn(r"\1gmRuntimeBridgeV2Stop();\2", source)

    if start_count == 0 and "gmRuntimeBridgeV2Start();" not in source:
        raise ValueError("active gmRuntimeBridgeStart lifecycle call was not found")
    if stop_count == 0 and "gmRuntimeBridgeV2Stop();" not in source:
        raise ValueError("active gmRuntimeBridgeStop lifecycle call was not found")
    return source


def build_installed_source(source: str, fragment: str) -> str:
    required = (
        "GM_RUNTIME_BRIDGE_V2_PROTOCOL_VERSION = 2",
        "function gmRuntimeBridgeV2Start()",
        "function gmRuntimeBridgeV2Stop()",
        "'change_gold'",
        "'inventory_snapshot'",
    )
    missing = [value for value in required if value not in fragment]
    if missing:
        raise ValueError("v2 fragment is incomplete: " + ", ".join(missing))

    source = _switch_lifecycle(_strip_managed_block(source)).rstrip()
    fragment = fragment.strip()
    return (
        source + "\n\n" + BEGIN_MARKER + "\n" + fragment + "\n" +
        END_MARKER + "\n"
    )


def validate_installed_source(source: str) -> None:
    if source.count(BEGIN_MARKER) != 1 or source.count(END_MARKER) != 1:
        raise ValueError("managed v2 bridge block is missing or duplicated")
    if not re.search(r"(?m)^\s*gmRuntimeBridgeV2Start\(\);", source):
        raise ValueError("v2 start lifecycle call is not active")
    if not re.search(r"(?m)^\s*gmRuntimeBridgeV2Stop\(\);", source):
        raise ValueError("v2 stop lifecycle call is not active")
    if re.search(r"(?m)^\s*gmRuntimeBridgeStart\(\);", source):
        raise ValueError("legacy bridge start lifecycle call is still active")


def _backup_path(target: pathlib.Path, backup_dir: Optional[pathlib.Path]) -> pathlib.Path:
    stamp = dt.datetime.now().strftime("%Y%m%d%H%M%S")
    directory = backup_dir if backup_dir is not None else target.parent
    directory.mkdir(parents=True, exist_ok=True)
    return directory / (target.name + ".pre-gm-runtime-v2." + stamp + ".bak")


def install(target: pathlib.Path, fragment_path: pathlib.Path,
            backup_dir: Optional[pathlib.Path]) -> pathlib.Path:
    source = target.read_text(encoding="utf-8")
    fragment = fragment_path.read_text(encoding="utf-8")
    installed = build_installed_source(source, fragment)
    validate_installed_source(installed)

    backup = _backup_path(target, backup_dir)
    shutil.copy2(str(target), str(backup))
    with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", newline="\n", delete=False,
            dir=str(target.parent), prefix=target.name + ".", suffix=".tmp") as handle:
        handle.write(installed)
        temporary = pathlib.Path(handle.name)
    temporary.replace(target)
    return backup


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, type=pathlib.Path)
    parser.add_argument(
        "--fragment", type=pathlib.Path,
        default=pathlib.Path(__file__).with_name("gm-runtime-bridge-v2.js"),
    )
    parser.add_argument("--backup-dir", type=pathlib.Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    try:
        if args.check:
            validate_installed_source(args.target.read_text(encoding="utf-8"))
            print("GM runtime bridge v2 installation is valid")
            return 0
        backup = install(args.target, args.fragment, args.backup_dir)
        print("Installed GM runtime bridge v2")
        print("Backup: " + str(backup))
        return 0
    except (OSError, ValueError) as error:
        print("Install failed: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
