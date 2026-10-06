/* Pure native normalization and restart validation; storage documents and paid progress stay unchanged. */
(function(global){
  'use strict';
  if(!global.MPosCore?.SplitRecoveryRead||!global.MPosCore?.SplitPayments?.enqueue)return;
  const queue=global.MPosCore.SplitPayments,originalNormalize=global.normalizeSplitPayments,originalOpen=global.openSplitPayment,originalValidate=global.validateSplitPaymentDraft;
  const enabled=()=>global.MPosNativeSplitRecoveryEnabled!==false;
  const canonical=value=>JSON.stringify(value,(_key,v)=>v&&typeof v==='object'&&!Array.isArray(v)?Object.fromEntries(Object.keys(v).sort().map(k=>[k,v[k]])):v);
  const freeze=value=>JSON.parse(JSON.stringify(value,(_key,v)=>{if(typeof v==='number'&&!Number.isFinite(v))throw Error('non-finite split recovery');return v;}));
  const records=value=>Array.isArray(value)?value.filter(v=>v&&typeof v==='object'&&!Array.isArray(v)):[];
  let cached=null;
  global.MPosCore.SplitRecovery=Object.freeze({
    async prepare(session){
      cached=null;
      if(!enabled()||!session||!Array.isArray(session.items)||session.paymentDraft==null)return;
      try{
        const discounts=records(await global.MPosCore.Storage.get('discounts',[]));
        const draft=freeze(session.paymentDraft),stamp=JSON.stringify(draft);
        const quote={version:1,items:records(session.items).map(i=>({productId:i.productId,price:i.price,qty:i.qty,discountId:i.discountId})),discounts,orderType:session.orderType||'На месте',deliveryFee:Number(session.deliveryFee||0),programs:Array.isArray(session.loyaltyPrograms)?session.loyaltyPrograms:[],redemptions:session.loyaltyRedemptions&&typeof session.loyaltyRedemptions==='object'&&!Array.isArray(session.loyaltyRedemptions)?session.loyaltyRedemptions:{}};
        const result=await global.MPosCore.SplitRecoveryRead.calculate(freeze({version:1,operation:'restore',draft,quote,now:Date.now()}));
        if(!enabled())return;
        if(typeof result?.valid!=='boolean'||!Number.isFinite(result.expectedTotal))throw Error('invalid recovery result');
        if(result.valid){
          const d=result.draft;
          if(d?.version!==1||!Number.isInteger(d.totalCents)||d.totalCents<0||d.totalCents!==Math.round(result.expectedTotal*100)||!Array.isArray(d.parts)||d.parts.length<2||d.parts.length>10||!d.parts.some(p=>p?.paid===true)||!Number.isFinite(d.updatedAt))throw Error('invalid native draft');
          if(d.parts.some(p=>!p||!['cash','card'].includes(p.method)||typeof p.paid!=='boolean'||typeof p.amount!=='number'||!Number.isFinite(p.amount)||p.amount<0||['cashGiven','change'].some(k=>p[k]!==null&&(typeof p[k]!=='number'||!Number.isFinite(p[k])||p[k]<0))))throw Error('invalid native draft part');
          if(d.parts.reduce((n,p)=>n+Math.round(p.amount*100),0)!==d.totalCents)throw Error('invalid native draft sum');
        }
        cached={stamp,result};
      }catch(_error){
        // A read/bridge failure must not discard recorded paid parts during startup.
        // Keep the reviewed synchronous validator as the compatibility path.
        cached=null;
      }
    }
  });
  if(typeof originalValidate==='function')global.validateSplitPaymentDraft=function(draft,total){
    if(!enabled()||!cached||JSON.stringify(draft)!==cached.stamp)return originalValidate.apply(this,arguments);
    const result=cached.result;
    if(Math.round(Number(total)*100)!==Math.round(result.expectedTotal*100))return originalValidate.apply(this,arguments);
    return result.valid?freeze(result.draft):null;
  };
  global.normalizeSplitPayments=function(total){
    if(!enabled()||!Array.isArray(state._splitPayments)||state._splitPayments.length<2||state._splitPayments.length>10)return originalNormalize.apply(this,arguments);
    global.MPosCore.SplitAmount?.seal();
    const generation=queue.generation(),cart=state.cart;
    return queue.enqueue(async()=>{
      const parts=state._splitPayments,before=JSON.stringify(parts),context=queue.context();
      const stale=()=>!enabled()||generation!==queue.generation()||cart!==state.cart||parts!==state._splitPayments||before!==JSON.stringify(parts)||context!==queue.context()||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.CartOperations?.hasPending();
      try{
        const result=await global.MPosCore.SplitRecoveryRead.calculate({version:1,operation:'normalize',total,parts:freeze(parts)});
        if(stale())return false;
        if(result?.changed!==true||result.count!==parts.length||!Array.isArray(result.parts)||result.parts.length!==parts.length)throw Error('invalid normalize result');
        result.parts.forEach((p,i)=>{const old=parts[i],expected={...old};if(!old.paid){if(typeof p.amount!=='number'||!Number.isFinite(p.amount)||p.amount<0)throw Error('invalid amount');expected.amount=p.amount;}if(canonical(expected)!==canonical(p))throw Error('changed paid part or metadata');});
        result.parts.forEach((p,i)=>{if(!parts[i].paid)parts[i].amount=p.amount;});
        state._splitCount=parts.length;return true;
      }catch(_error){if(!stale())flash('Не удалось проверить части оплаты. Повторите действие.');return false;}
    });
  };
  global.openSplitPayment=async function(validated=false){
    if(!enabled()||hasPaidSplitPayment()||(hasSelectedLoyaltyReward()&&!validated)||!Array.isArray(state._splitPayments)||!state._splitPayments.length||state._splitPayments.length<2||state._splitPayments.length>10)return originalOpen.apply(this,arguments);
    if(validated)renderPaymentScreen();
    if(await global.normalizeSplitPayments(cartTotal())!==true)return false;
    renderSplitPayment();return true;
  };
})(window);
