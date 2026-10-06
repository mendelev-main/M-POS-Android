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

  const nativeKeys=new Set(['products','layout','posNavigation','employees','shifts','orders','parked','currentOrderSession','criticalStorageJournal']);
  const ready=new Map();
  let cacheFailures=0;
  const cacheFailuresByKey={products:0,layout:0,posNavigation:0,employees:0,shifts:0,orders:0,parked:0,currentOrderSession:0,criticalStorageJournal:0};
  function requireNative(result,authority=false){
    if(!result?.ok)throw new Error(result?.reason||result?.message||'M POS native storage operation failed');
    if(authority&&result.authoritative!==true)throw new Error('M POS native storage authority missing');
    return result;
  }
  function domainAction(key,action){return (key==='products'?'catalog':key==='employees'?'employee':key==='shifts'?'shift':key==='orders'?'order':key==='parked'?'parked':(key==='currentOrderSession'||key==='criticalStorageJournal')?'recovery':'workspace')+action}
  function initializeNative(key){
    if(!ready.has(key)){
      const promise=(async()=>{
        const status=requireNative(await request(domainAction(key,'Status'),{key}));
        if(status.initialized)return;
        const absent={};
        const seed=await legacyStorage.get(key,absent);
        const payload=seed===absent?null:JSON.stringify(seed);
        requireNative(await request(domainAction(key,'Initialize'),{key,payload}),true);
      })().catch(error=>{ready.delete(key);throw error});
      ready.set(key,promise);
    }
    return ready.get(key);
  }
  async function cacheNative(key,value){
    try{await legacyStorage.set(key,value)}
    catch(error){cacheFailures++;cacheFailuresByKey[key]++;console.error('[MPosStorage] compatibility cache write failed',key,error)}
  }
  function invalidateReceiptCache(){
    try{legacyStorage.remove('orders')}
    catch(error){cacheFailures++;cacheFailuresByKey.orders++;console.error('[MPosStorage] receipt cache invalidation failed',error)}
  }
  async function readNative(key){
    await initializeNative(key);
    return requireNative(await request(domainAction(key,'Read'),{key}),true);
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
      if(!nativeKeys.has(key))return legacyStorage.get(key,fallback,onError);
      try{
        const result=await readNative(key);
        const value=result.found?JSON.parse(result.payload):fallback;
        if(key==='currentOrderSession'){try{await global.MPosCore?.SplitRecovery?.prepare(value);}catch(_error){global.console?.warn?.('[MPosStorage] split recovery compatibility path retained');}}
        return value;
      }catch(error){
        if(onError)onError(error);else console.error('[MPosStorage] read failed',key,error);
        if(key==='criticalStorageJournal')throw error;
        return fallback;
      }
    },
    async set(key,value){
      if(nativeKeys.has(key)){
        const payload=JSON.stringify(value);
        await initializeNative(key);
        requireNative(await request(domainAction(key,'Write'),{key,payload}),true);
        if(key==='orders')invalidateReceiptCache();else await cacheNative(key,JSON.parse(payload));
        if(key==='orders')global.MPosCore?.ReceiptsHistory?.invalidate();
        return;
      }
      await legacyStorage.set(key,value);
      mirrorValue(key,value);
    },
    remove(key){
      if(nativeKeys.has(key))return (async()=>{
        await initializeNative(key);
        requireNative(await request(domainAction(key,'Remove'),{key}),true);
        try{legacyStorage.remove(key)}catch(error){cacheFailures++;cacheFailuresByKey[key]++;console.error('[MPosStorage] compatibility cache remove failed',key,error)}
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
        authoritativeKeys:[...nativeKeys],
        catalogSourceOfTruth:'room',
        catalogCacheFailures:cacheFailuresByKey.products,
        nativeCacheFailures:cacheFailures,
        nativeCacheFailuresByKey:{...cacheFailuresByKey},
        workspaceSourceOfTruth:'room',employeeSourceOfTruth:'room',shiftSourceOfTruth:'room',orderSourceOfTruth:'room',parkedSourceOfTruth:'room',recoverySourceOfTruth:'room'
      });
    }
  });

  const mposCore=global.MPosCore=global.MPosCore||{};
  mposCore.Storage=mposStorage;
  mposCore.RecipeEdit=Object.freeze({
    async calculate(input){return requireNative(await request('recipeEditRead',{payload:JSON.stringify(input)}),true);}
  });
  mposCore.CatalogEdit=Object.freeze({
    async calculate(input){return requireNative(await request('catalogEditRead',{payload:JSON.stringify(input)}),true);}
  });
  mposCore.ParkedOrders=Object.freeze({
    async commit(input){return requireNative(await request('parkedCommit',{payload:JSON.stringify(input)}),true);}
  });
  mposCore.OrderContextRead=Object.freeze({
    async calculate(input){return requireNative(await request('orderContextRead',{payload:JSON.stringify(input)}),true);}
  });
  mposCore.DeliveryRead=Object.freeze({
    async calculate(input){return requireNative(await request('deliveryRead',{payload:JSON.stringify(input)}),true);}
  });
  mposCore.SplitRecoveryRead=Object.freeze({
    async calculate(input){
      return requireNative(await request('splitRecoveryRead',{payload:JSON.stringify(input)}),true);
    }
  });
  mposCore.SplitAmountRead=Object.freeze({
    async calculate(input){
      return requireNative(await request('splitAmountRead',{payload:JSON.stringify(input)}),true);
    }
  });
  mposCore.SplitCount=Object.freeze({
    async calculate(input){
      return requireNative(await request('splitCountRead',{payload:JSON.stringify(input)}),true);
    }
  });
  mposCore.CartTotals=Object.freeze({
    async calculate(input){
      return requireNative(await request('cartTotalsRead',{payload:JSON.stringify(input)}),true);
    }
  });
  mposCore.CartQuantity=Object.freeze({
    async check(input){
      return requireNative(await request('cartQuantityRead',{payload:JSON.stringify(input)}),true);
    }
  });
  mposCore.StockPreflight=Object.freeze({
    async check(input){
      return requireNative(await request('stockPreflightRead',{payload:JSON.stringify(input)}),true);
    }
  });
  mposCore.ConfiguredPrices=Object.freeze({
    async calculate(input){
      return requireNative(await request('configuredPriceRead',{payload:JSON.stringify(input)}),true);
    }
  });
  mposCore.Payments=Object.freeze({
    async commit(command){
      const payload=JSON.stringify(command);
      await Promise.all(['products','shifts','orders','currentOrderSession','criticalStorageJournal'].map(initializeNative));
      let result;
      try{result=await request('paymentCommit',{payload})}
      catch(error){
        if(!String(error?.message).includes('commit status is uncertain'))throw error;
        result=await request('paymentCommit',{payload});
      }
      requireNative(result,true);
      // Native commit owns these documents; obsolete secondary caches must not be reimported.
      for(const key of ['products','shifts','orders','currentOrderSession']){
        try{legacyStorage.remove(key)}catch(error){cacheFailures++;cacheFailuresByKey[key]++;console.error('[MPosStorage] payment cache invalidation failed',key,error)}
      }
      global.MPosCore?.ReceiptsHistory?.invalidate();
      return result;
    }
  });
  mposCore.Returns=Object.freeze({
    async commit(command){
      const payload=JSON.stringify(command);
      await Promise.all(['products','shifts','orders','criticalStorageJournal'].map(initializeNative));
      let result;
      try{result=await request('returnCommit',{payload})}
      catch(error){
        if(!String(error?.message).includes('commit status is uncertain'))throw error;
        result=await request('returnCommit',{payload});
      }
      requireNative(result,true);
      // Native commit owns these documents; obsolete secondary caches must not be reimported.
      for(const key of ['products','shifts','orders']){
        try{legacyStorage.remove(key)}catch(error){cacheFailures++;cacheFailuresByKey[key]++;console.error('[MPosStorage] return cache invalidation failed',key,error)}
      }
      global.MPosCore?.ReceiptsHistory?.invalidate();
      return result;
    }
  });
  mposCore.CashMovements=Object.freeze({
    async commit(command){
      const payload=JSON.stringify(command);
      await Promise.all(['shifts','orders','criticalStorageJournal'].map(initializeNative));
      let result;
      try{result=await request('cashMovementCommit',{payload})}
      catch(error){
        if(!String(error?.message).includes('commit status is uncertain'))throw error;
        result=await request('cashMovementCommit',{payload});
      }
      requireNative(result,true);
      // Native commit owns these documents; obsolete secondary caches must not be reimported.
      for(const key of ['shifts']){
        try{legacyStorage.remove(key)}catch(error){cacheFailures++;cacheFailuresByKey[key]++;console.error('[MPosStorage] cash movement cache invalidation failed',key,error)}
      }
      return result;
    }
  });
  mposCore.ShiftLifecycle=Object.freeze({
    async commit(command){
      const payload=JSON.stringify(command);
      await Promise.all(['shifts','criticalStorageJournal',command.operation==='open'?'employees':'orders'].map(initializeNative));
      let result;
      try{result=await request('shiftLifecycleCommit',{payload})}
      catch(error){
        if(!String(error?.message).includes('commit status is uncertain'))throw error;
        result=await request('shiftLifecycleCommit',{payload});
      }
      requireNative(result,true);
      // Native commit owns these documents; obsolete secondary caches must not be reimported.
      for(const key of ['shifts']){
        try{legacyStorage.remove(key)}catch(error){cacheFailures++;cacheFailuresByKey[key]++;console.error('[MPosStorage] shift lifecycle cache invalidation failed',key,error)}
      }
      return result;
    }
  });
  mposCore.ShiftReports=Object.freeze({
    async read(shiftId,presentation={},closedOnly=false){
      const payload=JSON.stringify({shiftId,currency:presentation.currency||'',establishmentName:presentation.establishmentName||'',closedOnly});
      await Promise.all(['shifts','orders','criticalStorageJournal'].map(initializeNative));
      const result=requireNative(await request('shiftReportRead',{payload}),true);
      if(!result.report||result.report.id!==shiftId||!result.summary)throw new Error('Invalid native shift report');
      return result;
    }
  });
  mposCore.Receipts=Object.freeze({
    async page(offset=0,limit=50){
      await initializeNative('orders');
      return requireNative(await request('orderPage',{key:'orders',payload:JSON.stringify({offset,limit})}),true);
    },
    async save(receipt,expectedRevision){
      const payload=JSON.stringify({receipt,expectedRevision});
      await initializeNative('orders');
      const result=requireNative(await request('orderUpsert',{key:'orders',payload}),true);
      invalidateReceiptCache();
      global.MPosCore?.ReceiptsHistory?.invalidate();
      // Individual writes invalidate the secondary archive cache; full native reads remain authoritative.
      return result;
    }
  });
  mposCore.Catalog=Object.freeze({
    nativeReadsEnabled:true,
    initialize:()=>initializeNative('products'),
    async getNativeSnapshot(){
      const result=await readNative('products');
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
        if(!storageKey||!storageKey.startsWith(legacyPrefix)||nativeKeys.has(storageKey.slice(legacyPrefix.length)))continue;
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
