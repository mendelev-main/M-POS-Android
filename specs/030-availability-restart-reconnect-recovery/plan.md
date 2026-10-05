# Plan
1. Start availability recovery only after `loadAll()` completes.
2. Publish a fresh snapshot on startup.
3. Publish again on browser `online`.
4. Publish on foreground resume and abort in-flight request on background.
5. Keep the existing revision and durable-source rules unchanged.
6. Synchronize canonical iPad source and Android source and verify parity.
