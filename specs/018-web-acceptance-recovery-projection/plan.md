# Implementation plan
1. Add Room `web_acceptance_projection` table with explicit 7→8 migration.
2. Project writes/removes for `webOrderAcceptances` from the existing storage mirror.
3. Retain stage, ready estimate, parked order id, timestamps and full JSON payload.
4. Add diagnostic counts only; no native retries or business transitions.
