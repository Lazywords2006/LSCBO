#!/usr/bin/env python3
"""Verify and integrate supplied cold-start records without reading warm data.

Uncertainty resamples complete scenario-seed clusters (fixed task scales), with
50,000 PCG64 draws and linear percentile quantiles. Writes CSV/JSON evidence,
and vector/PDF/PNG sensitivity plots. Runtime files remain on D:.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

RUNTIME = Path(os.environ.get("LSCBO_RUNTIME", "D:/CodexRuntime/runs/lscbo-analysis" if os.name == "nt" else "/tmp/lscbo-analysis"))
RUNTIME.mkdir(parents=True, exist_ok=True)
os.environ["MPLCONFIGDIR"] = str(RUNTIME / "matplotlib")
os.environ["TMP"] = os.environ["TEMP"] = str(RUNTIME)
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch
import numpy as np
import pandas as pd
from scipy.stats import friedmanchisquare, wilcoxon

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from publication_style import COLORS, WIDTH_INCHES, configure, grid, boxed_legend, panel_metrics, TICK_LABEL_SIZE
ALGS = ["LSCBO", "CBO", "PSO", "GWO", "WOA", "AOA", "GREEDY_LPT"]
METRICS = ["makespan", "energy", "cost", "imbalance", "objective"]
COMPONENTS = METRICS[:4]
NORMS = ["normMakespan", "normEnergy", "normCost", "normImbalance"]
PRIMARY_SCALES = list(range(100, 1001, 100))
ALL_SCALES = PRIMARY_SCALES + list(range(2000, 10001, 1000))
SEEDS = list(range(43, 73))
BLOCK = ["taskCount", "seed"]
ROW_KEY = ["experiment", "algorithm", "variant", "weightProfile", "vmProfile",
           "taskCount", "seed", "perturbation"]
TIE = 1e-8
RESAMPLES = 50000
RNG_SEED = 20260912
PROFILE_LABELS = {
    "W0_equal": "Equal", "W1_makespan_priority": "Makespan",
    "W2_energy_priority": "Energy", "W3_cost_priority": "Cost",
    "W4_balance_priority": "Balance", "near_homogeneous_1_5_to_1": "1.5:1",
    "medium_5_to_1": "5:1", "heterogeneous_20_to_1": "20:1"}
VARIANT_LABELS = {
    "greedy_reference": "Greedy-LPT reference", "LSCBO_full": "Full LSCBO",
    "no_levy": r"No L\'{e}vy", "ls_only": "Core-disabled pair search",
    "no_local_search": "No local search", "cbo_base": "CBO base",
    "objective_only_pair_ls": "Objective-only pair search",
    "pareto_relocate_ls": "Pareto-safe single relocation"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def bootstrap(values: np.ndarray) -> dict[str, float]:
    values = np.asarray(values, dtype=float)
    require(values.shape == (30,), f"Expected 30 seed clusters: {values.shape}")
    indices = np.random.Generator(np.random.PCG64(RNG_SEED)).integers(
        0, len(values), (RESAMPLES, len(values)))
    low, high = np.quantile(values[indices].mean(axis=1), [.025, .975], method="linear")
    return {"mean": float(values.mean()), "low": float(low), "high": float(high)}


def cluster_summary(values: pd.Series) -> dict[str, float]:
    return bootstrap(values.groupby(level="seed").mean().sort_index().to_numpy())


def wtl(left: pd.Series, right: pd.Series, tolerance: float = TIE) -> dict[str, int]:
    delta = left - right
    return {"wins": int((delta < -tolerance).sum()),
            "ties": int((delta.abs() <= tolerance).sum()),
            "losses": int((delta > tolerance).sum())}


def holm(values: list[float]) -> list[float]:
    order = np.argsort(values)
    result = np.zeros(len(values))
    running = 0.0
    for rank, index in enumerate(order):
        running = max(running, min(1.0, (len(values) - rank) * values[index]))
        result[index] = running
    return result.tolist()


def weights(profile: str) -> np.ndarray:
    result = np.full(4, .25 if profile == "W0_equal" else .15)
    if profile != "W0_equal":
        result[int(profile[1]) - 1] = .55
    return result


def verify(data: pd.DataFrame) -> dict:
    expected = {"E4_main": 3990, "weight_sensitivity": 2250, "vm_heterogeneity": 810}
    if "E2_ablation" in set(data.experiment):
        expected["E2_ablation"] = 2400
    require(data.groupby("experiment").size().to_dict() == expected, "Row counts differ")
    require(not data.duplicated(ROW_KEY).any(), "Duplicate canonical row key")
    require(np.isfinite(data.select_dtypes(include=[np.number])).all().all(), "Nonfinite numeric values")
    require(set(data.seed) == set(SEEDS), "Seed set differs")
    require(data.populationSize.eq(100).all() and data.maxIterations.eq(100).all(), "Population/iteration parameters differ")
    require(data.vmCount.eq(50).all(), "Expected 50 VMs in analytical inputs")
    require(data.cloudsimFinishedCloudlets.eq(-1).all(), "Analytical inputs unexpectedly include replay")
    greedy = data.algorithm.eq("GREEDY_LPT") | data.variant.eq("greedy_reference")
    require(data.loc[greedy, "totalEvaluations"].eq(1).all(), "Greedy scoring count differs")
    require(data.loc[~greedy, "totalEvaluations"].eq(10000).all(), "Metaheuristic scoring count differs")
    stages = data[["initialEvaluations", "iterationEvaluations", "localSearchEvaluations"]].sum(axis=1)
    require(stages.eq(data.totalEvaluations).all(), "Scoring stage sum differs")
    require(data[NORMS].gt(0).all().all(), "Nonpositive reference normalization")
    for experiment, sub in data.groupby("experiment"):
        if experiment == "E4_main":
            groupkey, variants, scales = BLOCK, ALGS, ALL_SCALES
            variant_column = "algorithm"
        elif experiment == "E2_ablation":
            groupkey, variants = BLOCK, list(VARIANT_LABELS)
            scales, variant_column = PRIMARY_SCALES, "variant"
        elif experiment == "weight_sensitivity":
            groupkey = ["weightProfile"] + BLOCK
            variants, scales, variant_column = ["LSCBO", "CBO", "GREEDY_LPT"], [100, 300, 500, 800, 1000], "algorithm"
        else:
            groupkey = ["vmProfile"] + BLOCK
            variants, scales, variant_column = ["LSCBO", "CBO", "GREEDY_LPT"], [500, 1000, 2000], "algorithm"
        require(set(sub.taskCount) == set(scales), f"Scales differ for {experiment}")
        grouped = sub.groupby(groupkey)
        require(grouped.size().eq(len(variants)).all(), f"Incomplete blocks in {experiment}")
        require(all(set(x) == set(variants) for x in grouped[variant_column].agg(list)), f"Method set differs in {experiment}")
        require(grouped[NORMS].nunique().eq(1).all().all(), f"Reference differs within {experiment} blocks")
        g = sub[sub.algorithm.eq("GREEDY_LPT") | sub.variant.eq("greedy_reference")]
        require(g.objective.eq(1).all(), f"Greedy reference F differs in {experiment}")
        require(np.array_equal(g[COMPONENTS].to_numpy(), g[NORMS].to_numpy()), f"Greedy physical reference differs in {experiment}")
    # Both numerator and denominator are printed to eight decimals. Check F
    # against the rigorous interval implied by those rounding bounds.
    halfunit = .5e-8
    component = data[COMPONENTS].to_numpy(float)
    norm = data[NORMS].to_numpy(float)
    w = np.array([weights(x) for x in data.weightProfile])
    low = (w * (component - halfunit) / (norm + halfunit)).sum(axis=1)
    high = (w * (component + halfunit) / (norm - halfunit)).sum(axis=1)
    f = data.objective.to_numpy()
    require(((f >= low - halfunit - 1e-12) & (f <= high + halfunit + 1e-12)).all(), "F inconsistent with formula and printed precision")
    computed = (w * component / norm).sum(axis=1)
    stage = data.groupby(["experiment", "variant"])[[
        "initialEvaluations", "iterationEvaluations", "localSearchEvaluations", "totalEvaluations"]].first().reset_index()
    return {"rows": len(data), "experiment_counts": expected, "duplicate_keys": 0,
            "nonfinite_numeric_values": 0, "paired_blocks_main_all": 570,
            "paired_blocks_main_primary": 300, "paired_blocks_ablation": 300 if "E2_ablation" in expected else 0,
            "objective_formula_rounding_interval_passed": True,
            "max_objective_error_using_printed_ratios": float(np.abs(computed - f).max()),
            "formula_max_rounding_interval_width": float((high-low).max()),
            "stage_counts": stage.to_dict("records")}


def main_results(main: pd.DataFrame) -> tuple[dict, dict[str, pd.DataFrame]]:
    primary = main[main.taskCount.isin(PRIMARY_SCALES)]
    means = main.groupby(["taskCount", "algorithm"]).objective.mean().unstack()[ALGS]
    p = primary.pivot(index=BLOCK, columns="algorithm", values="objective")
    l = primary[primary.algorithm.eq("LSCBO")].set_index(BLOCK).sort_index()
    g = primary[primary.algorithm.eq("GREEDY_LPT")].set_index(BLOCK).sort_index()
    reductions = 100 * (1-l.objective/g.objective)
    records, scale_records = [], []
    for n in PRIMARY_SCALES:
        a, b = l.xs(n), g.xs(n)
        item = {"taskCount": n, "n": 30}
        for metric in METRICS:
            item[f"lscbo_mean_{metric}"] = float(a[metric].mean())
            item[f"lscbo_sd_{metric}"] = float(a[metric].std(ddof=1))
            item[f"greedy_mean_{metric}"] = float(b[metric].mean())
            item[f"greedy_sd_{metric}"] = float(b[metric].std(ddof=1))
            item[f"reduction_pct_{metric}"] = float(100*(1-a[metric].mean()/b[metric].mean()))
            item[f"paired_reduction_pct_{metric}"] = float((100*(1-a[metric]/b[metric])).mean())
            for label, value in wtl(a[metric], b[metric], TIE if metric == "objective" else 0).items():
                item[f"{label}_{metric}"] = value
        scale_records.append(item)
    for opponent in ALGS[1:]:
        diff = p.LSCBO-p[opponent]
        clusters = diff.groupby(level="seed").mean()
        result = wilcoxon(clusters, alternative="two-sided", zero_method="zsplit")
        records.append({"opponent": opponent, "n_blocks": 300, "n_seed_clusters": 30,
                        **wtl(p.LSCBO, p[opponent]),
                        "median_difference": float(diff.median()), "mean_difference": float(diff.mean()),
                        "difference_cluster_ci": cluster_summary(diff),
                        "raw_seed_cluster_p": float(result.pvalue)})
    for r, value in zip(records, holm([x["raw_seed_cluster_p"] for x in records])):
        r["holm_seed_cluster_p"] = value
    component = [{"metric": m, "lscbo_mean": float(l[m].mean()), "greedy_mean": float(g[m].mean()),
                  "ratio_of_pooled_means": float(l[m].mean()/g[m].mean()),
                  "mean_instance_ratio": float((l[m]/g[m]).mean()), **wtl(l[m],g[m],0)} for m in COMPONENTS]
    increments = pd.DataFrame(.25*(l[COMPONENTS].to_numpy()/l[NORMS].to_numpy()-1),
                              index=l.index, columns=COMPONENTS)
    cmean = increments.mean()
    decomposition = {"definition": "Mean weighted excess over Greedy-LPT F=1; printed normalization constants",
                     "terms": [{"metric": m, "mean_excess_F": float(cmean[m]),
                                "mean_excess_percentage_points": float(100*cmean[m]),
                                "share_of_total_excess_percent": float(100*cmean[m]/cmean.sum())} for m in COMPONENTS],
                     "sum_excess_F_from_printed_components": float(cmean.sum()),
                     "observed_mean_excess_F": float((l.objective-1).mean()),
                     "mean_rounding_discrepancy": float(cmean.sum()-(l.objective-1).mean())}
    large = [{"taskCount": n, "best_metaheuristic": means.loc[n].drop("GREEDY_LPT").idxmin(),
              "lscbo_mean_F": float(means.loc[n,"LSCBO"]), "woa_mean_F": float(means.loc[n,"WOA"]),
              "woa_reduction_over_lscbo_percent": float(100*(1-means.loc[n,"WOA"]/means.loc[n,"LSCBO"]))} for n in ALL_SCALES]
    clusters = p.groupby(level="seed").mean()
    friedman = friedmanchisquare(*(clusters[a] for a in ALGS))
    summary = {"primary": {"mean_F_by_algorithm": primary.groupby("algorithm").objective.mean().to_dict(),
                           "mean_paired_reduction_over_greedy_percent": cluster_summary(reductions),
                           "mean_F_seed_cluster_ci": cluster_summary(l.objective),
                           "median_paired_reduction_percent": float(reductions.median()),
                           "median_F": float(l.objective.median()),
                           "lowest_metaheuristic_at_scales": [r["taskCount"] for r in large if r["taskCount"]<=1000 and r["best_metaheuristic"]=="LSCBO"],
                           "seed_cluster_friedman_statistic": float(friedman.statistic),
                           "seed_cluster_friedman_p": float(friedman.pvalue)},
               "main_by_scale": scale_records, "main_pairwise": records,
               "main_component_pooled": component,
               "objective_excess_decomposition": decomposition, "large_scale": large}
    objective_rows, component_rows = [], []
    for (n, alg), block in main.groupby(["taskCount", "algorithm"]):
        ci = bootstrap(block.sort_values("seed").objective.to_numpy())
        objective_rows.append({"taskCount": n, "algorithm": alg, "n": 30,
                               "mean": ci["mean"], "ci_low": ci["low"], "ci_high": ci["high"],
                               "source": "cold_E4_20261011", "quality_observations_per_block": 1,
                               "recorded_scoring_budget": int(block.totalEvaluations.iloc[0])})
        for metric in METRICS:
            component_rows.append({"taskCount": n, "algorithm": alg, "metric": metric,
                                   "n": 30, "mean": float(block[metric].mean()),
                                   "sample_sd": float(block[metric].std(ddof=1))})
    contrib = -increments.groupby(level="taskCount").mean()*100
    contrib.index.name = "taskCount"
    pair_reduction = pd.DataFrame({"reference_F": g.objective, "lscbo_F": l.objective,
                                   "reduction_pp": reductions}).reset_index()
    return summary, {"main_scale_summary": pd.DataFrame(scale_records),
                     "main_pairwise": pd.json_normalize(records),
                     "main_component_pooled": pd.DataFrame(component),
                     "main_all_scale_objective_means": means.reset_index(),
                     "cloud_objective_summary": pd.DataFrame(objective_rows),
                     "cloud_components_summary": pd.DataFrame(component_rows),
                     "cloud_objective_contributions": contrib.reset_index(),
                     "cloud_objective_paired_reductions": pair_reduction}


def ablation_results(data: pd.DataFrame) -> tuple[dict, dict[str, pd.DataFrame]]:
    a = data[data.experiment.eq("E2_ablation")]
    aggregate = a.groupby("variant")[METRICS+["runtimeMs"]].mean().sort_values("objective").reset_index()
    p = a.pivot(index=BLOCK, columns="variant", values="objective")
    records = []
    for variant in VARIANT_LABELS:
        if variant in ["LSCBO_full", "greedy_reference"]:
            continue
        diff = p[variant]-p.LSCBO_full
        clusters = diff.groupby(level="seed").mean()
        pv = wilcoxon(clusters, alternative="two-sided", zero_method="zsplit").pvalue
        records.append({"variant": variant, "mean_F": float(p[variant].mean()),
                        "mean_F_difference_vs_full": float(diff.mean()),
                        "relative_change_ratio_of_means_percent": float(100*(p[variant].mean()/p.LSCBO_full.mean()-1)),
                        "mean_paired_relative_change_percent": float((100*(p[variant]/p.LSCBO_full-1)).mean()),
                        "changed_objective_blocks": int(diff.abs().gt(TIE).sum()),
                        "full_wins": wtl(p.LSCBO_full,p[variant])["wins"],
                        "full_ties": wtl(p.LSCBO_full,p[variant])["ties"],
                        "full_losses": wtl(p.LSCBO_full,p[variant])["losses"],
                        "difference_cluster_ci": cluster_summary(diff), "raw_seed_cluster_p": float(pv)})
    for r, value in zip(records, holm([r["raw_seed_cluster_p"] for r in records])):
        r["holm_seed_cluster_p_all_six_ablation_comparisons"] = value
    full = a[a.variant.eq("LSCBO_full")].set_index(BLOCK)
    for r in records:
        other = a[a.variant.eq(r["variant"])].set_index(BLOCK)
        r["component_changed_blocks"] = {m: int((other[m]-full[m]).ne(0).sum()) for m in COMPONENTS}
        r["mean_paired_runtime_difference_vs_full_ms"] = float((other.runtimeMs-full.runtimeMs).mean())
        r["median_paired_runtime_difference_vs_full_ms"] = float((other.runtimeMs-full.runtimeMs).median())
    return {"ablation": aggregate.to_dict("records"), "ablation_pairwise": records}, {
        "ablation_summary": aggregate, "ablation_pairwise": pd.json_normalize(records)}


def profile_results(data: pd.DataFrame) -> tuple[dict, dict[str, pd.DataFrame]]:
    profiles, cells, seed_rows = [], [], []
    for experiment, col in [("weight_sensitivity","weightProfile"),("vm_heterogeneity","vmProfile")]:
        order = (["W0_equal","W1_makespan_priority","W2_energy_priority","W3_cost_priority","W4_balance_priority"]
                 if experiment=="weight_sensitivity" else
                 ["near_homogeneous_1_5_to_1","medium_5_to_1","heterogeneous_20_to_1"])
        for profile in order:
            sub = data[data.experiment.eq(experiment)&data[col].eq(profile)]
            l = sub[sub.algorithm.eq("LSCBO")].set_index(BLOCK).sort_index()
            g = sub[sub.algorithm.eq("GREEDY_LPT")].set_index(BLOCK).sort_index()
            increase = 100*(l.objective/g.objective-1)
            label = PROFILE_LABELS[profile]
            item = {"experiment": experiment, "profile": profile, "label": label,
                    "metric": "mean_paired_F_increase_over_greedy_percent",
                    **cluster_summary(increase), "mean_F": float(l.objective.mean()), **wtl(l.objective,g.objective),
                    "clusters":30,"rows":len(l),"scales":sorted(l.index.get_level_values("taskCount").unique().tolist()),
                    "resamples":RESAMPLES,"rng":"PCG64","rng_seed":RNG_SEED,"quantile_method":"linear"}
            profiles.append(item)
            for seed, block in increase.groupby(level="seed"):
                seed_rows.append({"experiment":experiment,"profile":profile,"seed":seed,
                                  "scale_count":len(block),"mean_increase_percent":float(block.mean())})
            for n in item["scales"]:
                a,b = l.xs(n),g.xs(n)
                cells.append({"experiment":experiment,"profile":profile,"label":label,"taskCount":n,"n":30,
                              "mean_F":float(a.objective.mean()),"sample_sd_F":float(a.objective.std(ddof=1)),
                              "paired_reduction_F_percent":float((100*(1-a.objective/b.objective)).mean()),
                              "paired_reduction_imbalance_percent":float((100*(1-a.imbalance/b.imbalance)).mean()),
                              **wtl(a.objective,b.objective)})
    return {"profiles":profiles,"profile_scale_details":cells}, {
        "fig8_profile_bootstrap":pd.DataFrame(profiles),
        "fig8_seed_cluster_means":pd.DataFrame(seed_rows),"profile_scale_details":pd.DataFrame(cells)}


def runtime_results(main: pd.DataFrame) -> tuple[list[dict],pd.DataFrame]:
    records = []
    for n in PRIMARY_SCALES:
        record = {"taskCount":n}
        for alg in ALGS:
            sub = main[main.taskCount.eq(n)&main.algorithm.eq(alg)].runtimeMs
            record[alg] = {"median":float(sub.median()),"q1":float(sub.quantile(.25)),
                           "q3":float(sub.quantile(.75)),"mean":float(sub.mean()),
                           "minimum":int(sub.min()),"maximum":int(sub.max())}
        records.append(record)
    return records,pd.json_normalize(records)


def draw_fig8(profiles:list[dict],output:Path,legend_location:str="upper left") -> None:
    configure()
    fig=plt.figure(figsize=(WIDTH_INCHES*1.22,4.2))
    ax=fig.add_axes([.215,.18,.75,.76])
    bars=[]
    ypos=np.array([0,1,2,3,4,5.35,6.35,7.35])
    highest=max(r["high"] for r in profiles)
    for i,r in enumerate(profiles):
        color=COLORS["weights"] if i<5 else COLORS["vms"]
        bar=ax.barh(ypos[i],r["mean"],height=.55,color=color,edgecolor="white",
                    hatch="//" if i<5 else None,linewidth=.5,zorder=2)[0]
        bar.set_gid(f"profile_bar_{i}")
        bars.append(bar)
        ax.errorbar(r["mean"],ypos[i],xerr=np.array([[r["mean"]-r["low"]],[r["high"]-r["mean"]]]),
                    fmt="none",ecolor="#333333",capsize=2.5,elinewidth=.9,zorder=3)
        ax.text(r["high"]+highest*.016,ypos[i],f"{r['mean']:,.0f}",va="center",fontsize=TICK_LABEL_SIZE,color=color)
    ax.set_yticks(ypos,[r["label"] for r in profiles])
    ax.set_xlim(0,highest*1.26)
    ax.set_ylim(7.95,-2.1)
    ax.axhline(4.68,color="#AAAAAA",ls="--",linewidth=.7)
    grid(ax,which="x")
    ax.set_xlabel(r"Mean paired increase in $F$ over Greedy-LPT (%)",labelpad=5)
    ax.ticklabel_format(style="plain",axis="x")
    handles=[Patch(facecolor=COLORS["weights"],edgecolor="white",hatch="//",label="Weight profiles"),
             Patch(facecolor=COLORS["vms"],label="VM-capacity profiles")]
    from matplotlib.path import Path as MplPath
    assert legend_location=='upper left'
    location=legend_location
    leg=boxed_legend(ax,handles,columns=1)
    covered=0
    leg.set_gid("legend_Fig8_0")
    fig.text(.5,.027,"Cold start; 95% percentile intervals over 30 seed clusters",ha="center",fontsize=TICK_LABEL_SIZE)
    output.mkdir(parents=True,exist_ok=True)
    fig.canvas.draw()
    renderer=fig.canvas.get_renderer()
    box=ax.get_position()
    axis_box=np.array([box.x0,box.y0,box.x1,box.y1])*np.array([
        fig.get_figwidth()*72,fig.get_figheight()*72,
        fig.get_figwidth()*72,fig.get_figheight()*72])
    legend_box=leg.get_window_extent(renderer).extents*72/fig.dpi
    assert axis_box[0]<=legend_box[0]<legend_box[2]<=axis_box[2]
    assert axis_box[1]<=legend_box[1]<legend_box[3]<=axis_box[3]
    layout={**panel_metrics(ax),"legend_policy":"boxed shared position determined first from Fig7(d)",
            "each_panel_has_legend":True,"axes_bbox_from_bottom_pt":axis_box.tolist(),
            "legend_bbox_from_bottom_pt":legend_box.tolist(),"legend_position":location,
            "legend_gid":leg.get_gid(),"legend_labels":[t.get_text() for t in leg.get_texts()],
            "legend_boxed":True,"legend_anchor":list(leg._anchor),"legend_rows":2,"legend_columns":1,
            "covered_data_artists":int(covered),
            "profiles":profiles,"bars":[]}
    for i,bar in enumerate(bars):
        vertices=bar.get_transform().transform(bar.get_path().vertices)*72/fig.dpi
        vertices[:,1]=fig.get_figheight()*72-vertices[:,1]
        layout["bars"].append({"gid":bar.get_gid(),"profile_index":i,
                               "mean":bar.get_width(),"color":COLORS["weights"] if i<5 else COLORS["vms"],
                               "expected_vector_points_pt":vertices.tolist()})
    (output/"Fig8_layout.json").write_text(json.dumps(layout,indent=2),encoding="utf-8")
    for ext in ["pdf","svg","png"]:
        fig.savefig(output/f"Fig8.{ext}",dpi=300,facecolor="white",
                    metadata={"Creator":"Cold-start analysis, supplied 2026-10-11 data"} if ext=="pdf" else None)
    plt.close(fig)


def run(args:argparse.Namespace) -> None:
    require(not args.out.resolve().is_relative_to(ROOT), "Analysis outputs must be outside the repository")
    require(not args.figures.resolve().is_relative_to(ROOT), "Figure outputs must be outside the repository")
    sources=[args.data/"冷启动_3990行.csv",args.data/"冷启动_敏感性_3060行.csv"]
    data=pd.concat([pd.read_csv(p) for p in sources],ignore_index=True)
    checks=verify(data)
    main=data[data.experiment.eq("E4_main")]
    summary,frames=main_results(main)
    ab,abframes = ablation_results(data) if args.include_archived_ablation else ({},{})
    profile,profileframes=profile_results(data)
    runtime,runtimeframe=runtime_results(main)
    summary.update(ab)
    summary.update(profile)
    summary["runtime"]=runtime
    current = data[~data.experiment.eq("E2_ablation")].copy()
    checks["current_experiment_rows"] = len(current)
    checks["current_experiment_counts"] = current.groupby("experiment").size().to_dict()
    checks["archived_ablation_rows_excluded_from_current_experiments"] = 0
    summary["verification"]=checks
    summary["protocol"]={"initialization":"All population vectors random; Greedy-LPT only normalization reference",
                         "cost_model":"batch_reserved_seconds",
                         "cost_description":"Fixed fleet reserved at time zero and released together; makespan times total synthetic VM rate, continuous seconds",
                         "populationSize":100,"nominal_maxIterations":100,"recorded_total_budget":10000,
                         "full_lscbo_initial":100,"full_lscbo_core_iteration_evaluations":500,
                         "full_lscbo_local_trials":9400,"primary_scales":PRIMARY_SCALES,"external_scales":ALL_SCALES[10:],
                         "seeds":SEEDS,"bootstrap_resamples":RESAMPLES,"bootstrap_rng":"PCG64",
                         "bootstrap_seed":RNG_SEED,"bootstrap_quantile":"linear",
                         "bootstrap_unit":"complete scenario seed; scales fixed",
                         "source_files":[{"path":str(p),"sha256":hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources]}
    args.out.mkdir(parents=True,exist_ok=True)
    data.to_csv(args.out/"claim_full_raw.csv",index=False,float_format="%.8f")
    current.to_csv(args.out/"claim_current_raw.csv",index=False,float_format="%.8f")
    main.to_csv(args.out/"cloud_main_E4_20261011.csv",index=False,float_format="%.8f")
    for name,frame in {**frames,**abframes,**profileframes,"runtime_summary":runtimeframe}.items():
        frame.to_csv(args.out/f"{name}.csv",index=False,float_format="%.12g")
    draw_fig8(summary["profiles"],args.figures)
    (args.out/"cold_analysis.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    manifest={name:{"sha256":hashlib.sha256((args.out/name).read_bytes()).hexdigest(),
                    "source":"Supplied cold-start sensitivity CSV; integrate_cold_analysis.py",
                    "metric":"mean_paired_F_increase_over_greedy_percent"}
              for name in ["fig8_profile_bootstrap.csv","fig8_seed_cluster_means.csv"]}
    (args.out/"fig8_provenance.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"verified":checks,"primary":summary["primary"],
                      "figures":str(args.figures)},ensure_ascii=False,indent=2))


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data",type=Path,default=ROOT/"实验数据/CloudSim/data")
    parser.add_argument("--out",type=Path,default=RUNTIME/"analysis")
    parser.add_argument("--figures",type=Path,default=RUNTIME/"figures")
    parser.add_argument("--include-archived-ablation",action="store_true",
                        help="Reproduce archival E2 summaries; excluded from current experiments by default")
    run(parser.parse_args())
