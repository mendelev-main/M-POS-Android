# Plan

1. Reproduce the old 100 → 100 instead of 80 cross-shift cash defect.
2. Implement Kotlin accounting plus a compatible Android runtime adapter.
3. Wire the corrected calculation into native delivery validation and JS screen/report/return guards.
4. Use identical synthetic fixtures in both engines; verify transaction rejection in real SQLite.
5. Document the partial domain boundary, legacy ambiguity and pending tablet checks; publish validated changes to main.

Next cohesive boundary: atomic Kotlin full-return command, preserving historical stockConsumption and the existing legacy recipe fallback. Do not conflate the accounting correction with this transaction migration.
