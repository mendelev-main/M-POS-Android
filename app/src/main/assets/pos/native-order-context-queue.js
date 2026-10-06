/* Ordered context commands share continuity only through acknowledged in-memory patches. */
(function(global){
  'use strict';
  global.MPosCore=global.MPosCore||{};
  let tail=Promise.resolve(),pending=0,epoch=0,presenting=false;
  const successors=new Map();
  const busy=()=>state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending()||global.MPosCore.SplitPayments?.hasPending();
  const stamp=()=>JSON.stringify([state.cart,state._splitPayments,state.orderLabel,state.orderComment,state.customer,state.orderType,state.deliveryFee,state.deliveryTariffSelected,state.deliveryRates,state.discounts,state.loyaltyPrograms,state.loyaltyRedemptions,state.currentOrderSource,state.currentWebOrderId,state.currentWebOrderStatus,typeof currentShift==='function'?currentShift()?.id:null]);
  const continuous=origin=>{const current=stamp(),seen=new Set();let next=origin;while(!seen.has(next)){if(next===current)return true;seen.add(next);if(!successors.has(next))break;next=successors.get(next);}return false;};
  for(const name of ['closeModal','closePaymentPage','showModal']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){if(!presenting)epoch++;return original.apply(this,args);};}
  global.MPosCore.OrderContext=Object.freeze({
    hasPending:()=>pending>0,stamp,generation:()=>epoch,
    commit(patch){const before=stamp();patch();successors.set(before,stamp());},
    present(action){presenting=true;try{return action();}finally{presenting=false;}},
    enqueue(action){
      if(busy()||pending>=32){flash('Дождитесь завершения изменения заказа');return;}
      const origin=stamp(),generation=epoch,cart=state.cart;pending++;
      const work=tail.then(()=>{if(generation===epoch&&cart===state.cart&&continuous(origin)&&!busy())return action();}).finally(()=>{pending--;if(!pending)successors.clear();});
      tail=work.catch(()=>{});return work;
    }
  });
})(window);
