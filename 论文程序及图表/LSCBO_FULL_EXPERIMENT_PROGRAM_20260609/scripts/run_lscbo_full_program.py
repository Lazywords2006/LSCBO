#!/usr/bin/env python3
"""Orchestrate the full LSCBO formal experiment package.

The Java program performs one deterministic shard at a time. This wrapper keeps
the formal run resumable: each seed batch writes to its own folder, then the
aggregator combines all shards into paper-ready tables and figures.

Parallelism:
  --parallel N   : number of concurrent JVM processes (0 = auto)
  --jvm-threads N: Java thread pool size inside each JVM (0 = auto)

  Auto mode uses sqrt(cpu_count) processes each with cpu_count/processes threads,
  which saturates the CPU while keeping per-process overhead low.
  Example on a 24-thread machine: 4 processes x 6 threads = 24 active threads.
"""

from __future__ import annotations

import argparse
import datetime as dt
import math
import os
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_MAIN = "com.edcbo.research.claim.ClaimPreservingCloudSimExperiment"


_MVN_CMD: str = "mvn"


def _resolve_mvn() -> str:
    """Return the full path to the mvn executable.

    On Windows, subprocess.Popen with shell=False cannot find .cmd files even
    when the directory is on PATH — it only finds .exe/.com files via CreateProcess.
    We resolve the real path here so we can pass it directly to Popen.
    """
    import shutil

    # Fast path: mvn or mvn.cmd already on PATH
    found = shutil.which("mvn") or shutil.which("mvn.cmd")
    if found:
        return found

    # Search common Windows installation locations
    search_bases = [Path.home(), Path("C:/tools"), Path("C:/Program Files")]
    for base in search_bases:
        try:
            for bin_dir in sorted(base.glob("apache-maven-*/bin"), reverse=True):
                for name in ("mvn.cmd", "mvn"):
                    candidate = bin_dir / name
                    if candidate.exists():
                        print(f"[config] Found Maven: {candidate}", flush=True)
                        return str(candidate)
        except (PermissionError, OSError):
            pass

    return "mvn"  # last resort — will fail loudly if not found


def _add_tools_to_path() -> None:
    global _MVN_CMD
    _MVN_CMD = _resolve_mvn()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the LSCBO full experiment program.")
    parser.add_argument(
        "--profile",
        choices=["formal", "verify", "formal-cloudsim", "smoke-cloudsim"],
        default="formal",
        help=(
            "formal: all experiment families for seeds 43-72 without per-row CloudSim; "
            "verify: all experiment families for one seed; "
            "formal-cloudsim: same as formal but validates every row in CloudSim; "
            "smoke-cloudsim: fast CloudSim sanity check."
        ),
    )
    parser.add_argument("--seed-start", type=int, default=43)
    parser.add_argument("--seed-end", type=int, default=72)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--experiments", default="all")
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--resume", action="store_true", default=True)
    parser.add_argument("--no-resume", dest="resume", action="store_false")
    parser.add_argument("--skip-aggregate", action="store_true")
    parser.add_argument(
        "--parallel", type=int, default=0,
        help="Number of concurrent JVM processes (0 = auto based on CPU count).",
    )
    parser.add_argument(
        "--jvm-threads", type=int, default=0,
        help="Java thread pool size inside each JVM (0 = auto based on CPU count).",
    )
    return parser.parse_args()


def timestamp() -> str:
    return dt.datetime.now().strftime("%Y%m%d_%H%M%S")


