## Summary

Describe what changed and why.

## How To Test

- `mvn "-Dperds.surefire.forkCount=0" test`
- (Optional) `mvn -q -DskipTests package && java -jar target/perds-0.1.0-SNAPSHOT.jar demo`
- (Optional) `java -jar target/perds-0.1.0-SNAPSHOT.jar scenario data/scenarios/mini-nodes.csv data/scenarios/mini-edges.csv data/scenarios/mini-events.csv data/out`

## Checklist

- [ ] Scoped changes only (no unrelated refactors)
- [ ] Tests added/updated and passing
- [ ] Docs updated (`docs/`) where relevant
- [ ] Diagrams updated (`docs/diagrams/`) where relevant
- [ ] Notes added to `docs/roadmap.md` / TODO file

