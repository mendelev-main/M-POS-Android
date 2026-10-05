# Feature Specification: WEB acceptance recovery projection

## Goal
Begin P4 without replacing the proven WEB-order acceptance recovery semantics.

## Existing authoritative workflow
`webOrderAcceptances` persists stages `prepared → local → confirmed`. Local parked-order durability precedes backend ACK; recovery retries ACK only after local commit.

## Native scope
- Shadow-project the existing journal into Room.
- Preserve the full record JSON for compatibility evidence.
- Expose total/pending counts through native shadow stats.
- Do not send ACKs, create parked orders, or advance stages from Kotlin.
- Keep backup v13 and legacy storage authoritative.

## Acceptance
Automated migration/build verification, then physical restart during prepared/local states before any native recovery authority.