def auto_settings(parallel_override: int, jvm_threads_override: int) -> tuple[int, int]:
    """Return (parallel_procs, jvm_threads) to saturate all logical CPUs."""
    cpus = os.cpu_count() or 4
    if parallel_override > 0 and jvm_threads_override > 0:
        return parallel_override, jvm_threads_override
    if parallel_override > 0:
        return parallel_override, max(1, cpus // parallel_override)
    if jvm_threads_override > 0:
        return max(1, cpus // jvm_threads_override), jvm_threads_override
    # sqrt split: balanced between process overhead and thread utilisation
    procs = max(1, int(math.sqrt(cpus)))
    threads = max(1, cpus // procs)
    return procs, threads


def setup_maven_env() -> None:
    """Set MAVEN_OPTS in the current process so all subprocesses inherit it.

    Avoids passing an explicit env dict to subprocess.Popen, which on Windows
    prevents .cmd files (like mvn.cmd) from being found via PATH lookup.
    """
    current = os.environ.get("MAVEN_OPTS", "")
    if "-Xmx" not in current:
        extra = "-Xms256m -Xmx4g -XX:+UseG1GC"
        os.environ["MAVEN_OPTS"] = (current + " " + extra).strip()


def run_command(command: list[str], log_file: Path) -> None:
    log_file.parent.mkdir(parents=True, exist_ok=True)
    print("\n[run]", " ".join(command), flush=True)
    with log_file.open("a", encoding="utf-8") as log:
        log.write("\n[run] " + " ".join(command) + "\n")
        process = subprocess.Popen(
            command,
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        assert process.stdout is not None
        for line in process.stdout:
            print(line, end="")
            log.write(line)
        return_code = process.wait()
        log.write(f"[exit] {return_code}\n")
    if return_code != 0:
        raise subprocess.CalledProcessError(return_code, command)


def seed_chunks(start: int, end: int, batch_size: int) -> list[tuple[int, int]]:
    if batch_size <= 0:
        raise ValueError("batch-size must be positive.")
    chunks: list[tuple[int, int]] = []
    current = start
    while current <= end:
        chunk_end = min(end, current + batch_size - 1)
        chunks.append((current, chunk_end))
        current = chunk_end + 1
    return chunks


def default_output_dir(profile: str) -> Path:
    return ROOT / "results" / f"{profile}_{timestamp()}"


def java_exec_args(mode: str, out_dir: Path, cloudsim: bool, seed_start: int | None,
                   seed_end: int | None, experiments: str,
                   parallelism: int = 1) -> list[str]:
    exec_args = [
        "--mode", mode,
        "--out", str(out_dir),
        "--cloudsim", "true" if cloudsim else "false",
        "--experiments", experiments,
        "--parallelism", str(parallelism),
    ]
    if seed_start is not None and seed_end is not None:
        exec_args.extend(["--seedRange", f"{seed_start}-{seed_end}"])
    return [
        _MVN_CMD, "-q",
        "org.codehaus.mojo:exec-maven-plugin:3.5.0:java",
        f"-Dexec.mainClass={JAVA_MAIN}",
        "-Dexec.args=" + " ".join(exec_args),
    ]


def run_smoke_cloudsim(out_dir: Path) -> None:
    shard_dir = out_dir / "shards" / "smoke_cloudsim"
    raw_file = shard_dir / "claim_smoke_raw.csv"
    if raw_file.exists():
        print(f"[skip] existing {raw_file}")
        return
    run_command(
        java_exec_args("smoke", shard_dir, True, None, None, "E4_main", 1),
        out_dir / "logs" / "smoke_cloudsim.log",
    )


def run_formal_shards(args: argparse.Namespace, out_dir: Path, cloudsim: bool,
                      seed_start: int, seed_end: int,
                      parallel: int, jvm_threads: int) -> None:
    chunks = seed_chunks(seed_start, seed_end, args.batch_size)
    pending: list[tuple[int, int, str, Path]] = []
    for start, end in chunks:
        label = f"seed_{start}" if start == end else f"seed_{start}_{end}"
        shard_dir = out_dir / "shards" / label
        raw_file = shard_dir / "claim_formalfull_raw.csv"
        if args.resume and raw_file.exists():
            print(f"[skip] existing shard {label}", flush=True)
            continue
        pending.append((start, end, label, shard_dir))

    if not pending:
        return

    def run_shard(item: tuple[int, int, str, Path]) -> None:
        start, end, label, shard_dir = item
        run_command(
            java_exec_args("formalfull", shard_dir, cloudsim, start, end,
                           args.experiments, jvm_threads),
            out_dir / "logs" / f"{label}.log",
        )

    if parallel <= 1:
        for item in pending:
            run_shard(item)
    else:
        with ThreadPoolExecutor(max_workers=parallel) as executor:
            futures = {executor.submit(run_shard, item): item[2] for item in pending}
            for future in as_completed(futures):
                label = futures[future]
                try:
                    future.result()
                    print(f"[done] shard {label}", flush=True)
                except subprocess.CalledProcessError as exc:
                    print(f"[error] shard {label}: {exc}", flush=True)
                    raise


def aggregate(out_dir: Path) -> None:
    run_command(
        [
            sys.executable,
            str(ROOT / "scripts" / "aggregate_lscbo_results.py"),
            "--run-dir",
            str(out_dir),
        ],
        out_dir / "logs" / "aggregate.log",
    )


def main() -> int:
    args = parse_args()
    _add_tools_to_path()
    out_dir = (args.out or default_output_dir(args.profile)).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    # CloudSim Plus is not guaranteed thread-safe across concurrent simulations.
    # For CloudSim profiles keep parallel=1, jvm_threads=1 unless explicitly overridden.
    cloudsim_profile = args.profile in ("formal-cloudsim", "smoke-cloudsim")
    if cloudsim_profile:
        parallel = args.parallel if args.parallel > 0 else 1
        jvm_threads = args.jvm_threads if args.jvm_threads > 0 else 1
    else:
        parallel, jvm_threads = auto_settings(args.parallel, args.jvm_threads)

    cpus = os.cpu_count() or 4
    print(
        f"[config] cpus={cpus}  parallel={parallel}  jvm_threads={jvm_threads}"
        f"  total_threads={parallel * jvm_threads}",
        flush=True,
    )

    setup_maven_env()
    print(f"[config] MAVEN_OPTS={os.environ.get('MAVEN_OPTS', '')}", flush=True)

    run_command([_MVN_CMD, "-q", "-DskipTests", "compile"],
                out_dir / "logs" / "compile.log")

    if args.profile == "smoke-cloudsim":
        run_smoke_cloudsim(out_dir)
    elif args.profile == "verify":
        # verify runs a single seed — no benefit from multiple processes
        run_formal_shards(args, out_dir, False, args.seed_start, args.seed_start,
                          parallel=1, jvm_threads=jvm_threads)
    elif args.profile == "formal":
        run_formal_shards(args, out_dir, False, args.seed_start, args.seed_end,
                          parallel=parallel, jvm_threads=jvm_threads)
    elif args.profile == "formal-cloudsim":
        run_formal_shards(args, out_dir, True, args.seed_start, args.seed_end,
                          parallel=parallel, jvm_threads=jvm_threads)
    else:
        raise ValueError(f"Unknown profile: {args.profile}")

    if not args.skip_aggregate:
        aggregate(out_dir)

    print(f"\n[DONE] Output folder: {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
