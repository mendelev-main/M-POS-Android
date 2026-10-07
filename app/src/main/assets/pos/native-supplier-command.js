(function(global){
 'use strict';
 const storage=global.PrilavokCore?.Storage;if(!storage||!global.MPosCore?.SupplierCommands)return;
 let gesture=null;const enabled=()=>global.MPosNativeSupplierCommandsEnabled!==false;
 const facade=Object.freeze({...storage,async set(key,value){
  if(key!=='suppliers'||!gesture||!enabled())return storage.set(key,value);
  if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw Error('Перезапустите M POS для восстановления данных');
  const command=JSON.parse(JSON.stringify({version:1,...gesture,expected:state.suppliers,next:value,shiftId:currentShift()?.id??null}));
  try{return await global.MPosCore.SupplierCommands.commit(command)}catch(error){if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;throw error}
 }});
 global.PrilavokCore.Storage=facade;
 const oldLoadKey=global.loadKey;if(typeof oldLoadKey==='function')global.loadKey=function(key,fallback){return key==='suppliers'?facade.get(key,fallback,error=>{if(typeof markStorageBroken==='function')markStorageBroken(error)}):oldLoadKey.apply(this,arguments)};
 for(const [name,operation]of [['saveSupplier','save'],['confirmDeleteSupplier','delete']]){const original=global[name];if(typeof original!=='function')continue;global[name]=function(id=''){if(!enabled())return original.apply(this,arguments);const old=gesture;gesture={operation,id};try{return original.apply(this,arguments)}finally{gesture=old}}}
})(window);
