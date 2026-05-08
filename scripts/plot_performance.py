#!/usr/bin/env python3
"""
Performance Analysis Plots for RideSync Distributed Matcher
Generates 8 graphs: Amdahl's Law, Strong Scaling, Efficiency, etc.
"""
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

# Load speedup data
csv_path = Path(__file__).parent.parent / 'results' / 'speedup.csv'
df = pd.read_csv(csv_path)

# Estimate parallel fraction f from measurements (average across all datasets and p >= 2)
# Amdahl: S(p) = 1 / ((1-f) + f/p)  =>  f = (p * (S(p) - 1)) / (p * S(p) - 1)
def estimate_f(S, p):
    if p <= 1 or S <= 1:
        return None
    return (p * (S - 1)) / (p * S - 1)

# Calculate f for all rows and average
df['f_calculated'] = df.apply(lambda row: estimate_f(row['speedup'], row['p']) if row['p'] > 1 else None, axis=1)
f_avg = df[df['f_calculated'].notna()]['f_calculated'].mean()
print(f"Average estimated parallel fraction f ≈ {f_avg:.3f}")

# Define theoretical Amdahl curve
def amdahl_max(p, f):
    return 1.0 / ((1 - f) + f / p)

# Define ideal (linear) speedup
def ideal_speedup(p):
    return p

# Setup figure for all 8 plots
fig = plt.figure(figsize=(16, 14))

# ============================================================================
# 1. Amdahl's Law: Measured vs Theoretical Speedup
# ============================================================================
ax1 = plt.subplot(3, 3, 1)
p_values = np.arange(1, 9)
amdahl_curve = [amdahl_max(p, f_avg) for p in p_values]
ideal_curve = [ideal_speedup(p) for p in p_values]

for dataset in df['dataset'].unique():
    subset = df[df['dataset'] == dataset]
    ax1.plot(subset['p'], subset['speedup'], marker='o', label=dataset, linewidth=2, markersize=8)

ax1.plot(p_values, amdahl_curve, 'r--', label=f'Amdahl (f={f_avg:.3f})', linewidth=2)
ax1.plot(p_values, ideal_curve, 'k:', label='Ideal Linear', linewidth=2)
ax1.set_xlabel('Processors/Workers (p)', fontsize=11)
ax1.set_ylabel('Speedup S(p)', fontsize=11)
ax1.set_title('Amdahl\'s Law: Measured vs Theoretical Speedup', fontsize=12, fontweight='bold')
ax1.legend(fontsize=9)
ax1.grid(True, alpha=0.3)
ax1.set_xticks(p_values)

# ============================================================================
# 2. Strong Scaling: Fixed Problem Size, Increasing Worker Count
# ============================================================================
ax2 = plt.subplot(3, 3, 2)
for dataset in df['dataset'].unique():
    subset = df[df['dataset'] == dataset]
    ax2.plot(subset['p'], subset['speedup'], marker='s', label=dataset, linewidth=2, markersize=8)

ax2.plot(p_values, ideal_curve, 'k--', label='Ideal Linear', linewidth=2, alpha=0.6)
ax2.set_xlabel('Worker Count (p)', fontsize=11)
ax2.set_ylabel('Speedup S(p)', fontsize=11)
ax2.set_title('Strong Scaling: Fixed Problem Size, Increasing Workers', fontsize=12, fontweight='bold')
ax2.legend(fontsize=9)
ax2.grid(True, alpha=0.3)
ax2.set_xticks(p_values)

# ============================================================================
# 3. Multi-Thread Speedup vs Worker Count (by Dataset)
# ============================================================================
ax3 = plt.subplot(3, 3, 3)
for dataset in df['dataset'].unique():
    subset = df[df['dataset'] == dataset].sort_values('p')
    ax3.plot(subset['p'], subset['speedup'], marker='^', label=dataset, linewidth=2, markersize=8)

ax3.set_xlabel('Worker Count (p)', fontsize=11)
ax3.set_ylabel('Speedup S(p)', fontsize=11)
ax3.set_title('Multi-Worker Speedup vs Worker Count', fontsize=12, fontweight='bold')
ax3.legend(fontsize=9)
ax3.grid(True, alpha=0.3)
ax3.set_xticks([1, 2, 4, 8])

