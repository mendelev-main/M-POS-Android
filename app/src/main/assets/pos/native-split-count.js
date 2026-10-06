/* Native count redistribution. FIFO and snapshot guards keep late replies from touching a newer payment. */
(function(global){
  'use strict';
  if(!global.MPosCore?.SplitCount||typeof global.adjustSplitCount!=='function')return;
  const original=global.adjustSplitCount;
  const enabled=()=>global.MPosNativeSplitCountEnabled!==false;
  let tail=Promise.resolve(),pending=0,epoch=0;
  const busy=()=>state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending();
  const canonical=value=>JSON.stringify(value,(_key,v)=>v&&typeof v==='object'&&!Array.isArray(v)?Object.fromEntries(Object.keys(v).sort().map(k=>[k,v[k]])):v);
  const stamp=()=>JSON.stringify([state.cart,state._splitPayments,state._splitPaymentTotalCents,state.paymentPage,state.discounts,state.customer,state.loyaltyPrograms,state.loyaltyRedemptions,state.orderType,state.deliveryFee,typeof currentShift==='function'?currentShift()?.id:null]);
  const context=()=>JSON.stringify([state.cart,state._splitPaymentTotalCents,state.paymentPage,state.discounts,state.customer,state.loyaltyPrograms,state.loyaltyRedemptions,state.orderType,state.deliveryFee,typeof currentShift==='function'?currentShift()?.id:null]);
  const enqueue=task=>{
    if(busy()||pending>=32){flash('Дождитесь завершения текущей операции');return;}
    const generation=epoch,cart=state.cart,before=context();
    pending++;
    const work=tail.then(()=>{if(generation===epoch&&cart===state.cart&&before===context()&&!busy())return task();}).finally(()=>pending--);
    tail=work.catch(()=>{});return work;
  };
  global.MPosCore.SplitPayments=Object.freeze({hasPending:()=>pending>0,enqueue,context,generation:()=>epoch});
  for(const name of ['closePaymentPage','closeModal','returnFromSplitPayment']){
    const fn=global[name];if(typeof fn!=='function')continue;
    global[name]=function(...args){epoch++;return fn.apply(this,args);};
  }
  global.adjustSplitCount=function(delta){
    if(!enabled()||!Array.isArray(state._splitPayments)||state._splitPayments.length<2||state._splitPayments.length>10)return original.apply(this,arguments);
    if(delta!==1&&delta!==-1)return original.apply(this,arguments);
    if(busy()||pending>=32){flash('Дождитесь завершения текущей операции');return;}
    const generation=epoch,cart=state.cart;
    global.MPosCore.SplitAmount?.seal();
    const task=async()=>{
      if(!enabled()||generation!==epoch||cart!==state.cart||busy())return;
      const parts=state._splitPayments,before=stamp();
      const stale=()=>!enabled()||generation!==epoch||cart!==state.cart||parts!==state._splitPayments||before!==stamp()||busy();
      try{
        const result=await global.MPosCore.SplitCount.calculate({version:1,total:cartTotal(),parts:JSON.parse(JSON.stringify(parts,(_key,v)=>{if(typeof v==='number'&&!Number.isFinite(v))throw Error('non-finite split amount');return v;})),delta});
        if(stale())return;
        if(typeof result?.allowed!=='boolean'||typeof result.changed!=='boolean')throw Error('invalid split decision');
        if(!result.allowed){flash(result.reason||'Не удалось изменить части оплаты');return;}
        if(!result.changed)return;
        const next=Math.max(2,Math.min(10,parts.length+delta));
        if(result.count!==next||!Array.isArray(result.parts)||result.parts.length!==next||!Array.isArray(result.indices)||result.indices.length!==next)throw Error('invalid split count');
        const seen=new Set();
        const rows=result.parts.map((p,i)=>{
          const index=result.indices[i];
          if(!Number.isInteger(index)||index< -1||index>=parts.length||!Number.isFinite(Number(p.amount)))throw Error('invalid split row');
          if(index===-1){if(p.method!=='cash'||p.paid!==false||p.cashGiven!==null||p.change!==null)throw Error('invalid new split');return p;}
          if(seen.has(index))throw Error('duplicate split row');seen.add(index);
          const old=parts[index],expected={...old};if(!old.paid)expected.amount=p.amount;
          if(canonical(expected)!==canonical(p))throw Error('changed paid split or metadata');
          return old;
        });
        if(parts.some((p,i)=>p.paid&&!seen.has(i)))throw Error('removed paid split');
        rows.forEach((p,i)=>{if(!p.paid)p.amount=result.parts[i].amount;});
        state._splitPayments=rows;state._splitCount=next;renderSplitPayment();
      }catch(_error){if(!stale())flash('Не удалось изменить части оплаты. Повторите действие.');}
    };
    return enqueue(task);
  };
})(window);
