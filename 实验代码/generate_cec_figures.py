#!/usr/bin/env python3
"""Reproduce the three CEC2017 figures without rerunning an experiment.

The bundled input preserves every algorithm/function/checkpoint arithmetic
mean (26,100 cells). To rebuild that input from the recorded 783,000 rows,
pass --source /path/to/validation/formal_full_results. No smoothing, error
floor, curve interpolation, or method omission is used.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.ticker import LogLocator, NullFormatter
import numpy as np
import pandas as pd
from figure_style import configure,grid,boxed_legend,place_captions,panel_metrics,COLORS,FIGURE_WIDTH_INCHES,TICK_LABEL_SIZE,LEGEND_FONT_SIZE,LINE_STYLES,algorithm_line,line_metadata


ROOT = Path(__file__).resolve().parents[1]
METHODS = ["LSCBO", "SA", "PSO", "HLBO", "GWCA", "CBO", "GWO", "AOA", "WOA"]
FUNCTIONS = [1, *range(3, 31)]
CHECKPOINTS = list(range(3000, 300001, 3000))
SELECTED = [1, 3, 5, 10, 15, 20]
EXPECTED_RANKS = np.array([49, 77, 97, 134, 139, 146, 199, 216, 248]) / 29
INPUT_NAME = "cec2017_checkpoint_means.csv"
PROVENANCE_NAME = "cec2017_figures.provenance.json"
STYLES = {method:(COLORS[method],*LINE_STYLES[method]) for method in METHODS}
DISPLAY_LABELS = {method:method for method in METHODS}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_input(source: Path, data_dir: Path) -> None:
    """Validate the raw protocol, then retain all means needed by the figures."""
    checkpoints = pd.read_csv(source / "convergence_checkpoints.csv")
    runs = pd.read_csv(source / "per_run_results.csv")
    statistics = pd.read_csv(source / "per_function_statistics.csv")
    ranks = pd.read_csv(source / "friedman_ranking.csv")
    if len(checkpoints) != 783000 or len(runs) != 7830:
        raise ValueError("Expected 783,000 checkpoints and 7,830 terminal records")
    parsed = checkpoints["task_key"].str.extract(
        r"^cec2017-d30-(.+?)-f(\d+)-r(\d+)-s\d+-b300000$"
    )
    if parsed.isna().any().any():
        raise ValueError("Unexpected archived task key")
    checkpoints = checkpoints.assign(
        algorithm=parsed[0], function=parsed[1].astype(int), run=parsed[2].astype(int)
    )
    keys = ["algorithm", "function", "run", "evaluations"]
    expected = pd.MultiIndex.from_product(
        [METHODS, FUNCTIONS, range(1, 31), CHECKPOINTS], names=keys
    )
    indexed = checkpoints.set_index(keys)
    if indexed.index.has_duplicates or not indexed.index.sort_values().equals(expected.sort_values()):
        raise ValueError("Incomplete or duplicated method/function/run/checkpoint coverage")
    if not np.isfinite(indexed[["error", "best_value"]].to_numpy()).all():
        raise ValueError("Nonfinite checkpoint values")
    if (indexed["error"] <= 0).any():
        raise ValueError("Log plots require strictly positive recorded errors; no floor is applied")
    run_keys = ["algorithm", "function", "run"]
    terminal = indexed.xs(300000, level="evaluations")["error"].sort_index()
    recorded = runs.set_index(run_keys)["error"].sort_index()
    if not terminal.index.equals(recorded.index):
        raise ValueError("Checkpoint and terminal run keys differ")
    if not np.allclose(terminal.to_numpy(), recorded.to_numpy(), rtol=1e-13, atol=0):
        raise ValueError("Terminal checkpoints disagree with the recorded results")
    if not (runs["dimension"].eq(30) & runs["budget"].eq(300000) & runs["evaluations"].eq(300000)).all():
        raise ValueError("Unexpected dimension or evaluation budget")
    if not np.allclose(runs["error"], runs["best_value"] - runs["optimum"], rtol=1e-12, atol=1e-9):
        raise ValueError("Stored error does not equal best value minus official optimum")
    monotone = checkpoints.sort_values(keys).groupby(run_keys)["error"].diff()
    if (monotone > 1e-8).any():
        raise ValueError("A best-so-far run increases between checkpoints")
    means = checkpoints.groupby(["evaluations", "function", "algorithm"], as_index=False).agg(
        mean_error=("error", "mean"), n_runs=("run", "size")
    )
    end = means[means["evaluations"].eq(300000)].set_index(["function", "algorithm"])
    reference = statistics.set_index(["function", "algorithm"]).reindex(end.index)
    if not np.allclose(end["mean_error"], reference["mean"], rtol=1e-13, atol=0):
        raise ValueError("Computed function means disagree with the archived summary")
    final_ranks = end["mean_error"].groupby(level="function").rank(method="average")
    avg = final_ranks.groupby(level="algorithm").mean().reindex(METHODS)
    archived = ranks.set_index("algorithm")["average_rank"].reindex(METHODS)
    if not np.allclose(avg, archived, rtol=0, atol=1e-12):
        raise ValueError("Final checkpoint ranks disagree with the archived Friedman table")
    data_dir.mkdir(parents=True, exist_ok=True)
    output = data_dir / INPUT_NAME
    means.to_csv(output, index=False, float_format="%.17g")
    provenance = {
        "description": "Complete 30-run arithmetic means at all 100 CEC2017 checkpoints; no run or function is dropped.",
        "source_directory": str(source.resolve()),
        "source_files": {
            name: {"sha256": sha256(source / name)}
            for name in ["convergence_checkpoints.csv", "per_run_results.csv", "per_function_statistics.csv", "friedman_ranking.csv"]
        },
        "subset_sha256": sha256(output),
        "source_checkpoint_rows": len(checkpoints),
        "source_terminal_rows": len(runs),
        "bundled_mean_rows": len(means),
        "functions": FUNCTIONS,
        "methods": METHODS,
        "runs_per_cell": 30,
        "checkpoints": CHECKPOINTS,
        "minimum_raw_error": float(checkpoints["error"].min()),
        "terminal_checkpoint_max_abs_difference": float(np.max(np.abs(terminal.to_numpy() - recorded.to_numpy()))),
        "archived_mean_max_relative_difference": float(np.max(np.abs(end["mean_error"].to_numpy() / reference["mean"].to_numpy() - 1))),
        "terminal_average_ranks": avg.to_dict(),
        "selected_function_ids_retained_from_previous_figure": SELECTED,
        "error_definition": "Recorded best_value minus the official per-function optimum. Arithmetic mean over 30 runs is formed before displaying a logarithmic axis. No offset or clipping.",
    }
    (data_dir / PROVENANCE_NAME).write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")


def load_means(data_dir: Path) -> pd.DataFrame:
    provenance = json.loads((data_dir / PROVENANCE_NAME).read_text(encoding="utf-8"))
    path = data_dir / INPUT_NAME
    if sha256(path) != provenance["subset_sha256"]:
        raise ValueError("CEC figure input hash differs from the provenance record")
    means = pd.read_csv(path)
    keys = ["evaluations", "function", "algorithm"]
    expected = pd.MultiIndex.from_product([CHECKPOINTS, FUNCTIONS, METHODS], names=keys)
    actual = means.set_index(keys).index
    if actual.has_duplicates or not actual.sort_values().equals(expected.sort_values()):
        raise ValueError("The complete 26,100-cell checkpoint grid is required")
    if not means["n_runs"].eq(30).all() or not np.isfinite(means["mean_error"]).all() or means["mean_error"].le(0).any():
        raise ValueError("Invalid run counts or nonpositive/nonfinite mean errors")
    means["rank"] = means.groupby(["evaluations", "function"])["mean_error"].rank(method="average")
    end = means[means["evaluations"].eq(300000)].groupby("algorithm")["rank"].mean().reindex(METHODS)
    if not np.allclose(end, EXPECTED_RANKS, rtol=0, atol=1e-12):
        raise ValueError("The figure endpoint does not reproduce the nine-method rank table")
    return means


def configure_style():
    configure()

def handles(rank=False):
    if rank:
        return [Line2D([],[],color=STYLES[a][0],marker='D' if a=='LSCBO' else 'o',linestyle='None',markersize=4,label=DISPLAY_LABELS[a]) for a in METHODS]
    return [Line2D([],[],**algorithm_line(a),label=DISPLAY_LABELS[a]) for a in METHODS]

def save(fig,name,output,editable,captions=None):
    output.mkdir(parents=True,exist_ok=True);editable.mkdir(parents=True,exist_ok=True)
    fig.canvas.draw();captions=captions or [None]*len(fig.axes)
    panels=[];curves=[];points=[]
    for index,(ax,caption) in enumerate(zip(fig.axes,captions)):
        leg=ax.get_legend();leg.set_gid(f'legend_{name}_{index}')
        for method,line in zip(METHODS,leg.get_lines()):line.set_gid(f'legend_line_{name}_{index}_{method}')
        record=panel_metrics(ax,caption);record['legend_gid']=leg.get_gid()
        record['function']=getattr(ax,'_cec_function',None);panels.append(record)
        for line in ax.lines:
            method=line.get_label()
            if method not in METHODS:continue
            gid=f'curve_{name}_{index}_{method}';line.set_gid(gid)
            coordinates=np.column_stack((line.get_xdata(),line.get_ydata()))
            display=ax.transData.transform(coordinates)*72/fig.dpi;display[:,1]=fig.get_figheight()*72-display[:,1]
            curves.append({'gid':gid,'panel':index,'algorithm':method,'function':record['function'],
                'x':coordinates[:,0].tolist(),'y':coordinates[:,1].tolist(),'color':line.get_color(),
                **line_metadata(line),'expected_vector_points_pt':display.tolist()})
        for collection in ax.collections:
            method=collection.get_label()
            if method not in METHODS:continue
            gid=f'marker_{name}_{index}_{method}';collection.set_gid(gid)
            coordinates=np.asarray(collection.get_offsets(),float)
            display=ax.transData.transform(coordinates)*72/fig.dpi;display[:,1]=fig.get_figheight()*72-display[:,1]
            points.append({'gid':gid,'algorithm':method,'x':coordinates[:,0].tolist(),'y':coordinates[:,1].tolist(),
                'color':STYLES[method][0],'expected_vector_points_pt':display.tolist()})
    layout={'data_sha256':DATA_SHA256,'standard':'Fig7 physical typography and boxed upper-left legends',
        'panels':panels,'curves':curves,'points':points,'every_panel_has_legend':True,
        'mean_cells_retained':26100,'checkpoints_per_curve':100,'figure_width_mm':fig.get_figwidth()*25.4}
    (output/f'{name}_layout.json').write_text(json.dumps(layout,indent=2),encoding='utf-8')
    fig.savefig(output/f'{name}.pdf',metadata={'CreationDate':None,'ModDate':None})
    fig.savefig(output/f'{name}.png',dpi=300,facecolor='white')
    fig.savefig(output/f'{name}.svg',metadata={'Date':None})
    if editable.resolve()!=output.resolve():
        fig.savefig(editable/f'{name}_cec.svg',metadata={'Date':None})
    plt.close(fig)

def draw_ranks(means,output,editable):
    ranks=means[means.evaluations.eq(300000)].groupby('algorithm')['rank'].mean().reindex(METHODS)
    fig,ax=plt.subplots(figsize=(FIGURE_WIDTH_INCHES,4.2))
    fig.subplots_adjust(left=.235,right=.975,bottom=.16,top=.96)
    for pos,(method,rank) in enumerate(ranks.items()):
        ax.plot([1,rank],[pos,pos],color='#D3D7DC',linewidth=1.1,zorder=1)
        ax.scatter(rank,pos,color=STYLES[method][0],marker='D' if method=='LSCBO' else 'o',s=26,zorder=3,label=method)
        ax.text(rank+.14,pos,f'{rank:.3f}',va='center',fontsize=TICK_LABEL_SIZE,weight='bold' if method=='LSCBO' else 'normal')
    ax.set_yticks(range(9),[DISPLAY_LABELS[method] for method in METHODS]);ax.set_ylim(8.6,-2.6)
    ax.set_xlim(.85,9.4);ax.set_xticks(range(1,10));ax.set_xlabel('Friedman average rank (lower is better)',labelpad=3)
    grid(ax,'x');ax.tick_params(axis='y',length=0)
    boxed_legend(ax,handles(rank=True));save(fig,'Fig1',output,editable)

def draw_aggregate(means,output,editable):
    summary=means.groupby(['evaluations','algorithm'])['rank'].mean().rename('mean').reset_index()
    fig,ax=plt.subplots(figsize=(FIGURE_WIDTH_INCHES,4.2))
    fig.subplots_adjust(left=.13,right=.975,bottom=.16,top=.96)
    for method in reversed(METHODS):
        data=summary[summary.algorithm.eq(method)].sort_values('evaluations')
        x,y=data.evaluations.to_numpy()/1000,data['mean'].to_numpy()
        ax.plot(x,y,**algorithm_line(method),markevery=(METHODS.index(method)%5,12),label=method)
    ax.set_xlim(0,300);ax.set_xticks([0,50,100,150,200,250,300]);ax.set_ylim(9.15,.65);ax.set_yticks(range(1,10))
    ax.set_xlabel('Objective evaluations (thousands)',labelpad=3);ax.set_ylabel('Mean function rank (lower is better)',labelpad=3)
    grid(ax,'y');boxed_legend(ax,handles());save(fig,'Fig2',output,editable)

def function_panel(ax,means,function,legend=True):
    ax._cec_function=function;subset=means[means.function.eq(function)]
    for method in reversed(METHODS):
        data=subset[subset.algorithm.eq(method)].sort_values('evaluations');color,linestyle,width=STYLES[method]
        ax.plot(data.evaluations/1000,data.mean_error,**algorithm_line(method),markevery=(METHODS.index(method)%5,12),label=method)
    ax.set_yscale('log');ax.set_xlim(0,300);ax.set_xticks([0,100,200,300])
    ax.set_xlabel('Objective evaluations (thousands)',labelpad=3);ax.set_ylabel('Mean benchmark error',labelpad=3)
    grid(ax,'y')
    lo,hi=subset.mean_error.min(),subset.mean_error.max()
    if function in {5,10,20}:
        ticks=np.array([70,100,200,400,600,1000,2000,3000,4000,6000,8000,10000])
        ticks=ticks[(ticks>=lo*.96)&(ticks<=hi*1.04)]
        if len(ticks)>5:ticks=ticks[::2]
        ax.set_yticks(ticks,[f'{value:g}' for value in ticks])
    else:
        exponents=np.arange(int(np.ceil(np.log10(lo))),int(np.floor(np.log10(hi)))+1)
        if len(exponents)>4:exponents=exponents[::2]
        ax.set_yticks(10.**exponents,[f'1e{int(e)}' for e in exponents])
    ax.yaxis.set_minor_locator(LogLocator(base=10,subs=[2,3,4,5,6,7,8,9]))
    ax.yaxis.set_minor_formatter(NullFormatter());ax.tick_params(axis='y',which='minor',length=1.5)
    if legend:boxed_legend(ax,handles())

def draw_functions(means,output,editable):
    # Full-width individual panels keep the native Fig7 font sizes when placed separately.
    titles=[f'({chr(97+i)}) CEC F{function}' for i,function in enumerate(SELECTED)]
    fig,axes=plt.subplots(3,2,figsize=(FIGURE_WIDTH_INCHES*2.12,10.8))
    fig.subplots_adjust(left=.065,right=.985,bottom=.085,top=.96,wspace=.26,hspace=.58)
    for ax,function in zip(axes.flat,SELECTED):function_panel(ax,means,function)
    captions=place_captions(fig,list(axes.flat),titles,2);save(fig,'Fig3',output,editable,captions)
    for title,function in zip(titles,SELECTED):
        fig,ax=plt.subplots(figsize=(FIGURE_WIDTH_INCHES,3.65))
        fig.subplots_adjust(left=.13,right=.975,bottom=.25,top=.96)
        function_panel(ax,means,function);captions=place_captions(fig,[ax],[title],1)
        save(fig,f'Fig3_F{function}',output,editable,captions)


def draw_function_grid(means,output):
    # Manuscript Fig5: the six panels in three rows and two columns at the standard
    # figure width, so the printed fonts match the other figures. A half-width panel
    # cannot hold the nine-entry legend, so one boxed legend is shared above the grid.
    titles=[f'({chr(97+i)}) CEC F{function}' for i,function in enumerate(SELECTED)]
    fig,axes=plt.subplots(3,2,figsize=(FIGURE_WIDTH_INCHES,8.2))
    fig.subplots_adjust(left=.105,right=.965,bottom=.075,top=.93,wspace=.36,hspace=.64)
    for ax,function in zip(axes.flat,SELECTED):function_panel(ax,means,function,legend=False)
    leg=fig.legend(handles=handles(),loc='upper center',bbox_to_anchor=(.5,.995),ncol=5,
        fontsize=LEGEND_FONT_SIZE,frameon=True,fancybox=False,facecolor='white',framealpha=1.,
        edgecolor='#6D7380',borderpad=.30,labelspacing=.30,handlelength=1.6,
        handletextpad=.35,columnspacing=.70,labelcolor='linecolor')
    leg.get_frame().set_linewidth(.65)
    captions=place_captions(fig,list(axes.flat),titles,2)
    output.mkdir(parents=True,exist_ok=True)
    fig.canvas.draw();renderer=fig.canvas.get_renderer();height_pt=fig.get_figheight()*72
    panels=[];curves=[]
    for index,(ax,caption) in enumerate(zip(axes.flat,captions)):
        box=ax.get_position()
        panels.append({'function':ax._cec_function,'title':caption.get_text(),'xlabel':ax.get_xlabel(),'ylabel':ax.get_ylabel(),
            'y_scale':ax.get_yscale(),'display_y_limits':list(ax.get_ylim()),
            'axes_bbox_from_bottom_pt':(np.array([box.x0,box.y0,box.x1,box.y1])*np.array([fig.get_figwidth()*72,height_pt]*2)).tolist()})
        for line in ax.lines:
            if line.get_label() not in METHODS:continue
            curves.append({'panel':index,'function':ax._cec_function,'algorithm':line.get_label(),
                'x':np.asarray(line.get_xdata(),float).tolist(),'y':np.asarray(line.get_ydata(),float).tolist(),**line_metadata(line)})
    layout={'data_sha256':DATA_SHA256,'standard':'Fig7 physical typography; one shared boxed legend above a 3x2 grid',
        'figure_width_pt':fig.get_figwidth()*72,'figure_height_pt':height_pt,'panels':panels,'curves':curves,
        'shared_legend':{'labels':[t.get_text() for t in leg.get_texts()],'columns':5,
            'bbox_from_bottom_pt':(leg.get_window_extent(renderer).extents*72/fig.dpi).tolist()},
        'mean_cells_retained':26100,'checkpoints_per_curve':100}
    (output/'Fig3_grid_layout.json').write_text(json.dumps(layout,indent=2),encoding='utf-8')
    fig.savefig(output/'Fig3_grid.pdf',metadata={'CreationDate':None,'ModDate':None})
    fig.savefig(output/'Fig3_grid.png',dpi=300,facecolor='white')
    fig.savefig(output/'Fig3_grid.svg',metadata={'Date':None})
    plt.close(fig)


def main() -> None:
    global DATA_SHA256
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, help="Optional original formal_full_results directory; validates and regenerates bundled means")
    parser.add_argument("--data-dir", type=Path, default=ROOT / "实验数据" / "数据")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "实验数据" / "CEC2017" / "figures")
    parser.add_argument("--editable-dir", type=Path, help="Defaults to --output-dir")
    args = parser.parse_args()
    args.editable_dir=args.editable_dir or args.output_dir
    if args.source:
        prepare_input(args.source, args.data_dir)
    means = load_means(args.data_dir)
    DATA_SHA256=sha256(args.data_dir/INPUT_NAME)
    configure_style()
    draw_ranks(means, args.output_dir, args.editable_dir)
    draw_aggregate(means, args.output_dir, args.editable_dir)
    draw_functions(means, args.output_dir, args.editable_dir)
    draw_function_grid(means, args.output_dir)
    print("Verified 26,100 mean cells and all nine final ranks; wrote Fig1--Fig3 PDF and editable SVG.")


if __name__ == "__main__":
    main()
