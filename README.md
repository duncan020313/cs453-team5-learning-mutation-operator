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

## Build

```bash
gradle clean build
```
