# AI Workflow: Junior Implementation + Senior Review

This project uses a two-pass AI workflow to balance cost and quality:

- cheaper model = junior developer (implementation pass)
- stronger model = senior developer (review pass)

## Why This Workflow

- Lowers cost by using cheaper models for most code writing.
- Improves quality by adding a focused senior review gate.
- Creates a repeatable, team-friendly process for every task.

## One Task Cycle

1. Select a cheaper model and enable the junior rule.
2. Ask for implementation of the task.
3. Switch to a stronger model and enable the senior rule.
4. Ask for a review of the exact changes.
5. Switch back to cheaper model and apply review fixes.
6. Run one final senior review pass before commit/merge.

## Rule Files Used

- `.cursor/rules/junior-implementation.mdc`
- `.cursor/rules/senior-review.mdc`

## Copy/Paste Prompt Templates

### Junior Implementation Prompt

```text
You are a junior developer.
Implement this task: <task>.

Constraints:
- Keep changes minimal and scoped.
- Do not refactor unrelated code.
- Follow existing project patterns.

At the end, output:
1) What changed
2) Risks and assumptions
3) Test checklist
```

### Senior Review Prompt

```text
You are a senior reviewer.
Review the junior changes for this task: <task>.

Focus on:
- correctness
- regressions
- security/performance risks
- missing tests

Output:
1) Blockers
2) Non-blocking improvements
3) Exact patch instructions
```

## Recommended Working Agreement

- Do not skip the senior review step for production-impacting tasks.
- Keep each task small; large tasks reduce review quality.
- If senior feedback is unclear, ask for specific code-level instructions.

## Example Micro-Cycle

Task: "Add input validation for distance fields in run logging form."

1. Junior pass: implement validation and basic tests.
2. Senior pass: review for edge cases (nulls, negatives, unit mismatch).
3. Junior pass: fix findings and update tests.
4. Senior pass: verify blockers are closed.
