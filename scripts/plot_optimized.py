#!/usr/bin/env python3
"""
Comprehensive Performance Analysis for RideSync — Original + Optimized.

Generates two sets of plots:
  A. Original distributed analysis (replaces/regenerates the existing 9 plots)
  B. Optimized-specific analysis (worker scaling, Amdahl, efficiency)
  C. Comparison plots (optimized vs original vs baseline)

Run:
    python scripts/plot_optimized.py
"""
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

RESULTS = Path(__file__).parent.parent / 'results'

# ── Load data ─────────────────────────────────────────────────────────────────
orig = pd.read_csv(RESULTS / 'speedup.csv')
opt  = pd.read_csv(RESULTS / 'speedup-optimized.csv')

# Raw timing tables (from fresh benchmark run)
WORKERS   = [1, 2, 4, 8]
SCALES    = ['small', 'medium', 'large']
SCALE_COLORS = {'small': '#4C72B0', 'medium': '#DD8452', 'large': '#55A868'}
SCALE_MARKERS = {'small': 'o', 'medium': 's', 'large': '^'}

# Baseline times (single sequential run, same JVM settings)
BASELINE = {'small': 884.520, 'medium': 40618.474, 'large': 109642.952}

# Original distributed compute-only times (ms)
ORIG_TIMES = {
    'small':  {1: 625.679,  2: 572.378,  4: 528.748,  8: 558.528},
    'medium': {1: 23006.896, 2: 20023.528, 4: 19108.371, 8: 19582.557},
    'large':  {1: 61117.774, 2: 52558.500, 4: 52130.170, 8: 50522.299},
}

# Optimized distributed compute-only times (ms)
OPT_TIMES = {
    'small':  {1: 897.898,  2: 883.814,  4: 870.643,  8: 1010.494},
    'medium': {1: 12910.211, 2: 11577.473, 4: 11059.900, 8: 11532.493},
    'large':  {1: 33592.797, 2: 30052.506, 4: 28099.648, 8: 29787.271},
}

# Amdahl helpers
def amdahl(p, f):
    return 1.0 / ((1 - f) + f / p)

def estimate_f(S, p):
    if p <= 1 or S <= 1:
        return None
    return (p * (S - 1)) / (p * S - 1)


# ── Compute Amdahl parallel fractions ─────────────────────────────────────────
orig['f'] = orig.apply(lambda r: estimate_f(r['speedup'], r['p']), axis=1)
opt['f']  = opt.apply(lambda r: estimate_f(r['speedup'], r['p']), axis=1)

f_orig = orig[orig['f'].notna()]['f'].mean()
f_opt  = opt[opt['f'].notna()]['f'].mean()
p_range = np.arange(1, 9)

print(f"Amdahl parallel fraction — original: {f_orig:.3f}  optimized: {f_opt:.3f}")


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION A — Original Distributed Analysis (9 plots, same as before)
# ═══════════════════════════════════════════════════════════════════════════════

def save(fig, name):
    path = RESULTS / name
    fig.savefig(path, dpi=150, bbox_inches='tight')
    plt.close(fig)
    print(f"  saved {name}")


