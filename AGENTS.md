# Repository Guidelines

## Project Purpose
This repository contains coursework for building a Java-based **Predictive Emergency Response Dispatch System (PERDS)**. The assessment brief is in `2025-10-03-cps5001-assessment-2-brief.pdf` (a text copy also exists in `requirements.txt`).

## Assessment Constraints (Must Follow)
- Language: **Java SE 21+** only.
- Allowed: Java SE standard library, **JUnit** for testing, and (optionally) a lightweight visualisation approach.
- Prohibited: external algorithmic frameworks (e.g., **Guava graph** libraries) and **AI/ML libraries** (e.g., TensorFlow, WEKA).
- Default stance: **no additional third‑party dependencies** unless you have written permission from the module convenor.
- Key expectation: implement and justify core **data structures and algorithms** (e.g., adjacency‑list graph, priority queues/heaps, Dijkstra/A*) rather than importing them.

## Delivery Strategy (Grade Bands Are Cumulative)
- Build from **Third Class → 2:2 → 2:1 → First**; do not advance until the current band is complete, reviewed, and working.
- Keep the architecture modular so later features (dynamic updates, predictive pre‑positioning, evaluation/visualisation) can be added without rewrites.

## Project Structure & Module Organization
Use a standard Java layout so code, tests, and documentation stay easy to navigate:
- `src/main/java/<package>/` - application source (e.g., graph model, dispatch logic, prediction).
- `src/test/java/<package>/` - unit/integration tests.
- `docs/` — report material, design notes, and diagrams (e.g., `docs/diagrams/`).
- `data/` — optional simulation inputs/outputs (CSV/JSON) used for predictive analysis.

If you add new top-level folders, keep names short and document them in `docs/README.md`.

## Build, Test, and Development Commands
This repo does not currently include a build tool configuration. Prefer Maven when scaffolding the Java project:
- `mvn test` — run the full test suite.
- `mvn package` — build a runnable JAR into `target/`.
- `mvn -q -DskipTests package` — quick compile/package while iterating.

If you choose Gradle instead, add equivalent commands to `docs/README.md`.

## Coding Style & Naming Conventions
- Indentation: 4 spaces; no tabs.
- Naming: `PascalCase` (classes), `camelCase` (methods/fields), `UPPER_SNAKE_CASE` (constants), lowercase packages.
- Keep algorithms and data structures explicit and testable (e.g., `Graph`, `PriorityQueueDispatcher`, `DijkstraRouter`).

## Testing Guidelines
- Use JUnit 5 (`*Test` naming, e.g., `DijkstraRouterTest`).
- Prefer deterministic tests: fixed seeds for simulations and stable inputs in `data/`.
- Add tests alongside features (route finding, unit allocation, dynamic updates).

## Commit & Pull Request Guidelines
No Git history is present in this folder yet. When initializing version control:
- Use small, scoped commits with imperative subjects (e.g., `feat: add adjacency-list graph`).
- Reference assessment requirements where helpful (e.g., `fix: reassign units on incident completion (Req 2)`).
- PRs should include: summary, how to run tests, and any updated diagrams/report notes in `docs/`.

## Security & Configuration Tips
Do not commit generated build output (`target/`) or local IDE files. Keep any environment-specific configuration out of source control and provide a sanitized example (e.g., `config/example.yaml`) if needed.
