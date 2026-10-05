# Plan
1. Persist `webOrderReadyJournal[id]` as pending before local ready status.
2. Attempt `/ready`; on success mark confirmed then remove the journal record.
3. Recover pending records at startup and network reconnect.
4. Keep local ready state usable when backend is offline.
5. Add architecture tests; physical offline/restart verification remains required.
