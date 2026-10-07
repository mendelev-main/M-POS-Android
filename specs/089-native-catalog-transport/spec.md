# 089 — Manual catalogue and media transport in Kotlin

## Scope

OkHttp now sends only the exact configured `/api/menu/sync` and `/api/media/upload` POST routes. The Android adapter forwards the reviewed JSON body unchanged; Kotlin validates HTTPS/configuration, applies the configured backend path prefix, device header and no-store policy, handles response status/strict JSON and closes responses. Native calls are cancelled by the source AbortSignal, bridge watchdog or Activity destruction. No automatic retry or sync scheduler is introduced. Unrelated fetch and `MPosNativeCatalogTransportEnabled=false` delegate to the previous runtime transport; already started requests complete through their original owner.

Manual catalogue authorization, buildMenuSyncPayload category/product/channel mappings, lastSyncAt/status and UI feedback remain reviewed functions. Catalogue serialization is not claimed as native business authority. Media remains a product-save action after the product is persisted; upload does not roll back local saving. Success updates imageUrl/removes imageUploadPending only when the latest product still has the same localImageId. HTTP 413 retains the reviewed oversized-image message and pending local image. No data-size limit is removed or introduced; backup reading retains its independent 500 MB limit.

## Transport bounds and compatibility

Media retains the reviewed ten-second deadline and AbortError contract. Menu sync previously had no fetch deadline; it now has a sixty-second native bound, leaving a manual error/status and permitting another explicit sync rather than indefinite waiting. JS watchdogs are 11/61 seconds and cancel the socket. No timer schedules another send. Server status and JSON are returned separately: HTTP errors are interpreted by the reviewed handlers, preserving the special media 413 branch. A shared strict MPosHttpJson response parser replaces the identical loyalty parsing implementation; loyalty/WEB ACK behavior remains covered by their existing tests.

The existing availability source hook after manual menu sync is left to the separate Android availability policy; this transport never authorizes a new availability request or retries a blocked failed request. [090](../090-native-payment-availability/spec.md) resolves the older 033 allowance for ordinary successful stock/manual-sync sends: current AGENTS rule 7 permits publication only after payment. This is a policy boundary outside catalogue/media transport, not permission to bypass the failed-send gate.

## Verification and rollback

Actual-source buildMenuSyncPayload/syncMenuToBackend and saveProduct tests verify manual-only initiation, unchanged category/product payload, server status, local save before media, success and 413 retention. Adapter tests cover cancellation/late response, timeout, already-aborted signals, unrelated routes and rollback. JVM tests verify body bytes, exact routes/prefix/headers, no automatic retry, native deadlines, strict response parsing and one failed OkHttp POST without replay. Complete JS/JVM/lint evidence below. Backup v13 and reviewed source files are unchanged. Physical acceptance pending.

## Preserved business questions

Pending product images are retried by an explicit save of the product; neither catalogue sync nor reconnect uploads them automatically. This is preserved. The server may accept a catalogue/photo request whose response is lost; manual retry may repeat the same request. Transport cannot promise exactly-once side effects; backend upsert/productId semantics remain required.

Verified: 373 JS / 296 JVM tests passed, no failures/errors/skips; lint zero errors / 15 existing warnings. No local APK assembly.
