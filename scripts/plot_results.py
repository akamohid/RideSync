from pathlib import Path
import csv

import matplotlib.pyplot as plt


def load_csv(path: Path):
    with path.open(newline='', encoding='utf-8') as handle:
        reader = csv.DictReader(handle)
        rows = list(reader)
    return rows


def plot_speedup(csv_path: Path, output_path: Path):
    rows = load_csv(csv_path)
    workers = [int(row['workers']) for row in rows]
    speedup = [float(row['speedup']) for row in rows]
    efficiency = [float(row['efficiency']) for row in rows]

    fig, ax1 = plt.subplots(figsize=(8, 5))
    ax1.plot(workers, speedup, marker='o', label='Speedup')
    ax1.set_xlabel('Workers')
    ax1.set_ylabel('Speedup')
    ax1.grid(True, alpha=0.3)

    ax2 = ax1.twinx()
    ax2.plot(workers, efficiency, marker='s', color='orange', label='Efficiency')
    ax2.set_ylabel('Efficiency')

    fig.tight_layout()
    fig.savefig(output_path, dpi=160)


if __name__ == '__main__':
    import sys

    if len(sys.argv) != 3:
        raise SystemExit('Usage: python scripts/plot_results.py results.csv output.png')
    plot_speedup(Path(sys.argv[1]), Path(sys.argv[2]))
