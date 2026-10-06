/* Count and amount edits share one queue. Only not-yet-started consecutive edits are coalesced. */
(function(global){
  'use strict';
  if(!global.MPosCore?.SplitPayments?.enqueue||!global.MPosCore?.SplitAmountRead||typeof global.updateSplitAmountLive!=='function')return;
  const original=global.updateSplitAmountLive,queue=global.MPosCore.SplitPayments;
  const enabled=()=>global.MPosNativeSplitAmountEnabled!==false;
  const canonical=value=>JSON.stringify(value,(_key,v)=>v&&typeof v==='object'&&!Array.isArray(v)?Object.fromEntries(Object.keys(v).sort().map(k=>[k,v[k]])):v);
  const stamp=()=>JSON.stringify([state.cart,state._splitPayments,state._splitPaymentTotalCents,state.paymentPage,state.discounts,state.customer,state.loyaltyPrograms,state.loyaltyRedemptions,state.orderType,state.deliveryFee,typeof currentShift==='function'?currentShift()?.id:null]);
  let queued=null;
  global.MPosCore.SplitAmount=Object.freeze({seal(){queued=null;}});
  global.updateSplitAmountLive=function(index,value){
    if(!enabled()||!Number.isInteger(index)||!Array.isArray(state._splitPayments)||state._splitPayments.length<2||state._splitPayments.length>10)return original.apply(this,arguments);
    if(!state._splitPayments[index]||state._splitPayments[index].paid)return;
    // Presentation gate only; Kotlin owns authoritative parsing and arithmetic.
    if(parsePaymentNumber(value)===null)return;
    const raw=String(value??''),generation=queue.generation(),context=queue.context(),cart=state.cart,target=state._splitPayments[index];
    if(queued&&queued.index===index&&queued.target===target&&queued.generation===generation&&queued.context===context&&queued.cart===cart){queued.raw=raw;return queued.promise;}
    const job={index,raw,generation,context,cart,target};queued=job;
    job.promise=queue.enqueue(async()=>{
      if(queued===job)queued=null;
      if(!enabled()||queue.generation()!==generation||cart!==state.cart||state._splitPayments[index]!==target)return;
      const parts=state._splitPayments,before=stamp();
      const stale=()=>!enabled()||queue.generation()!==generation||cart!==state.cart||parts!==state._splitPayments||before!==stamp()||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending();
      try{
        const request={version:1,total:cartTotal(),index,raw:job.raw,parts:JSON.parse(JSON.stringify(parts,(_key,v)=>{if(typeof v==='number'&&!Number.isFinite(v))throw Error('non-finite split amount');return v;}))};
        const result=await global.MPosCore.SplitAmountRead.calculate(request);
        if(stale())return;
        if(typeof result?.changed!=='boolean')throw Error('invalid amount decision');
        if(!result.changed)return;
        if(result.index!==index||!Array.isArray(result.parts)||result.parts.length!==parts.length)throw Error('invalid amount rows');
        result.parts.forEach((p,i)=>{
          const old=parts[i],expected={...old};
          if(!old.paid){if(typeof p.amount!=='number'||!Number.isFinite(p.amount)||p.amount<0)throw Error('invalid amount');expected.amount=p.amount;if(i===index){expected.cashGiven=null;expected.change=null;}}
          if(canonical(expected)!==canonical(p))throw Error('changed paid row or metadata');
        });
        result.parts.forEach((p,i)=>{if(!parts[i].paid)parts[i].amount=p.amount;});
        target.cashGiven=null;target.change=null;syncSplitPaymentUI();
      }catch(_error){if(!stale())flash('Не удалось изменить сумму части оплаты. Повторите ввод.');}
    });
    if(!job.promise&&queued===job)queued=null;
    if(job.promise)job.promise=job.promise.finally(()=>{if(queued===job)queued=null;});
    return job.promise;
  };
})(window);
