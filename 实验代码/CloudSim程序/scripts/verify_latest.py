"""Audit every Section 4.2 table against new-cost raw rows and bundle sources."""
from pathlib import Path
import hashlib
import importlib.util
import json
import re
import shutil
import argparse
import os
import fitz
import numpy as np
import pandas as pd

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/"论文/Cost与云调度两个板块"
SCRIPTS=Path(__file__).resolve().parent
KEYS=['taskCount','seed']
METRICS=['makespan','energy','cost','imbalance','objective']
ALGS=['LSCBO','CBO','PSO','GWO','WOA','AOA','GREEDY_LPT']

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',default='D:/CodexRuntime/runs/lscbo-release-audit' if os.name=='nt' else None)
    args=parser.parse_args()
    if not args.runtime:parser.error('--runtime is required on non-Windows systems')
    runtime=Path(args.runtime).resolve();runtime.mkdir(parents=True,exist_ok=True)
    os.environ['LSCBO_RUNTIME']=str(runtime)
    for name in ['TEMP','TMP','MPLCONFIGDIR']:os.environ[name]=str(runtime)
    manifest=json.loads((ROOT/'RELEASE_MANIFEST.json').read_text(encoding='utf-8'))
    for name,expected in manifest['files'].items():
        assert sha(ROOT/name)==expected, name
    data=OUT/'数据';summary=data/'图表与统计汇总';selection=data/'成本模型追加验证'
    main=pd.read_csv(data/'冷启动_3990行.csv');sens=pd.read_csv(data/'冷启动_敏感性_3060行.csv')
    assert len(main)==3990 and len(sens)==3060
    assert sens.groupby('experiment').size().to_dict()=={'vm_heterogeneity':810,'weight_sensitivity':2250}
    assert sorted(main.taskCount.unique())==list(range(100,1001,100))+list(range(2000,10001,1000))
    assert sorted(main.seed.unique())==list(range(43,73))
    assert not main.duplicated(KEYS+['algorithm']).any()
    for name in ['主实验协议.csv','敏感性协议.csv']:
        # Historical metadata values can contain unquoted commas; its schema
        # is key followed by the complete remainder of the line.
        meta=dict(line.split(',',1) for line in (data/name).read_text(encoding='utf-8').splitlines()[1:] if ',' in line)
        assert meta['adoptedCostModel']=='batch_reserved_seconds'
    helper=load('profile_audit',SCRIPTS/'analysis_helpers.py')
    checked=helper.verify(pd.concat([main,sens],ignore_index=True))
    raw=pd.read_csv(data/'直接精修_4500条计时.csv')
    unique=pd.read_csv(data/'直接精修_900条质量.csv')
    derived=raw.sort_values('repetition').drop_duplicates(['method']+KEYS).sort_values(['method']+KEYS).reset_index(drop=True)
    pd.testing.assert_frame_equal(unique.sort_values(['method']+KEYS).reset_index(drop=True),derived)
    cloud=(OUT/'Cloud_Section4_2_替换章节.tex').read_text(encoding='utf-8')
    tables={}
    def table(label):
        text=cloud.split(r'\label{tab:'+label+'}',1)[1]
        middle=text.split(r'\midrule',1)[1].split(r'\bottomrule',1)[0]
        return [[c.strip() for c in line.strip().removesuffix(r'\\').split('&')]
                for line in middle.splitlines() if '&' in line]
    def exact(label,expected):
        actual=table(label)
        assert actual==expected,(label,[(a,b) for a,b in zip(actual,expected) if a!=b][:2])
        tables[label]={'rows':len(expected),'checked_cells':sum(len(row) for row in expected),'passed':True}
    primary=main[main.taskCount<=1000]
    means=primary.groupby(['taskCount','algorithm']).objective.mean().unstack()
    exact('cloud_main_objective',[[str(n)]+[f'{means.loc[n,a]:.4f}' for a in ALGS] for n in range(100,1001,100)])
    improvements=[];components=[]
    for n in range(100,1001,100):
        ls=primary[(primary.taskCount==n)&(primary.algorithm=='LSCBO')].set_index('seed').sort_index()
        g=primary[(primary.taskCount==n)&(primary.algorithm=='GREEDY_LPT')].set_index('seed').sort_index()
        row=[str(n)]
        for metric in METRICS:
            percent=100*(1-ls[metric].mean()/g[metric].mean())
            delta=ls[metric]-g[metric];tol=1e-8 if metric=='objective' else 0
            wtl='/'.join(str(int(v)) for v in [(delta< -tol).sum(),(abs(delta)<=tol).sum(),(delta>tol).sum()])
            row.append(r'\shortstack{'+f'{percent:.4f}'+r'\%\\('+wtl+')}')
        improvements.append(row)
        for frame,label in [(g,'Greedy-LPT'),(ls,'LSCBO')]:
            row=[str(n),label]
            for metric in METRICS:
                digits=5 if metric=='imbalance' else 3
                row.append(r'\shortstack{'+f'{frame[metric].mean():.{digits}f}'+r'\\('+f'{frame[metric].std(ddof=1):.{digits}f}'+')}')
            components.append(row)
    exact('main_improvement',improvements);exact('main_scale_dispersion',components)
    direct=[]
    for method,trials in [('pair_only_9400',9400),('pair_only_10000',10000)]:
        q=unique[unique.method==method]
        timing=raw[raw.method==method].groupby(KEYS).elapsedMs.median().mean()
        direct.append([f'Greedy + pair search, {trials} trials',str(trials+2),f'{q.objective.mean():.6f}',
                       f'{q.acceptedExchanges.mean():.2f}',f'{timing:.2f}'])
    exact('direct_pair',direct)
    for experiment,col,order,label in [
        ('weight_sensitivity','weightProfile',['W0_equal','W1_makespan_priority','W2_energy_priority','W3_cost_priority','W4_balance_priority'],'weight_scale_details'),
        ('vm_heterogeneity','vmProfile',['near_homogeneous_1_5_to_1','medium_5_to_1','heterogeneous_20_to_1'],'heterogeneity_scale_details')]:
        rows=[]
        for profile in order:
            subset=sens[(sens.experiment==experiment)&(sens[col]==profile)]
            for n in sorted(subset.taskCount.unique()):
                ls=subset[(subset.taskCount==n)&(subset.algorithm=='LSCBO')].set_index('seed').sort_index()
                g=subset[(subset.taskCount==n)&(subset.algorithm=='GREEDY_LPT')].set_index('seed').sort_index()
                delta=ls.objective-g.objective
                wtl='/'.join(str(int(v)) for v in [(delta< -1e-8).sum(),(abs(delta)<=1e-8).sum(),(delta>1e-8).sum()])
                rows.append([helper.PROFILE_LABELS[profile],str(n),f'{ls.objective.mean():.6f} '+r'$\pm$ '+f'{ls.objective.std(ddof=1):.6f}',
                    f'{(100*(1-ls.objective/g.objective)).mean():.4f}',f'{(100*(1-ls.imbalance/g.imbalance)).mean():.4f}',wtl])
        exact(label,rows)
    runtime=[]
    for n in range(100,1001,100):
        row=[str(n)]
        for alg in ['GREEDY_LPT','LSCBO']:
            block=primary[(primary.taskCount==n)&(primary.algorithm==alg)].runtimeMs
            row.append(f'{block.median():.1f} [{block.quantile(.25):.2f}, {block.quantile(.75):.2f}]')
        runtime.append(row)
    exact('runtime_descriptive',runtime)
    config=table('configuration_map')
    assert config==[['LSCBO','600','9400','10001','100'],['Adapted CBO, PSO, GWO, WOA, AOA','10000','0','10000','100'],['Greedy-LPT','--','--','1','--']]
    tables['configuration_map']={'rows':3,'passed':True}
    # All displayed summary means, confidence intervals, counts and the large
    # extension are checked independently of the manuscript generation script.
    ls=primary[primary.algorithm=='LSCBO'].set_index(KEYS).sort_index()
    g=primary[primary.algorithm=='GREEDY_LPT'].set_index(KEYS).sort_index()
    ci=helper.cluster_summary(ls.objective)
    expected_text=[f'{ls.objective.mean():.6f}',f"{ci['low']:.6f}--{ci['high']:.6f}",
                   f'{(100*(ls.cost/g.cost-1)).mean():.4f}']
    assert means.drop(columns='GREEDY_LPT').idxmin(axis=1).eq('LSCBO').all()
    other_means=primary[~primary.algorithm.isin(['GREEDY_LPT','LSCBO'])].groupby('algorithm').objective.mean()
    expected_text.append(f'{other_means.min():.6f}--{other_means.max():.6f}')
    pivot=primary.pivot(index=KEYS,columns='algorithm',values='cost')
    expected_text.append(f'{(100*(1-pivot.LSCBO/pivot.PSO)).mean():.4f}')
    delta=pivot.LSCBO-pivot.PSO
    expected_text.append('/'.join(str(int(v)) for v in [(delta<0).sum(),(delta==0).sum(),(delta>0).sum()]))
    for exp,col,order in [('weight_sensitivity','weightProfile',list(helper.PROFILE_LABELS)[:5]),
                          ('vm_heterogeneity','vmProfile',list(helper.PROFILE_LABELS)[5:])]:
        vals=[sens[(sens.experiment==exp)&(sens[col]==p)&(sens.algorithm=='LSCBO')].objective.mean() for p in order]
        digits=4 if exp=='weight_sensitivity' else 6
        expected_text.append(', '.join(f'{v:.{digits}f}' for v in vals))
    large=main[main.taskCount>=2000].groupby(['taskCount','algorithm']).objective.mean().unstack()
    reductions=100*(1-large.WOA/large.LSCBO)
    won=reductions[reductions>0]
    expected_text += [f'{won.min():.2f}--{won.max():.2f}',', '.join(str(n) for n in won.index)]
    for method in ['pair_only_9400','pair_only_10000']:
        q=unique[unique.method==method];expected_text.append(f'{q.objective.mean():.6f}')
        direct_delta=q.objective-1
        expected_text.append('/'.join(str(int(v)) for v in [(direct_delta< -1e-12).sum(),(abs(direct_delta)<=1e-12).sum(),(direct_delta>1e-12).sum()]))
    low=unique[unique.method=='pair_only_9400'].set_index(KEYS).sort_index()
    high=unique[unique.method=='pair_only_10000'].set_index(KEYS).sort_index()
    extra=high.objective-low.objective
    expected_text.append('/'.join(str(int(v)) for v in [(extra< -1e-12).sum(),(abs(extra)<=1e-12).sum(),(extra>1e-12).sum()]))
    medians=primary[primary.algorithm=='LSCBO'].groupby('taskCount').runtimeMs.median()
    expected_text.append(f'{medians.min():.1f}--{medians.max():.1f}')
    assert all(text in cloud for text in expected_text),[x for x in expected_text if x not in cloud]
    # Confirmation data are not substituted for the main screening-seed plots.
    fresh=pd.read_csv(selection/'追加种子103-132/batch_reserved_seconds/claim_mainfull_raw.csv')
    p=fresh.pivot(index=KEYS,columns='algorithm',values='cost')
    changes=100*(1-p.LSCBO/p.PSO);clusters=changes.groupby(level='seed').mean().to_numpy()
    draws=np.random.default_rng(20261011).integers(0,30,(30000,30))
    lo,hi=np.quantile(clusters[draws].mean(axis=1),[.025,.975])
    for x in [f'{changes.mean():.4f}',f'{lo:.4f}'+r'\%--'+f'{hi:.4f}']:
        assert x in cloud,x
    assert sorted(fresh.seed.unique())==list(range(103,133))
    comparison=pd.read_csv(selection/'全部公式效果比较.csv')
    assert comparison.model.nunique()==9
    rescored=pd.read_csv(selection/'confirmation_old_cost_rescoring.csv')
    batch=rescored[(rescored.native_model=='batch_reserved_seconds')&(rescored.algorithm=='LSCBO')].set_index(KEYS).sort_index()
    control=pd.read_csv(selection/'追加种子103-132/completion/claim_mainfull_raw.csv')
    control=control[control.algorithm=='LSCBO'].set_index(KEYS).sort_index()
    old_increase=100*(batch.completion_cost_recomputed/control.cost-1)
    old_clusters=old_increase.groupby('seed').mean().to_numpy()
    old_lo,old_hi=np.quantile(old_clusters[draws].mean(axis=1),[.025,.975])
    assert f'{old_increase.mean():.4f}' in cloud
    assert f'{old_lo:.4f}'+r'\%--'+f'{old_hi:.4f}' in cloud
    profile_stats,_=helper.profile_results(sens)
    csv=pd.read_csv(summary/'fig8_profile_bootstrap.csv')
    for record in profile_stats['profiles']:
        row=csv[csv.profile==record['profile']].iloc[0]
        np.testing.assert_allclose([row['mean'],row.low,row.high],[record['mean'],record['low'],record['high']],rtol=1e-11,atol=1e-8)
    figure_verifier=load('figure_audit',SCRIPTS/'figure_audit.py');figure_verifier.OUT=OUT
    figures,error=figure_verifier.verify_figures()
    pdf=fitz.open(OUT/'Cloud_Scheduling_Block.pdf');pdf_text='\n'.join(p.get_text() for p in pdf)
    for value in ['19.953855','0.974373','0.973684','24.189983','50.219427','53.619678']:
        assert value in pdf_text,value
    assert '20.043620' not in pdf_text and '0.980701' not in pdf_text
    report={'cost_model':'batch_reserved_seconds','primary_and_external_main_rows':3990,
        'weight_rows':2250,'capacity_rows':810,'direct_timing_rows':4500,'direct_unique_quality_rows':900,
        'confirmation_optimized_rows':4200,'table_audit':tables,'text_numerical_claims_checked':len(expected_text)+3,
        'figures':figures,'Fig8_means_and_seed_cluster_intervals_recomputed':True,
        'max_vector_coordinate_error_pt':error,'all_checks_passed':True,
        'prior_assignment_level_recomputation_reused_with_unchanged_hashed_sources':True,
        'standalone_PDF_text_verified':True,'checks':checked}
    (Path(args.runtime).resolve()/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'all_checks_passed':True,'tables':tables,'primary_F':float(ls.objective.mean()),'data':str(data)},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
