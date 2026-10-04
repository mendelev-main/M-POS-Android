(function(global){
  'use strict';

  const bridge=global.webkit?.messageHandlers?.storage;
  const core=global.PrilavokCore?.Storage;
  if(!bridge||!core)return;

  let sequence=0;
  const prefix='prilavok_';

  function post(action,payload){
    try{
      bridge.postMessage({action,requestId:'storage-'+(++sequence),...(payload||{})});
    }catch(error){
      console.error('[NativeStorageShadow] bridge failed',error);
    }
  }

  function mirrorSerialized(key,serialized){
    if(typeof key!=='string'||typeof serialized!=='string')return;
    post('put',{key,payload:serialized});
  }

  function mirrorValue(key,value){
    try{mirrorSerialized(key,JSON.stringify(value))}
    catch(error){console.error('[NativeStorageShadow] serialization failed',key,error)}
  }

  global.PrilavokCore.Storage=Object.freeze({
    async get(key,fallback,onError){
      return core.get(key,fallback,onError);
    },
    async set(key,value){
      await core.set(key,value);
      mirrorValue(key,value);
    },
    remove(key){
      const result=core.remove(key);
      post('remove',{key});
      return result;
    },
    describe(){
      const current=core.describe();
      return Object.freeze({...current,nativeShadow:'room',nativeShadowAuthoritative:false,sourceOfTruth:'local-pos'});
    }
  });

  function mirrorExistingLocalStorage(){
    const description=core.describe?.();
    if(description?.mode!=='localStorage')return;
    try{
      for(let i=0;i<global.localStorage.length;i++){
        const storageKey=global.localStorage.key(i);
        if(!storageKey||!storageKey.startsWith(prefix))continue;
        const serialized=global.localStorage.getItem(storageKey);
        if(serialized!==null)mirrorSerialized(storageKey.slice(prefix.length),serialized);
      }
    }catch(error){
      console.error('[NativeStorageShadow] initial mirror failed',error);
    }
  }

  global.__nativeStorageShadowStats=()=>post('stats');
  global.__nativeStorageResult=result=>{global.__lastNativeStorageResult=result||null};
  setTimeout(mirrorExistingLocalStorage,0);
})(window);
