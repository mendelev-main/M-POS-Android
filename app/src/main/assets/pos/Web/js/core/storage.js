// Prilavok POS v130.17
// Offline-first storage adapter.
//
// Contract:
// - local POS data is authoritative;
// - reads/writes stay local and must not depend on network access;
// - storage keys and serialized payloads stay compatible with the existing pos.html implementation;
// - this adapter never starts synchronization;
// - synchronization is a separate, manual-only concern.

(function (global) {
  'use strict';

  const root = global.PrilavokCore = global.PrilavokCore || {};
  const LS_PREFIX = 'prilavok_';

  // Match pos.html: select the provider once, including its legacy null behavior.
  const usesCloudStorage = typeof global.storage !== 'undefined';

  function hasCloudStorage() {
    return usesCloudStorage;
  }

  async function get(key, fallback, onError) {
    try {
      if (hasCloudStorage()) {
        const result = await global.storage.get(key, false);
        return result ? JSON.parse(result.value) : fallback;
      }

      const raw = global.localStorage.getItem(LS_PREFIX + key);
      return raw !== null ? JSON.parse(raw) : fallback;
    } catch (error) {
      // Let the POS facade preserve its existing storage warning.
      if (onError) onError(error);
      else console.error('[PrilavokStorage] read failed', key, error);
      return fallback;
    }
  }

  async function set(key, value) {
    const serialized = JSON.stringify(value);

    if (hasCloudStorage()) {
      await global.storage.set(key, serialized, false);
      return;
    }

    global.localStorage.setItem(LS_PREFIX + key, serialized);
  }

  function remove(key) {
    if (hasCloudStorage()) {
      throw new Error('Cloud storage removal is not supported by the v130.16 adapter');
    }

    global.localStorage.removeItem(LS_PREFIX + key);
  }

  function describe() {
    return Object.freeze({
      mode: hasCloudStorage() ? 'window.storage' : 'localStorage',
      prefix: LS_PREFIX,
      sourceOfTruth: 'local-pos',
      networkRequired: false,
      startsSynchronization: false
    });
  }

  root.Storage = Object.freeze({
    get,
    set,
    remove,
    describe
  });
})(window);
