# -*- coding: utf-8 -*-
"""E4: 修正版 LSCBO-LS-final 完整分析。"""
import pandas as pd, numpy as np
from scipy import stats

df = pd.read_csv(r'results\E4_LSCBOFinal_20260605_101152.csv')
order = ['LSCBO-LS-final','LSCBO-LS-wrong','GTO','WOA','GWO','HHO','DBO','CBO','PSO','AOA']

print('='*72)
print('E4: 修正版 LSCBO-LS-final 主对比 (10 算法, 1500 paired runs)')
print('='*72)
print()

print('### Makespan 均值 (按规模 x 算法) ###')
pivot = df.groupby(['TaskCount','Algorithm'])['Makespan'].mean().round(1).unstack()
print(pivot[order].to_string())
print()

print('### LBR 均值 ###')
pl = df.groupby(['TaskCount','Algorithm'])['LoadBalanceRatio'].mean().round(3).unstack()
print(pl[order].to_string())
print()

# Friedman 排名
ranks = []
for (M,seed), grp in df.groupby(['TaskCount','Seed']):
    ranks.append(grp.set_index('Algorithm')['Makespan'].rank().to_dict())
rdf = pd.DataFrame(ranks)
avg_ranks = rdf.mean().sort_values()

print('### Friedman 平均排名 ###')
print(avg_ranks.round(3).to_string())
print()

# Friedman 统计
k = len(order); N = len(rdf)
chi2 = 12*N/(k*(k+1)) * np.sum((avg_ranks.values - (k+1)/2)**2)
p_fried = 1 - stats.chi2.cdf(chi2, df=k-1)
q = 3.164  # k=10 alpha=0.05 (approximated)
CD = q * np.sqrt(k*(k+1)/(6*N))
print(f'Friedman chi2={chi2:.2f}, df={k-1}, p={p_fried:.2e}, Nemenyi CD={CD:.3f}')
print()

# 关键对比 1: final vs wrong
print('### 关键对比 1: LSCBO-LS-final vs LSCBO-LS-wrong ###')
final = df[df.Algorithm=='LSCBO-LS-final'].sort_values(['TaskCount','Seed'])['Makespan'].values
wrong = df[df.Algorithm=='LSCBO-LS-wrong'].sort_values(['TaskCount','Seed'])['Makespan'].values
_,p = stats.wilcoxon(final, wrong)
d = (wrong.mean()-final.mean())/np.sqrt((final.std()**2+wrong.std()**2)/2)
diff = (final.mean()-wrong.mean())/wrong.mean()*100
print(f'final makespan: {final.mean():.1f}  wrong makespan: {wrong.mean():.1f}')
print(f'final 改进 wrong: {diff:+.1f}%  p={p:.2e}  d={d:+.3f}')
print()

# 按规模
print('按规模:')
for M in sorted(df.TaskCount.unique()):
    f = df[(df.Algorithm=='LSCBO-LS-final')&(df.TaskCount==M)].sort_values('Seed')['Makespan'].values
    w = df[(df.Algorithm=='LSCBO-LS-wrong')&(df.TaskCount==M)].sort_values('Seed')['Makespan'].values
    _,p = stats.wilcoxon(f, w)
    diff = (f.mean()-w.mean())/w.mean()*100
    d = (w.mean()-f.mean())/np.sqrt((f.std()**2+w.std()**2)/2)
    print(f'  N={M}: final={f.mean():.1f}, wrong={w.mean():.1f}, diff={diff:+5.1f}%, p={p:.2e}, d={d:+.2f}')
print()

# 关键对比 2: final vs 各对手 (Holm)
print('### 关键对比 2: LSCBO-LS-final vs SOTA (Wilcoxon + Holm) ###')
pvals = []
for opp in [a for a in order if a not in ('LSCBO-LS-final','LSCBO-LS-wrong')]:
    a = df[df.Algorithm==opp].sort_values(['TaskCount','Seed'])['Makespan'].values
    _,p = stats.wilcoxon(final, a)
    d = (a.mean()-final.mean())/np.sqrt((a.std()**2+final.std()**2)/2)
    diff = (final.mean()-a.mean())/a.mean()*100
    pvals.append((p, opp, d, diff))
m = len(pvals)
sp = sorted(enumerate(pvals), key=lambda x: x[1][0])
for rank,(orig,(p,opp,d,diff)) in enumerate(sp):
    p_holm = min(p*(m-rank), 1.0)
    sig = '***' if p_holm<0.001 else ('**' if p_holm<0.01 else ('*' if p_holm<0.05 else 'ns'))
    print(f'  vs {opp:<4}: diff={diff:+5.1f}%  p_raw={p:.2e}  p_Holm={p_holm:.2e}{sig}  d={d:+.2f}')
print()

# 关键对比 3: end-to-end CBO -> LSCBO-LS-final
print('### 关键对比 3: CBO baseline → LSCBO-LS-final 端到端改进 ###')
cbo = df[df.Algorithm=='CBO'].sort_values(['TaskCount','Seed'])['Makespan'].values
_,p = stats.wilcoxon(cbo, final)
d = (cbo.mean()-final.mean())/np.sqrt((cbo.std()**2+final.std()**2)/2)
diff = (final.mean()-cbo.mean())/cbo.mean()*100
print(f'CBO: {cbo.mean():.1f}  LSCBO-LS-final: {final.mean():.1f}')
print(f'端到端改进: {diff:+.1f}% (p={p:.2e}, d={d:+.3f})')
print()
print('按规模:')
for M in sorted(df.TaskCount.unique()):
    c = df[(df.Algorithm=='CBO')&(df.TaskCount==M)].sort_values('Seed')['Makespan'].values
    f = df[(df.Algorithm=='LSCBO-LS-final')&(df.TaskCount==M)].sort_values('Seed')['Makespan'].values
    _,p = stats.wilcoxon(c, f)
    diff = (f.mean()-c.mean())/c.mean()*100
    d = (c.mean()-f.mean())/np.sqrt((c.std()**2+f.std()**2)/2)
    print(f'  N={M}: CBO={c.mean():.1f}, final={f.mean():.1f}, diff={diff:+5.1f}%, p={p:.2e}, d={d:+.2f}')
print()

# 胜场
print('### 胜场（每 (M, seed) makespan 最低）###')
wins = {a:0 for a in order}
for (M, seed), grp in df.groupby(['TaskCount','Seed']):
    bm = grp.set_index('Algorithm')['Makespan']
    wins[bm.idxmin()] += 1
print(sorted(wins.items(), key=lambda x: -x[1]))
