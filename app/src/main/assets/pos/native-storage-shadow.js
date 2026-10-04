(function(global){
  'use strict';

  const bridge=global.webkit?.messageHandlers?.storage;
  const legacyStorage=global.PrilavokCore?.Storage;
  if(!bridge||!legacyStorage)return;

  let sequence=0;
  const pending=new Map();
  const legacyPrefix='prilavok_';

  function post(action,payload){
    const requestId='storage-'+(++sequence);
    try{
      bridge.postMessage({action,requestId,...(payload||{})});
      return requestId;
    }catch(error){
      console.error('[MPosStorageShadow] bridge failed',error);
      return null;
    }
  }

  function request(action,payload){
    return new Promise((resolve,reject)=>{
      const requestId=post(action,payload);
      if(!requestId){reject(new Error('M POS native storage bridge unavailable'));return}
      const timer=setTimeout(()=>{
        pending.delete(requestId);
        reject(new Error('M POS native storage request timed out'));
      },3000);
      pending.set(requestId,{resolve,reject,timer});
    });
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
  mposCore.Catalog=Object.freeze({
    nativeReadsEnabled:false,
    async getNativeSnapshot(){
      const result=await request('catalogSnapshot');
      if(!result?.ok)throw new Error(result?.reason||result?.message||'M POS native catalog unavailable');
      return result;
    },
    async parity(){
      return request('catalogParity');
    }
  });

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

  global.__mposNativeStorageStats=()=>request('stats');
  global.__mposCatalogParity=()=>mposCore.Catalog.parity();
  global.__mposCatalogSnapshot=()=>mposCore.Catalog.getNativeSnapshot();
  global.__nativeStorageShadowStats=global.__mposNativeStorageStats;
  global.__nativeStorageResult=result=>{
    global.__lastNativeStorageResult=result||null;
    const requestId=result?.requestId;
    const waiter=requestId?pending.get(requestId):null;
    if(!waiter)return;
    pending.delete(requestId);
    clearTimeout(waiter.timer);
    waiter.resolve(result);
  };
  setTimeout(mirrorExistingLocalStorage,0);
})(window);
