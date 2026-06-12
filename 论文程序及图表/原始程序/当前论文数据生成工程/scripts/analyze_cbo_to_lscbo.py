# -*- coding: utf-8 -*-
"""
分析 CBO -> LSCBO 的优化路径贡献
读 E1 (CBO baseline) + E2 (LSCBO 各变体)，按 (M,seed) 配对统计。
"""
import pandas as pd, numpy as np
from scipy import stats

df1 = pd.read_csv(r'results\E1_Main_20260605_000018.csv')
df2 = pd.read_csv(r'results\E2_Ablation_20260605_000119.csv')

# 对齐 CBO baseline 与 LSCBO 变体
e1_cbo = df1[df1.Algorithm=='CBO'].sort_values(['TaskCount','Seed'])[['TaskCount','Seed','Makespan']].rename(columns={'Makespan':'CBO'})
variants = {
    'LSCBO_ops_only': 'base',       # CBO + Lévy + 旋转 + 动态权重 (无 LS)
    'no_Levy':        'noPhase1',   # 移除 Lévy 看贡献
    'no_Rotation':    'noPhase2',
    'no_Attack':      'noPhase3',
    'pop50':          'pop50',
    'LS_only':        'LS-only',    # 仅 LS（无 LSCBO 算子）
    'FULL':           'FULL',       # LSCBO 算子 + LS
}

merged = e1_cbo.copy()
for col, vn in variants.items():
    e2v = df2[df2.Variant==vn].sort_values(['TaskCount','Seed'])[['TaskCount','Seed','Makespan']].rename(columns={'Makespan':col})
    merged = merged.merge(e2v, on=['TaskCount','Seed'])

print('='*72)
print('CBO → LSCBO 优化路径完整分析（每对都在相同 M+seed 上对齐）')
print('='*72)
print(f'对齐样本数: {len(merged)} (5 scales × 30 seeds)')
print()

def report(name, a, b, label_a='CBO baseline', label_b='LSCBO 变体'):
    _,p = stats.wilcoxon(a, b)
    d = (a.mean()-b.mean())/np.sqrt((a.std()**2+b.std()**2)/2)
    pct = (a.mean()-b.mean())/a.mean()*100
    arrow = '↓优于' if b.mean()<a.mean() else '↑劣于'
    sig = '***' if p<0.001 else ('**' if p<0.01 else ('*' if p<0.05 else 'ns'))
    print(f'{name:<30} {label_b} {arrow} {label_a}: {pct:+5.1f}%  p={p:.2e}{sig}  d={d:+.2f}')

print('### Step 1: CBO baseline → LSCBO 算子（不含 LS）')
print('  即"用 Lévy + 动态权重 + 阶段化改造原版 CBO"的纯算子贡献')
print()
report('STEP 1 (整体)', merged.CBO.values, merged.LSCBO_ops_only.values)
print()
print('按规模分解:')
print(f'{"M":<8} {"CBO":<10} {"LSCBO-ops":<12} {"改进%":<8} {"p":<10} {"d":<6}')
for M in sorted(merged.TaskCount.unique()):
    sub = merged[merged.TaskCount==M]
    c = sub.CBO.values; l = sub.LSCBO_ops_only.values
    _,p = stats.wilcoxon(c, l)
    d = (c.mean()-l.mean())/np.sqrt((c.std()**2+l.std()**2)/2)
    pct = (c.mean()-l.mean())/c.mean()*100
    print(f'{M:<8} {c.mean():<10.1f} {l.mean():<12.1f} {pct:+6.1f}%  {p:.2e}  {d:+.2f}')
print()

print('### Step 2: LSCBO 算子内部消融（验证每个新增组件的必要性）')
print()
report('  Phase 1 (Lévy 飞行)', merged.LSCBO_ops_only.values, merged.no_Levy.values, 'LSCBO 算子', 'no_Levy')
report('  Phase 2 (旋转矩阵)', merged.LSCBO_ops_only.values, merged.no_Rotation.values, 'LSCBO 算子', 'no_Rotation')
report('  Phase 3 (动态权重攻击)', merged.LSCBO_ops_only.values, merged.no_Attack.values, 'LSCBO 算子', 'no_Attack')
print()
print('解读: "no_XXX 劣于 LSCBO 算子" 即"XXX 组件对结果有正贡献"')
print()

print('### Step 3: 加入种群放大 pop50 的边际贡献')
print()
report('  pop30 → pop50', merged.LSCBO_ops_only.values, merged.pop50.values, 'LSCBO-pop30', 'LSCBO-pop50')
print()

print('### Step 4: 加入任务迁移局部搜索（FULL = LSCBO 算子 + LS）')
print()
report('  LSCBO 算子 → LSCBO+LS', merged.LSCBO_ops_only.values, merged.FULL.values, 'LSCBO 算子', 'LSCBO+LS (FULL)')
print()
report('  LSCBO+LS vs LS-only', merged.LS_only.values, merged.FULL.values, 'LS-only', 'LSCBO 算子+LS')
print()

print('### Step 5: 端到端 CBO → LSCBO+LS 总改进')
print()
report('  ★ CBO baseline → LSCBO-LS', merged.CBO.values, merged.FULL.values, 'CBO baseline', 'LSCBO-LS (FULL)')
print()
print('按规模分解:')
print(f'{"M":<8} {"CBO":<10} {"LSCBO-LS":<12} {"改进%":<8} {"p":<10} {"d":<6}')
for M in sorted(merged.TaskCount.unique()):
    sub = merged[merged.TaskCount==M]
    c = sub.CBO.values; l = sub.FULL.values
    _,p = stats.wilcoxon(c, l)
    d = (c.mean()-l.mean())/np.sqrt((c.std()**2+l.std()**2)/2)
    pct = (c.mean()-l.mean())/c.mean()*100
    print(f'{M:<8} {c.mean():<10.1f} {l.mean():<12.1f} {pct:+6.1f}%  {p:.2e}  {d:+.2f}')
print()

print('### Step 6: 各组件贡献量化分解')
print()
cbo_mean = merged.CBO.mean()
full_mean = merged.FULL.mean()
total_improvement = cbo_mean - full_mean

stages = [
    ('CBO baseline', cbo_mean, 0),
    ('+ LSCBO 算子 (Lévy+旋转+动态权重+阶段化)', merged.LSCBO_ops_only.mean(),
     cbo_mean - merged.LSCBO_ops_only.mean()),
    ('+ 任务迁移局部搜索 (LS)', merged.FULL.mean(),
     merged.LSCBO_ops_only.mean() - merged.FULL.mean()),
]

print(f'{"阶段":<48} {"makespan":<12} {"该步改进":<12} {"占总比":<8}')
print('-'*82)
for name, val, delta in stages:
    pct = delta/total_improvement*100 if total_improvement>0 else 0
    print(f'{name:<48} {val:<12.1f} {delta:<+12.1f} {pct:<7.1f}%')
print('-'*82)
print(f'{"总改进":<48} {full_mean:<12.1f} {total_improvement:<+12.1f} 100.0%')