# ── A1: Amdahl's Law ──────────────────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = orig[orig['dataset'] == scale]
    ax.plot(sub['p'], sub['speedup'], marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.plot(p_range, [amdahl(p, f_orig) for p in p_range], 'r--',
        label=f'Amdahl (f={f_orig:.3f})', linewidth=2.5)
ax.plot(p_range, p_range, 'k:', label='Ideal Linear', linewidth=2.5)
ax.set_xlabel('Workers (p)', fontsize=13); ax.set_ylabel('Speedup S(p)', fontsize=13)
ax.set_title("Amdahl's Law: Measured vs Theoretical Speedup\n(Original Distributed)", fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks(p_range)
save(fig, '01-amdahls-law.png')

# ── A2: Strong Scaling ────────────────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = orig[orig['dataset'] == scale]
    ax.plot(sub['p'], sub['speedup'], marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.plot(p_range, p_range, 'k--', label='Ideal Linear', linewidth=2.5, alpha=0.6)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Speedup S(p)', fontsize=13)
ax.set_title('Strong Scaling: Fixed Problem Size, Increasing Workers\n(Original Distributed)', fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks(p_range)
save(fig, '02-strong-scaling.png')

# ── A3: Multi-Worker Speedup ──────────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = orig[orig['dataset'] == scale].sort_values('p')
    ax.plot(sub['p'], sub['speedup'], marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Speedup S(p)', fontsize=13)
ax.set_title('Multi-Worker Speedup vs Worker Count\n(Original Distributed)', fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8])
save(fig, '03-multi-worker-speedup.png')

# ── A4: Baseline Execution Times ──────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
baseline_ms = [BASELINE[s] for s in SCALES]
bars = ax.bar(SCALES, baseline_ms, color=[SCALE_COLORS[s] for s in SCALES],
              edgecolor='black', linewidth=1.5)
ax.set_ylabel('Execution Time (ms)', fontsize=13); ax.set_xlabel('Dataset Size', fontsize=13)
ax.set_title('Sequential Baseline: Execution Time vs Dataset Size', fontsize=14, fontweight='bold')
ax.grid(True, alpha=0.3, axis='y')
for bar in bars:
    h = bar.get_height()
    ax.text(bar.get_x() + bar.get_width() / 2, h, f'{h:,.0f} ms',
            ha='center', va='bottom', fontsize=11, fontweight='bold')
save(fig, '04-baseline-times.png')

# ── A5: Parallel Efficiency ───────────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = orig[orig['dataset'] == scale].sort_values('p')
    ax.plot(sub['p'], sub['efficiency'] * 100, marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.axhline(100, color='k', linestyle=':', linewidth=2, alpha=0.5, label='Ideal 100%')
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Efficiency E(p) = S(p)/p (%)', fontsize=13)
ax.set_title('Parallel Efficiency vs Worker Count\n(Original Distributed)', fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8]); ax.set_ylim(0, 200)
save(fig, '05-efficiency-vs-workers.png')

# ── A6: Execution Time Comparison (Medium, Sequential vs Distributed) ─────────
fig, ax = plt.subplots(figsize=(10, 7))
med = orig[orig['dataset'] == 'medium'].sort_values('p')
x = np.arange(len(med)); w = 0.35
ax.bar(x - w / 2, [BASELINE['medium']] * len(med), w,
       label='Sequential', color='steelblue', edgecolor='black', linewidth=1.5)
ax.bar(x + w / 2, med['T_par_ms'], w,
       label='Original Distributed', color='coral', edgecolor='black', linewidth=1.5)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Execution Time (ms)', fontsize=13)
ax.set_title('Execution Time: Sequential vs Original Distributed (Medium)', fontsize=14, fontweight='bold')
ax.set_xticks(x); ax.set_xticklabels(med['p']); ax.legend(fontsize=11); ax.grid(True, alpha=0.3, axis='y')
save(fig, '06-execution-time-comparison.png')

# ── A7: Speedup Scaling All Datasets ─────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = orig[orig['dataset'] == scale].sort_values('p')
    ax.plot(sub['p'], sub['speedup'], marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.axhline(1, color='gray', linestyle='--', linewidth=1.5, alpha=0.7)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Speedup S(p)', fontsize=13)
ax.set_title('Speedup Scaling Across All Datasets\n(Original Distributed vs Baseline)', fontsize=14, fontweight='bold')
ax.legend(title='Dataset', fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8])
save(fig, '07-speedup-scaling.png')

# ── A8: Speedup Gap ───────────────────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = orig[orig['dataset'] == scale]
    gaps = [row['speedup'] - amdahl(row['p'], f_orig) for _, row in sub.iterrows()]
    ax.plot(sub['p'], gaps, marker='*', color=SCALE_COLORS[scale],
            label=scale, linewidth=2.5, markersize=15)
ax.axhline(0, color='black', linewidth=1.5)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Speedup Gap (Measured − Amdahl)', fontsize=13)
ax.set_title(f"Speedup Gap vs Amdahl Theory (f={f_orig:.3f})\n(Original Distributed)", fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8])
save(fig, '08-speedup-gap.png')

# ── A9: Speedup Heatmap ───────────────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
pivot = orig.pivot(index='dataset', columns='p', values='speedup')
# Ensure row order
pivot = pivot.reindex(['small', 'medium', 'large'])
im = ax.imshow(pivot.values, cmap='RdYlGn', aspect='auto', vmin=0, vmax=4)
ax.set_xticks(np.arange(len(pivot.columns))); ax.set_yticks(np.arange(len(pivot.index)))
ax.set_xticklabels(pivot.columns); ax.set_yticklabels(pivot.index)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Dataset', fontsize=13)
ax.set_title('Speedup Heatmap — Original Distributed vs Baseline', fontsize=14, fontweight='bold')
for i in range(len(pivot.index)):
    for j in range(len(pivot.columns)):
        ax.text(j, i, f'{pivot.values[i, j]:.2f}',
                ha='center', va='center', fontsize=12, fontweight='bold')
plt.colorbar(im, ax=ax, label='Speedup vs Baseline')
save(fig, '09-speedup-heatmap.png')

print("\nSection A done (9 original plots regenerated).")


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION B — Optimized-Specific Analysis
# ═══════════════════════════════════════════════════════════════════════════════

# ── B1: Optimized Worker Scaling (Amdahl's Law) ───────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = opt[opt['dataset'] == scale]
    ax.plot(sub['p'], sub['speedup'], marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.plot(p_range, [amdahl(p, f_opt) for p in p_range], 'r--',
        label=f'Amdahl (f={f_opt:.3f})', linewidth=2.5)
ax.plot(p_range, p_range, 'k:', label='Ideal Linear', linewidth=2.5)
ax.set_xlabel('Workers (p)', fontsize=13); ax.set_ylabel('Speedup vs Baseline S(p)', fontsize=13)
ax.set_title("Amdahl's Law: Optimized Distributed vs Baseline", fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks(p_range)
save(fig, '10-opt-amdahls-law.png')

# ── B2: Optimized Strong Scaling ──────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = opt[opt['dataset'] == scale].sort_values('p')
    ax.plot(sub['p'], sub['speedup'], marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.plot(p_range, p_range, 'k--', label='Ideal Linear', linewidth=2.5, alpha=0.6)
ax.axhline(1, color='gray', linestyle='--', linewidth=1, alpha=0.5)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Speedup vs Baseline S(p)', fontsize=13)
ax.set_title('Strong Scaling — Optimized Distributed', fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8])
save(fig, '11-opt-strong-scaling.png')

# ── B3: Optimized Parallel Efficiency ────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    sub = opt[opt['dataset'] == scale].sort_values('p')
    ax.plot(sub['p'], sub['efficiency'] * 100, marker=SCALE_MARKERS[scale],
            color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
ax.axhline(100, color='k', linestyle=':', linewidth=2, alpha=0.5, label='Ideal 100%')
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Efficiency E(p) = S(p)/p (%)', fontsize=13)
ax.set_title('Parallel Efficiency vs Worker Count — Optimized', fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8]); ax.set_ylim(0, 400)
save(fig, '12-opt-efficiency.png')

# ── B4: Optimized Speedup Heatmap ─────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
pivot_o = opt.pivot(index='dataset', columns='p', values='speedup').reindex(['small', 'medium', 'large'])
im = ax.imshow(pivot_o.values, cmap='RdYlGn', aspect='auto', vmin=0, vmax=5)
ax.set_xticks(np.arange(len(pivot_o.columns))); ax.set_yticks(np.arange(len(pivot_o.index)))
ax.set_xticklabels(pivot_o.columns); ax.set_yticklabels(pivot_o.index)
ax.set_xlabel('Worker Count (p)', fontsize=13); ax.set_ylabel('Dataset', fontsize=13)
ax.set_title('Speedup Heatmap — Optimized Distributed vs Baseline', fontsize=14, fontweight='bold')
for i in range(len(pivot_o.index)):
    for j in range(len(pivot_o.columns)):
        ax.text(j, i, f'{pivot_o.values[i, j]:.2f}',
                ha='center', va='center', fontsize=12, fontweight='bold')
plt.colorbar(im, ax=ax, label='Speedup vs Baseline')
save(fig, '13-opt-speedup-heatmap.png')

print("Section B done (4 optimized-specific plots).")


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION C — Comparison Plots
# ═══════════════════════════════════════════════════════════════════════════════

# ── C1: Speedup vs Baseline — Original vs Optimized (all scales) ─────────────
fig, axes = plt.subplots(1, 3, figsize=(18, 6), sharey=False)
fig.suptitle('Speedup vs Baseline: Original vs Optimized Distributed', fontsize=15, fontweight='bold')
for ax, scale in zip(axes, SCALES):
    o_sub = orig[orig['dataset'] == scale].sort_values('p')
    p_sub = opt[opt['dataset'] == scale].sort_values('p')
    ax.plot(o_sub['p'], o_sub['speedup'], marker='o', color='coral',
            label='Original Distributed', linewidth=2.5, markersize=10)
    ax.plot(p_sub['p'], p_sub['speedup'], marker='s', color='steelblue',
            label='Optimized Distributed', linewidth=2.5, markersize=10)
    ax.axhline(1, color='gray', linestyle='--', linewidth=1.2, alpha=0.6, label='Baseline (1×)')
    ax.set_title(f'{scale.capitalize()} Dataset', fontsize=13, fontweight='bold')
    ax.set_xlabel('Workers (p)', fontsize=11); ax.set_ylabel('Speedup vs Baseline', fontsize=11)
    ax.legend(fontsize=9); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8])
fig.tight_layout()
save(fig, '14-comparison-speedup-vs-baseline.png')

# ── C2: Execution Time — Baseline / Original / Optimized (bar chart per scale) ─
fig, axes = plt.subplots(1, 3, figsize=(18, 7))
fig.suptitle('Execution Time: Baseline vs Original vs Optimized\n(4 Workers)', fontsize=15, fontweight='bold')
w_idx = 4  # show 4-worker comparison
for ax, scale in zip(axes, SCALES):
    labels = ['Baseline\n(sequential)', f'Original\nDistributed\n({w_idx}w)', f'Optimized\nDistributed\n({w_idx}w)']
    times = [BASELINE[scale], ORIG_TIMES[scale][w_idx], OPT_TIMES[scale][w_idx]]
    colors_bar = ['#5B5EA6', '#C84B31', '#2D6A4F']
    bars = ax.bar(labels, times, color=colors_bar, edgecolor='black', linewidth=1.5, width=0.5)
    ax.set_title(f'{scale.capitalize()} Dataset', fontsize=13, fontweight='bold')
    ax.set_ylabel('Compute Time (ms)', fontsize=11)
    ax.grid(True, alpha=0.3, axis='y')
    for bar in bars:
        h = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2, h * 1.01, f'{h:,.0f}',
                ha='center', va='bottom', fontsize=10, fontweight='bold')
    # Add speedup annotations
    opt_speedup = BASELINE[scale] / OPT_TIMES[scale][w_idx]
    orig_speedup = BASELINE[scale] / ORIG_TIMES[scale][w_idx]
    ax.text(1, times[1] * 0.5, f'{orig_speedup:.2f}×', ha='center', color='white',
            fontsize=12, fontweight='bold')
    ax.text(2, times[2] * 0.5, f'{opt_speedup:.2f}×', ha='center', color='white',
            fontsize=12, fontweight='bold')
fig.tight_layout()
save(fig, '15-execution-time-3way.png')

# ── C3: Execution Time Across Worker Counts — Original vs Optimized ───────────
fig, axes = plt.subplots(1, 3, figsize=(18, 6), sharey=False)
fig.suptitle('Execution Time by Worker Count: Original vs Optimized', fontsize=15, fontweight='bold')
for ax, scale in zip(axes, SCALES):
    orig_ms = [ORIG_TIMES[scale][w] for w in WORKERS]
    opt_ms  = [OPT_TIMES[scale][w]  for w in WORKERS]
    x = np.arange(len(WORKERS)); bw = 0.35
    ax.bar(x - bw / 2, orig_ms, bw, label='Original', color='coral',   edgecolor='black', linewidth=1.2)
    ax.bar(x + bw / 2, opt_ms,  bw, label='Optimized', color='steelblue', edgecolor='black', linewidth=1.2)
    ax.axhline(BASELINE[scale], color='purple', linestyle='--', linewidth=2, alpha=0.7, label='Baseline')
    ax.set_title(f'{scale.capitalize()} Dataset', fontsize=13, fontweight='bold')
    ax.set_xlabel('Workers', fontsize=11); ax.set_ylabel('Compute Time (ms)', fontsize=11)
    ax.set_xticks(x); ax.set_xticklabels(WORKERS); ax.legend(fontsize=9); ax.grid(True, alpha=0.3, axis='y')
fig.tight_layout()
save(fig, '16-execution-time-by-workers.png')

# ── C4: Speedup Gain Factor (Optimized / Original) ────────────────────────────
fig, ax = plt.subplots(figsize=(10, 7))
for scale in SCALES:
    o_sub = orig[orig['dataset'] == scale].sort_values('p')
    p_sub = opt[opt['dataset'] == scale].sort_values('p')
    gain  = p_sub['speedup'].values / o_sub['speedup'].values
    ax.plot(WORKERS, gain, marker=SCALE_MARKERS[scale], color=SCALE_COLORS[scale],
            label=scale, linewidth=2.5, markersize=10)
ax.axhline(1, color='gray', linestyle='--', linewidth=1.5, alpha=0.7, label='No gain (1×)')
ax.set_xlabel('Worker Count (p)', fontsize=13)
ax.set_ylabel('Speedup Ratio (Optimized ÷ Original)', fontsize=13)
ax.set_title('Optimization Gain: How Much Faster is Optimized vs Original?', fontsize=14, fontweight='bold')
ax.legend(fontsize=11); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8])
save(fig, '17-optimization-gain-ratio.png')

