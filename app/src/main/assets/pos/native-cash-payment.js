/* Native ordinary cash editing and tender decision; stock/quote/gift guards remain before sale. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.paymentScreen;
  if(!bridge||!global.MPosCore?.PaymentTotals)return;
  const original=global.openPaymentKeypad,close=global.closeModal,show=global.showModal;
  const enabled=()=>global.MPosNativeCashPaymentEnabled!==false&&global.MPosCore.PaymentTotals.enabled();
  const stamp=()=>JSON.stringify([state.cart,state.discounts,state.loyaltyPrograms,state.loyaltyRedemptions,state.customer,state.orderType,state.deliveryFee,state.deliveryTariffSelected,state._splitPayments,state.currentOrderSource,state.currentWebOrderId,state.paymentPage,state.currency,state._paymentCashGiven,currentShift()?.id]);
  let active=null,generation=0;
  function abandon(){const old=active;active=null;if(old)bridge.postMessage({action:'inputHide',token:old.token});}
  global.closeModal=function(...args){abandon();return close.apply(this,args);};
  global.showModal=function(...args){abandon();return show.apply(this,args);};
  global.openPaymentKeypad=function(total){
    abandon();if(!enabled())return original.apply(this,arguments);
    if(typeof total!=='number'||!Number.isFinite(total)||total<0){flash('Некорректная сумма заказа');return;}
    global.closeModal();
    const token='native-cash-input-'+(++generation);active={token,total,cart:state.cart,stamp:stamp()};
    if(bridge.postMessage({action:'inputShow',token,amount:total,given:0,givenInput:'',amountLabel:fullMoney(total),currency:state.currency||'',theme:document.documentElement?.dataset?.theme||'light'})===false){active=null;return original.apply(this,arguments);}
  };
  global.MPosCore.NativeCashPayment=Object.freeze({
    enabled,
    handleAction(payload){
      const form=active;if(!form||payload?.token!==form.token||!['cancel','confirm'].includes(payload.action))return;
      const unchanged=form.cart===state.cart&&form.stamp===stamp();global.closeModal();
      if(payload.action==='cancel')return;
      if(!unchanged||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending()||typeof payload.cashGiven!=='number'||!Number.isFinite(payload.cashGiven)||payload.cashGiven<0){flash('Заказ изменился. Повторите ввод суммы.');return;}
      state._paymentCashGiven=payload.cashGiven;renderPaymentAmount(form.total);
    },
    confirm(loyaltyValidated,given){
      if(hasSelectedLoyaltyReward()&&!loyaltyValidated){
        const cart=state.cart,root=document.getElementById('payment-page-root');
        // Server validation may update programs, and explicit offline continuation clears
        // redemptions. Those expected changes must still re-enter a fresh native quote.
        const context=()=>JSON.stringify([state.cart,state.discounts,state.customer,state.orderType,state.deliveryFee,state.deliveryTariffSelected,state.paymentPage,state._paymentCashGiven,state._splitPayments,currentShift()?.id]);
        const before=context();
        return beginPaymentWithLoyaltyGuard(()=>{
          if(cart!==state.cart||before!==context()||root!==document.getElementById('payment-page-root')){flash('Заказ изменился. Повторите оплату.');return;}
          return global.confirmPaymentScreen('cash',true);
        });
      }
      const cash=global.MPosCore.PaymentTotals.cash(given);
      if(!cash){flash('Не удалось проверить оплату. Повторите попытку.');return;}
      if(!cash.allowed){flash('Недостаточно внесённой суммы');return;}
      return global.finalizePayment([{method:'cash',amount:cartTotal(),cashGiven:cash.cashGiven,change:cash.change,paid:true}]);
    }
  });
})(window);
