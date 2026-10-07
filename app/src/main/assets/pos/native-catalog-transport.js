(function(global){
 'use strict';
 const bridge=global.webkit?.messageHandlers?.network;if(!bridge||typeof global.fetch!=='function')return;
 const original=global.fetch,previous=global.__nativeNetworkResult,pending=new Map();let sequence=0;
 function abortError(){return new DOMException('The operation was aborted','AbortError')}
 global.__nativeNetworkResult=function(result){const item=pending.get(result?.requestId);if(!item){if(typeof previous==='function')previous(result);return}item.finish();if(result.ok===true&&result.authoritative===true)item.resolve({ok:result.httpOk,status:result.httpStatus,json:async()=>result.data});else item.reject(result.timeout&&item.kind==='media'?abortError():new Error(result.message||'Сервер не ответил вовремя'))};
 global.fetch=function(url,options){
  if(global.MPosNativeCatalogTransportEnabled===false||options?.method?.toUpperCase()!=='POST'||typeof options.body!=='string')return original.apply(this,arguments);
  const config=typeof networkConfigFromState==='function'?networkConfigFromState():null,base=String(config?.backendUrl||'').replace(/\/+$/,'');
  const kind=String(url)===base+'/api/menu/sync'?'menu':String(url)===base+'/api/media/upload'?'media':null;
  if(!kind||!config?.deviceKey)return original.apply(this,arguments);
  if(options.signal?.aborted)return Promise.reject(abortError());
  return new Promise((resolve,reject)=>{
   const requestId='catalog-'+Date.now()+'-'+(++sequence);let timer;
   const finish=()=>{pending.delete(requestId);clearTimeout(timer);options.signal?.removeEventListener('abort',cancel)};
   const cancel=()=>{if(!pending.has(requestId))return;finish();try{bridge.postMessage({action:'catalogCancel',requestId})}catch(_){}reject(abortError())};
   pending.set(requestId,{resolve,reject,finish,kind});options.signal?.addEventListener('abort',cancel,{once:true});
   timer=setTimeout(()=>{if(!pending.has(requestId))return;finish();try{bridge.postMessage({action:'catalogCancel',requestId})}catch(_){}reject(kind==='media'?abortError():new Error('Сервер не ответил вовремя'))},kind==='media'?11000:61000);
   try{if(bridge.postMessage({action:'catalogExchange',requestId,kind,backendUrl:config.backendUrl,deviceKey:config.deviceKey,body:options.body})===false)throw new Error('Нативный сетевой шлюз недоступен')}catch(error){finish();reject(error)}
  });
 };
 global.MPosCore=global.MPosCore||{};global.MPosCore.CatalogTransport=Object.freeze({transport:'native',automaticSync:false});
})(window);
