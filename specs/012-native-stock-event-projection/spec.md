# Feature Specification: Native warehouse stock-event projection

## Goal
Complete the P2 stock-movement groundwork using the data the POS actually persists.

The legacy runtime has no standalone stock-movement ledger. Stock quantity is held on products. Historical causes are represented by paid order lines, receiving history and inventory history. This stage therefore projects existing receiving and inventory events without inventing a new compatibility key.

## Safety
- Product stock remains authoritative in legacy storage.
- Receiving and inventory workflows remain unchanged.
- Paid sale lines remain represented by the existing order projection.
- Full source JSON is retained.
- Projection failure never blocks a successful legacy write.
- Backup v13 is unchanged.

## Migration
Room v6→v7 adds stock event and stock event line tables with an event parent index.

## Acceptance
Automated build must pass. Physical receiving/inventory/restart parity remains required before any authoritative stock cutover.
