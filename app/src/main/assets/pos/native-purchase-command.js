(function(global){
 'use strict';
 const original=global.commitCriticalStorage,core=global.MPosCore;if(typeof original!=='function'||!core?.PurchaseCommands)return;
 global.commitCriticalStorage=async function(operation,writes){
  if(!['create-purchase-order','delete-purchase-order'].includes(operation)||global.MPosNativePurchaseCommandsEnabled===false)return original.apply(this,arguments);
  if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw Error('Перезапустите M POS для восстановления данных');
  const command={version:1,operation,writes,expected:{purchaseOrders:state.purchaseOrders,products:state.products,receivings:state.receivings},shiftId:currentShift()?.id??null};
  if(operation==='create-purchase-order'){command.supplierId=state.purchaseOrderSupplierId;command.cart=state.purchaseOrderCart}
  else{const changed=writes.purchaseOrders.filter(row=>!state.purchaseOrders.some(old=>old.id===row.id&&JSON.stringify(old)===JSON.stringify(row)));if(changed.length!==1)throw Error('Заказ поставщику изменился');command.id=changed[0].id}
  try{return await core.PurchaseCommands.commit(JSON.parse(JSON.stringify(command)))}catch(error){if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;if(typeof markStorageBroken==='function')markStorageBroken(error);throw error}
 };
 const oldLoadKey=global.loadKey;if(typeof oldLoadKey==='function')global.loadKey=function(key,fallback){return ['purchaseOrders','receivings'].includes(key)?global.PrilavokCore.Storage.get(key,fallback,error=>{if(typeof markStorageBroken==='function')markStorageBroken(error)}):oldLoadKey.apply(this,arguments)};
})(window);
