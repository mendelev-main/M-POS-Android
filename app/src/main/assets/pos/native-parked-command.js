(function(global){
  'use strict';
  const original=global.commitCriticalStorage;
  if(typeof original!=='function'||!global.MPosCore?.ParkedOrders)return;
  const operations=new Set(['park-order','resume-parked','delete-parked','park-order-print-state']);
  global.commitCriticalStorage=async function(operation,writes){
    if(!operations.has(operation)||global.MPosNativeParkedCommandsEnabled===false)return original(operation,writes);
    if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw Error('Перезапустите M POS для восстановления данных');
    if(global.MPosCore.OrderContext?.hasPending()||global.MPosCore.CartOperations?.hasPending()||global.MPosCore.SplitPayments?.hasPending())throw Error('Дождитесь завершения изменения заказа');
    const command={version:1,operation,expected:state.parked,writes};
    if(operation==='resume-parked'||operation==='delete-parked'){
      const removed=state.parked.filter(row=>!writes.parked.some(next=>next.id===row.id));
      if(!removed.length)throw Error('Отложенный заказ изменился');
      command.id=removed[0].id;command.cartEmpty=state.cart.length===0;
    }
    try{return await global.MPosCore.ParkedOrders.commit(JSON.parse(JSON.stringify(command)));}
    catch(error){
      if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
      if(typeof global.markStorageBroken==='function')global.markStorageBroken(error);
      throw error;
    }
  };
})(window);
