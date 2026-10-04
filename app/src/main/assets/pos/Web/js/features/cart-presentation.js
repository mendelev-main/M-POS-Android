function renderCartPanel(shift){
  const rows = state.cart.length ? state.cart.map(i=>`
    <div class="cart-row" data-cart-id="${escapeAttr(cartItemKey(i))}" onclick="handleCartRowClick(event, '${escapeAttr(cartItemKey(i))}')" ontouchstart="cartRowTouchStart(event, this)" ontouchmove="cartRowTouchMove(event, this)" ontouchend="cartRowTouchEnd(event, this)" ontouchcancel="cartRowTouchCancel(event, this)">
      <div class="cart-row-swipe-bg"><div class="cart-swipe-delete-action"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16"/><path d="M9 7V4h6v3"/><path d="M6 7l1 13h10l1-13"/><path d="M10 11v5"/><path d="M14 11v5"/></svg><span>Удалить</span></div></div>
      <div class="cart-row-content">
      <div class="cart-row-top">
        <div class="cart-row-main">
          <div class="cart-row-title-line"><div class="cart-row-name">${escapeHtml(i.name)}</div><div class="cart-row-linetotal">${money(itemTotal(i))}</div></div>
          <div class="cart-row-sub">${money(i.price)} / шт · ×${i.qty}${i.discountId ? ' · '+escapeHtml((state.discounts.find(d=>d.id===i.discountId)||{}).name||'Скидка') : ''}</div>
          ${(i.selectedModifiers||[]).length?`<div class="cart-row-sub cart-row-detail">↳ ${(i.selectedModifiers||[]).map(m=>escapeHtml(m.name)).join(' · ')}</div>`:''}
          ${i.comment ? `<div class="cart-row-sub cart-row-detail"><strong>Комментарий:</strong> ${escapeHtml(i.comment)}</div>` : ''}
        </div>
      </div>
      </div>
    </div>
  `).join('') : `<div class="cart-empty">Заказ пуст.<br>Нажмите на товар слева, чтобы добавить его.</div>`;

  const subtotal = cartSubtotal();
  const deliveryFee = state.orderType==='Доставка' ? Number(state.deliveryFee||0) : 0;
  const total = Math.max(0,subtotal + deliveryFee - loyaltyRewardDiscount());
  const hasItems = state.cart.length>0;
  return `
  <div class="cart-panel">
    <div class="cart-head"><span class="cart-order-title">Текущий заказ <span class="cart-position-count">— ${state.cart.reduce((s,i)=>s+i.qty,0)} поз.</span></span><div class="cart-head-actions"><span id="cart-add-feedback" class="cart-add-feedback">Добавлено</span><button class="cart-customer-button ${state.customer?.id?'selected':''}" onclick="openOrderCustomer()" aria-label="${state.customer?.id?'Изменить клиента заказа':'Выбрать клиента'}" title="${escapeAttr(state.customer?.name||'Выбрать клиента')}"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></svg></button></div></div>
    <div class="order-meta">
      <button class="order-type-btn" onclick="openOrderSettings()">${state.orderLabel?'<b>'+escapeHtml(state.orderLabel)+'</b> · ':''}${escapeHtml(state.orderType)}${state.customer.name||state.customer.phone?' · '+escapeHtml(state.customer.name||state.customer.phone):''}</button>
      ${state.orderComment?`<div class="cart-row-sub cart-order-comment"><strong>Комментарий:</strong> ${escapeHtml(state.orderComment)}</div>`:''}
    </div>
    <div class="cart-items">${rows}</div>
    <div class="cart-foot">
      ${state.orderType==='Доставка' ? `<div class="total-row cart-total-meta"><span class="label">Доставка</span><span class="value">${hasDeliveryTariff()?fullMoney(deliveryFee):'Выберите тариф'}</span></div>` : ''}
      ${loyaltyRewardDiscount()>0?`<div class="total-row cart-total-meta"><span class="label">Программа лояльности</span><span class="value">−${fullMoney(loyaltyRewardDiscount())}</span></div>`:''}
      <div class="total-row"><span class="label">Итого</span><span class="value">${fullMoney(total)}</span></div>
      <div class="cart-actions">
        <button class="btn btn-secondary" ${hasItems?'':'disabled'} onclick="parkOrder()">Отложить</button>
        ${hasItems&&state.currentOrderSource==='web'&&state.currentWebOrderId&&state.currentWebOrderStatus!=='ready'?'<button class="btn btn-primary" onclick="markCurrentWebOrderReady()">Заказ готов</button>':''}
      </div>
      <div class="cart-actions cart-actions-primary">
        <button class="btn btn-primary" ${hasItems&&shift?'':'disabled'} onclick="openPaymentModal()">Оплатить</button>
      </div>
    </div>
  </div>`;
}

