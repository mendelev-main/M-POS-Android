(function(global){
  'use strict';
  const original=global.commitCriticalStorage;
  if(typeof original!=='function'||!global.MPosCore?.Payments)return;
  global.commitCriticalStorage=async function(type,writes){
    if(type!=='payment')return original(type,writes);
    if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw new Error('Незавершённая операция хранения. Перезапустите M POS для восстановления данных');
    const current=typeof state!=='undefined'?state:global.state;
    const keys=Object.keys(writes||{}).sort().join(',');
    if(keys!=='currentOrderSession,orders,products,shifts'||!Array.isArray(current?.orders)||!Array.isArray(writes.orders)||writes.orders.length!==current.orders.length+1)
      throw new Error('Некорректная команда завершения оплаты');
    for(let i=0;i<current.orders.length;i++)if(JSON.stringify(current.orders[i])!==JSON.stringify(writes.orders[i]))throw new Error('Архив чеков изменился перед оплатой');
    const order=writes.orders.at(-1);
    const command={order,products:writes.products,shifts:writes.shifts,session:writes.currentOrderSession,
      expected:{products:current.products,shifts:current.shifts},expectedOrderCount:current.orders.length};
    if(global.MPosNativePricingEnabled!==false){
      command.pricing={version:1,discounts:JSON.parse(JSON.stringify(current.discounts||[])),loyaltyDiscount:Number(order.loyaltyDiscount||0)};
    }
    if(global.MPosNativeLoyaltyRewardsEnabled!==false){
      command.loyalty={version:1,programs:JSON.parse(JSON.stringify(current.loyaltyPrograms||[]))};
    }
    if(global.MPosNativeConfiguredPricesEnabled!==false)command.configuredPrices={version:1};
    if(Number(order.deliveryFee)>0){
      const before=current.shifts.find(s=>s.id===order.shiftId),after=writes.shifts.find(s=>s.id===order.shiftId);
      const count=Array.isArray(before?.cashMovements)?before.cashMovements.length:0;
      if(!Array.isArray(after?.cashMovements)||after.cashMovements.length!==count+1)throw new Error('Некорректное движение доставки');
      command.deliveryMovement=after.cashMovements.at(-1);
    }
    try{await global.MPosCore.Payments.commit(command)}
    catch(error){
      // A timeout cannot prove rollback. Block subsequent critical actions until native state is reloaded.
      if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
      if(typeof global.markStorageBroken==='function')global.markStorageBroken(error);
      throw error;
    }
  };
})(window);
