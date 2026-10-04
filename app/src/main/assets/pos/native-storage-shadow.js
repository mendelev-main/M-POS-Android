(function(global){
  'use strict';

  const bridge=global.webkit?.messageHandlers?.storage;
  const legacyStorage=global.PrilavokCore?.Storage;
  if(!bridge||!legacyStorage)return;

  let sequence=0;
  const legacyPrefix='prilavok_';

  function post(action,payload){
    try{
      bridge.postMessage({action,requestId:'storage-'+(++sequence),...(payload||{})});
    }catch(error){
      console.error('[MPosStorageShadow] bridge failed',error);
    }
  }

  function mirrorSerialized(key,serialized){
    if(typeof key!=='string'||typeof serialized!=='string')return;
    post('put',{key,payload:serialized});
  }

  function mirrorValue(key,value){
    try{mirrorSerialized(key,JSON.stringify(value))}
    catch(error){console.error('[MPosStorageShadow] serialization failed',key,error)}
  }

  const mposStorage=Object.freeze({
    async get(key,fallback,onError){
      return legacyStorage.get(key,fallback,onError);
    },
    async set(key,value){
      await legacyStorage.set(key,value);
      mirrorValue(key,value);
    },
    remove(key){
      const result=legacyStorage.remove(key);
      post('remove',{key});
      return result;
    },
    describe(){
      const current=legacyStorage.describe();
      return Object.freeze({
        ...current,
        runtimeNamespace:'MPosCore',
        nativeShadow:'room',
        nativeShadowAuthoritative:false,
        sourceOfTruth:'local-pos'
      });
    }
  });

  const mposCore=global.MPosCore=global.MPosCore||{};
  mposCore.Storage=mposStorage;

  // Temporary compatibility alias for the bundled parity runtime.
  // New Android-specific code must use MPosCore.
  global.PrilavokCore=global.PrilavokCore||{};
  global.PrilavokCore.Storage=mposStorage;

  function mirrorExistingLocalStorage(){
    const description=legacyStorage.describe?.();
    if(description?.mode!=='localStorage')return;
    try{
      for(let i=0;i<global.localStorage.length;i++){
        const storageKey=global.localStorage.key(i);
        if(!storageKey||!storageKey.startsWith(legacyPrefix))continue;
        const serialized=global.localStorage.getItem(storageKey);
        if(serialized!==null)mirrorSerialized(storageKey.slice(legacyPrefix.length),serialized);
      }
    }catch(error){
      console.error('[MPosStorageShadow] initial mirror failed',error);
    }
  }

  global.__mposNativeStorageStats=()=>post('stats');
  global.__mposCatalogParity=()=>post('catalogParity');
  global.__nativeStorageShadowStats=global.__mposNativeStorageStats;
  global.__nativeStorageResult=result=>{global.__lastNativeStorageResult=result||null};
  setTimeout(mirrorExistingLocalStorage,0);
})(window);
