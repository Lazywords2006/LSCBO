"""Audit experiment files, raw records, derived statistics, and result plots."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import numpy as np
import pandas as pd

ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'实验数据/CloudSim'
SCRIPTS=Path(__file__).resolve().parent
KEYS=['taskCount','seed']

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module

def compare(expected,actual,label):
    if isinstance(expected,dict):
        for key,value in expected.items():
            assert key in actual,(label,key)
            compare(value,actual[key],label+'.'+key)
    elif isinstance(expected,list):
        assert len(expected)==len(actual),label
        for index,(left,right) in enumerate(zip(expected,actual)):
            compare(left,right,f'{label}[{index}]')
    elif isinstance(expected,(int,float,np.number)):
        np.testing.assert_allclose(expected,actual,rtol=1e-10,atol=1e-8,err_msg=label)
    else:assert expected==actual,(label,expected,actual)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',default='D:/CodexRuntime/runs/lscbo-release-audit' if os.name=='nt' else None)
    args=parser.parse_args()
    if not args.runtime:parser.error('--runtime is required on non-Windows systems')
    runtime=Path(args.runtime).resolve()
    if runtime.is_relative_to(ROOT):parser.error('Audit outputs must be outside the repository')
    runtime.mkdir(parents=True,exist_ok=True)
    os.environ['LSCBO_RUNTIME']=str(runtime)
    for key in ['TEMP','TMP','MPLCONFIGDIR']:os.environ[key]=str(runtime)
    manifest=json.loads((ROOT/'RELEASE_MANIFEST.json').read_text(encoding='utf-8'))
    assert manifest['scope']=='experiments_only'
    actual_files={p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*') if p.is_file()
                  and '.git' not in p.parts and '__pycache__' not in p.parts and p.name!='RELEASE_MANIFEST.json'}
    assert actual_files==set(manifest['files'])
    for name,digest in manifest['files'].items():
        assert sha(ROOT/name)==digest,name
        assert Path(name).parts[0] in ['实验代码','实验数据','.gitignore','.gitattributes','LICENSE','README.md'],name
        assert Path(name).suffix.lower() not in ['.tex','.bib','.doc','.docx'],name
    assert not (ROOT/'论文').exists() and not (ROOT/'参考论文').exists()
    source=ROOT/'实验代码/CloudSim程序/src/main/java/com/edcbo/research/claim/ClaimPreservingCloudSimExperiment.java'
    assert sha(source)==manifest['main_source_sha256']=='a52444877768a42129eb9dcd637f9a3d18f25eb94a8441e21b541ff773854b15'
    data=OUT/'data';summary=data/'图表与统计汇总';selection=data/'成本模型追加验证'
    main_rows=pd.read_csv(data/'冷启动_3990行.csv')
    sens=pd.read_csv(data/'冷启动_敏感性_3060行.csv')
    assert len(main_rows)==3990 and len(sens)==3060
    assert sens.groupby('experiment').size().to_dict()=={'vm_heterogeneity':810,'weight_sensitivity':2250}
    assert sorted(main_rows.taskCount.unique())==list(range(100,1001,100))+list(range(2000,10001,1000))
    assert sorted(main_rows.seed.unique())==list(range(43,73))
    assert not main_rows.duplicated(KEYS+['algorithm']).any()
    for name in ['主实验协议.csv','敏感性协议.csv']:
        meta=dict(line.split(',',1) for line in (data/name).read_text(encoding='utf-8').splitlines()[1:] if ',' in line)
        assert meta['adoptedCostModel']=='batch_reserved_seconds'
    helper=load('experiment_analysis',SCRIPTS/'analysis_helpers.py')
    checked=helper.verify(pd.concat([main_rows,sens],ignore_index=True))
    stored=json.loads((summary/'analysis.json').read_text(encoding='utf-8'))
    assert stored['protocol']['cost_model']=='batch_reserved_seconds'
    main_stats,main_frames=helper.main_results(main_rows)
    profile_stats,profile_frames=helper.profile_results(sens)
    runtime_stats,runtime_frame=helper.runtime_results(main_rows)
    compare(main_stats,stored,'main_statistics')
    compare(profile_stats,stored,'sensitivity_statistics')
    compare(runtime_stats,stored['runtime'],'runtime_statistics')
    csv_checks=[]
    frames={**main_frames,**profile_frames,'runtime_summary':runtime_frame}
    for name,frame in frames.items():
        path=summary/(name+'.csv')
        if not path.exists():continue
        actual=pd.read_csv(path)
        frame=frame.copy()
        for column in frame.columns:
            frame[column]=frame[column].map(lambda value:str(value) if isinstance(value,(list,dict)) else value)
        pd.testing.assert_frame_equal(frame.reset_index(drop=True),actual,check_dtype=False,check_names=False,check_like=True,
                                      check_exact=False,rtol=1e-10,atol=1e-7,obj=name)
        csv_checks.append(name)
    assert len(csv_checks)==9,csv_checks
    raw=pd.read_csv(data/'直接精修_4500条计时.csv')
    unique=pd.read_csv(data/'直接精修_900条质量.csv')
    assert len(raw)==4500 and len(unique)==900
    derived=raw.sort_values('repetition').drop_duplicates(['method']+KEYS).sort_values(['method']+KEYS).reset_index(drop=True)
    pd.testing.assert_frame_equal(unique.sort_values(['method']+KEYS).reset_index(drop=True),derived)
    diagnostics=json.loads((summary/'diagnostics.json').read_text(encoding='utf-8'))
    for method,record in diagnostics['direct']['methods'].items():
        block=unique[unique.method==method]
        assert len(block)==300
        compare(float(block.objective.mean()),record['meanF'],method+'.mean_F')
        compare(float(block.acceptedExchanges.mean()),record['mean_accepted_exchanges'],method+'.accepted_exchanges')
        timing=raw[raw.method==method].groupby(KEYS).elapsedMs.median().mean()
        compare(float(timing),record['mean_block_median_time_ms'],method+'.median_time')
    fresh=pd.read_csv(selection/'追加种子103-132/batch_reserved_seconds/claim_mainfull_raw.csv')
    control=pd.read_csv(selection/'追加种子103-132/completion/claim_mainfull_raw.csv')
    assert len(fresh)+len(control)==4200
    assert sorted(fresh.seed.unique())==list(range(103,133))
    comparison=pd.read_csv(selection/'全部公式效果比较.csv');assert comparison.model.nunique()==9
    selected=json.loads((selection/'selected_model.json').read_text(encoding='utf-8'))
    assert selected['model']=='batch_reserved_seconds'
    confirmation=json.loads((selection/'confirmation_analysis.json').read_text(encoding='utf-8'))
    batch=next(r for r in confirmation['results'] if r['model']=='batch_reserved_seconds')
    pivot=fresh.pivot(index=KEYS,columns='algorithm',values='cost')
    changes=100*(1-pivot.LSCBO/pivot.PSO)
    clusters=changes.groupby(level='seed').mean().to_numpy()
    draws=np.random.default_rng(20261011).integers(0,30,(30000,30))
    interval=np.quantile(clusters[draws].mean(axis=1),[.025,.975])
    compare(float(changes.mean()),batch['pairwise']['PSO']['mean_paired_reduction_percent'],'confirmation.PSO.mean')
    compare(interval.tolist(),batch['pairwise']['PSO']['CI95_seed_cluster'],'confirmation.PSO.interval')
    figure_verifier=load('experiment_figures',SCRIPTS/'figure_audit.py')
    figures,error=figure_verifier.verify_figures()
    report={'scope':'experiments_only','manifest_files_verified':len(manifest['files']),
            'main_rows':3990,'sensitivity_rows':3060,'direct_timing_rows':4500,'direct_unique_quality_rows':900,
            'confirmation_rows':4200,'derived_csv_files_verified':csv_checks,'figures':figures,
            'primary_mean_F':main_stats['primary']['mean_F_seed_cluster_ci']['mean'],
            'max_vector_coordinate_error_pt':error,'raw_objective_and_budget_checks':checked,
            'main_statistics_recomputed':True,'sensitivity_seed_cluster_intervals_recomputed':True,
            'runtime_statistics_recomputed':True,'confirmation_PSO_statistics_recomputed':True,
            'all_checks_passed':True}
    (runtime/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({key:report[key] for key in ['scope','manifest_files_verified','main_rows','sensitivity_rows',
                     'derived_csv_files_verified','primary_mean_F','all_checks_passed']},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
