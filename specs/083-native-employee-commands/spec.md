# 083 — Employee commands and compatible authorization gateway

Implemented scope: create/edit/delete employees in a Room transaction after the reviewed employee handler's existing authorization gate. Not a native credential verifier or completed native employee UI. Password checks remain unchanged in the source handlers; no credential or verifier crosses the bridge. Native employee/settings UI and native authorization completion remain required in 100 before WebView removal 109.

## Contract

`employeeCommit` v1 contains operation, id, expected employees, candidate next employees, current shift id and the symbolic reviewed-handler gateway marker. The marker records the trusted local handler path; it is not cryptographic authorization evidence or security hardening. Kotlin requires the marker for role changes and deletion, compares expected with the authoritative Room document, reconstructs permitted changes, verifies the candidate and atomically updates full JSON plus projections. Editing preserves unknown fields. New records retain the original four fields. No network work, schema/key changes or backup v13 changes.

Save retains name/phone trimming, mandatory name, roles admin/employee, original UID and missing-record behavior. Deletion rechecks the live Room open shift, prohibits self/admin deletion and requires a shift. Original deletion password check remains before command dispatch. Memory/render/close remain in the reviewed handler, only after durable acknowledgement. Import and unrelated writes delegate to existing storage. Frozen storage receives a compatibility facade rather than mutation. Gesture scope is synchronous because reviewed handlers call storage before first await. Uncertain commit blocks further employee commands until recovery. Explicit rollback: `MPosNativeEmployeeCommandsEnabled=false` returns to reviewed handler/storage behavior.

## Preserved policy questions

The original policy allows demoting the last administrator with the existing password and saving employees without an open shift. Deleting another ordinary employee requires an open shift and the original password but no additional role requirement on the shift employee. This stage preserves these rules; changing them requires a separate business decision.

## Verification

Actual-source JS tests cover original password/self/admin gates, create acknowledgement ordering, failed/uncertain commits, import delegation and rollback. Room JVM tests cover create/edit extension retention and projections, stale/tampered commands, role-change gateway requirement, last-admin demotion, live shift/self/admin restrictions and rollback on projection failure. Full JS/JVM and Android lint required. Physical acceptance remains pending: create/edit/delete, role changes with correct/incorrect password, last administrator, no shift/self/admin delete, restart and v13 export/import.

Next boundary: 084 customer local commands/storage. Engineering task count is not native feature coverage; employee views and password validation still use the reviewed runtime.

Automated evidence: 323 JS tests and 264 JVM tests passed; lint: 0 errors, 15 existing warnings. No local APK assembly.
