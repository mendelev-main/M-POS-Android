(function(global){
  'use strict';
  const original=global.commitCriticalStorage;
  if(typeof original!=='function'||!global.MPosCore?.ShiftLifecycle)return;
  global.commitCriticalStorage=async function(type,writes){
    if(type!=='open-shift'&&type!=='close-shift')return original(type,writes);
    if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw new Error('Незавершённая операция хранения. Перезапустите M POS для восстановления данных');
    const current=typeof state!=='undefined'?state:global.state;
    if(Object.keys(writes||{}).join(',')!=='shifts'||!Array.isArray(writes.shifts)||!Array.isArray(current.shifts))throw new Error('Некорректная команда смены');
    let command;
    if(type==='open-shift'){
      if(writes.shifts.length!==current.shifts.length+1)throw new Error('Открытие должно добавлять одну смену');
      for(let i=0;i<current.shifts.length;i++)if(JSON.stringify(current.shifts[i])!==JSON.stringify(writes.shifts[i]))throw new Error('История смен изменилась');
      command={operation:'open',shift:writes.shifts.at(-1),expectedEmployees:current.employees,expectedShifts:current.shifts,shifts:writes.shifts};
    }else{
      if(writes.shifts.length!==current.shifts.length)throw new Error('Некорректное закрытие смены');
      const changed=current.shifts.map((shift,i)=>JSON.stringify(shift)!==JSON.stringify(writes.shifts[i])?i:-1).filter(i=>i>=0);
      if(changed.length!==1)throw new Error('Закрытие должно изменять одну смену');
      const before=current.shifts[changed[0]];
      command={operation:'close',shift:writes.shifts[changed[0]],expectedShifts:current.shifts,shifts:writes.shifts,
        expectedOrderCount:current.orders.length,expectedCash:cashDrawerBalance(before)};
    }
    try{await global.MPosCore.ShiftLifecycle.commit(command)}
    catch(error){
      if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
      if(typeof global.markStorageBroken==='function')global.markStorageBroken(error);
      throw error;
    }
  };
})(window);
