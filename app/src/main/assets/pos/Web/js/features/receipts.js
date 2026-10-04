function showReceipt(order){
  showPaymentReceipt(order);
}
function viewReceiptModal(orderId){
  const order = state.orders.find(o=>o.id===orderId);
  if(!order) return;
  showModal(`
    <div class="modal-title">Чек</div>
    <div id="receipt-body">${receiptBodyHtml(order)}</div>
    <div class="receipt-modal-actions">
      <button class="btn receipt-action receipt-action-print" onclick="printReceipt('${order.id}')">Печать</button>
      ${order.returnedAt ? `<div class="receipt-action receipt-return-sticker">Возврат</div>` : `<button type="button" class="btn receipt-action receipt-action-return" onclick="window.openReturnConfirm('${escapeAttr(order.id)}')">Вернуть</button>`}
      <button class="btn receipt-action receipt-action-close" onclick="closeModal()">Закрыть</button>
    </div>
  `);
}
function restoreOrderStock(order,products=state.products){
  const resolveProduct=id=>products.find(p=>p.id===id);
  if(Object.prototype.hasOwnProperty.call(order,'stockConsumption')){
    const saved=order.stockConsumption;
    if(!saved || saved.version!==1 || !Array.isArray(saved.items)) throw new Error('Некорректные данные списания чека');
    const seen=new Set();
    // Validate every target before changing any stock or return/cash state.
    const updates=saved.items.map(item=>{
      if(!item || seen.has(item.productId) || !Number.isFinite(item.qty) || item.qty<=0) throw new Error('Некорректные данные списания чека');
      seen.add(item.productId);
      const p=resolveProduct(item.productId);
      if(!p || p.type!=='simple') throw new Error('Не найден исходный складской товар для возврата');
      const stock=Number(p.stock);
      if(!Number.isFinite(stock) || !Number.isFinite(stock+item.qty)) throw new Error('Некорректный остаток для возврата');
      return {p,stock:stock+item.qty};
    });
    for(const update of updates) update.p.stock=roundStockQty(update.stock);
    return;
  }
  // Old receipts have no historical recipe. Preserve their legacy return path.

  for(const item of (order.items||[])){
    const p=resolveProduct(item.productId);
    if(!p) continue;
    if(p.type==='simple'){
      p.stock = roundStockQty(Number(p.stock||0) + Number(item.qty||0));
    }else{
      for(const c of (p.components||[])){
        const comp=resolveProduct(c.productId);
        if(productTracksStock(comp)) comp.stock = roundStockQty(Number(comp.stock||0) + Number(c.qty||0)*Number(item.qty||0));
      }
    }
  }
}
function openReturnConfirm(orderId){
  const order=state.orders.find(o=>o.id===orderId);
  if(!order) return;
  if(order.returnedAt){ flash('Этот чек уже возвращён'); return; }
  const shift=currentShift();
  if(!shift){ flash('Для возврата откройте кассовую смену'); return; }
  const total=Number(order.total||0);
  showModal(`
    <div class="modal-title">Возврат товара</div>
    <div class="settings-note receipt-return-note">Будет выполнен полный возврат чека. Все товары вернутся на склад, а покупателю будет возвращена сумма <b>${fullMoney(total)}</b>.</div>
    <div class="field"><label>Чек</label><div class="receipt-return-meta">${fmtDate(order.timestamp)} · ${order.method==='cash'?'Наличные':order.method==='card'?'Карта':'Наличные + карта'}</div></div>
    <div class="field"><label>К возврату</label><div class="receipt-return-amount">${fullMoney(total)}</div></div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
      <button class="btn btn-danger" onclick="window.processFullReturn('${escapeAttr(order.id)}')">Вернуть ${fullMoney(total)}</button>
    </div>`);
}
async function processFullReturn(orderId){
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return;}
  try{
    const order=state.orders.find(o=>String(o.id)===String(orderId));
    const shift=currentShift();
    if(!order){flash('Чек не найден');return;}
    if(order.returnedAt){flash('Этот чек уже возвращён');return;}
    if(!shift){flash('Для возврата откройте кассовую смену');return;}
    const total=Math.round(Number(order.total||0)*100)/100;
    if(!Number.isFinite(total)||total<0){flash('Некорректная сумма возврата');return;}
    const cashPayments=Array.isArray(order.payments)?order.payments.filter(p=>p.method==='cash'):null;
    if(cashPayments?.some(p=>!Number.isFinite(Number(p.amount))||Number(p.amount)<0)){flash('Некорректные данные оплаты чека');return;}
    const refundCash=cashPayments?cashPayments.reduce((s,p)=>s+Number(p.amount),0):(order.method==='cash'?total:0);
    if(!Number.isFinite(refundCash)||refundCash<0||refundCash>total+0.0001){flash('Некорректные данные оплаты чека');return;}
    if(refundCash>0){
      const t=shiftTotals(shift.id);
      const available=cashDrawerBalance(shift,t);
      if(!Number.isFinite(available)||available<0){flash('Некорректные данные кассовой смены');return;}
      if(refundCash>available+0.0001){flash('Недостаточно наличных в кассе для возврата');return;}
    }
    const nextProducts=storageSnapshot(state.products),nextOrders=storageSnapshot(state.orders),nextShifts=storageSnapshot(state.shifts);
    const nextOrder=nextOrders.find(o=>String(o.id)===String(orderId));
    const nextShift=nextShifts.find(s=>s.id===shift.id);
    restoreOrderStock(nextOrder,nextProducts);
    const now=Date.now();
    nextOrder.returnedAt=now;
    nextOrder.returnedShiftId=shift.id;
    nextOrder.returnAmount=total;
    if(nextOrder.customer?.id)nextOrder.loyaltyReversal={status:'pending',at:now};
    if(!Array.isArray(nextShift.cashMovements)) nextShift.cashMovements=[];
    if(refundCash>0){
      nextShift.cashMovements.push({id:uid(),type:'withdrawal',subtype:'refund',amount:refundCash,timestamp:now,note:'Возврат чека'});
    }
    criticalOperationBusy=true;
    try{await commitCriticalStorage('return',{products:nextProducts,orders:nextOrders,shifts:nextShifts})}
    finally{criticalOperationBusy=false}
    state.products=nextProducts;state.orders=nextOrders;state.shifts=nextShifts;
    void publishAvailability();
    const committedOrder=state.orders.find(o=>String(o.id)===String(orderId));
    if(committedOrder.customer?.id)void settleReturnedOrderLoyalty(committedOrder);
    closeModal();
    render();
    setTimeout(()=>showModal(`
      <div class="modal-title">Возврат выполнен</div>
      <div class="settings-note">Товары возвращены на остатки. ${refundCash>0?'Наличная часть списана из текущей кассовой смены как «Возврат чека».':''}${(order.method==='card'||order.method==='split')?' Карточная часть возвращается через банковский терминал.':''}</div>
      <div class="field"><label>Сумма возврата</label><div class="receipt-return-amount">${fullMoney(total)}</div></div>
      <div class="modal-actions"><button type="button" class="btn btn-secondary" onclick="viewReceiptModal('${escapeAttr(committedOrder.id)}')">Открыть чек</button><button type="button" class="btn btn-primary" onclick="closeModal()">Готово</button></div>`),50);
  }catch(e){
    console.error('processFullReturn error',e);
    flash('Не удалось выполнить возврат: '+(e.message||'неизвестная ошибка'));
  }
}
window.processFullReturn = processFullReturn;
window.openReturnConfirm = openReturnConfirm;

