# Loyalty interrupted-send restart recovery

## Goal
Ensure a crash after durably persisting `sending` cannot strand loyalty sale or reversal recovery forever.

## Contract
On restart/reconnect recovery, persisted `sending` states are treated as interrupted attempts and normalized in memory to `pending` before the existing retry path runs. Retrying is safe because backend sale/reversal operations are idempotent. JS remains recovery authority; Room remains diagnostic-only.
