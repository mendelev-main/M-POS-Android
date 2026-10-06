/* Native delivery decisions; UI/state/session effects retain their reviewed order. */
(function(global){
  'use strict';
  if(!global.MPosCore?.DeliveryRead||!global.MPosCore?.OrderContext)return;
  const queue=global.MPosCore.OrderContext,enabled=()=>global.MPosNativeDeliveryEnabled!==false;
  const input=()=>({orderType:state.orderType,fee:state.deliveryFee,selected:state.deliveryTariffSelected,rates:(state.deliveryRates||[]).map(r=>({amount:r.amount}))});
  global.MPosCore.NativeDelivery=Object.freeze({enabled,input});
  const originalHas=global.hasDeliveryTariff;
  global.hasDeliveryTariff=function(){const verdict=enabled()?global.MPosCore.PaymentTotals?.delivery():null;return verdict===null||verdict===undefined?originalHas.apply(this,arguments):verdict;};
  for(const [name,operation] of [['selectDeliveryFee','select'],['setOrderType','type']]){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(value){
      if(!enabled()||(operation==='type'&&typeof value!=='string')||state.deliveryFee===undefined)return original.apply(this,arguments);
      const generation=queue.generation(),cart=state.cart;
      return queue.enqueue(async()=>{
        if(!enabled())return;
        const before=queue.stamp();
        const stale=()=>!enabled()||generation!==queue.generation()||cart!==state.cart||before!==queue.stamp()||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending()||global.MPosCore.SplitPayments?.hasPending();
        try{
          const request={version:1,operation,state:input(),...(operation==='type'?{orderType:value}:{amount:value})};
          const result=await global.MPosCore.DeliveryRead.calculate(JSON.parse(JSON.stringify(request,(_key,v)=>{if(typeof v==='number'&&!Number.isFinite(v))throw Error('non-finite delivery');return v;})));
          if(stale())return;
          if(typeof result?.changed!=='boolean')throw Error('invalid delivery result');
          if(!result.changed)return;
          if(operation==='select'&&(result.selected!==true||typeof result.fee!=='number'||!Number.isFinite(result.fee)))throw Error('invalid selected fee');
          if(operation==='type'&&(result.orderType!==value||!Object.prototype.hasOwnProperty.call(result,'fee')||!Object.prototype.hasOwnProperty.call(result,'selected')))throw Error('invalid order type');
          queue.commit(()=>{if(operation==='type')state.orderType=result.orderType;state.deliveryFee=result.fee;state.deliveryTariffSelected=result.selected;});
          saveCurrentOrderSession();queue.present(()=>{render();openOrderSettings();});
        }catch(_error){if(!stale())flash('Не удалось изменить доставку. Повторите действие.');}
      });
    };
  }
})(window);
