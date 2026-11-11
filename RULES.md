# Agent Rules & Execution Protocols

> **Core Mandate:** Zero blind fixing. Zero overthinking. Query the index first, diagnose the root cause with evidence, present findings to the user, and wait for confirmation before modifying code.

---

## 1. Zero Search Overhead — Query the Function Index First

Before making any tool calls to search, grep, or read whole directory trees:
- **Rule:** Always query [`.planning/codebase/FUNCTION_INDEX.md`](file:///.planning/codebase/FUNCTION_INDEX.md) first to find the exact file and line number (`file:line`) of relevant classes, composables, or functions.
- **Token Efficiency:** When inspecting code, read only the target line range identified in `FUNCTION_INDEX.md` (e.g. lines 40–110) rather than reading 500+ line files into context.

---

## 2. Diagnostic Gate — No Blind Fixing

Before touching any code or making an edit:
1. **Find What Is Broken:** Trace the bug to the root cause using real logs, tests, or code inspection.
2. **Explain to the User First (Strict Gate):** Present a concise diagnosis:
   - What is happening vs. what is expected.
   - Exact file and line: `[File.kt:L123](file:///path/to/File.kt#L123)`.
   - Root cause explanation.
   - Proposed surgical fix.
3. **Wait for User Approval:** Do **NOT** execute edits until the user reviews and confirms the proposed change, unless explicitly directed otherwise.

---

## 3. Editing & Fixing Guidelines (No Overthinking)

- **Surgical Edits Only:** Never rewrite an entire file or reformat unrelated blocks. Touch only the lines directly causing the bug or implementing the requested feature.
- **Move, Don't Rewrite:** If code needs reorganizing, move existing logic rather than rewriting from scratch.
- **No Speculative Abstractions:** Do not create wrappers, speculative factories, or extra abstraction layers unless directly requested. Fix the specific issue directly and cleanly.
- **Preserve Architectural Boundaries:**
  - Pure Kotlin modules (`:core-model`, `:core-dsp`, `:core-library`, `:core-common`, `:integration-api`) must **never** import `android.*` packages.
  - Features must not import internal packages of sibling features.
  - Keep Compose Multiplatform UI in `:core-ui` shared between Android and Windows Desktop.

---

## 4. Verification & Validation Gate

- **Verify Every Change After Writing:** Immediately after writing or editing code, verify it thoroughly:
  - **Logical Error & Goal Check:** Review the implemented code against edge cases, unintended regressions, and off-by-one or state mismatch errors to ensure there are no logical flaws and that the code completely succeeds at its intended goal on every execution.
  - **Executable Verification:** Run relevant unit/integration tests or build checks (e.g. `./gradlew :core-dsp:test`, `./gradlew :core-ui:desktopTest`, compilation tasks) to validate the fix or feature.
  - Never claim a task is completed or bug is fixed without actual test output, compiler evidence, or concrete verification.
- **No Hallucinations:** Every claim regarding behavior or test results must be backed by executed commands, logs, or file contents.

---

## 5. Git & State Hygiene

- Work strictly on the designated feature branch (`feature/liquid-dock`). Never push or merge to `main`.
- Never run `git push`, `git merge`, or create release tags without explicit user instruction.
