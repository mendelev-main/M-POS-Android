---
name: mpos-native-design
description: Design and review native M POS tablet surfaces in the existing app's visual language, preserving POS behavior.
---

# M POS native design

Read ../frontend-design/SKILL.md for the visual design workflow. The concrete
brief is always the existing POS, not a new brand. Use ../../../docs/NATIVE_DESIGN_SYSTEM.md
and MPosNativeTheme. Compare with the reviewed Web CSS tokens in ../../../app/src/main/assets/pos/pos.html.

Before editing, identify the affected surface and read applicable AGENTS.md.
Describe a short design plan: hierarchy, tokens, spacing, font, primary action.
Preserve all financial formulas, permissions, Room boundaries, selection mapping,
password handling, busy/blocked state, offline behavior and backup v13 shapes.
Never read/print/copy the original administrator password/verifier constant.

Use the bundled Manrope font offline, both native palettes, clear primary vs
secondary/danger actions, card/panel spacing, 48dp touch targets and sp text.
Respect system font scale. Give long employee names and monetary values room.
Use scroll containers for tablet landscape, keyboard and large fonts. Avoid OS
all-caps, unstyled spinners, equal emphasis for every datum, and stacked giant
buttons when a compact action row or a history card communicates better.

Apply shared primitives rather than copying more inline theme constants. Keep
model-independent theme tests and existing behavior tests. When supported render
native previews with synthetic data and inspect light/dark output; label these as
previews, not screenshots from a physical tablet. Do not claim performance or
physical acceptance without measurement. Do not assemble product APK locally.

Update design documentation and tablet acceptance list. Run JS/JVM/lint, preserve
user changes, then commit/push main under existing authorization. Explain which
surfaces changed and which are still pending.
