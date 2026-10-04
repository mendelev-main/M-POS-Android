function viewReceivingModal(receivingId){
  const r=state.receivings.find(x=>x.id===receivingId);
  if(!r)return;
  const items=Array.isArray(r.items)?r.items:[{
    productId:r.productId,
    productName:r.productName,
    qty:r.qty,
    unitCost:r.unitCost||0,
    totalCost:r.totalCost??((r.qty||0)*(r.unitCost||0))
  }];
  const total=Number(r.totalCost??items.reduce((sum,i)=>sum+(Number(i.totalCost)||0),0));
  showModal(`
    <div class="modal-title">${r.type==='purchase'?'Закупка':'Приёмка'}</div>
    <div class="supply-modal-meta">
      ${r.type==='purchaseOrder'?`<strong>${escapeHtml(r.supplierName||'Поставщик не указан')}</strong><br>`:''}
      ${fmtDate(r.timestamp)}${r.invoiceNumber?'<br>Номер '+escapeHtml(r.invoiceNumber):''}${r.invoiceDate?' · '+escapeHtml(r.invoiceDate):''}
      ${r.type==='purchaseOrder'?`<br><span class="badge supply-status-badge ${r.adminDeleted?'deleted-admin':(r.shortage?'shortage':'received')}">${r.adminDeleted?'Удалено администратором':(r.shortage?'Расхождение':'Принято')}</span>`:''}
    </div>
    <div class="receiving-detail-list">
      ${items.map(i=>`
        <div class="list-row receiving-detail-row">
          <div class="receiving-detail-main">
            <div class="list-row-name">${escapeHtml(i.productName||'Товар')}</div>
            <div class="list-row-sub">Количество: ${escapeHtml(String(i.invoiceQty??i.qty??0))} ${unitLabel(i.invoiceUnit??i.stockUnit??stockUnit(getProduct(i.productId)))} · Цена: ${Number(Number(i.invoiceUnitPrice??i.unitCost??0).toPrecision(10))} · Сумма: ${money(i.totalCost||0)}</div>
          </div>
          <div class="badge">${money(i.totalCost||0)}</div>
        </div>`).join('')}
    </div>
    <div class="receipt-total receiving-detail-total"><span>Итого</span><span>${money(total)}</span></div>
    <div class="modal-actions"><button class="btn btn-primary" onclick="closeModal()">Закрыть</button></div>
  `);
}

function receivingHistoryMarkup(){
  const history=state.receivings.slice().sort((a,b)=>b.timestamp-a.timestamp);
  return history.map(r=>{const status=r.adminDeleted?{label:'Удалено администратором',style:'deleted'}:r.shortage?{label:'Недовоз',style:'shortage'}:{label:'Принят',style:'received'};return `<button class="purchase-history-row" onclick="viewReceivingModal(${escapeAttr(JSON.stringify(r.id))})"><span class="purchase-history-title"><strong>${escapeHtml(r.supplierName||'Без поставщика')}</strong><small>${fmtDate(r.timestamp)}${r.invoiceNumber?' · Номер '+escapeHtml(r.invoiceNumber):''} · ${money(r.totalCost||0)}</small></span><span class="purchase-status ${status.style}">${status.label}</span></button>`;}).join('')||'<p class="pe-note supply-history-empty">Приёмок пока нет.</p>';
}
function toggleReceivingHistory(){
  window._receivingHistoryExpanded=!window._receivingHistoryExpanded;
  document.getElementById('receiving-history-panel').hidden=!window._receivingHistoryExpanded;
  document.getElementById('receiving-history-button').setAttribute('aria-expanded',String(window._receivingHistoryExpanded));
}
function renderReceivingScreen(){
  const pending=state.purchaseOrders.filter(o=>!['received','deleted'].includes(o.status)).slice().sort((a,b)=>b.timestamp-a.timestamp);
  return `<div class="screen content-screen ${state.tab==='receiving'?'active':''}"><div class="supply-stack"><div class="supply-stack-actions"><button class="btn btn-outline supply-history-button" id="receiving-history-button" aria-controls="receiving-history-panel" aria-expanded="${!!window._receivingHistoryExpanded}" onclick="toggleReceivingHistory()"><svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>История</button><div id="receiving-history-panel" class="purchase-history-panel" ${window._receivingHistoryExpanded?'':'hidden'}>${receivingHistoryMarkup()}</div><button class="btn btn-primary" aria-expanded="${!!window._receivingExpanded}" id="receiving-expand-button" onclick="toggleReceivingPanel()">Новая приёмка</button></div><div id="receiving-inline-panel" class="supply-document supply-inline" ${window._receivingExpanded?'':'hidden'}>${window._receivingDraft&&!window._receivingDraft.orderId?receivingDocumentMarkup():''}</div>
  <div class="supply-layout supply-focused"><div class="card supply-pending"><h2>К приёмке <span class="badge">${pending.length}</span></h2><div class="supply-pending-grid">${pending.map(o=>`<div class="supply-request"><strong>${escapeHtml(o.supplierName||'Поставщик')}</strong><p class="pe-note">${fmtDate(o.timestamp)} · ${(o.items||[]).length} позиций</p><div class="supply-preview">${(o.items||[]).slice(0,5).map(i=>`<div>${escapeHtml(i.productName)} <b>${escapeHtml(requestedQuantityText(i))}</b></div>`).join('')}</div><div class="supply-actions"><button class="btn btn-primary ${o.receivingDraftV2||o.receivingIncomplete?'receiving-resume':''}" onclick="openReceivingDocument(${escapeAttr(JSON.stringify(o.id))})">${o.receivingDraftV2||o.receivingIncomplete?'Продолжить приёмку':'Принять'}</button>${canAdminDeletePurchaseOrder()?`<button class="btn btn-outline" onclick="openDeletePurchaseOrderModal(${escapeAttr(JSON.stringify(o.id))})">Удалить</button>`:''}</div></div>`).join('')||'<p class="pe-note">Нет ожидающих заказов.</p>'}</div></div>
</div></div></div>`;
}