# ── C5: Efficiency Comparison — Original vs Optimized (medium only, detailed) ─
fig, axes = plt.subplots(1, 2, figsize=(16, 7))
fig.suptitle('Parallel Efficiency: Original vs Optimized Distributed', fontsize=15, fontweight='bold')
for ax, (data, label, color) in zip(axes, [
    (orig, 'Original Distributed', 'coral'),
    (opt,  'Optimized Distributed', 'steelblue'),
]):
    for scale in SCALES:
        sub = data[data['dataset'] == scale].sort_values('p')
        ax.plot(sub['p'], sub['efficiency'] * 100, marker=SCALE_MARKERS[scale],
                color=SCALE_COLORS[scale], label=scale, linewidth=2.5, markersize=10)
    ax.axhline(100, color='k', linestyle=':', linewidth=2, alpha=0.5, label='Ideal 100%')
    ax.set_title(label, fontsize=13, fontweight='bold')
    ax.set_xlabel('Worker Count (p)', fontsize=11); ax.set_ylabel('Efficiency (%)', fontsize=11)
    ax.legend(fontsize=10); ax.grid(True, alpha=0.3); ax.set_xticks([1, 2, 4, 8])
fig.tight_layout()
save(fig, '18-efficiency-comparison.png')

# ── C6: Side-by-side Speedup Heatmaps ─────────────────────────────────────────
fig, axes = plt.subplots(1, 2, figsize=(18, 6))
fig.suptitle('Speedup vs Baseline Heatmap: Original vs Optimized', fontsize=15, fontweight='bold')
vmin, vmax = 0, 5
for ax, (data, title) in zip(axes, [
    (orig, 'Original Distributed'),
    (opt,  'Optimized Distributed'),
]):
    pv = data.pivot(index='dataset', columns='p', values='speedup').reindex(['small', 'medium', 'large'])
    im = ax.imshow(pv.values, cmap='RdYlGn', aspect='auto', vmin=vmin, vmax=vmax)
    ax.set_xticks(np.arange(len(pv.columns))); ax.set_yticks(np.arange(len(pv.index)))
    ax.set_xticklabels(pv.columns); ax.set_yticklabels(pv.index)
    ax.set_xlabel('Workers (p)', fontsize=12); ax.set_ylabel('Dataset', fontsize=12)
    ax.set_title(title, fontsize=13, fontweight='bold')
    for i in range(len(pv.index)):
        for j in range(len(pv.columns)):
            ax.text(j, i, f'{pv.values[i, j]:.2f}',
                    ha='center', va='center', fontsize=12, fontweight='bold')
    plt.colorbar(im, ax=ax, label='Speedup vs Baseline')
