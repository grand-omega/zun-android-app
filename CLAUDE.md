# CLAUDE.md

## Surgical Changes

- If you notice unrelated dead code, mention it - don't delete it.
- Remove imports/variables/functions that YOUR changes made unused;
  don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.
