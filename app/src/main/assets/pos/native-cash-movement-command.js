(function(global){
  'use strict';
  const original=global.commitCriticalStorage;
  if(typeof original!=='function'||!global.MPosCore?.CashMovements)return;
  global.commitCriticalStorage=async function(type,writes){
    if(type!=='cash-movement')return original(type,writes);
    if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw new Error('Незавершённая операция хранения. Перезапустите M POS для восстановления данных');
    const current=typeof state!=='undefined'?state:global.state;
    if(Object.keys(writes||{}).join(',')!=='shifts'||!Array.isArray(writes.shifts)||writes.shifts.length!==current.shifts.length)throw new Error('Некорректная команда движения наличных');
    const changed=current.shifts.map((shift,i)=>JSON.stringify(shift)!==JSON.stringify(writes.shifts[i])?i:-1).filter(i=>i>=0);
    if(changed.length!==1)throw new Error('Движение должно изменять одну смену');
    const before=current.shifts[changed[0]],after=writes.shifts[changed[0]];
    const previous=Array.isArray(before.cashMovements)?before.cashMovements:[];
    if(after.id!==before.id||!Array.isArray(after.cashMovements)||after.cashMovements.length!==previous.length+1)throw new Error('Некорректное движение наличных');
    for(let i=0;i<previous.length;i++)if(JSON.stringify(previous[i])!==JSON.stringify(after.cashMovements[i]))throw new Error('История движения наличных изменилась');
    const command={shiftId:before.id,movement:after.cashMovements.at(-1),expectedShifts:current.shifts,shifts:writes.shifts};
    try{await global.MPosCore.CashMovements.commit(command)}
    catch(error){
      if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
      if(typeof global.markStorageBroken==='function')global.markStorageBroken(error);
      throw error;
    }
  };
})(window);
