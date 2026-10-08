(function(global){
 'use strict';
 const bridge=global.webkit?.messageHandlers?.network;if(!bridge||typeof global.fetch!=='function')return;
 const original=global.fetch,previous=global.__nativeNetworkResult,pending=new Map();let sequence=0;
 function abortError(){return new DOMException('The operation was aborted','AbortError')}
 global.__nativeNetworkResult=function(result){const item=pending.get(result?.requestId);if(!item){if(typeof previous==='function')previous(result);return}item.finish();if(item.kind==='menu'&&result.ok===true&&result.authoritative===true&&result.httpOk===true){try{global.startWebOrderEvents?.()}catch(_error){/* manual sync is already complete; reconnect remains explicit */}}if(result.ok===true&&result.authoritative===true)item.resolve({ok:result.httpOk,status:result.httpStatus,json:async()=>result.data});else item.reject(result.timeout&&item.kind==='media'?abortError():new Error(result.message||'Сервер не ответил вовремя'))};
 global.fetch=function(url,options){
  options=options||{};
  const config=typeof networkConfigFromState==='function'?networkConfigFromState():null,base=String(config?.backendUrl||'').replace(/\/+$/,'');
  const method=String(options.method||'GET').toUpperCase(),path=String(url).startsWith(base+'/')?String(url).slice(base.length):'';
  let kind=null,reportId='';
  if(global.MPosNativeCatalogTransportEnabled!==false&&method==='POST'&&typeof options.body==='string')kind=path==='/api/menu/sync'?'menu':path==='/api/media/upload'?'media':null;
  if(!kind&&global.MPosNativeBackendTransportEnabled!==false){
   if(method==='GET'&&path==='/health')kind='health';
   else if(method==='GET'&&path==='/api/orders/events?deviceKey='+encodeURIComponent(config?.deviceKey||''))kind='eventsProbe';
   else if(method==='POST'&&path==='/api/orders/test')kind='testOrder';
   else if(method==='PUT'&&path==='/api/device/telegram-settings')kind='telegramSettings';
   else if(method==='POST'&&/^\/api\/device\/live-report\/[A-Za-z0-9_-]{20,100}$/.test(path)){kind='liveReport';reportId=path.split('/').pop()}
  }
  if(!kind||!config?.backendUrl||kind!=='health'&&!config?.deviceKey)return original.apply(this,arguments);
  const catalog=kind==='menu'||kind==='media';
  if(options.signal?.aborted)return Promise.reject(abortError());
  return new Promise((resolve,reject)=>{
   const requestId='catalog-'+Date.now()+'-'+(++sequence);let timer;
   const finish=()=>{pending.delete(requestId);clearTimeout(timer);options.signal?.removeEventListener('abort',cancel)};
   const cancel=()=>{if(!pending.has(requestId))return;finish();try{bridge.postMessage({action:'catalogCancel',requestId})}catch(_){}reject(abortError())};
   pending.set(requestId,{resolve,reject,finish,kind});options.signal?.addEventListener('abort',cancel,{once:true});
   timer=setTimeout(()=>{if(!pending.has(requestId))return;finish();try{bridge.postMessage({action:'catalogCancel',requestId})}catch(_){}reject(kind==='media'?abortError():new Error('Сервер не ответил вовремя'))},kind==='media'?11000:catalog?61000:13000);
   try{if(bridge.postMessage({action:catalog?'catalogExchange':'backendExchange',requestId,kind,reportId,backendUrl:config.backendUrl,deviceKey:config.deviceKey,body:options.body||'{}'})===false)throw new Error('Нативный сетевой шлюз недоступен')}catch(error){finish();reject(error)}
  });
 };
 global.MPosCore=global.MPosCore||{};global.MPosCore.CatalogTransport=Object.freeze({transport:'native',automaticSync:false});
})(window);
