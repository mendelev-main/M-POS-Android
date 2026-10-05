# Current order session recovery projection

## Goal
Preserve crash/restart evidence for the existing authoritative `currentOrderSession`, especially split-payment progress, without moving payment authority to Kotlin.

## Contract
Room is shadow-only. Project the full legacy session plus diagnostic fields. Legacy `commitCriticalStorage` remains authoritative. No native payment continuation, mutation, or replay.