function discountValue(item){
  if(!item || !item.discountId) return 0;
  const d=state.discounts.find(x=>x.id===item.discountId);
  if(!d) return 0;
  const base=item.price*item.qty;
  return d.type==='percent' ? base*Math.max(0,Math.min(100,Number(d.value)||0))/100 : Math.min(base,Math.max(0,Number(d.value)||0)*item.qty);
}
function itemTotal(item){ return Math.max(0,item.price*item.qty-discountValue(item)); }
function cartSubtotal(){ return state.cart.reduce((sum,i)=>sum+itemTotal(i),0); }
function openCartItemModal(id){
  const item=state.cart.find(i=>cartItemKey(i)===id); if(!item) return;
  const discounts=state.discounts||[];
  showModal(`<div class="modal-title">${escapeHtml(item.name)}</div>
    <div class="field"><label>Количество</label>
      <div class="cart-item-qty-editor">
        <button type="button" onclick="changeCartItemModalQty(-1)">−</button>
        <span id="ci-qty">${item.qty}</span>
        <button type="button" onclick="changeCartItemModalQty(1)">+</button>
      </div>
    </div>
    <div class="field"><label>Комментарий к товару</label><textarea id="ci-comment" rows="3" placeholder="Например: без сахара, хорошо прожарить...">${escapeHtml(item.comment||'')}</textarea></div>
    <div class="field"><label>Скидка</label>
      <input type="hidden" id="ci-discount" value="${escapeAttr(item.discountId||'')}">
      <div class="discount-choice-grid">
        <button type="button" class="discount-choice ${!item.discountId?'selected':''}" onclick="selectCartDiscount('')">Без скидки</button>
        ${discounts.map(d=>`<button type="button" class="discount-choice ${item.discountId===d.id?'selected':''}" data-discount-id="${escapeAttr(d.id)}" onclick="selectCartDiscount('${escapeAttr(d.id)}')">${escapeHtml(d.name)}<span>${d.type==='percent'?Number(d.value)+'%':money(d.value)}</span></button>`).join('')}
      </div>
    </div>
    <div class="settings-note">Скидка применяется только к этому товару в текущем заказе.</div>
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="saveCartItemOptions('${escapeAttr(id)}')">Сохранить</button></div>`, true);
}
function changeCartItemModalQty(delta){
  const out=document.getElementById('ci-qty');if(!out)return;
  const current=Math.max(1,Number(out.textContent)||1);
  out.textContent=String(Math.max(1,current+Number(delta||0)));
}
function selectCartDiscount(id){
  const input=document.getElementById('ci-discount'); if(input) input.value=id||'';
  document.querySelectorAll('.discount-choice').forEach(b=>b.classList.toggle('selected',(b.dataset.discountId||'')===(id||'')));
}
function saveCartItemOptions(id){
  const item=state.cart.find(i=>cartItemKey(i)===id); if(!item) return;
  const qty=Math.max(1,Number(document.getElementById('ci-qty')?.textContent)||1);
  if(qty!==item.qty){
    const proposed=state.cart.map(i=>({...i,qty:cartItemKey(i)===id?qty:i.qty}));
    if(!canFulfillCart(proposed))return;
    item.qty=qty;
  }
  item.comment=(document.getElementById('ci-comment')?.value||'').trim();
  item.discountId=document.getElementById('ci-discount')?.value||'';
  saveCurrentOrderSession();
  closeModal(); render();
}


function openOrderSettings(){
  showModal(`
    <div class="modal-title">Параметры заказа</div>
    <div class="field"><label>Тип заказа</label><div class="radio-row">
      ${['На месте','С собой','Доставка'].map(t=>`<button class="radio-opt ${state.orderType===t?'selected':''}" onclick="setOrderType('${t}')">${t}</button>`).join('')}
    </div></div>
    <div class="field"><label>Название / подпись чека</label><input id="order-label" value="${escapeAttr(state.orderLabel)}" placeholder="Например: Иван · стол 4"></div>
    <div class="field"><label>Имя клиента</label><input id="customer-name" value="${escapeAttr(state.customer.name)}" placeholder="Необязательно"></div>
    <div class="field"><label>Телефон</label><input id="customer-phone" inputmode="tel" value="${escapeAttr(state.customer.phone)}" placeholder="Необязательно"></div>
    ${state.orderType==='Доставка'?`<div class="field delivery-address-field"><label>Адрес доставки</label><input id="customer-address" type="text" autocomplete="street-address" value="${escapeAttr(state.customer.address)}" placeholder="Введите адрес доставки"></div>
      <div class="field"><label>Стоимость доставки</label>
        ${state.deliveryRates.length ? `<div class="radio-row">${state.deliveryRates.map(r=>`<button class="radio-opt ${state.deliveryTariffSelected&&Number(state.deliveryFee||0)===Number(r.amount)?'selected':''}" onclick="selectDeliveryFee(${Number(r.amount)||0})">${escapeHtml(r.name)} · ${money(r.amount)}</button>`).join('')}</div>` : `<div class="settings-note">Стоимость доставки пока не настроена. Добавьте варианты во вкладке Настройки.</div>`}
      </div>
      <div class="field delivery-total-field"><label>Итого заказа</label><div class="delivery-total-value">${fullMoney(cartTotal())}</div></div>`:''}
    <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="saveOrderSettings()">Сохранить</button></div>`);
}
function setOrderType(t){ if(state.orderType!==t)state.deliveryTariffSelected=false;state.orderType=t; if(t!=='Доставка') state.deliveryFee=0; saveCurrentOrderSession(); render(); openOrderSettings(); }
function selectDeliveryFee(amount){ if(!state.deliveryRates.some(r=>Number(r.amount)===Number(amount)))return;state.deliveryTariffSelected=true;state.deliveryFee=Number(amount)||0; saveCurrentOrderSession(); render(); openOrderSettings(); }
function saveOrderSettings(){
  state.orderLabel=(document.getElementById('order-label')?.value||'').trim();
  state.customer.name=(document.getElementById('customer-name')?.value||'').trim();
  state.customer.phone=(document.getElementById('customer-phone')?.value||'').trim();
  state.customer.address=(document.getElementById('customer-address')?.value||'').trim();
  saveCurrentOrderSession();
  closeModal(); render();
}
