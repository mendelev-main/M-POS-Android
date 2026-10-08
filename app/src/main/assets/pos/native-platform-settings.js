(function(global){
  'use strict';
  const appState=()=>typeof state!=='undefined'?state:global.state;
  const bridge=global.webkit?.messageHandlers?.settings;
  if(!bridge||typeof global.__printerSettingsSnapshot!=='function')return;
  const originalSnapshot=global.__printerSettingsSnapshot,legacy=global.localStorage;
  const clone=value=>JSON.parse(JSON.stringify(value));
  const pending=new Map();let sequence=0,cache=null,initialization=null,frame=null,busy=false,blocked=false,backupFrame=null;
  const fields={printers:'printers',posNotificationSettings:'posNotifications'};
  const owns=key=>Object.prototype.hasOwnProperty.call(fields,String(key));
  function request(action,payload={}){
    const requestId='platform-settings-'+(++sequence);
    return new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{pending.delete(requestId);blocked=true;reject(Error('native settings commit status is uncertain'))},15000);
      pending.set(requestId,{resolve,reject,timer});
      try{if(bridge.postMessage({action,requestId,...clone(payload)})===false)throw Error('Модуль настроек Android недоступен')}
      catch(error){clearTimeout(timer);pending.delete(requestId);reject(error)}
    });
  }
  const priorResult=global.__nativeSettingsResult;
  global.__nativeSettingsResult=result=>{
    const job=pending.get(result?.requestId);
    if(!job){priorResult?.(result);return}
    pending.delete(result.requestId);clearTimeout(job.timer);
    if(!result.ok){if(result.uncertain)blocked=true;job.reject(Error(result.message||'Не удалось сохранить настройки'));return}
    job.resolve(result);
  };
  function snapshot(){if(!cache)throw Error('Настройки Android ещё загружаются');return clone(frame?.next||cache)}
  function facade(){
    const storage={getItem(key){return owns(key)?JSON.stringify(snapshot()[fields[String(key)]]):legacy.getItem(key)},
      setItem(key,value){if(!owns(key))return legacy.setItem(key,value);if(!frame)throw Error('Настройки сохраняются только через модуль Android');frame.next[fields[String(key)]]=JSON.parse(String(value))},
      removeItem(key){if(!owns(key))return legacy.removeItem(key);if(!frame)throw Error('Настройки сохраняются только через модуль Android');frame.next[fields[String(key)]]=String(key)==='printers'?[]:{}},
      clear(){throw Error('Для очистки данных используйте настройки M POS')},
      key(index){const keys=[];for(let i=0;i<legacy.length;i++){const key=legacy.key(i);if(key!==null&&!keys.includes(key))keys.push(key)}for(const key of Object.keys(fields))if(!keys.includes(key))keys.push(key);return keys[index]??null}};
    Object.defineProperty(storage,'length',{get(){let n=0;while(storage.key(n)!==null)n++;return n}});
    Object.defineProperty(global,'localStorage',{configurable:true,value:Object.freeze(storage)});
  }
  function initialize(){
    if(initialization)return initialization;
    initialization=(async()=>{
      let result=await request('platformSettingsStatus');
      if(!result.authoritative)result=await request('platformSettingsInitialize',{settings:clone(originalSnapshot())});
      if(!result.authoritative||!result.snapshot?.settings)throw Error('Не удалось открыть настройки Android');
      cache=clone(result.snapshot.settings);facade();global.__printerSettingsSnapshot=snapshot;
      if(appState()){appState().printers=clone(cache.printers);appState().posNotifications=clone(cache.posNotifications)}
      return snapshot();
    })();
    return initialization;
  }
  function recovery(error){if(blocked&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;global.flash?.(blocked?'Неизвестен результат сохранения. Перезапустите M POS':error?.message||'Не удалось сохранить настройки')}
  async function transaction(original,self,args){
    await initialize();
    if(blocked)throw Error('Перезапустите M POS для восстановления настроек');
    if(busy)throw Error('Дождитесь сохранения настроек');
    busy=true;
    const previous=clone(cache),oldState=appState()?{printers:appState().printers,posNotifications:appState().posNotifications}:null;
    const effects=[],replaced=[],handlers=global.webkit.messageHandlers;
    let next,result,syncFailure;
    frame={next:clone(cache)};
    try{
      // Reviewed handlers are synchronous. Stage their UI and test-print effects until disk acknowledgement.
      for(const name of ['flash','render','closeModal','openPrintersManager','closePrinterPage']){
        const fn=global[name];if(typeof fn!=='function')continue;
        replaced.push([name,fn]);global[name]=function(...values){effects.push(()=>fn.apply(this,values))};
      }
      global.webkit.messageHandlers={...handlers,printer:{postMessage(payload){const frozen=clone(payload);effects.push(()=>handlers.printer.postMessage(frozen));return true}}};
      result=original.apply(self,args);
      if(result&&typeof result.then==='function')throw Error('Reviewed settings handler must stage synchronously');
      next=clone(frame.next);
    }catch(error){syncFailure=error}
    finally{
      frame=null;global.webkit.messageHandlers=handlers;for(const [name,fn]of replaced)global[name]=fn;
      if(oldState){appState().printers=oldState.printers;appState().posNotifications=oldState.posNotifications}
      // The asynchronous portion keeps the busy guard; synchronous failures release below.
      if(!next)busy=false;
    }
    if(syncFailure){recovery(syncFailure);throw syncFailure}
    try{
      if(result===false||JSON.stringify(next)===JSON.stringify(previous)){
        for(const effect of effects)effect();return result;
      }
      const acknowledged=await request('platformSettingsWrite',{settings:next,expected:previous});
      if(!acknowledged.authoritative||!acknowledged.snapshot?.settings){blocked=true;throw Error('native settings commit status is uncertain')}
      cache=clone(acknowledged.snapshot.settings);
      if(appState()){appState().printers=clone(cache.printers);appState().posNotifications=clone(cache.posNotifications)}
      // Browser keys are a best-effort compatibility copy, never the authority after cutover.
      try{for(const [key,field]of Object.entries(fields))legacy.setItem(key,JSON.stringify(cache[field]))}catch(_){/* native commit already confirmed */}
      for(const effect of effects)effect();return result;
    }catch(error){recovery(error);throw error}finally{busy=false}
  }
  for(const name of ['savePrinterFromPage','deletePrinter','saveNotificationSettings','testPrinterFromPage','__restorePrinterSettings']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){const promise=transaction(original,this,args);promise.catch(()=>{});if(name==='__restorePrinterSettings'&&backupFrame)backupFrame.promise=promise;return promise};
  }
  // Backup v13's reviewed apply handler calls restore synchronously. Its caller must also await
  // the new native disk acknowledgement; no source/verifier modifications are required.
  const apply=global.applyBackupData;
  if(typeof apply==='function')global.applyBackupData=async function(...args){
    if(backupFrame)throw Error('Импорт уже выполняется');const scope={promise:null};backupFrame=scope;
    try{const result=await apply.apply(this,args);if(scope.promise)await scope.promise;return result}finally{backupFrame=null}
  };
  const load=global.loadAll;
  if(typeof load==='function')global.loadAll=async function(...args){await initialize();return load.apply(this,args)};
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.PlatformSettings=Object.freeze({initialize,snapshot,get ready(){return !!cache},get blocked(){return blocked}});
})(window);
