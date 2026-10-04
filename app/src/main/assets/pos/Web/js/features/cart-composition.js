let cartSwipeState=null;
function modifierSelectionSignature(mods){return JSON.stringify((mods||[]).map(m=>[m.groupId,m.productId,Number(m.qty)||0,Number(m.priceDelta)||0]).sort());}
function cartItemKey(item){return item.cartLineId||item.productId;}
function productNeedsManualPrice(p){return !!p && (!Number.isFinite(Number(p.price)) || Number(p.price)<=0);}
function addToCart(id){const p=getProduct(id);if(!p)return;if(Array.isArray(p.modifierGroups)&&p.modifierGroups.length){openModifierSelection(id);return;}if(productNeedsManualPrice(p)){openManualPriceModal(p,[]);return;}addConfiguredCartItem(p,[]);}
function openManualPriceModal(p,mods=[]){if(!p)return;window._manualPriceContext={productId:p.id,mods:JSON.parse(JSON.stringify(mods||[]))};const extra=(mods||[]).reduce((x,m)=>x+Number(m.priceDelta||0),0);showModal(`<div class="modal-title">${escapeHtml(p.name)}</div><div class="settings-note">У товара не указана цена продажи. Введите цену для этой позиции заказа.</div><div class="field"><label>Цена продажи, BYN</label><input id="manual-sale-price" type="number" inputmode="decimal" min="0.01" step="0.01" placeholder="0,00" autofocus></div>${extra?`<div class="settings-note">Модификаторы: ${extra>0?'+':''}${money(extra)}</div>`:''}<div class="modal-actions"><button class="btn btn-secondary" onclick="window._manualPriceContext=null;closeModal()">Отмена</button><button class="btn btn-primary" onclick="confirmManualPrice()">Добавить в заказ</button></div>`,true);setTimeout(()=>document.getElementById('manual-sale-price')?.focus(),50);}
function confirmManualPrice(){const context=window._manualPriceContext;if(!context)return;const input=document.getElementById('manual-sale-price'),raw=String(input?.value||'').replace(',','.'),price=Number(raw);if(!Number.isFinite(price)||price<=0){flash('Укажите цену больше 0');input?.focus();return;}const p=getProduct(context.productId);if(!p){flash('Товар не найден');return;}const mods=context.mods||[];window._manualPriceContext=null;addConfiguredCartItem(p,mods,Math.round(price*100)/100);}
function addConfiguredCartItem(p,mods,manualBasePrice=null){const extra=(mods||[]).reduce((x,m)=>x+Number(m.priceDelta||0),0),basePrice=manualBasePrice===null?(Number(p.price)||0):Number(manualBasePrice),sig=modifierSelectionSignature(mods),manual=manualBasePrice!==null;const existing=manual?null:state.cart.find(i=>i.productId===p.id&&modifierSelectionSignature(i.selectedModifiers)===sig&&!i.comment&&!i.discountId);const proposed=state.cart.map(i=>({...i,qty:i===existing?i.qty+1:i.qty}));if(!existing)proposed.push({productId:p.id,qty:1,selectedModifiers:mods});if(!canFulfillCart(proposed))return;let animatedId,animationClass;if(existing){existing.qty++;animatedId=cartItemKey(existing);animationClass='cart-item-updated';}else{const item={cartLineId:uid(),productId:p.id,name:p.name,price:basePrice+extra,basePrice,manualPrice:manual,qty:1,selectedModifiers:JSON.parse(JSON.stringify(mods||[]))};state.cart.push(item);animatedId=cartItemKey(item);animationClass='cart-item-added';}window.__cartAnimation={id:animatedId,className:animationClass};saveCurrentOrderSession();closeModal();render();}
function openModifierSelection(productId){const p=getProduct(productId);if(!p)return;const groups=(p.modifierGroups||[]).map(normalizeModifierGroup),html=groups.map((g,gi)=>'<div class="field"><label>'+escapeHtml(g.name)+'</label><div class="modifier-choice-grid">'+g.options.map(o=>{const mp=getProduct(o.productId),delta=Number(o.priceDelta)||0,displayName=String(o.posName||'').trim()||mp?.name||'Товар недоступен',priceText=delta?'<span class="modifier-choice-price">'+(delta>0?'+':'')+money(delta)+'</span>':'';return '<button type="button" class="modifier-choice" data-modifier-group="'+gi+'" data-modifier-option="'+escapeAttr(o.id)+'" data-modifier-max="'+g.max+'" aria-pressed="false" onclick="toggleModifierChoice(this,'+productInlineArg(productId)+')"><span class="modifier-choice-name">'+escapeHtml(displayName)+'</span>'+priceText+'</button>';}).join('')+'</div></div>').join('');showModal('<div class="modal-title">'+escapeHtml(p.name)+'</div><div class="settings-note">Выберите модификаторы товара.</div>'+html+'<div class="modifier-selection-total">Итого: <span id="modifier-selection-total">'+fullMoney(p.price)+'</span></div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="confirmModifierSelection('+productInlineArg(productId)+')">Добавить в заказ</button></div>',true);updateModifierSelectionTotal(productId);}
function toggleModifierChoice(el,productId){const gi=el.dataset.modifierGroup,max=Math.max(1,Number(el.dataset.modifierMax)||1),selected=el.classList.contains('selected');if(max===1){document.querySelectorAll('.modifier-choice[data-modifier-group="'+gi+'"]').forEach(x=>{x.classList.remove('selected');x.setAttribute('aria-pressed','false');});if(!selected){el.classList.add('selected');el.setAttribute('aria-pressed','true');}}else{if(!selected){const count=document.querySelectorAll('.modifier-choice[data-modifier-group="'+gi+'"].selected').length;if(count>=max){flash('Можно выбрать не больше '+max);return;}}el.classList.toggle('selected');el.setAttribute('aria-pressed',el.classList.contains('selected')?'true':'false');}updateModifierSelectionTotal(productId);}
function readModifierSelection(productId){const p=getProduct(productId),groups=(p?.modifierGroups||[]).map(normalizeModifierGroup),selected=[];for(let gi=0;gi<groups.length;gi++){const g=groups[gi],checked=[...document.querySelectorAll('.modifier-choice[data-modifier-group="'+gi+'"].selected')];if(checked.length<g.min||checked.length>g.max)throw new Error('«'+g.name+'»: выберите от '+g.min+' до '+g.max);for(const input of checked){const o=g.options.find(x=>x.id===input.dataset.modifierOption);if(!o)continue;const mp=getProduct(o.productId);if(!mp)throw new Error('Модификатор больше недоступен: '+g.name);selected.push({groupId:g.id,groupName:g.name,optionId:o.id,productId:o.productId,name:String(o.posName||'').trim()||mp.name,qty:Number(o.qty)||1,priceDelta:Number(o.priceDelta)||0});}}return selected;}
function updateModifierSelectionTotal(productId){try{const p=getProduct(productId),mods=readModifierSelection(productId),total=(Number(p?.price)||0)+mods.reduce((x,m)=>x+Number(m.priceDelta||0),0),el=document.getElementById('modifier-selection-total');if(el)el.textContent=fullMoney(total);}catch(e){const p=getProduct(productId),selected=[...document.querySelectorAll('.modifier-choice.selected')],groups=(p?.modifierGroups||[]).map(normalizeModifierGroup),delta=selected.reduce((sum,input)=>{const g=groups[Number(input.dataset.modifierGroup)],o=g?.options.find(x=>x.id===input.dataset.modifierOption);return sum+(Number(o?.priceDelta)||0);},0),el=document.getElementById('modifier-selection-total');if(el)el.textContent=fullMoney((Number(p?.price)||0)+delta);}}
function confirmModifierSelection(productId){try{const p=getProduct(productId);if(!p)return;const mods=readModifierSelection(productId);if(productNeedsManualPrice(p)){openManualPriceModal(p,mods);return;}addConfiguredCartItem(p,mods);}catch(e){flash(e.message);}}
function changeQty(id,delta){const item=state.cart.find(i=>cartItemKey(i)===id);if(!item)return;const q=item.qty+delta;if(q<=0){state.cart=state.cart.filter(i=>cartItemKey(i)!==id);saveCurrentOrderSession();render();return;}if(!canFulfillCart(state.cart.map(i=>({...i,qty:cartItemKey(i)===id?q:i.qty}))))return;item.qty=q;saveCurrentOrderSession();render();}
function isFormControlTarget(target){return !!target?.closest?.('input,textarea,select,[contenteditable="true"]');}
function handleCartRowClick(e,id){
  if(e.target.closest('button,.qty-stepper')) return;
  if(cartSwipeState && cartSwipeState.suppressClick){
    e.preventDefault();
    cartSwipeState=null;
    return;
  }
  openCartItemModal(id);
}
function cartRowTouchStart(e,row){
  if(!e.touches || !e.touches[0]) return;
  const t=e.touches[0];
  cartSwipeState={row,startX:t.clientX,startY:t.clientY,currentX:t.clientX,dragging:false,suppressClick:false};
  row.classList.add('swiping');
}
function cartRowTouchMove(e,row){
  if(!cartSwipeState || cartSwipeState.row!==row || !e.touches || !e.touches[0]) return;
  const t=e.touches[0];
  const dx=t.clientX-cartSwipeState.startX;
  const dy=t.clientY-cartSwipeState.startY;
  cartSwipeState.currentX=t.clientX;
  if(!cartSwipeState.dragging){
    if(Math.abs(dy)>Math.abs(dx) || Math.abs(dx)<8) return;
    if(dx>=0) return;
    cartSwipeState.dragging=true;
  }
  if(cartSwipeState.dragging){
    e.preventDefault();
    const reveal=Math.max(-150,Math.min(0,dx));
    row.querySelector('.cart-row-content').style.transform=`translateX(${reveal}px)`;
    row.classList.toggle('swipe-ready', reveal <= -82);
  }
}
function cartRowTouchEnd(e,row){
  if(!cartSwipeState || cartSwipeState.row!==row) return;
  const dx=cartSwipeState.currentX-cartSwipeState.startX;
  const swiped=cartSwipeState.dragging && dx < -82;
  row.classList.remove('swiping');
  if(swiped){
    e.preventDefault();
    cartSwipeState.suppressClick=true;
    row.classList.add('swipe-delete');
    row.querySelector('.cart-row-content').style.transform='';
    const id=row.dataset.cartId;
    setTimeout(()=>removeFromCart(id),230);
    return;
  }
  row.classList.remove('swipe-ready');
  row.querySelector('.cart-row-content').style.transform='';
  cartSwipeState=null;
}
function cartRowTouchCancel(e,row){
  if(!cartSwipeState || cartSwipeState.row!==row) return;
  row.classList.remove('swiping','swipe-ready');
  row.querySelector('.cart-row-content').style.transform='';
  cartSwipeState=null;
}
function resetCurrentOrderState(){
  state.cart=[];
  state.orderLabel='';
  state.orderType='На месте';
  state.deliveryFee=0;state.deliveryTariffSelected=false;
  state.customer={name:'',phone:'',address:'',id:''};
  state.loyaltyPrograms=[];
  state.loyaltyRedemptions={};
  state.loyaltyCustomerId='';
  state.loyaltyLoadingCustomerId='';
  state.loyaltyLoadError='';
  state.orderComment='';
  state.currentOrderSource='';
  state.currentWebOrderId='';
  state.currentWebOrderStatus='';
  window.__currentOrderKitchenPrinted=false;
  window.__currentOrderPrintedItems=[];
  state._splitPayments=[];
  state._splitCount=0;
  state._splitPaymentTotalCents=null;
}
function removeFromCart(id){
  state.cart = state.cart.filter(i=>cartItemKey(i)!==id);
  if(!state.cart.length){
    resetCurrentOrderState();
    clearCurrentOrderSession();
  }else{
    saveCurrentOrderSession();
  }
  render();
}
