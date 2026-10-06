/* Native terminal confirmation; settlement and split-progress persistence keep their approved paths. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.paymentScreen;
  if(!bridge||typeof global.openCardPartConfirmation!=='function')return;
  const original=global.openCardPartConfirmation,close=global.closeModal,show=global.showModal;
  let active=null,generation=0;
  const stamp=()=>JSON.stringify([state.cart,state.discounts,state.loyaltyPrograms,state.loyaltyRedemptions,state.customer,state.orderType,state.deliveryFee,state.deliveryTariffSelected,state._splitPayments,state.currentOrderSource,state.currentWebOrderId,state.paymentPage,state.currency,currentShift()?.id]);
  function abandon(){const old=active;active=null;if(old)bridge.postMessage({action:'hide',token:old.token});}
  global.closeModal=function(...args){abandon();return close.apply(this,args);};
  global.showModal=function(...args){abandon();return show.apply(this,args);};
  global.openCardPartConfirmation=function(amount,onSuccess){
    abandon();
    if(global.MPosNativeCardConfirmationEnabled===false)return original.apply(this,arguments);
    if(typeof amount!=='number'||!Number.isFinite(amount)||amount<0||typeof onSuccess!=='function'){flash('Некорректная сумма оплаты');return;}
    global.closeModal();global.__cardPaymentConfirm=null;
    const token='native-card-confirmation-'+(++generation);
    const form={token,onSuccess,cart:state.cart,stamp:stamp()};active=form;
    if(bridge.postMessage({action:'show',token,amount,amountLabel:fullMoney(amount),theme:document.documentElement?.dataset?.theme||'light'})===false){
      active=null;return original.apply(this,arguments);
    }
  };
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.NativeCardConfirmation=Object.freeze({
    handleAction(payload){
      const form=active;if(!form||payload?.token!==form.token||!['cancel','confirm'].includes(payload.action))return;
      const unchanged=form.cart===state.cart&&form.stamp===stamp();
      active=null;bridge.postMessage({action:'hide',token:form.token});global.closeModal();
      if(payload.action==='cancel'){renderSplitPayment();return;}
      if(!unchanged||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||(global.MPosCore.CartOperations?.hasPending()||global.MPosCore.SplitPayments?.hasPending())){
        flash('Заказ изменился. Повторите подтверждение оплаты.');return;
      }
      return form.onSuccess();
    }
  });
})(window);
