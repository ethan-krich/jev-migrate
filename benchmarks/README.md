# Benchmarks

Live runs against paid APIs (OpenRouter or TypeSafe). They are never part of `test.sh` or
`docker.sh test`, and pytest does not collect this directory.

| Directory | What it measures | Run with |
|---|---|---|
| [`jev-vs-llm/`](jev-vs-llm/results.md) | Translating a payroll project (copybooks, a Java library mapping, runtime-or-JDK choices) three ways: Jev decides, a chat LLM decides in Jev's place, or a chat LLM writes the Java. Cost and time per file, decisions against a rubric, compile, exact output. | `uv run --frozen --all-packages --extra jev python benchmarks/jev-vs-llm/bench.py` |
| [`dialect-inference/`](dialect-inference/results.md) | How reliably Jev infers `Dialect.collating_sequence` for the two demo projects in `examples/cobol-to-java-jev`, with and without source comments in the context. | `./scripts/jev-smoke.sh` |

Each directory holds its `results.md`. Raw output goes to `build/` (git-ignored).