fig.tight_layout()
save(fig, '19-speedup-heatmap-comparison.png')

# ── C7: Combined Summary — All 3 versions, Large Dataset ──────────────────────
fig, ax = plt.subplots(figsize=(12, 7))
orig_large = [ORIG_TIMES['large'][w] for w in WORKERS]
opt_large  = [OPT_TIMES['large'][w]  for w in WORKERS]
x_labels = [f'{w}w' for w in WORKERS]
x = np.arange(len(x_labels)); bw = 0.35
ax.bar(x - bw / 2, orig_large, bw, label='Original Distributed', color='coral',
       edgecolor='black', linewidth=1.2)
ax.bar(x + bw / 2, opt_large,  bw, label='Optimized Distributed', color='steelblue',
       edgecolor='black', linewidth=1.2)
ax.axhline(BASELINE['large'], color='purple', linestyle='--', linewidth=2.5,
           label=f"Sequential Baseline ({BASELINE['large']:,.0f} ms)")
ax.set_xlabel('Worker Count', fontsize=13); ax.set_ylabel('Compute Time (ms)', fontsize=13)
ax.set_title('Execution Time Across Configurations — Large Dataset', fontsize=14, fontweight='bold')
ax.set_xticks(x); ax.set_xticklabels(x_labels); ax.legend(fontsize=11); ax.grid(True, alpha=0.3, axis='y')
for i, (o, p) in enumerate(zip(orig_large, opt_large)):
    ratio = o / p
    ax.annotate(f'{ratio:.2f}×\nfaster', xy=(i + bw / 2, p), xytext=(i + bw / 2, p + 1500),
                ha='center', fontsize=9, color='steelblue', fontweight='bold')
