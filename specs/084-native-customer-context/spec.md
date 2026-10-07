# 084 — Local customer association commands and session storage

The reviewed customer directory belongs to the backend, not a local `customers` table. This stage moves order customer select/remove/profile application into MPosCustomerEngine. Customer creation/search/admin-directory transport remain backend operations; the next 085 introduces native loyalty-profile transport. No local directory, offline creation or new backup section is introduced.

## Behavior and persistence

Select spreads the previous customer, replaces id/name/phone and clears loyalty programs/redemptions/customer-id. Remove creates only blank name/phone and previous truthy address; unknown customer extensions are removed exactly as source. Profile spreads the selected customer, applies server id/name/normalized_phone and programs, clears redemptions and records the requested customer id. Other session fields (cart, WEB metadata, printing, delivery, paid split draft) remain unchanged. Names and phones are not additionally trimmed or normalized.

Pure correlated Kotlin model runs in the shared context FIFO. A current-order-session snapshot goes through the existing authoritative Room recovery write path, including projections, before applying state or starting loyalty retrieval. Stale model/order/customer/modal results are discarded before write; committed results update memory after ack even if the modal closed, without closing a new modal. If context changes during a write, recovery is required rather than overwriting newer memory. Only uncertain writes set the recovery flag, not failed pure calculations. Source profile responses for replaced customers are ignored. Save failure clears loading and keeps the previous profile. Native read does not itself persist.

This strengthens local-before-network ordering; the source void save helper did not wait for durability. Keys/shapes and backup v13 are unchanged; no Room schema migration needed. Rollback: MPosNativeCustomerCommandsEnabled=false restores reviewed handlers. Directory remains server-owned; existing offline sales remain supported, central customer creation/search still require internet. No catalogue/availability publication is added.

## Verification and remaining work

Nine actual-source fixtures cover select/remove/profile, Unicode/numeric IDs, extension/metadata preservation and address truthiness. JVM model parity and input immutability; actual-source JS comparison, durable-before-render/network, save refusal, uncertain write vs pure failure, late profile, profile persistence/refusal and rollback; correlated bridge frozen inputs. Full JS/JVM/lint required. Physical acceptance pending: select/remove/profile offline and online, rapid gestures/close, restart and v13 export/import with WEB and paid split metadata. Native UI remains 105, native central transport begins 085.

Preserved business questions: removal drops customer extensions but retains address; selection retains the previous address and extensions; manual customer edits from 078 do not rebind id. These are existing policies, not silently corrected here.

Evidence: 333 JS / 266 JVM tests passed; lint 0 errors / 15 existing warnings. No APK assembly.
