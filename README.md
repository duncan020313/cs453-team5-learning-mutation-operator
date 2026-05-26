# ASTraMut: Mutation Operator Generator Based on Patch Pattern Learning

## Setup

Run following command:

```bash
./setup.sh
```

What `setup.sh` does:
- Builds the `dev` Docker image and verifies that the image was created.
- Starts the `dev` container, checks that it is running, and prints the command for entering it.

## Defects4J

Run `./setup-defects4j` to install Defects4J and a project-local Java 11 under `tools/` without changing the system default Java 17. Before using Defects4J in a shell, run `source tools/defects4j-env.sh`, then commands such as `defects4j info -p Lang` will be available.

## PIT Mutation Score Experiment

After setting up Defects4J, measure PIT mutation scores for Defects4J bugs with:

```bash
source tools/defects4j-env.sh
gradle run --args="experiment pitest-score --projects Lang --bugs 1"
```

The experiment checks out each requested fixed bug version, finds the methods changed by the bug fix, runs PIT against the Defects4J relevant tests, and reports only mutants in those changed methods. Results are written under `data/defects4j-pitest/results/`, including `summary.csv`, `average.txt`, per-bug `pitest.log`, and filtered `mutations.xml` files.

Useful options:
- `--projects Lang,Math` selects Defects4J projects. If omitted, all active Defects4J projects are used.
- `--bugs 1,2,10..15` selects bug ids. If omitted, all active bugs for each selected project are used.
- `--work-dir <dir>` and `--out-dir <dir>` override the checkout and result directories.
- `--no-resume` re-runs bugs even if a successful row already exists in `summary.csv`.
- `--keep-workdirs` keeps checked-out Defects4J projects for debugging.

## Learned Mutation Score Experiment

To score learned source-level mutation operators on the same Defects4J fixed versions and relevant tests, run:

```bash
source tools/defects4j-env.sh
gradle run --args="experiment learned-score --model-archive learned_260520.tar.gz --projects Lang --bugs 1 --preset top100"
```

The learned experiment extracts `learned/patterns-full.json` from the model archive, ranks all learned operators by global score, and evaluates single-site mutants for each selected preset. Results are written to `data/defects4j-learned/results/summary.csv`, with separate rows for `LEARNED_TOP_1000` and `LEARNED_TOP_100` when both presets are selected.

Useful options:
- `--preset top1000,top100` selects the learned operator sets. Default: both.
- `--bug-type <label>` filters learned patterns by training label.
- `--min-support <n>`, `--min-specificity <x>`, and `--min-cohort-ratio <x>` filter learned operators before ranking.
- `--mutant-timeout-seconds <n>` sets the per-mutant relevant-test timeout.
- `--no-resume` re-runs bugs even if a successful learned row already exists.

## Build

```bash
gradle clean build
```
