# PERDS (Predictive Emergency Response Dispatch System)

Coursework repository for a Java SE 21+ simulation of a national emergency response dispatch network.

See:
- `requirements.txt` for the assessment brief (text copy).
- `docs/README.md` for documentation structure.

## Quick start

- `mvn test`
- `mvn -q -DskipTests package`
- `java -jar target/perds-0.1.0-SNAPSHOT.jar demo`

If your environment blocks forked test JVMs, run `mvn "-Dperds.surefire.forkCount=0" test`.
