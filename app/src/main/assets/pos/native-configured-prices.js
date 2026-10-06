/* Kotlin forms unit prices; the adapter retains reviewed cart/session orchestration. */
(function(global){
  'use strict';
  const prices=global.MPosCore?.ConfiguredPrices;
  if(!prices||typeof global.addConfiguredCartItem!=='function')return;
  const originalAdd=global.addConfiguredCartItem,originalManual=global.confirmManualPrice;
  let tail=Promise.resolve(),pending=0;
  const enabled=()=>global.MPosNativeConfiguredPricesEnabled!==false;
  const busy=()=>!!state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy);
  const overlay=()=>document.querySelector('#modal-root .modal-overlay');
  function enqueue(product,mods,input,manualContext=null){
    if(busy()){flash('Дождитесь завершения текущей операции');return Promise.resolve();}
    const cart=state.cart,modal=overlay(),snapshot=JSON.parse(JSON.stringify(product)),modifiers=JSON.parse(JSON.stringify(mods||[]));
    const stale=()=>!enabled()||busy()||state.cart!==cart||(modal&&overlay()!==modal)||(manualContext&&global._manualPriceContext!==manualContext);
    let checkingStock=false;
    pending++;
    const work=tail.then(async()=>{
      if(stale())return;
      const result=await prices.calculate({version:1,catalogPrice:snapshot.price,modifiers,...input});
      if(stale())return;
      const current=getProduct(snapshot.id);
      if(!current||JSON.stringify([current.price,current.name])!==JSON.stringify([snapshot.price,snapshot.name])){flash('Товар изменился. Повторите добавление.');return;}
      if(![result.basePrice,result.price].every(v=>typeof v==='number'&&Number.isFinite(v))||typeof result.manualPrice!=='boolean')throw new Error('invalid configured price result');
      const manual=result.manualPrice,sig=modifierSelectionSignature(modifiers);
      const existing=manual?null:state.cart.find(i=>i.productId===snapshot.id&&modifierSelectionSignature(i.selectedModifiers)===sig&&!i.comment&&!i.discountId);
      const proposed=state.cart.map(i=>({...i,qty:i===existing?i.qty+1:i.qty}));
      if(!existing)proposed.push({productId:snapshot.id,qty:1,selectedModifiers:modifiers});
      if(global.MPosNativeStockPreflightEnabled!==false){
        checkingStock=true;
        const cartBefore=JSON.stringify(state.cart);
        const stockState=()=>JSON.stringify((state.products||[]).map(p=>[p.id,p.name,p.type,p.stock,p.noStockTracking,p.components]));
        const catalogueBefore=stockState();
        const verdict=await global.MPosCore.StockPreflight.check({version:1,items:proposed.map(i=>({productId:i.productId,qty:i.qty,selectedModifiers:(i.selectedModifiers||[]).map(m=>({productId:m.productId,qty:m.qty}))}))});
        if(stale())return;
        if(cartBefore!==JSON.stringify(state.cart)||catalogueBefore!==stockState()||JSON.stringify([getProduct(snapshot.id)?.price,getProduct(snapshot.id)?.name])!==JSON.stringify([snapshot.price,snapshot.name])){flash('Корзина или товары изменились. Повторите добавление.');return;}
        if(typeof verdict.allowed!=='boolean')throw new Error('invalid stock preflight result');
        if(manualContext)global._manualPriceContext=null;
        if(!verdict.allowed){flash(verdict.reason||'Недостаточно остатка');return;}
      }else{
        if(manualContext)global._manualPriceContext=null;
        if(!canFulfillCart(proposed))return;
      }
      let animatedId,animationClass;
      if(existing){existing.qty++;animatedId=cartItemKey(existing);animationClass='cart-item-updated';}
      else{const item={cartLineId:uid(),productId:snapshot.id,name:snapshot.name,price:result.price,basePrice:result.basePrice,manualPrice:manual,qty:1,selectedModifiers:modifiers};state.cart.push(item);animatedId=cartItemKey(item);animationClass='cart-item-added';}
      global.__cartAnimation={id:animatedId,className:animationClass};
      saveCurrentOrderSession();closeModal();render();
    }).catch(()=>{
      if(!stale()){flash(checkingStock?'Не удалось проверить остатки. Повторите добавление.':manualContext?'Не удалось рассчитать ручную цену. Повторите добавление.':'Не удалось рассчитать цену. Повторите добавление.');}
    }).finally(()=>{pending--;});
    tail=work;return work;
  }
  global.addConfiguredCartItem=function(product,mods,manualBasePrice=null){
    if(!enabled())return originalAdd.apply(this,arguments);
    return enqueue(product,mods,{manualBasePrice});
  };
  global.confirmManualPrice=function(){
    if(!enabled())return originalManual.apply(this,arguments);
    const context=global._manualPriceContext;if(!context)return;
    const input=document.getElementById('manual-sale-price'),raw=String(input?.value||''),value=Number(raw.replace(',','.'));
    // Immediate form feedback; authoritative rounding and final price are Kotlin-owned.
    if(!Number.isFinite(value)||value<=0){flash('Укажите цену больше 0');input?.focus();return;}
    const product=getProduct(context.productId);if(!product){flash('Товар не найден');return;}
    return enqueue(product,context.mods||[],{manualInput:raw},context);
  };
  for(const name of ['openPaymentModal','confirmPaymentScreen','finalizePayment']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){if(pending){flash('Дождитесь расчёта добавляемого товара');return;}return original.apply(this,args);};
  }
})(window);