# ============================================================================
# 4. Sequential Baseline: Execution Time vs Dataset Size
# ============================================================================
ax4 = plt.subplot(3, 3, 4)
baseline_times = df.drop_duplicates('dataset')[['dataset', 'T_seq_ms']].sort_values('T_seq_ms')
colors = ['lightblue', 'lightgreen', 'lightcoral']
bars = ax4.bar(baseline_times['dataset'], baseline_times['T_seq_ms'], color=colors, edgecolor='black', linewidth=1.5)
ax4.set_ylabel('Execution Time (ms)', fontsize=11)
ax4.set_xlabel('Dataset Size', fontsize=11)
ax4.set_title('Sequential Baseline: Execution Time vs Dataset Size', fontsize=12, fontweight='bold')
ax4.grid(True, alpha=0.3, axis='y')

# Add value labels on bars
for bar in bars:
    height = bar.get_height()
    ax4.text(bar.get_x() + bar.get_width()/2., height,
             f'{height:.1f}ms', ha='center', va='bottom', fontsize=10, fontweight='bold')

# ============================================================================
# 5. Parallel Efficiency vs Worker Count
# ============================================================================
ax5 = plt.subplot(3, 3, 5)
for dataset in df['dataset'].unique():
    subset = df[df['dataset'] == dataset].sort_values('p')
    ax5.plot(subset['p'], subset['efficiency'] * 100, marker='D', label=dataset, linewidth=2, markersize=8)

ax5.axhline(y=100, color='k', linestyle=':', linewidth=2, alpha=0.5, label='Ideal (100%)')
ax5.set_xlabel('Worker Count (p)', fontsize=11)
ax5.set_ylabel('Efficiency E(p) = S(p)/p (%)', fontsize=11)
ax5.set_title('Parallel Efficiency vs Worker Count', fontsize=12, fontweight='bold')
ax5.legend(fontsize=9)
ax5.grid(True, alpha=0.3)
ax5.set_xticks([1, 2, 4, 8])
ax5.set_ylim(0, 120)

# ============================================================================
# 6. Execution Time Comparison: Sequential vs Distributed (Medium Dataset)
# ============================================================================
ax6 = plt.subplot(3, 3, 6)
medium_data = df[df['dataset'] == 'medium'].sort_values('p')
x_pos = np.arange(len(medium_data))
width = 0.35

bars1 = ax6.bar(x_pos - width/2, [medium_data.iloc[0]['T_seq_ms']] * len(medium_data), 
                width, label='Sequential (T_seq)', color='steelblue', edgecolor='black', linewidth=1.5)
bars2 = ax6.bar(x_pos + width/2, medium_data['T_par_ms'], 
                width, label='Distributed (T_par)', color='coral', edgecolor='black', linewidth=1.5)

ax6.set_xlabel('Worker Count (p)', fontsize=11)
ax6.set_ylabel('Execution Time (ms)', fontsize=11)
ax6.set_title('Execution Time: Sequential vs Distributed (Medium)', fontsize=12, fontweight='bold')
ax6.set_xticks(x_pos)
ax6.set_xticklabels(medium_data['p'])
ax6.legend(fontsize=9)
ax6.grid(True, alpha=0.3, axis='y')

# ============================================================================
# 7. Speedup Scaling Across All Datasets and Worker Counts
# ============================================================================
ax7 = plt.subplot(3, 3, 7)
pivot_speedup = df.pivot(index='p', columns='dataset', values='speedup')
pivot_speedup.plot(kind='line', ax=ax7, marker='o', linewidth=2, markersize=8)
ax7.axhline(y=1, color='gray', linestyle='--', linewidth=1, alpha=0.7)
ax7.set_xlabel('Worker Count (p)', fontsize=11)
ax7.set_ylabel('Speedup S(p)', fontsize=11)
ax7.set_title('Speedup Scaling Across All Datasets', fontsize=12, fontweight='bold')
ax7.legend(title='Dataset', fontsize=9)
ax7.grid(True, alpha=0.3)
ax7.set_xticks([1, 2, 4, 8])

