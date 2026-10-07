(function(global){
  'use strict';
  const core=global.MPosCore,bridge=global.webkit?.messageHandlers?.network;
  if(!core?.LoyaltyJournal||!bridge)return;
  const original={publishPaidOrderLoyalty:global.publishPaidOrderLoyalty,reverseOrderLoyalty:global.reverseOrderLoyalty,settleReturnedOrderLoyalty:global.settleReturnedOrderLoyalty,retryPendingLoyalty:global.retryPendingLoyalty},previous=global.__nativeNetworkResult;
  const enabled=()=>global.MPosNativeLoyaltyOutboxEnabled!==false;
  const active=new Map(),pending=new Map();let seq=0,recovering=null,running=0;
  const tasks=[];
  function drain(){while(running<4&&tasks.length){const item=tasks.shift();running++;Promise.resolve(item.action()).then(item.resolve,item.reject).finally(()=>{running--;drain();});}}
  function schedule(action){return new Promise((resolve,reject)=>{tasks.push({action,resolve,reject});drain();});}
  const blocked=()=>typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending;
  function patch(order,fields=['loyaltySync','loyaltyReversal']){if(!order)return;const current=state.orders?.find(x=>x.id===order.id);if(!current||String(current.customer?.id)!==String(order.customer?.id))return;for(const field of fields)if(Object.prototype.hasOwnProperty.call(order,field))current[field]=order[field];}
  async function journal(command){try{return await core.LoyaltyJournal.execute({version:1,at:Date.now(),...command});}catch(error){if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;if(typeof markStorageBroken==='function')markStorageBroken(error);throw error;}}
  global.__nativeNetworkResult=function(result){const entry=pending.get(result?.requestId);if(!entry){if(typeof previous==='function')previous(result);return;}pending.delete(result.requestId);clearTimeout(entry.timer);if(result.ok===true&&result.authoritative===true)entry.resolve(result.data);else entry.reject(Error(result.message||'Ошибка программы лояльности'));};
  function send(kind,body){return new Promise((resolve,reject)=>{const requestId='loyalty-mutation-'+Date.now()+'-'+(++seq),config=networkConfigFromState();const timer=setTimeout(()=>{if(!pending.delete(requestId))return;try{bridge.postMessage({action:'loyaltyProfileCancel',requestId});}catch(_error){}reject(Error('Сервер не ответил вовремя'));},6000);pending.set(requestId,{resolve,reject,timer});try{if(bridge.postMessage({action:'loyaltyMutation',requestId,kind,body,backendUrl:config.backendUrl,deviceKey:config.deviceKey})===false)throw Error('Нативный серверный шлюз недоступен');}catch(error){pending.delete(requestId);clearTimeout(timer);reject(error);}});}
  function work(id,kind){
    const key=kind+':'+id;if(active.has(key))return active.get(key);
    if(!enabled()||blocked())return Promise.resolve();
    const task=schedule(async()=>{
      if(!enabled()||blocked())return;
      const claim=await journal({operation:'claim',id,kind});if(claim.kind)patch(claim.order,[claim.kind==='sale'?'loyaltySync':'loyaltyReversal']);if(!claim.send)return;
      let data,error;
      try{data=await send(claim.kind,claim.payload);if(claim.kind==='sale'&&data==null)throw Error('Некорректный ответ программы лояльности');}catch(e){error=String(e?.message||e);}
      const result=await journal({operation:'finish',id,kind:claim.kind,token:claim.token,success:error===undefined,...(error===undefined?{data}:{error})});patch(result.order,[claim.kind==='sale'?'loyaltySync':'loyaltyReversal']);
      return result;
    }).then(result=>{if(result?.reverseNext)return work(id,'reversal');}).catch(()=>{}).finally(()=>active.delete(key));active.set(key,task);return task;
  }
  for(const [name,kind]of [['publishPaidOrderLoyalty','sale'],['reverseOrderLoyalty','reversal'],['settleReturnedOrderLoyalty','settle']])global[name]=function(order){if(!enabled())return original[name].apply(this,arguments);if(!order?.customer?.id)return;return work(order.id,kind);};
  global.retryPendingLoyalty=function(){
    if(!enabled())return original.retryPendingLoyalty.apply(this,arguments);
    if(recovering||blocked())return recovering;
    recovering=(async()=>{
      const live=[...active.keys()];
      // A settle command may resolve to either journal kind; protect both while active.
      for(const key of live)if(key.startsWith('settle:')){const id=key.slice(7);live.push('sale:'+id,'reversal:'+id);}
      const result=await journal({operation:'recover',active:live});for(const change of result.orders||[])patch(change.order,change.fields);
      for(const action of result.actions||[])void work(action.id,action.kind);
    })().catch(()=>{}).finally(()=>{recovering=null;});return recovering;
  };
})(window);
