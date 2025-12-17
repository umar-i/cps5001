# Version Control Evidence (Submission)

The assessment brief asks for **evidence** of version control usage (commit history, incremental development, and for higher bands: feature branches, pull requests, merges, and tags). This folder contains exported logs so you can include them in a zipped submission if needed.

## GitHub Links (preferred evidence)

Repository:
- https://github.com/umar-i/cps5001

Pull requests (open + closed):
- https://github.com/umar-i/cps5001/pulls?q=is%3Apr+is%3Aopen
- https://github.com/umar-i/cps5001/pulls?q=is%3Apr+is%3Aclosed

Tags (milestones):
- https://github.com/umar-i/cps5001/tags

## Included log exports (attach these in your submission)

- `docs/vc-evidence/git-log.txt` — `git log --oneline --decorate --graph --all --date-order`
- `docs/vc-evidence/git-branches.txt` — `git branch -avv`
- `docs/vc-evidence/git-tags.txt` — `git tag -n`
- `docs/vc-evidence/github-prs.txt` — `gh pr list --state all --limit 200`
- `docs/vc-evidence/git-remotes.txt` — `git remote -v`
- `docs/vc-evidence/generated-at.txt` — when these exports were generated

Notes:
- These files are evidence; they are **not** required for the system to run.
- Do **not** submit the `.git/` folder unless your lecturer explicitly requests the full repository metadata.
