(function(global){
  'use strict';
  const core=global.MPosCore,storage=global.PrilavokCore?.Storage,bridge=global.webkit?.messageHandlers?.network;
  if(!core?.WebJournal||!storage||!bridge)return;
  const keys=new Set(['webOrderAcceptances','webOrderReadyJournal']),snapshots=new WeakMap(),pending=new Map();let seq=0;
  const enabled=()=>global.MPosNativeWebJournalEnabled!==false;
  const blocked=()=>typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending;
  const clone=value=>JSON.parse(JSON.stringify(value));
  async function command(input){
    if(blocked())throw Error('Перезапустите M POS для восстановления данных');
    try{return await core.WebJournal.execute({version:1,...input});}
    catch(error){if(['patch','markReady'].includes(input.operation)&&String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;throw error;}
  }
  const facade=Object.freeze({...storage,
    async get(key,...args){const value=await storage.get(key,...args);if(keys.has(key)&&value&&typeof value==='object'&&!Array.isArray(value))snapshots.set(value,clone(value));return value;},
    async set(key,value){
      if(!enabled()||!keys.has(key)||!value||!snapshots.has(value))return storage.set(key,value);
      const next=clone(value);await command({operation:'patch',key,expected:snapshots.get(value),next});snapshots.set(value,next);
    }
  });
  global.PrilavokCore.Storage=facade;
  const originalLoad=global.loadKey;
  global.loadKey=function(key,fallback){if(enabled()&&keys.has(key))return facade.get(key,fallback,markStorageBroken);return originalLoad.apply(this,arguments);};
  const previous=global.__nativeNetworkResult;
  global.__nativeNetworkResult=function(result){const entry=pending.get(result?.requestId);if(!entry){if(typeof previous==='function')previous(result);return;}pending.delete(result.requestId);entry.cleanup();if(result.ok===true&&result.authoritative===true)entry.resolve(result.data);else entry.reject(Error(result.message||'Ошибка подтверждения WEB заказа'));};
  function send(input,signal){return new Promise((resolve,reject)=>{
    const requestId='web-ack-'+Date.now()+'-'+(++seq);let timer;
    const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener?.('abort',abort);};
    const cancel=error=>{if(!pending.delete(requestId))return;cleanup();try{bridge.postMessage({action:'loyaltyProfileCancel',requestId});}catch(_error){}reject(error);};
    const abort=()=>{const error=Error('Подтверждение отменено');error.name='AbortError';cancel(error);};
    if(signal?.aborted){const error=Error('Подтверждение отменено');error.name='AbortError';reject(error);return;}
    pending.set(requestId,{resolve,reject,cleanup});signal?.addEventListener?.('abort',abort,{once:true});timer=setTimeout(()=>cancel(Error('Сервер не ответил вовремя')),31000);
    try{if(bridge.postMessage({action:'webAck',requestId,...input})===false)throw Error('Нативный WEB шлюз недоступен');}catch(error){pending.delete(requestId);cleanup();reject(error);}
  });}
  const originalFetch=global.fetch;
  global.fetch=async function(url,options={}){
    const config=networkConfigFromState(),base=String(config.backendUrl||'').replace(/\/+$/,'');
    const match=typeof url==='string'&&url.startsWith(base+'/api/orders/')&&url.slice(base.length).match(/^\/api\/orders\/([^/]+)\/(accept|ready)$/);
    if(!enabled()||!match||options.method!=='POST')return originalFetch.apply(this,arguments);
    const key=match[2]==='accept'?'webOrderAcceptances':'webOrderReadyJournal',id=decodeURIComponent(match[1]),body=JSON.parse(options.body||'{}');
    // Initialize/migrate the document before the command; never use a stale shadow.
    await facade.get(key,{});
    const gate=await command({operation:'gate',key,id,body});
    if(gate.confirmed)return {ok:true,status:200,json:async()=>({})};
    const data=await send({key,id,body,backendUrl:config.backendUrl,deviceKey:config.deviceKey},options.signal);
    await command({operation:'ack',key,id,token:gate.token});
    return {ok:true,status:200,json:async()=>data};
  };
  const originalCleanup=global.removeConfirmedWebEvents;
  global.removeConfirmedWebEvents=async function(journal){
    if(!enabled())return originalCleanup.apply(this,arguments);
    return originalCleanup(await facade.get('webOrderAcceptances',{}));
  };
  const originalReady=global.markCurrentWebOrderReady,queue=core.OrderContext;
  global.markCurrentWebOrderReady=async function(){
    if(!enabled()||!queue)return originalReady.apply(this,arguments);
    const id=state.currentWebOrderId;if(!id||state.currentOrderSource!=='web')return;
    const generation=queue.generation();
    const result=await queue.enqueue(async()=>{
      const before=queue.stamp();
      try{
        await facade.get('webOrderReadyJournal',{});
        const expectedSession=await core.Storage.get('currentOrderSession',null);
        if(before!==queue.stamp()||generation!==queue.generation()||state.currentWebOrderId!==id)return;
        const session=currentOrderSessionSnapshot();session.webOrderStatus='ready';
        const saved=await command({operation:'markReady',key:'webOrderReadyJournal',id,at:Date.now(),expectedSession,session});
        if(before!==queue.stamp()){if(typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;throw Error('Заказ изменился во время сохранения');}
        queue.commit(()=>{state.currentWebOrderStatus='ready';});render();
        snapshots.set(saved.journal,clone(saved.journal));return saved.journal;
      }catch(error){markStorageBroken(error);flash('Не удалось сохранить локальный статус готовности. Подтверждение на сайте не отправлено');}
    });
    if(!result)return;
    if(await confirmWebOrderReady(id,result)){
      delete result[id];try{await facade.set('webOrderReadyJournal',result);}catch(error){markStorageBroken(error);}
      flash('Веб-заказ отмечен как готовый');
    }else flash('Заказ отмечен готовым локально. Подтверждение на сайте будет повторено автоматически');
  };
})(window);
