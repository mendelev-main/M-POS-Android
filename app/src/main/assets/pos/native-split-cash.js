/* Native cash entry/rounding; persist split progress before marking a part paid. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.paymentScreen;
  if(!bridge||typeof global.openSplitCashPayment!=='function')return;
  const original=global.openSplitCashPayment,close=global.closeModal,show=global.showModal;
  let active=null,generation=0;
  const stamp=()=>JSON.stringify([state.cart,state.discounts,state.loyaltyPrograms,state.loyaltyRedemptions,state.customer,state.orderType,state.deliveryFee,state.deliveryTariffSelected,state._splitPayments,state.currentOrderSource,state.currentWebOrderId,state.paymentPage,state.currency,currentShift()?.id]);
  function abandon(){const old=active;active=null;if(old)bridge.postMessage({action:'cashHide',token:old.token});}
  global.closeModal=function(...args){abandon();return close.apply(this,args);};
  global.showModal=function(...args){abandon();return show.apply(this,args);};
  global.openSplitCashPayment=function(index){
    abandon();
    if(global.MPosNativeSplitCashEnabled===false)return original.apply(this,arguments);
    const part=state._splitPayments?.[index];if(!part||part.paid||part.method!=='cash')return;
    const amount=Math.round(Number(part.amount||0)*100)/100;
    if(!Number.isFinite(amount)||amount<=0){flash('Введите сумму платежа');return;}
    const given=part.cashGiven==null?amount:Number(part.cashGiven);
    if(!Number.isFinite(given)||given<0){flash('Некорректная внесённая сумма');return;}
    global.closeModal();
    const token='native-split-cash-'+(++generation);
    active={token,index,part,amount,cart:state.cart,stamp:stamp()};
    if(bridge.postMessage({action:'cashShow',token,index,amount,given,givenInput:given.toFixed(2),amountLabel:fullMoney(amount),currency:state.currency||'',theme:document.documentElement?.dataset?.theme||'light'})===false){active=null;return original.apply(this,arguments);}
  };
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.NativeSplitCash=Object.freeze({
    handleAction(payload){
      const form=active;if(!form||payload?.token!==form.token||!['cancel','confirm'].includes(payload.action))return;
      if(payload.action==='cancel'){global.closeModal();renderSplitPayment();return;}
      const valid=typeof payload.cashGiven==='number'&&Number.isFinite(payload.cashGiven)&&payload.cashGiven>=form.amount&&typeof payload.change==='number'&&Number.isFinite(payload.change)&&payload.change>=0&&Math.abs(payload.change-(payload.cashGiven-form.amount))<=0.001;
      const unchanged=form.cart===state.cart&&state._splitPayments?.[form.index]===form.part&&form.stamp===stamp();
      global.closeModal();
      if(!valid||!unchanged||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending()){flash('Платёж изменился. Повторите оплату части.');return;}
      // Preserve reviewed mutation timing: tender is assigned before progress commit;
      // completeSplitPayment alone marks paid after its successful persistence.
      form.part.cashGiven=payload.cashGiven;form.part.change=payload.change;
      return global.completeSplitPayment(form.index);
    }
  });
})(window);
