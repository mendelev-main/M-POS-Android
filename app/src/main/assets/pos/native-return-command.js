(function(global){
  'use strict';
  const original=global.commitCriticalStorage;
  if(typeof original!=='function'||!global.MPosCore?.Returns)return;
  global.commitCriticalStorage=async function(type,writes){
    if(type!=='return')return original(type,writes);
    if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw new Error('Незавершённая операция хранения. Перезапустите M POS для восстановления данных');
    const current=typeof state!=='undefined'?state:global.state;
    if(Object.keys(writes||{}).sort().join(',')!=='orders,products,shifts'||!Array.isArray(writes.orders)||writes.orders.length!==current.orders.length)throw new Error('Некорректная команда возврата');
    const changed=current.orders.map((order,i)=>JSON.stringify(order)!==JSON.stringify(writes.orders[i])?i:-1).filter(i=>i>=0);
    if(changed.length!==1)throw new Error('Возврат должен изменять один чек');
    const expectedReceipt=current.orders[changed[0]],receipt=writes.orders[changed[0]];
    // Historical recipes cannot be reconstructed for old receipts. Keep their reviewed path.
    if(!Object.prototype.hasOwnProperty.call(expectedReceipt,'stockConsumption'))return original(type,writes);
    const command={receipt,expectedReceipt,products:writes.products,shifts:writes.shifts,
      expected:{products:current.products,shifts:current.shifts},expectedOrderCount:current.orders.length};
    const before=current.shifts.find(s=>s.id===receipt.returnedShiftId),after=writes.shifts.find(s=>s.id===receipt.returnedShiftId);
    const count=Array.isArray(before?.cashMovements)?before.cashMovements.length:0;
    if(!Array.isArray(after?.cashMovements)||after.cashMovements.length<count||after.cashMovements.length>count+1)throw new Error('Некорректное движение возврата');
    if(after.cashMovements.length===count+1)command.refundMovement=after.cashMovements.at(-1);
    try{await global.MPosCore.Returns.commit(command)}
    catch(error){
      if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
      if(typeof global.markStorageBroken==='function')global.markStorageBroken(error);
      throw error;
    }
  };
})(window);
