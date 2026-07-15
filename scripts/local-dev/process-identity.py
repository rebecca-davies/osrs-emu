#!/usr/bin/env python3
"""Spawn, identify, and stop launcher-owned Linux processes without PID-reuse races."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import select
import signal
import subprocess
import sys
import tempfile
import time


def read_pid(path: Path) -> int:
    value = path.read_text(encoding="utf-8").strip()
    pid = int(value)
    if pid <= 0:
        raise ValueError("PID must be positive")
    return pid


def process_start_time(pid: int) -> int:
    stat = Path(f"/proc/{pid}/stat").read_text(encoding="utf-8")
    command_end = stat.rfind(")")
    if command_end < 0:
        raise ValueError(f"invalid /proc/{pid}/stat")
    fields_after_command = stat[command_end + 2 :].split()
    return int(fields_after_command[19])


def boot_id() -> str:
    return Path("/proc/sys/kernel/random/boot_id").read_text(encoding="utf-8").strip()


def atomic_write(path: Path, value: str) -> None:
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=path.parent,
        prefix=f".{path.name}.",
        delete=False,
    ) as output:
        temporary = Path(output.name)
        output.write(value)
        output.flush()
        os.fsync(output.fileno())
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def write_identity(pid: int, marker: str, pid_file: Path, identity_file: Path) -> None:
    identity = {
        "boot_id": boot_id(),
        "marker": marker,
        "pid": pid,
        "start_time": process_start_time(pid),
    }
    atomic_write(identity_file, json.dumps(identity, sort_keys=True) + "\n")
    atomic_write(pid_file, f"{pid}\n")


def read_identity(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("process identity must be an object")
    return value


def identity_matches(pid: int, marker: str, identity_file: Path) -> bool:
    identity = read_identity(identity_file)
    return (
        identity.get("pid") == pid
        and identity.get("marker") == marker
        and identity.get("boot_id") == boot_id()
        and identity.get("start_time") == process_start_time(pid)
    )


def command_matches(pid: int, marker: str) -> bool:
    command = Path(f"/proc/{pid}/cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")
    return marker in command


def open_verified_pidfd(pid: int, marker: str, identity_file: Path) -> int | None:
    try:
        pidfd = os.pidfd_open(pid)
    except ProcessLookupError:
        return None
    try:
        if not identity_matches(pid, marker, identity_file):
            raise ValueError("recorded process identity does not match")
    except BaseException:
        os.close(pidfd)
        raise
    return pidfd


def spawn_process(args: argparse.Namespace) -> int:
    command = args.command
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        raise ValueError("spawn requires a command")

    output_path = Path(args.output)
    output_path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with output_path.open("wb", buffering=0) as output:
        process = subprocess.Popen(
            command,
            stdin=subprocess.DEVNULL,
            stdout=output,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
    try:
        pidfd = os.pidfd_open(process.pid)
        try:
            write_identity(
                process.pid,
                args.marker,
                Path(args.pid_file),
                Path(args.identity_file),
            )
        finally:
            os.close(pidfd)
    except BaseException:
        process.terminate()
        try:
            process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            process.kill()
        raise
    return 0


def process_matches(args: argparse.Namespace) -> int:
    try:
        pid = read_pid(Path(args.pid_file))
        pidfd = open_verified_pidfd(pid, args.marker, Path(args.identity_file))
        if pidfd is None:
            return 1
        try:
            return 0 if command_matches(pid, args.marker) else 1
        finally:
            os.close(pidfd)
    except (FileNotFoundError, json.JSONDecodeError, OSError, ValueError):
        return 1


def stop_recorded_processes(
    records: list[tuple[str, str, str, str]],
    grace_seconds: int,
) -> int:
    if grace_seconds <= 0:
        raise ValueError("stop grace period must be positive")
    poller = select.poll()
    active: dict[int, str] = {}
    status = 0

    def discard(pidfd: int) -> None:
        poller.unregister(pidfd)
        active.pop(pidfd, None)
        os.close(pidfd)

    def poll_until(deadline: float) -> None:
        while active:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return
            for pidfd, _ in poller.poll(max(1, int(remaining * 1000))):
                if pidfd in active:
                    discard(pidfd)

    try:
        for pid_path, identity_path, marker, label in records:
            try:
                pid = read_pid(Path(pid_path))
            except FileNotFoundError:
                continue
            except (OSError, ValueError) as failure:
                print(f"local-dev: refusing to stop {label}: invalid PID record ({failure})", file=sys.stderr)
                status = 1
                continue
            try:
                pidfd = open_verified_pidfd(pid, marker, Path(identity_path))
            except (FileNotFoundError, json.JSONDecodeError, OSError, ValueError) as failure:
                print(
                    f"local-dev: refusing to stop {label}: PID {pid} failed identity verification ({failure})",
                    file=sys.stderr,
                )
                status = 1
                continue
            if pidfd is None:
                continue
            active[pidfd] = label
            poller.register(pidfd, select.POLLIN)

        for pidfd in list(active):
            try:
                signal.pidfd_send_signal(pidfd, signal.SIGTERM)
            except ProcessLookupError:
                discard(pidfd)
        poll_until(time.monotonic() + grace_seconds)

        for pidfd in list(active):
            try:
                signal.pidfd_send_signal(pidfd, signal.SIGKILL)
            except ProcessLookupError:
                discard(pidfd)
        poll_until(time.monotonic() + 1)
        for label in active.values():
            print(f"local-dev: {label} did not exit after SIGKILL", file=sys.stderr)
            status = 1
        return status
    finally:
        for pidfd in list(active):
            discard(pidfd)


def stop_process(args: argparse.Namespace) -> int:
    return stop_recorded_processes(
        [(args.pid_file, args.identity_file, args.marker, args.label)],
        args.grace_seconds,
    )


def stop_many_processes(args: argparse.Namespace) -> int:
    records = args.records[1:] if args.records and args.records[0] == "--" else args.records
    if len(records) % 4 != 0:
        raise ValueError("stop-many requires PID file, identity file, marker, and label quadruples")
    grouped = [tuple(records[offset : offset + 4]) for offset in range(0, len(records), 4)]
    return stop_recorded_processes(grouped, args.grace_seconds)


def wait_for_any_process(args: argparse.Namespace) -> int:
    if len(args.records) % 3 != 0:
        raise ValueError("wait-any requires PID file, identity file, and marker triples")
    pidfds: list[int] = []
    poller = select.poll()
    try:
        for offset in range(0, len(args.records), 3):
            pid_file, identity_file, marker = args.records[offset : offset + 3]
            pid = read_pid(Path(pid_file))
            pidfd = open_verified_pidfd(pid, marker, Path(identity_file))
            if pidfd is None or not command_matches(pid, marker):
                if pidfd is not None:
                    os.close(pidfd)
                return 0
            pidfds.append(pidfd)
            poller.register(pidfd, select.POLLIN)
        poller.poll()
        return 0
    except (FileNotFoundError, json.JSONDecodeError, OSError, ValueError):
        return 0
    finally:
        for pidfd in pidfds:
            os.close(pidfd)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    subparsers = result.add_subparsers(dest="operation", required=True)

    spawn = subparsers.add_parser("spawn")
    spawn.add_argument("--marker", required=True)
    spawn.add_argument("pid_file")
    spawn.add_argument("identity_file")
    spawn.add_argument("output")
    spawn.add_argument("command", nargs=argparse.REMAINDER)
    spawn.set_defaults(action=spawn_process)

    matches = subparsers.add_parser("matches")
    matches.add_argument("--marker", required=True)
    matches.add_argument("pid_file")
    matches.add_argument("identity_file")
    matches.set_defaults(action=process_matches)

    stop = subparsers.add_parser("stop")
    stop.add_argument("--marker", required=True)
    stop.add_argument("pid_file")
    stop.add_argument("identity_file")
    stop.add_argument("label")
    stop.add_argument("--grace-seconds", type=int, default=5)
    stop.set_defaults(action=stop_process)

    stop_many = subparsers.add_parser("stop-many")
    stop_many.add_argument("--grace-seconds", type=int, default=5)
    stop_many.add_argument("records", nargs=argparse.REMAINDER)
    stop_many.set_defaults(action=stop_many_processes)

    wait_any = subparsers.add_parser("wait-any")
    wait_any.add_argument("records", nargs="+")
    wait_any.set_defaults(action=wait_for_any_process)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return args.action(args)
    except (OSError, ValueError) as failure:
        print(f"local-dev: process control failed: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
