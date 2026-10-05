# Critical storage journal parity diagnostics

## Goal
Compare the authoritative legacy critical-storage recovery journal with its Room shadow before any native recovery cutover.

## Contract
The diagnostic is read-only and non-authoritative. It compares journal presence, id, operation type and the set of write keys. It must not replay, replace, clear or otherwise mutate recovery state.