save(fig, '20-large-exec-time-summary.png')

print("Section C done (7 comparison plots).")


# ═══════════════════════════════════════════════════════════════════════════════
# SECTION D — Weak Scaling Analysis
# ═══════════════════════════════════════════════════════════════════════════════
# True weak scaling requires workers proportional to problem size (1:10:20 for
# small:medium:large). The available hardware supports a maximum of 8 simultaneous
# worker JVMs before CPU contention dominates, so an exact match is not possible.
# The closest approximation using existing measurements is:
#
#   1 worker  → small  (50,000 riders → 50,000 riders/worker)
#   4 workers → medium (500,000 riders → 125,000 riders/worker)
#   8 workers → large  (1,000,000 riders → 125,000 riders/worker)
#
# Work-per-worker is 2.5× higher at medium/large than at small due to the
# hardware constraint, and the search radius also grows with scale (3→4→5 cells),
# making the per-rider work super-linear. Both factors are documented in the plot.

ws = pd.read_csv(RESULTS / 'weak-scaling.csv')

fig, axes = plt.subplots(1, 2, figsize=(16, 7))
fig.suptitle(
    'Weak Scaling Analysis (Approximate)\n'
    'Approximation: 1w→small, 4w→medium, 8w→large  |  '
    'True weak scaling requires 1:10:20 workers — limited by hardware to 8 simultaneous JVMs',
    fontsize=13, fontweight='bold'
)

