#!/usr/bin/env python3
"""Build the main PDF, synchronized method figures, and standalone Chapter 3.

Requires latexmk/pdflatex on PATH. Existing Fig6 is included unchanged; regenerate
it separately with generate_cloud_objective_figure.py when needed.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

MAIN = "LSCBO_Evolutionary_Intelligence"
FIGURES = ("Fig1_LSCBO_mechanism", "Fig2_cloud_application")

def compile_tex(name: str, cwd: Path, output: Path) -> Path:
    output.mkdir(parents=True, exist_ok=True)
    command = ["latexmk", "-pdf", "-interaction=nonstopmode", "-halt-on-error",
               "-outdir=" + str(output), name + ".tex"]
    log = output / (name + ".build.log")
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"Compilation failed; inspect {log}")
    return output / (name + ".pdf")

def export_chapter(paper: Path, output: Path) -> None:
    main = (paper / (MAIN + ".tex")).read_text(encoding="utf-8")
    chapter_start = re.search(r"(?m)^\\section\{[^\n]+\}\\label\{sec3\}", main)
    chapter_end = re.search(r"(?m)^\\section\{[^\n]+\}\\label\{sec5\}", main)
    if not chapter_start or not chapter_end or chapter_start.start() >= chapter_end.start():
        raise ValueError("Cannot locate Chapter 3 and Experimental Results in the main TeX")
    chapter = main[chapter_start.start():chapter_end.start()]
    if re.search(r"\\(?:input|include)\{", chapter):
        raise ValueError("Chapter 3 must be embedded in the main TeX without external inputs")
    cited: list[str] = []
    for group in re.findall(r"\\cite(?:p|t)?(?:\[[^\]]*\])*\{([^}]+)\}", chapter):
        for key in group.split(","):
            key = key.strip()
            if key not in cited:
                cited.append(key)
    bibliography = main.split(r"\begin{thebibliography}", 1)[1].split(r"\end{thebibliography}", 1)[0]
    entries = re.findall(r"\\bibitem\{([^}]+)\}([\s\S]*?)(?=\\bibitem\{|$)", bibliography)
    entries_by_key = dict(entries)
    missing = set(cited) - entries_by_key.keys()
    if missing:
        raise ValueError(f"Missing Chapter 3 bibliography entries: {sorted(missing)}")
    selected = "".join(r"\bibitem{" + key + "}" + entries_by_key[key] for key in cited)
    preamble = main.split(r"\begin{document}", 1)[0]
    chapter_style = (r"\makeatletter\def\ps@chapteronly{\let\@oddhead\@empty"
                     r"\let\@evenhead\@empty\def\@oddfoot{\hfil\thepage\hfil}"
                     r"\let\@evenfoot\@oddfoot}\makeatother" + "\n")
    standalone = (preamble + chapter_style + "\\begin{document}\n\\pagestyle{chapteronly}\n\\setcounter{section}{2}\n"
                  + chapter + "\n\\setlength{\\bibsep}{0.1em}\n\\begin{thebibliography}{99}\n"
                  + "\\interlinepenalty=10000\n" + selected
                  + "\\end{thebibliography}\n\\end{document}\n")
    output.mkdir(parents=True, exist_ok=True)
    (output / "Chapter3_complete.tex").write_text(standalone, encoding="utf-8")

def build(release: Path, work: Path) -> None:
    paper = release / "论文"
    editable = release / "实验数据" / "图表"
    sync = release / "实验代码" / "sync_figure_references.py"
    compile_tex(MAIN, paper, work / "main")
    auxiliary = work / "main" / (MAIN + ".aux")
    macros = editable / "manuscript_refs.tex"
    for attempt in range(3):
        subprocess.run([sys.executable, str(sync), str(auxiliary)], check=True)
        used_refs = macros.read_bytes()
        for name in FIGURES:
            pdf = compile_tex(name, editable, work / name)
            shutil.copy2(pdf, paper / "figures" / "publication" / pdf.name)
        main_pdf = compile_tex(MAIN, paper, work / "main")
        subprocess.run([sys.executable, str(sync), str(auxiliary)], check=True)
        if macros.read_bytes() == used_refs:
            break
    else:
        raise RuntimeError("Figure reference numbers did not stabilize")
    chapter_src = work / "chapter_src"
    chapter_src.mkdir(parents=True, exist_ok=True)
    shutil.copy2(paper / "sn-jnl.cls", chapter_src / "sn-jnl.cls")
    shutil.copytree(paper / "figures", chapter_src / "figures", dirs_exist_ok=True)
    export_chapter(paper, chapter_src)
    chapter_pdf = compile_tex("Chapter3_complete", chapter_src, work / "chapter")
    shutil.copy2(main_pdf, paper / main_pdf.name)
    print(f"Built {main_pdf.name} in {paper}; synchronized both method figures. "
          f"Chapter 3 export ({chapter_pdf.name}) kept in {work}.")

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, help="Keep build logs and intermediates here")
    args = parser.parse_args()
    release = Path(__file__).resolve().parents[1]
    if args.work_dir:
        build(release, args.work_dir.resolve())
    else:
        work = Path(tempfile.mkdtemp(prefix="lscbo-release-"))
        try:
            build(release, work)
        except BaseException:
            print(f"Build failed; retained logs and intermediates at {work}", file=sys.stderr)
            raise
        else:
            shutil.rmtree(work)

if __name__ == "__main__":
    main()
