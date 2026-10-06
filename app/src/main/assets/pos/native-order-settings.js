/* Local settings use a single native command per save gesture. */
(function(global){
  'use strict';
  if(!global.MPosCore?.OrderContextRead||!global.MPosCore?.OrderContext)return;
  const original=global.saveOrderSettings,queue=global.MPosCore.OrderContext;
  if(typeof original!=='function')return;
  const enabled=()=>global.MPosNativeOrderSettingsEnabled!==false;
  const ids={label:'order-label',name:'customer-name',phone:'customer-phone',address:'customer-address'};
  global.saveOrderSettings=function(){
    if(!enabled())return original.apply(this,arguments);
    const nodes={},fields={};for(const key of Object.keys(ids)){nodes[key]=document.getElementById(ids[key]);fields[key]=nodes[key]?.value||'';}
    const generation=queue.generation(),cart=state.cart,customer=state.customer;
    const formChanged=()=>Object.keys(ids).some(key=>document.getElementById(ids[key])!==nodes[key]||(nodes[key]?.value||'')!==fields[key]);
    return queue.enqueue(async()=>{
      const before=queue.stamp();
      const stale=()=>!enabled()||generation!==queue.generation()||cart!==state.cart||customer!==state.customer||before!==queue.stamp()||formChanged()||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending()||global.MPosCore.SplitPayments?.hasPending();
      if(stale())return;
      try{
        const result=await global.MPosCore.OrderContextRead.calculate({version:1,operation:'save',fields:{...fields}});
        if(stale())return;
        for(const key of ['orderLabel','name','phone','address'])if(typeof result?.[key]!=='string')throw Error('invalid order settings');
        queue.commit(()=>{state.orderLabel=result.orderLabel;customer.name=result.name;customer.phone=result.phone;customer.address=result.address;});
        saveCurrentOrderSession();closeModal();render();
      }catch(_error){if(!stale())flash('Не удалось сохранить данные заказа. Повторите действие.');}
    });
  };
})(window);
