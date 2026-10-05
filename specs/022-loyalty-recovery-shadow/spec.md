# Loyalty recovery shadow status

## Goal
Expose the existing durable loyalty sale/reversal recovery state in Room without moving loyalty network or mutation authority to Kotlin.

## Contract
Paid orders remain authoritative in legacy storage. Existing `loyaltySync` and `loyaltyReversal` statuses are projected with the order. Kotlin does not call loyalty endpoints, retry requests, grant rewards, or reverse rewards.
