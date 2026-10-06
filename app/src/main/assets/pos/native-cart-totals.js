/* A quote applies only to exactly matching inputs; unrelated screens retain reviewed helpers. */
(function(global){
  'use strict';
  if(!global.MPosCore?.CartTotals)return;
  const enabled=()=>global.MPosNativeCartTotalsEnabled!==false;
  const input=()=>({version:1,items:state.cart.map(i=>({productId:i.productId,price:i.price,qty:i.qty,discountId:i.discountId})),discounts:state.discounts||[],orderType:state.orderType,deliveryFee:state.deliveryFee,programs:state.loyaltyPrograms||[],redemptions:state.loyaltyRedemptions||{},...(global.MPosCore.NativeDelivery?.enabled()?{deliveryState:{selected:state.deliveryTariffSelected,rates:(state.deliveryRates||[]).map(r=>({amount:r.amount}))}}:{})});
  const stamp=()=>JSON.stringify(input(),(_key,value)=>{if(typeof value==='number'&&!Number.isFinite(value))throw new Error('non-finite cart amount');return value;});
  let cached=null;
  const inflight=new Map();
  const quote=()=>{if(!enabled()||!cached)return null;try{return cached.stamp===stamp()?cached.value:null;}catch(_error){return null;}};
  const line=item=>{const result=quote(),index=state.cart.indexOf(item);return result&&index>=0?result.pricing.lines[index]:null;};
  function valid(result,count){
    return result?.pricing&&Array.isArray(result.pricing.lines)&&result.pricing.lines.length===count&&
      ['subtotal','total','productDiscountTotal','subtotalBeforeDiscounts'].every(k=>Number.isFinite(result.pricing[k]))&&
      result.pricing.lines.every(l=>Number.isFinite(l.discount)&&Number.isFinite(l.total))&&
      Number.isFinite(result.loyalty?.discount)&&result.loyalty.allocations&&result.loyalty.programDiscounts&&result.loyalty.snapshot;
  }
  global.MPosCore.PaymentTotals=Object.freeze({
    enabled,
    current:()=>!!quote(),
    delivery:()=>quote()?.delivery?.allowed??null,
    presentation(){const value=quote();return value?{lines:value.pricing.lines.map(line=>({...line})),total:value.pricing.total,loyaltyDiscount:value.loyalty.discount}:null;},
    split(count,total){
      if(global.MPosNativeSplitPlansEnabled===false)return null;
      const result=quote(),bounded=Math.max(2,Math.min(10,count));
      if(!result||!Number.isInteger(bounded)||total!==result.pricing.total)return null;
      const parts=result.splitPlans?.[bounded];
      if(!Array.isArray(parts)||parts.length!==bounded||parts.some(p=>p.method!=='cash'||p.paid!==false||p.cashGiven!==null||p.change!==null||!Number.isFinite(p.amount)||p.amount<0))return null;
      if(parts.reduce((n,p)=>n+Math.round(p.amount*100),0)!==Math.round(total*100))return null;
      return JSON.parse(JSON.stringify(parts));
    },
    cash(given){const result=quote();return result&&cached.given===given&&result.cash?JSON.parse(JSON.stringify(result.cash)):null;},
    async prepare(given){
      const serialized=stamp(),request=JSON.parse(serialized);
      if(given!==undefined){if(typeof given!=='number'||!Number.isFinite(given))throw new Error('invalid cash given');request.cashGiven=given;}
      const key=serialized+'|'+(given===undefined?'no-tender':String(given));
      if(!inflight.has(key))inflight.set(key,Promise.resolve(global.MPosCore.CartTotals.calculate(request)).finally(()=>inflight.delete(key)));
      const value=await inflight.get(key);
      if(!valid(value,request.items.length))throw new Error('invalid native cart totals');
      if(request.deliveryState&&typeof value.delivery?.allowed!=='boolean')throw new Error('invalid native delivery status');
      if(given!==undefined&&(typeof value.cash?.allowed!=='boolean'||(value.cash.allowed&&(!Number.isFinite(value.cash.cashGiven)||!Number.isFinite(value.cash.change)))))throw new Error('invalid native cash totals');
      if(!enabled()||serialized!==stamp())return false;
      // A late plain preview must not evict tender needed by a concurrent cash confirmation.
      if(given===undefined&&cached?.stamp===serialized&&cached.given!==undefined)return true;
      cached={stamp:serialized,value,given};return true;
    }
  });
  const build=global.buildSplitPayments;
  if(typeof build==='function')global.buildSplitPayments=function(count,total){
    return global.MPosCore.PaymentTotals.split(count,total)??build.apply(this,arguments);
  };
  const helpers={cartTotal:()=>quote()?.pricing.total,cartSubtotal:()=>quote()?.pricing.subtotal,
    discountValue:item=>line(item)?.discount,itemTotal:item=>line(item)?.total,
    loyaltyRewardDiscount:()=>quote()?.loyalty.discount,
    loyaltyRewardAllocation:()=>{const l=quote()?.loyalty;return l?JSON.parse(JSON.stringify({discount:l.discount,allocations:l.allocations,programDiscounts:l.programDiscounts})):undefined;},
    loyaltyReceiptSnapshot:allocation=>allocation===undefined&&quote()?JSON.parse(JSON.stringify(quote().loyalty.snapshot)):undefined};
  for(const [name,native] of Object.entries(helpers)){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){const value=native(...args);return value===undefined?original.apply(this,args):value;};
  }
})(window);
