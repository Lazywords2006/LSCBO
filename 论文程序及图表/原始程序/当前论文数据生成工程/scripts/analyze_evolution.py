# -*- coding: utf-8 -*-
"""分析 CBO → LSCBO-LS 演进路径每一步的真实贡献。"""
import pandas as pd, numpy as np
from scipy import stats

df = pd.read_csv(r'results\E3_Evolution_20260605_100334.csv')
order = ['V1_CBO_orig','V2_addLevy','V3_addDynW','V4_addStaged','V5_addLS']

print('='*72)
print('CBO → LSCBO-LS 演进路径完整分析（750 paired runs）')
print('='*72)
print(f'阶段数: {len(order)}, 规模: {sorted(df.TaskCount.unique())}, seeds: {df.Seed.nunique()}')
print()

print('### 跨规模均值 makespan ###')
print(df.groupby(['TaskCount','Stage'])['Makespan'].mean().round(1).unstack()[order].to_string())
print()

print('### 跨所有规模整体 makespan & 累积改进 ###')
overall = df.groupby('Stage')['Makespan'].mean()
v1 = overall['V1_CBO_orig']
print(f'{"Stage":<18} {"makespan":<10} {"vs 上一步":<12} {"vs CBO 累积":<12} {"贡献占总":<8}')
print('-'*72)
prev = v1
for s in order:
    mk = overall[s]
    delta_step = prev - mk
    delta_total = v1 - mk
    pct_step = delta_step/v1*100 if v1>0 else 0
    pct_cum = delta_total/v1*100 if v1>0 else 0
    label = {
        'V1_CBO_orig': 'V1 CBO 原版',
        'V2_addLevy':  'V2 +Lévy 飞行',
        'V3_addDynW':  'V3 +动态权重',
        'V4_addStaged':'V4 +阶段化',
        'V5_addLS':    'V5 +局部搜索'
    }[s]
    print(f'{label:<18} {mk:<10.1f} {delta_step:<+12.1f} {delta_total:<+12.1f} {pct_step:<7.1f}%')
    prev = mk

print()
print('### 按规模分解（关键：哪个改进在大规模上更有效）###')
for M in sorted(df.TaskCount.unique()):
    sub = df[df.TaskCount==M].groupby('Stage')['Makespan'].mean()
    v1m = sub['V1_CBO_orig']
    print(f'\n[M={M}]')
    print(f'{"Stage":<18} {"makespan":<10} {"vs V1":<10} {"%":<8}')
    for s in order:
        delta = v1m - sub[s]
        pct = delta/v1m*100
        print(f'{s:<18} {sub[s]:<10.1f} {delta:<+10.1f} {pct:<+7.1f}%')

print()
print('### Wilcoxon 配对检验（每相邻阶段）###')
def get(stage):
    return df[df.Stage==stage].sort_values(['TaskCount','Seed'])['Makespan'].values

for i in range(1, len(order)):
    prev_s = order[i-1]; cur_s = order[i]
    a = get(prev_s); b = get(cur_s)
    _, p = stats.wilcoxon(a, b)
    d = (a.mean()-b.mean())/np.sqrt((a.std()**2+b.std()**2)/2)
    pct = (a.mean()-b.mean())/a.mean()*100
    arrow = 'improves' if b.mean()<a.mean() else 'worsens'
    sig = '***' if p<0.001 else ('**' if p<0.01 else ('*' if p<0.05 else 'ns'))
    print(f'  {prev_s} → {cur_s:<14} {arrow}: {pct:+5.1f}%  p={p:.2e}{sig}  d={d:+.3f}')

print()
print('### 端到端 V1 → V5（CBO 原版 → LSCBO-LS 完整版）###')
a = get('V1_CBO_orig'); b = get('V5_addLS')
_, p = stats.wilcoxon(a, b)
d = (a.mean()-b.mean())/np.sqrt((a.std()**2+b.std()**2)/2)
pct = (a.mean()-b.mean())/a.mean()*100
print(f'  V1 CBO → V5 LSCBO-LS: {pct:+5.1f}%  p={p:.2e}  d={d:+.3f}')
print(f'  CBO mean: {a.mean():.1f}, LSCBO-LS mean: {b.mean():.1f}')

print()
print('### LBR 演进 ###')
print(df.groupby(['TaskCount','Stage'])['LoadBalanceRatio'].mean().round(3).unstack()[order].to_string())
