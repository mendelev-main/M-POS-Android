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
      const requestId='storage-'+(++sequence);
      const timer=setTimeout(()=>{
        pending.delete(requestId);
        reject(new Error('M POS native storage request timed out; commit status is uncertain'));
      },15000);
      pending.set(requestId,{resolve,reject,timer});
      try{
        if(bridge.postMessage({action,requestId,...(payload||{})})===false)throw new Error('M POS native storage bridge unavailable');
      }catch(error){pending.delete(requestId);clearTimeout(timer);reject(error)}
    });
  }

  let catalogReady=null;
  let cacheFailures=0;
  function requireNative(result,authority=false){
    if(!result?.ok)throw new Error(result?.reason||result?.message||'M POS native catalog operation failed');
    if(authority&&result.authoritative!==true)throw new Error('M POS native catalog authority missing');
    return result;
  }
  function initializeCatalog(){
    if(!catalogReady){
      catalogReady=(async()=>{
        const status=requireNative(await request('catalogStatus'));
        if(status.initialized)return;
        const absent={};
        const seed=await legacyStorage.get('products',absent);
        const payload=seed===absent?null:JSON.stringify(seed);
        requireNative(await request('catalogInitialize',{payload}),true);
      })().catch(error=>{catalogReady=null;throw error});
    }
    return catalogReady;
  }
  async function cacheCatalog(value){
    try{await legacyStorage.set('products',value)}
    catch(error){cacheFailures++;console.error('[MPosCatalog] compatibility cache write failed',error)}
  }
  async function readCatalog(){
    await initializeCatalog();
    const result=requireNative(await request('catalogRead'));
    if(result.authoritative!==true)throw new Error('M POS native catalog authority missing');
    return result;
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
      if(key!=='products')return legacyStorage.get(key,fallback,onError);
      try{
        const result=await readCatalog();
        return result.found?JSON.parse(result.payload):fallback;
      }catch(error){
        if(onError)onError(error);else console.error('[MPosCatalog] read failed',error);
        return fallback;
      }
    },
    async set(key,value){
      if(key==='products'){
        const payload=JSON.stringify(value);
        await initializeCatalog();
        requireNative(await request('catalogWrite',{payload}),true);
        await cacheCatalog(JSON.parse(payload));
        return;
      }
      await legacyStorage.set(key,value);
      mirrorValue(key,value);
    },
    remove(key){
      if(key==='products')return (async()=>{
        await initializeCatalog();
        requireNative(await request('catalogRemove'),true);
        try{legacyStorage.remove(key)}catch(error){cacheFailures++;console.error('[MPosCatalog] compatibility cache remove failed',error)}
      })();
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
        sourceOfTruth:'local-pos',
        authoritativeKeys:['products'],
        catalogSourceOfTruth:'room',
        catalogCacheFailures:cacheFailures
      });
    }
  });

  const mposCore=global.MPosCore=global.MPosCore||{};
  mposCore.Storage=mposStorage;
  mposCore.Catalog=Object.freeze({
    nativeReadsEnabled:true,
    initialize:initializeCatalog,
    async getNativeSnapshot(){
      const result=await readCatalog();
      return {...result,products:result.found?JSON.parse(result.payload):[]};
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
        if(!storageKey||!storageKey.startsWith(legacyPrefix)||storageKey===legacyPrefix+'products')continue;
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
