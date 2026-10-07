(function(global){
 'use strict';
 const original=global.commitCriticalStorage,core=global.MPosCore;if(typeof original!=='function'||!core?.ReceivingCommands)return;
 global.commitCriticalStorage=async function(operation,writes){
  if(operation!=='receiving'||global.MPosNativeReceivingCommandsEnabled===false)return original.apply(this,arguments);
  if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw Error('Перезапустите M POS для восстановления данных');
  const pending=global._receivingPending;if(!pending)throw Error('Документ приёмки недоступен');
  const command=JSON.parse(JSON.stringify({version:1,draft:pending.draft,orderId:pending.orderId,writes,expected:{products:state.products,purchaseOrders:state.purchaseOrders,receivings:state.receivings}}));
  try{return await core.ReceivingCommands.commit(command)}catch(error){if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;if(typeof markStorageBroken==='function')markStorageBroken(error);throw error}
 };
 const oldLoadKey=global.loadKey;if(typeof oldLoadKey==='function')global.loadKey=function(key,fallback){return key==='receivingDraft'?global.PrilavokCore.Storage.get(key,fallback,error=>{if(typeof markStorageBroken==='function')markStorageBroken(error)}):oldLoadKey.apply(this,arguments)};
})(window);