function receivingDocumentInput(){
  const d=window._receivingDraft;if(!d)return;
  d.invoiceNumber=document.getElementById('ttn-number').value;d.invoiceDate=document.getElementById('ttn-date').value;
  d.supplierId=document.getElementById('ttn-supplier').value;
}
function updateInvoiceLine(index,field,value){
  const line=window._receivingDraft.lines[index];if(!line)return;
  if(field==='unit'&&!['bottle','pack','box'].includes(line.unit)&&!['bottle','pack','box'].includes(value)){
    try{const factor=convertProductQty(1,line.unit,value);if(line.qtyInput!=='')line.qtyInput=convertProductQty(line.qtyInput,line.unit,value);if(line.priceInput!=='')line.priceInput=Number(line.priceInput)/factor;}catch(e){flash(e.message);return;}
  }
  line[field]=value;
  if(field==='unit'&&['bottle','pack','box'].includes(value)){const p=getProduct(line.productId);line.packSize=line.packSize||p?.purchasePackSize||'';line.contentUnit=line.contentUnit||p?.purchaseContentUnit||stockUnit(p);}
  if(field==='totalInput')line.priceMode='total';if(field==='priceInput')line.priceMode='price';
  const q=Number(line.qtyInput);
  if(line.priceMode==='price'&&line.priceInput!==''&&line.qtyInput!=='')line.totalInput=Number((q*Number(line.priceInput)).toFixed(2));
  else if(line.totalInput!==''&&q>0)line.priceInput=Number((Number(line.totalInput)/q).toPrecision(12));
  const row=document.querySelector(`[data-invoice-line="${index}"]`);
  if(row){for(const key of ['qtyInput','priceInput','totalInput']){const input=row.querySelector(`[data-invoice-field="${key}"]`);if(input&&document.activeElement!==input)input.value=line[key];}}
  const sum=document.getElementById('ttn-total');if(sum)sum.textContent=money(window._receivingDraft.lines.reduce((s,l)=>s+(Number(l.totalInput)||0),0));
}
function addInvoiceProduct(){
  if(window._receivingDraft?.orderId)return;
  const id=document.getElementById('ttn-add-product').value,p=getProduct(id);if(!p)return;
  receivingDocumentInput();window._receivingDraft.lines.push({productId:id,qtyInput:'',unit:stockUnit(p),priceInput:'',totalInput:'',priceMode:'total'});renderReceivingDocument();
}
function removeInvoiceLine(index){
  receivingDocumentInput();const d=window._receivingDraft;if(d.orderId){flash('Для непоставленного товара укажите 0 и сумму 0');return;}d.lines.splice(index,1);renderReceivingDocument();
}
function receivingDocumentMarkup(){
  const d=window._receivingDraft,order=state.purchaseOrders.find(o=>o.id===d.orderId);
  return `<div class="supply-doc-header"><div><div class="modal-title">${order?'Приёмка заказа':'Приёмка без заказа'}</div><p class="pe-note">Не поставлено — укажите 0.</p></div>${order?'<div class="receiving-page-actions"><button data-receiving-draft-save class="btn btn-outline" onclick="saveReceivingDraft()">← Назад</button><button data-receiving-draft-save class="btn btn-outline" onclick="saveReceivingDraft()">Завершить приёмку позже</button></div>':'<button data-receiving-draft-save class="btn btn-outline" onclick="saveReceivingDraft()">Сохранить и выйти</button>'}</div>
  <div class="supply-document-meta" oninput="receivingDocumentInput()"><div class="field"><label for="ttn-supplier">Поставщик</label><select id="ttn-supplier" ${order?'disabled':''}><option value="">Не указан</option>${state.suppliers.map(s=>`<option value="${escapeAttr(s.id)}" ${s.id===d.supplierId?'selected':''}>${escapeHtml(s.name)}</option>`).join('')}${order&&!state.suppliers.some(s=>s.id===d.supplierId)?`<option selected value="${escapeAttr(d.supplierId)}">${escapeHtml(order.supplierName)}</option>`:''}</select></div><div class="field"><label for="ttn-number">Номер</label><input id="ttn-number" maxlength="100" value="${escapeAttr(d.invoiceNumber||'')}" placeholder="Номер"></div><div class="field"><label for="ttn-date">Дата</label><input id="ttn-date" type="date" value="${escapeAttr(d.invoiceDate||'')}"></div></div>
  <div class="supply-invoice-table">${d.lines.map((line,index)=>{const p=getProduct(line.productId),ordered=order?.items.find(i=>i.productId===line.productId),isPack=['bottle','pack','box'].includes(line.unit);return `<div class="supply-invoice-row" data-invoice-line="${index}"><div class="supply-product"><strong>${escapeHtml(p?.name||'Товар не найден')}</strong><small>${ordered?'Заказано: '+escapeHtml(requestedQuantityText(ordered)):'Дополнительная позиция'} · Склад: ${unitLabel(stockUnit(p))}</small></div><div class="field"><label>Принято</label><input type="number" min="0" step="any" inputmode="decimal" data-invoice-field="qtyInput" value="${escapeAttr(String(line.qtyInput))}" oninput="updateInvoiceLine(${index},'qtyInput',this.value)"></div><div class="field"><label>Единица</label><select onchange="updateInvoiceLine(${index},'unit',this.value);renderReceivingDocument()">${purchaseUnitOptions(p,line.unit)}</select></div>${isPack?`<div class="field"><label>В единице</label><input type="number" min="0" step="any" inputmode="decimal" value="${escapeAttr(String(line.packSize??''))}" oninput="updateInvoiceLine(${index},'packSize',this.value)"></div><div class="field"><label>Единица внутри</label><select onchange="updateInvoiceLine(${index},'contentUnit',this.value)">${productUnitOptions(stockUnit(p),line.contentUnit||stockUnit(p))}</select></div>`:''}<div class="field"><label>Сумма строки</label><input type="number" min="0" step="0.01" inputmode="decimal" data-invoice-field="totalInput" value="${escapeAttr(String(line.totalInput))}" oninput="updateInvoiceLine(${index},'totalInput',this.value)"></div>${!order?`<button class="icon-btn danger" onclick="removeInvoiceLine(${index})" aria-label="Убрать строку"><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button>`:''}</div>`;}).join('')}</div>
  ${!order?`<div class="supply-actions"><select id="ttn-add-product"><option value="">Добавить товар</option>${state.products.filter(p=>p.type==='simple').map(p=>`<option value="${escapeAttr(p.id)}">${escapeHtml(p.name)}</option>`).join('')}</select><button class="btn btn-outline" onclick="addInvoiceProduct()">Добавить</button></div>`:''}
  <div class="supply-doc-footer"><div><span>Итого</span><strong id="ttn-total">${money(d.lines.reduce((s,l)=>s+(Number(l.totalInput)||0),0))}</strong></div><button id="ttn-confirm" class="btn btn-primary" onclick="confirmReceivingDocument()">Далее</button></div>`;
}
async function toggleReceivingPanel(){
  if(!window._receivingDraft){await openReceivingDocument();return;}
  window._receivingExpanded=!window._receivingExpanded;
  document.getElementById('receiving-inline-panel').hidden=!window._receivingExpanded;
  document.getElementById('receiving-expand-button').setAttribute('aria-expanded',String(window._receivingExpanded));
}
function finishReceivingPage(){
  const root=document.getElementById('receiving-page-root');if(root)root.innerHTML='';
  const app=document.getElementById('app');if(app)app.inert=false;
  const screen=document.querySelector('.screen.active');if(screen)screen.scrollTop=window._receivingPageScroll||0;
}
function renderReceivingDocument(){
  const page=document.getElementById('receiving-page-root');
  if(window._receivingDraft?.orderId&&page){
    closeModal();window._receivingExpanded=false;
    const inline=document.getElementById('receiving-inline-panel');if(inline){inline.innerHTML='';inline.hidden=true;}
    document.getElementById('app').inert=true;
    page.innerHTML='<section class="receiving-page supply-document" aria-label="Приёмка поставки">'+receivingDocumentMarkup()+'</section>';
    return;
  }
  const panel=document.getElementById('receiving-inline-panel');
  if(panel){
    closeModal();window._receivingExpanded=true;panel.innerHTML=receivingDocumentMarkup();panel.hidden=false;
    document.getElementById('receiving-expand-button')?.setAttribute('aria-expanded','true');
    panel.scrollIntoView({block:'nearest',behavior:'smooth'});
  }else{
    showModal(receivingDocumentMarkup(),true);
    document.querySelector('#modal-root .modal')?.classList.add('supply-document');
    const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.onclick=null;
  }
}
