# Plan

1. Keep existing preference representation and isolate ordered disk-confirmed writes in Kotlin.
2. Reuse the bounded native queue, snapshot submitted settings and integrate Activity closure.
3. Verify delayed/failed commits, FIFO, backpressure, cancellation and legacy-save failure isolation.
4. Validate full suites/builds, document physical restart gates and publish to main.
