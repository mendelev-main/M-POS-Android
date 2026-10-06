/* A quote applies only to exactly matching inputs; unrelated screens retain reviewed helpers. */
(function(global){
  'use strict';
  if(!global.MPosCore?.CartTotals)return;
  const enabled=()=>global.MPosNativeCartTotalsEnabled!==false;
  const input=()=>({version:1,items:state.cart.map(i=>({productId:i.productId,price:i.price,qty:i.qty,discountId:i.discountId})),discounts:state.discounts||[],orderType:state.orderType,deliveryFee:state.deliveryFee,programs:state.loyaltyPrograms||[],redemptions:state.loyaltyRedemptions||{}});
  const stamp=()=>JSON.stringify(input(),(_key,value)=>{if(typeof value==='number'&&!Number.isFinite(value))throw new Error('non-finite cart amount');return value;});
  let cached=null;
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
    async prepare(){
      const serialized=stamp(),request=JSON.parse(serialized);
      const value=await global.MPosCore.CartTotals.calculate(request);
      if(!valid(value,request.items.length))throw new Error('invalid native cart totals');
      if(!enabled()||serialized!==stamp())return false;
      cached={stamp:serialized,value};return true;
    }
  });
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
