# WEB ready recovery parity diagnostics

## Goal
Compare the authoritative legacy `webOrderReadyJournal` with its Room shadow by order IDs and recovery stages.

## Contract
Diagnostics are read-only and non-authoritative. They may report missing/extra IDs and stage mismatches but must never retry `/ready`, mutate the journal, or change order state.