x_labels = [f"{int(row.workers)}w\n{row.dataset}\n({int(row.riders/1000)}K riders)" for _, row in ws.iterrows()]
x = np.arange(len(ws))

# ── Left: Absolute T_par ──────────────────────────────────────────────────────
ax = axes[0]
bars = ax.bar(x, ws['T_par_ms'], color=['#4C72B0', '#DD8452', '#55A868'],
              edgecolor='black', linewidth=1.5, width=0.5)
ax.axhline(ws['T_par_ms'].iloc[0], color='gray', linestyle='--', linewidth=1.5, alpha=0.7,
           label=f"Ideal (flat at {ws['T_par_ms'].iloc[0]:.0f} ms)")
ax.set_xticks(x); ax.set_xticklabels(x_labels, fontsize=10)
ax.set_ylabel('Compute Time (ms)', fontsize=12)
ax.set_title('Absolute Execution Time\n(ideal weak scaling = flat line)', fontsize=12, fontweight='bold')
ax.legend(fontsize=10); ax.grid(True, alpha=0.3, axis='y')
for bar, val in zip(bars, ws['T_par_ms']):
    ax.text(bar.get_x() + bar.get_width() / 2, val * 1.01, f'{val:,.0f} ms',
            ha='center', va='bottom', fontsize=10, fontweight='bold')

