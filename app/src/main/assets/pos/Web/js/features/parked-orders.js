/* ---- Park (save receipt for later) ---- */
function kitchenPrintLineKey(item){
  return item?.cartLineId || [item?.productId||'', modifierSelectionSignature(item?.selectedModifiers||[]), item?.comment||''].join('|');
}
function kitchenPrintedSnapshot(items){
  return (items||[]).map(i=>({key:kitchenPrintLineKey(i),qty:Number(i.qty)||0}));
}
function kitchenPrintDelta(items,printedItems){
  const printed=new Map((printedItems||[]).map(x=>[String(x.key||''),Number(x.qty)||0]));
  return (items||[]).map(i=>{
    const qty=Number(i.qty)||0, previous=printed.get(kitchenPrintLineKey(i))||0, delta=qty-previous;
    return delta>0.0001 ? {...JSON.parse(JSON.stringify(i)),qty:delta} : null;
  }).filter(Boolean);
}
function parkOrder(){
  if(!state.cart.length) return;
  if(!String(state.orderLabel||'').trim()){
    showModal(`
      <div class="modal-title">Подпись чека</div>
      <div class="settings-note park-order-note">Добавьте подпись, чтобы потом было проще найти этот чек в отложенных.</div>
      <div class="field">
        <label>Подпись</label>
        <input id="park-order-label" type="text" placeholder="Например: Стол 4 или Александр" autocomplete="off" onkeydown="if(event.key==='Enter')confirmParkOrderLabel()">
      </div>
      <div class="modal-actions">
        <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
        <button class="btn btn-primary" onclick="confirmParkOrderLabel()">Отложить</button>
      </div>`);
    setTimeout(()=>document.getElementById('park-order-label')?.focus(),50);
    return;
  }
  parkOrderNow();
}
async function confirmParkOrderLabel(){
  const input=document.getElementById('park-order-label');
  const label=String(input?.value||'').trim();
  if(!label){flash('Введите подпись чека');input?.focus();return;}
  state.orderLabel=label;
  if(await parkOrderNow())closeModal();
}
async function parkOrderNow(){
  if(!state.cart.length)return false;
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  const shift=currentShift(), employee=state.employees.find(e=>e.id===shift?.employeeId);
  const parked = {
    id:uid(), receiptDisplayNumber:'#'+(state.parked.length+1),
    items: state.cart.map(i=>{const product=getProduct(i.productId);return {...JSON.parse(JSON.stringify(i)),category:product?.category||i.category||''}}),
    total:cartTotal(), subtotal:cartSubtotal(), deliveryTariffSelected:!!state.deliveryTariffSelected, deliveryFee:state.orderType==='Доставка'?Number(state.deliveryFee||0):0,
    orderLabel:state.orderLabel, orderType:state.orderType, customer:JSON.parse(JSON.stringify(state.customer)),
    comment:state.orderComment||'', source:state.currentOrderSource||'', webOrderId:state.currentWebOrderId||'', webOrderStatus:state.currentWebOrderStatus||'',
    employeeName:employee?.name||employee?.fullName||'Сотрудник',registerName:'POS 1',timestamp:Date.now(),createdAt:Date.now(),kitchenPrinted:!!window.__currentOrderKitchenPrinted,
    printedItems:JSON.parse(JSON.stringify(window.__currentOrderPrintedItems||[]))
  };
  const deltaItems=kitchenPrintDelta(parked.items,parked.printedItems);
  const shouldPrint=deltaItems.length && typeof window.printKitchenOrderNow==='function';
  const nextParked=storageSnapshot(state.parked);nextParked.push(parked);
  criticalOperationBusy=true;
  try{await commitCriticalStorage('park-order',{parked:nextParked,currentOrderSession:emptyCurrentOrderSession()})}
  catch(e){criticalOperationBusy=false;flash('Чек не отложен: '+(e?.message||e));return false}
  state.parked=nextParked;
  resetCurrentOrderState();
  let printWarning='';
  if(shouldPrint){
    try{
      window.printKitchenOrderNow({...parked,items:deltaItems});
      const printedParked=storageSnapshot(state.parked),printed=printedParked.find(x=>x.id===parked.id);
      printed.kitchenPrinted=true;printed.printedItems=kitchenPrintedSnapshot(printed.items);
      await commitCriticalStorage('park-order-print-state',{parked:printedParked});
      state.parked=printedParked;
    }catch(e){printWarning='Чек отложен, но статус печати кухни не сохранён: '+(e?.message||e)}
  }
  criticalOperationBusy=false;
  render();flash(printWarning||'Чек отложен');
  return true;
}
function openParkedModal(){
  const list=state.parked.slice().sort((a,b)=>b.createdAt-a.createdAt);
  showModal(`<div class="modal-title">Отложенные чеки</div>${list.length?list.map(o=>`
    <div class="list-row">
      <div class="parked-order-main"><div class="list-row-name">${escapeHtml(o.orderLabel||'Без подписи')}</div>
      <div class="list-row-sub">${o.items.reduce((sum,i)=>sum+i.qty,0)} поз. · ${fullMoney(o.total)} · ${escapeHtml(o.orderType||'На месте')} · ${fmtDate(o.createdAt)}</div>
      ${o.comment?`<div class="list-row-sub parked-order-comment"><strong>Комментарий:</strong> ${escapeHtml(o.comment)}</div>`:''}</div>
      <button class="btn btn-outline parked-order-open" onclick="resumeParked('${o.id}')">Открыть</button>
      <button class="icon-btn danger" aria-label="Удалить отложенный заказ" onclick="deleteParked('${o.id}')"><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button>
    </div>`).join(''):`<div class="center-note">Нет отложенных чеков</div>`}
    <div class="modal-actions"><button class="btn btn-secondary modal-full-width-action" onclick="closeModal()">Закрыть</button></div>`);
}
async function resumeParked(id){
  if(state.cart.length){flash('Сначала завершите текущий заказ');return false;}
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  const o=state.parked.find(x=>x.id===id);if(!o)return false;
  const items=storageSnapshot(o.items||[]),customer=Object.assign({name:'',phone:'',address:''},storageSnapshot(o.customer||{}));
  const printedItems=Array.isArray(o.printedItems)?storageSnapshot(o.printedItems):(o.kitchenPrinted?kitchenPrintedSnapshot(o.items):[]);
  const session={items,orderLabel:o.orderLabel||'',orderType:o.orderType||'На месте',customer,deliveryFee:Number(o.deliveryFee||0),deliveryTariffSelected:o.deliveryTariffSelected===true,orderComment:o.comment||'',source:o.source||'',webOrderId:o.webOrderId||'',webOrderStatus:o.webOrderStatus||'',kitchenPrinted:!!o.kitchenPrinted,printedItems,updatedAt:Date.now()};
  const nextParked=state.parked.filter(x=>x.id!==id).map(storageSnapshot);
  criticalOperationBusy=true;
  try{await commitCriticalStorage('resume-parked',{currentOrderSession:session,parked:nextParked})}
  catch(e){flash('Чек не открыт: '+(e?.message||e));return false}
  finally{criticalOperationBusy=false}
  state.cart=items;state.orderLabel=session.orderLabel;state.orderType=session.orderType;
  state.loyaltyPrograms=[];state.loyaltyRedemptions={};state.customer=customer;
  state.deliveryFee=session.deliveryFee;state.deliveryTariffSelected=session.deliveryTariffSelected;
  state.orderComment=session.orderComment;state.currentOrderSource=session.source;state.currentWebOrderId=session.webOrderId;state.currentWebOrderStatus=session.webOrderStatus;
  window.__currentOrderKitchenPrinted=session.kitchenPrinted;window.__currentOrderPrintedItems=printedItems;
  state.parked=nextParked;
  closeModal(); render();
  if(state.customer?.id){
    const selectedCustomer=state.customer;
    void loadCustomerLoyalty(selectedCustomer.id).catch(()=>{
      if(state.customer===selectedCustomer)flash('Клиент привязан. Программа лояльности временно недоступна');
    });
  }
  return true;
}

async function deleteParked(id){
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  if(!state.parked.some(x=>x.id===id))return false;
  const nextParked=state.parked.filter(x=>x.id!==id).map(storageSnapshot);
  criticalOperationBusy=true;
  try{await commitCriticalStorage('delete-parked',{parked:nextParked})}
  catch(e){flash('Чек не удалён: '+(e?.message||e));return false}
  finally{criticalOperationBusy=false}
  state.parked=nextParked;
  openParkedModal();
  return true;
}
