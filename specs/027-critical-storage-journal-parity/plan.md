# Plan
1. Read legacy shadow payload and Room singleton.
2. Compare presence, journal id, operation type and write-key set.
3. Return a non-authoritative diagnostic report.
4. Add a source test proving the parity branch has no mutation/replay calls.
5. Keep Room schema unchanged.