# ── Right: Normalised T_par (T / T_1worker) ───────────────────────────────────
ax2 = axes[1]
ax2.bar(x, ws['T_par_normalized'], color=['#4C72B0', '#DD8452', '#55A868'],
        edgecolor='black', linewidth=1.5, width=0.5)
ax2.axhline(1.0, color='gray', linestyle='--', linewidth=2, alpha=0.8,
            label='Ideal weak scaling (1.0)')
ax2.set_xticks(x); ax2.set_xticklabels(x_labels, fontsize=10)
ax2.set_ylabel('Normalised T_par  (T / T at 1 worker)', fontsize=12)
ax2.set_title('Normalised Execution Time\n(ideal = 1.0 regardless of scale)', fontsize=12, fontweight='bold')
ax2.legend(fontsize=10); ax2.grid(True, alpha=0.3, axis='y')
for xi, val in zip(x, ws['T_par_normalized']):
    ax2.text(xi, val * 1.01, f'{val:.1f}×', ha='center', va='bottom', fontsize=11, fontweight='bold')

# Annotation explaining the deviation
ax2.text(0.97, 0.95,
    'Deviation from ideal caused by:\n'
    '• Work/worker not constant (50K vs 125K riders)\n'
    '• Search radius grows with scale (3→4→5 cells)\n'
    '• Hardware cap: ≤8 JVM processes on one machine',
    transform=ax2.transAxes, fontsize=9, va='top', ha='right',
    bbox=dict(boxstyle='round,pad=0.4', facecolor='lightyellow', edgecolor='gray', alpha=0.9))

fig.tight_layout()
save(fig, '21-weak-scaling.png')

print("Section D done (weak scaling plot).")
print(f"\nAll 21 plots saved to {RESULTS}/")
print("\nSummary of optimized speedups vs baseline:")
print(f"  {'Scale':<8} {'Workers':<9} {'Original':<12} {'Optimized':<12} {'Gain'}")
for scale in SCALES:
    for w in WORKERS:
        os_ = BASELINE[scale] / ORIG_TIMES[scale][w]
        ps_ = BASELINE[scale] / OPT_TIMES[scale][w]
        print(f"  {scale:<8} {w:<9} {os_:<12.2f} {ps_:<12.2f} {ps_/os_:.2f}×")