function printReceipt(orderId){
  const order = state.orders.find(o=>o.id===orderId);
  if(!order) return;
  if(typeof window.sendOrderToPrint==='function'){
    window.sendOrderToPrint(order);
    return;
  }
  flash('Модуль сетевой печати не загружен');
}

/* ============================= RECEIPTS SCREEN (история чеков) ============================= */
function selectReceipt(orderId){
  const list=document.getElementById('receipts-list');
  const scrollTop=list?.scrollTop||0;
  state.selectedReceiptId=orderId;
  render();
  requestAnimationFrame(()=>{
    const nextList=document.getElementById('receipts-list');
    if(nextList) nextList.scrollTop=scrollTop;
  });
}
function renderReceiptsScreen(){
  const list = state.orders.slice().sort((a,b)=>b.timestamp-a.timestamp).slice(0,300);
  const selected = list.find(o=>o.id===state.selectedReceiptId) || list[0] || null;
  if(selected && state.selectedReceiptId!==selected.id) state.selectedReceiptId=selected.id;
  return `
  <div class="screen content-screen ${state.tab==='receipts'?'active':''}">
    <div class="content-head"><div class="content-title">Чеки</div></div>
    <div class="receipts-layout">
      <aside class="card receipts-list-panel">
        <div class="receipts-list-header">
          <div class="receipts-list-heading">История чеков</div>
          <div class="receipts-list-count">Показано: ${list.length}</div>
        </div>
        <div class="receipts-list" id="receipts-list">
          ${list.length ? list.map(o=>`
            <button type="button" class="receipts-list-row ${o.id===selected?.id?'selected':''} ${o.returnedAt?'returned':''}" aria-pressed="${o.id===selected?.id?'true':'false'}" onclick="selectReceipt('${escapeAttr(o.id)}')">
              <span class="receipts-list-topline">
                <span class="receipts-list-number">Чек ${escapeHtml(o.receiptDisplayNumber||'')}</span>
                <span class="receipts-list-amount">${fullMoney(o.total)}</span>
              </span>
              <span class="receipts-list-meta">${fmtDate(o.timestamp)} · ${o.method==='cash'?'Наличные':o.method==='card'?'Карта':'Наличные + карта'}</span>
              <span class="receipts-list-statusline">
                <span class="receipts-order-type">${escapeHtml(o.orderType||'На месте')}</span>
                ${o.returnedAt ? '<span class="receipt-return-sticker">Возврат</span>' : ''}
              </span>
            </button>
          `).join('') : `<div class="center-note receipts-list-empty">Чеков пока нет — они появятся здесь после первой оплаты.</div>`}
        </div>
      </aside>
      <div class="card receipt-detail-card">
        ${selected ? `
          <div class="receipt-detail-title">Чек ${escapeHtml(selected.receiptDisplayNumber||'')}</div>
          <div id="receipt-body" class="receipt-detail-body">${receiptBodyHtml(selected)}</div>
          <div class="receipt-modal-actions receipt-detail-actions">
            <button class="btn receipt-action receipt-action-print receipt-detail-action" onclick="printReceipt('${escapeAttr(selected.id)}')">Печать</button>
            ${selected.returnedAt ? `<div class="receipt-action receipt-return-sticker receipt-detail-action">Возврат</div>` : `<button type="button" class="btn receipt-action receipt-action-return receipt-detail-action" onclick="window.openReturnConfirm('${escapeAttr(selected.id)}')">Вернуть</button>`}
          </div>
        ` : `<div class="center-note">Выберите чек слева.</div>`}
      </div>
    </div>
  </div>`;
}