# ============================================================================
# 8. Speedup Gap: Measured vs Amdahl Theoretical
# ============================================================================
ax8 = plt.subplot(3, 3, 8)
gap_data = []
for dataset in df['dataset'].unique():
    subset = df[df['dataset'] == dataset]
    for _, row in subset.iterrows():
        p = row['p']
        measured_s = row['speedup']
        theoretical_s = amdahl_max(p, f_avg)
        gap = measured_s - theoretical_s
        gap_data.append({'dataset': dataset, 'p': p, 'gap': gap})

gap_df = pd.DataFrame(gap_data)
for dataset in gap_df['dataset'].unique():
    subset = gap_df[gap_df['dataset'] == dataset]
    ax8.plot(subset['p'], subset['gap'], marker='*', label=dataset, linewidth=2, markersize=12)

ax8.axhline(y=0, color='black', linestyle='-', linewidth=1)
ax8.set_xlabel('Worker Count (p)', fontsize=11)
ax8.set_ylabel('Speedup Gap (Measured - Amdahl)', fontsize=11)
ax8.set_title(f'Speedup Gap vs Theory (f={f_avg:.3f})', fontsize=12, fontweight='bold')
ax8.legend(fontsize=9)
ax8.grid(True, alpha=0.3)
ax8.set_xticks([1, 2, 4, 8])

# ============================================================================
# 9. Speedup per Dataset: Heatmap View
# ============================================================================
ax9 = plt.subplot(3, 3, 9)
pivot_table = df.pivot(index='dataset', columns='p', values='speedup')
im = ax9.imshow(pivot_table.values, cmap='RdYlGn', aspect='auto', vmin=0, vmax=3)
ax9.set_xticks(np.arange(len(pivot_table.columns)))
ax9.set_yticks(np.arange(len(pivot_table.index)))
ax9.set_xticklabels(pivot_table.columns)
ax9.set_yticklabels(pivot_table.index)
ax9.set_xlabel('Worker Count (p)', fontsize=11)
ax9.set_ylabel('Dataset Size', fontsize=11)
ax9.set_title('Speedup Heatmap (Measured)', fontsize=12, fontweight='bold')

# Add text annotations
for i in range(len(pivot_table.index)):
    for j in range(len(pivot_table.columns)):
        text = ax9.text(j, i, f'{pivot_table.values[i, j]:.2f}',
                       ha="center", va="center", color="black", fontsize=10, fontweight='bold')

plt.colorbar(im, ax=ax9, label='Speedup')

# ============================================================================
# Finalize and Save
# ============================================================================
plt.tight_layout()
output_path = Path(__file__).parent.parent / 'results' / 'performance_analysis.png'
plt.savefig(output_path, dpi=150, bbox_inches='tight')
print(f"\n✓ All 8 plots saved to {output_path}")

# Also save individual high-res versions for embedding in report
individual_figs = [
    ('01-amdahls-law.png', 1),
    ('02-strong-scaling.png', 2),
    ('03-multi-worker-speedup.png', 3),
    ('04-baseline-times.png', 4),
    ('05-efficiency-vs-workers.png', 5),
    ('06-execution-time-comparison.png', 6),
    ('07-speedup-scaling.png', 7),
    ('08-speedup-gap.png', 8),
    ('09-speedup-heatmap.png', 9),
]

