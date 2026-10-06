/* Room is authoritative for payment-entry stock checks; settlement checks again atomically. */
(function(global){
  'use strict';
  if(!global.MPosCore?.StockPreflight)return;
  let pending=false,epoch=0;
  const enabled=()=>global.MPosNativePaymentPreflightEnabled!==false;
  const fingerprint=()=>JSON.stringify([state.cart,state.orderLabel,state.tab,state.busy,state.products,state.customer,state.loyaltyRedemptions,state.loyaltyPrograms,state.discounts,state.orderType,state.deliveryFee,state.deliveryTariffSelected,state.deliveryRates,state.paymentPage,state._paymentCashGiven,state._splitPayments,state.shifts]);
  for(const name of ['closePaymentPage','closeModal']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){epoch++;return original.apply(this,args);};
  }
  for(const name of ['openPaymentModal','confirmPaymentScreen','paySplitPart']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=async function(...args){
      if(!enabled())return original.apply(this,args);
      if(state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)){flash('Дождитесь завершения текущей операции');return;}
      if(global.MPosCore.CartOperations?.hasPending()||(global.MPosCore.SplitPayments?.hasPending()||global.MPosCore.OrderContext?.hasPending())){flash('Дождитесь завершения изменения заказа');return;}
      if(pending){flash('Дождитесь проверки остатков');return;}
      if(name==='confirmPaymentScreen'&&typeof loyaltyPaymentGuardBusy!=='undefined'&&loyaltyPaymentGuardBusy){flash('Дождитесь проверки подарка');return;}
      // Preserve reviewed guards that precede the stock decision.
      if(name!=='paySplitPart'&&!requireDeliveryTariff())return;
      if(name==='openPaymentModal'){
        if(!state.cart.length){flash('Заказ пуст');return;}
        if(!currentShift()){flash('Смена не открыта');return;}
      }
      const cart=state.cart,before=fingerprint(),generation=epoch;
      const stale=()=>state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||!enabled()||generation!==epoch||cart!==state.cart||before!==fingerprint()||global.MPosCore.CartOperations?.hasPending()||(global.MPosCore.SplitPayments?.hasPending()||global.MPosCore.OrderContext?.hasPending());
      const cashGiven=name==='confirmPaymentScreen'&&args[0]==='cash'&&global.MPosCore.NativeCashPayment?.enabled()?paymentGivenValue():undefined;
      pending=true;
      let verdict;
      try{
        verdict=await global.MPosCore.StockPreflight.check({version:1,items:cart.map(i=>({productId:i.productId,qty:i.qty,selectedModifiers:(i.selectedModifiers||[]).map(m=>({productId:m.productId,qty:m.qty}))}))});
        if(!stale()&&verdict?.allowed===true&&global.MPosCore.PaymentTotals?.enabled()){
          if(!await global.MPosCore.PaymentTotals.prepare(cashGiven))return;
        }
      }catch(_error){if(!stale())flash('Не удалось проверить заказ. Повторите оплату.');return;}
      finally{pending=false;}
      if(stale())return;
      if(typeof verdict?.allowed!=='boolean'){flash('Не удалось проверить заказ. Повторите оплату.');return;}
      if(!verdict.allowed){flash(verdict.reason||'Недостаточно остатка');return;}
      if(name!=='paySplitPart'&&global.MPosCore.NativeDelivery?.enabled()&&!requireDeliveryTariff())return;
      if(cashGiven!==undefined)return global.MPosCore.NativeCashPayment.confirm(args[1]===true,cashGiven);
      // These reviewed handlers are synchronous. Loyalty callbacks re-enter this wrapper
      // after awaiting the server and therefore obtain a fresh Room decision.
      const legacy=global.canFulfillCart;
      global.canFulfillCart=()=>true;
      try{return original.apply(this,args);}finally{global.canFulfillCart=legacy;}
    };
  }
})(window);
