(function(global){
 'use strict';
 const original=global.commitCriticalStorage,core=global.MPosCore;if(typeof original!=='function'||!core?.InventoryCommands)return;
 global.commitCriticalStorage=async function(operation,writes){
  if(!['inventory-fix','inventory-complete'].includes(operation)||global.MPosNativeInventoryCommandsEnabled===false)return original.apply(this,arguments);
  if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw Error('Перезапустите M POS для восстановления данных');
  const command={version:1,operation,writes,expected:{products:state.products,inventoryDraft:state.inventoryDraft,inventoryConfig:state.inventoryConfig,inventoryHistory:state.inventoryHistory}};
  if(operation==='inventory-fix'){const changed=writes.inventoryDraft.items.filter(row=>!state.inventoryDraft.items.some(old=>old.productId===row.productId&&JSON.stringify(old)===JSON.stringify(row)));if(changed.length!==1)throw Error('Строки инвентаризации изменились');command.id=changed[0].productId}
  try{return await core.InventoryCommands.commit(JSON.parse(JSON.stringify(command)))}catch(error){if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;if(typeof markStorageBroken==='function')markStorageBroken(error);throw error}
 };
 const oldLoadKey=global.loadKey;if(typeof oldLoadKey==='function')global.loadKey=function(key,fallback){return ['inventoryConfig','inventoryDraft','inventoryHistory'].includes(key)?global.PrilavokCore.Storage.get(key,fallback,error=>{if(typeof markStorageBroken==='function')markStorageBroken(error)}):oldLoadKey.apply(this,arguments)};
})(window);