print("\nGenerating individual high-res plots...")
for fig_name, plot_idx in individual_figs:
    fig_ind = plt.figure(figsize=(10, 7))
    
    # Recreate the specific subplot in a larger figure
    ax = fig_ind.add_subplot(111)
    
    if plot_idx == 1:  # Amdahl's Law
        p_values = np.arange(1, 9)
        amdahl_curve = [amdahl_max(p, f_avg) for p in p_values]
        ideal_curve = [ideal_speedup(p) for p in p_values]
        for dataset in df['dataset'].unique():
            subset = df[df['dataset'] == dataset]
            ax.plot(subset['p'], subset['speedup'], marker='o', label=dataset, linewidth=2.5, markersize=10)
        ax.plot(p_values, amdahl_curve, 'r--', label=f'Amdahl (f={f_avg:.3f})', linewidth=2.5)
        ax.plot(p_values, ideal_curve, 'k:', label='Ideal Linear', linewidth=2.5)
        ax.set_xlabel('Processors/Workers (p)', fontsize=13)
        ax.set_ylabel('Speedup S(p)', fontsize=13)
        ax.set_title('Amdahl\'s Law: Measured vs Theoretical Speedup', fontsize=14, fontweight='bold')
        ax.legend(fontsize=11)
        ax.grid(True, alpha=0.3)
        ax.set_xticks(p_values)
    
    elif plot_idx == 2:  # Strong Scaling
        p_values = np.arange(1, 9)
        ideal_curve = [ideal_speedup(p) for p in p_values]
        for dataset in df['dataset'].unique():
            subset = df[df['dataset'] == dataset]
            ax.plot(subset['p'], subset['speedup'], marker='s', label=dataset, linewidth=2.5, markersize=10)
        ax.plot(p_values, ideal_curve, 'k--', label='Ideal Linear', linewidth=2.5, alpha=0.6)
        ax.set_xlabel('Worker Count (p)', fontsize=13)
        ax.set_ylabel('Speedup S(p)', fontsize=13)
        ax.set_title('Strong Scaling: Fixed Problem Size, Increasing Workers', fontsize=14, fontweight='bold')
        ax.legend(fontsize=11)
        ax.grid(True, alpha=0.3)
        ax.set_xticks(p_values)
    
    elif plot_idx == 3:  # Multi-Worker Speedup
        for dataset in df['dataset'].unique():
            subset = df[df['dataset'] == dataset].sort_values('p')
            ax.plot(subset['p'], subset['speedup'], marker='^', label=dataset, linewidth=2.5, markersize=10)
        ax.set_xlabel('Worker Count (p)', fontsize=13)
        ax.set_ylabel('Speedup S(p)', fontsize=13)
        ax.set_title('Multi-Worker Speedup vs Worker Count', fontsize=14, fontweight='bold')
        ax.legend(fontsize=11)
        ax.grid(True, alpha=0.3)
        ax.set_xticks([1, 2, 4, 8])
    
    elif plot_idx == 4:  # Baseline Times
        baseline_times = df.drop_duplicates('dataset')[['dataset', 'T_seq_ms']].sort_values('T_seq_ms')
        colors = ['lightblue', 'lightgreen', 'lightcoral']
        bars = ax.bar(baseline_times['dataset'], baseline_times['T_seq_ms'], color=colors, edgecolor='black', linewidth=2)
        ax.set_ylabel('Execution Time (ms)', fontsize=13)
        ax.set_xlabel('Dataset Size', fontsize=13)
        ax.set_title('Sequential Baseline: Execution Time vs Dataset Size', fontsize=14, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        for bar in bars:
            height = bar.get_height()
            ax.text(bar.get_x() + bar.get_width()/2., height, f'{height:.1f}ms',
                   ha='center', va='bottom', fontsize=11, fontweight='bold')
    
    elif plot_idx == 5:  # Efficiency
        for dataset in df['dataset'].unique():
            subset = df[df['dataset'] == dataset].sort_values('p')
            ax.plot(subset['p'], subset['efficiency'] * 100, marker='D', label=dataset, linewidth=2.5, markersize=10)
        ax.axhline(y=100, color='k', linestyle=':', linewidth=2.5, alpha=0.5, label='Ideal (100%)')
        ax.set_xlabel('Worker Count (p)', fontsize=13)
        ax.set_ylabel('Efficiency E(p) = S(p)/p (%)', fontsize=13)
        ax.set_title('Parallel Efficiency vs Worker Count', fontsize=14, fontweight='bold')
        ax.legend(fontsize=11)
        ax.grid(True, alpha=0.3)
        ax.set_xticks([1, 2, 4, 8])
        ax.set_ylim(0, 120)
    
    elif plot_idx == 6:  # Execution Time Comparison (Medium)
        medium_data = df[df['dataset'] == 'medium'].sort_values('p')
        x_pos = np.arange(len(medium_data))
        width = 0.35
        bars1 = ax.bar(x_pos - width/2, [medium_data.iloc[0]['T_seq_ms']] * len(medium_data), 
                        width, label='Sequential (T_seq)', color='steelblue', edgecolor='black', linewidth=2)
        bars2 = ax.bar(x_pos + width/2, medium_data['T_par_ms'], 
                        width, label='Distributed (T_par)', color='coral', edgecolor='black', linewidth=2)
        ax.set_xlabel('Worker Count (p)', fontsize=13)
        ax.set_ylabel('Execution Time (ms)', fontsize=13)
        ax.set_title('Execution Time: Sequential vs Distributed (Medium)', fontsize=14, fontweight='bold')
        ax.set_xticks(x_pos)
        ax.set_xticklabels(medium_data['p'])
        ax.legend(fontsize=11)
        ax.grid(True, alpha=0.3, axis='y')
    
    elif plot_idx == 7:  # Speedup Scaling
        pivot_speedup = df.pivot(index='p', columns='dataset', values='speedup')
        for col in pivot_speedup.columns:
            ax.plot(pivot_speedup.index, pivot_speedup[col], marker='o', label=col, linewidth=2.5, markersize=10)
        ax.axhline(y=1, color='gray', linestyle='--', linewidth=1.5, alpha=0.7)
        ax.set_xlabel('Worker Count (p)', fontsize=13)
        ax.set_ylabel('Speedup S(p)', fontsize=13)
        ax.set_title('Speedup Scaling Across All Datasets', fontsize=14, fontweight='bold')
        ax.legend(title='Dataset', fontsize=11)
        ax.grid(True, alpha=0.3)
        ax.set_xticks([1, 2, 4, 8])
    
    elif plot_idx == 8:  # Speedup Gap
        gap_data = []
        for dataset in df['dataset'].unique():
            subset = df[df['dataset'] == dataset]
            for _, row in subset.iterrows():
                p = row['p']
                measured_s = row['speedup']
                theoretical_s = amdahl_max(p, f_avg)
                gap = measured_s - theoretical_s
                gap_data.append({'dataset': dataset, 'p': p, 'gap': gap})
        gap_df = pd.DataFrame(gap_data)
        for dataset in gap_df['dataset'].unique():
            subset = gap_df[gap_df['dataset'] == dataset]
            ax.plot(subset['p'], subset['gap'], marker='*', label=dataset, linewidth=2.5, markersize=15)
        ax.axhline(y=0, color='black', linestyle='-', linewidth=1.5)
        ax.set_xlabel('Worker Count (p)', fontsize=13)
        ax.set_ylabel('Speedup Gap (Measured - Amdahl)', fontsize=13)
        ax.set_title(f'Speedup Gap vs Theory (f={f_avg:.3f})', fontsize=14, fontweight='bold')
        ax.legend(fontsize=11)
        ax.grid(True, alpha=0.3)
        ax.set_xticks([1, 2, 4, 8])
    
    elif plot_idx == 9:  # Speedup Heatmap
        pivot_table = df.pivot(index='dataset', columns='p', values='speedup')
        im = ax.imshow(pivot_table.values, cmap='RdYlGn', aspect='auto', vmin=0, vmax=3)
        ax.set_xticks(np.arange(len(pivot_table.columns)))
        ax.set_yticks(np.arange(len(pivot_table.index)))
        ax.set_xticklabels(pivot_table.columns)
        ax.set_yticklabels(pivot_table.index)
        ax.set_xlabel('Worker Count (p)', fontsize=13)
        ax.set_ylabel('Dataset Size', fontsize=13)
        ax.set_title('Speedup Heatmap (Measured)', fontsize=14, fontweight='bold')
        for i in range(len(pivot_table.index)):
            for j in range(len(pivot_table.columns)):
                text = ax.text(j, i, f'{pivot_table.values[i, j]:.2f}',
                               ha="center", va="center", color="black", fontsize=11, fontweight='bold')
        cbar = plt.colorbar(im, ax=ax, label='Speedup')
    
    fig_ind.tight_layout()
    fig_path = Path(__file__).parent.parent / 'results' / fig_name
    fig_ind.savefig(fig_path, dpi=150, bbox_inches='tight')
    plt.close(fig_ind)

print("✓ Individual high-res plots saved to results/")
print("\nPlotting complete!")
