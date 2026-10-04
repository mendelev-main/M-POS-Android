function canAdminDeletePurchaseOrder(){
  const shift=currentShift();
  if(!shift)return false;
  const employee=state.employees.find(e=>e.id===shift.employeeId);
  return employee?.role==='admin';
}

function openDeletePurchaseOrderModal(orderId){
  if(!canAdminDeletePurchaseOrder()){
    flash('Удалять приёмки может только администратор при открытой им смене');
    return;
  }
  const order=state.purchaseOrders.find(o=>o.id===orderId);
  if(!order||order.deletedAt)return;
  if(order.status==='received'){
    flash('Принятую приёмку удалить нельзя');
    return;
  }
  showModal(`
    <div class="modal-title">Удалить приёмку?</div>
    <div class="center-note supply-delete-note">Заказ от «${escapeHtml(order.supplierName||'Поставщик')}» будет помечен как удалённый администратором и останется в истории.</div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
      <button class="btn btn-danger" onclick="deletePurchaseOrderAsAdmin(${escapeAttr(JSON.stringify(order.id))})">Удалить приёмку</button>
    </div>`,false);
}

async function deletePurchaseOrderAsAdmin(orderId){
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  if(!canAdminDeletePurchaseOrder()){
    closeModal();
    flash('Удалять приёмки может только администратор при открытой им смене');
    return false;
  }
  const order=state.purchaseOrders.find(o=>o.id===orderId);
  if(!order||order.deletedAt)return false;
  if(order.status==='received'){
    closeModal();
    flash('Принятую приёмку удалить нельзя');
    return false;
  }
  const shift=currentShift();
  const employee=state.employees.find(e=>e.id===shift.employeeId);
  const nextPurchaseOrders=storageSnapshot(state.purchaseOrders),nextReceivings=storageSnapshot(state.receivings),nextOrder=nextPurchaseOrders.find(o=>o.id===orderId),deletedAt=Date.now();
  nextOrder.deletedAt=deletedAt;
  nextOrder.deletedByEmployeeId=employee?.id||'';
  nextOrder.deletedByEmployeeName=employee?.name||shift.employeeName||'Администратор';
  nextOrder.status='deleted';
  nextOrder.receivingIncomplete=false;
  delete nextOrder.receivingDraft;
  delete nextOrder.receivingDraftV2;
  const alreadyInHistory=nextReceivings.some(r=>r.purchaseOrderId===nextOrder.id&&r.adminDeleted);
  if(!alreadyInHistory){
    nextReceivings.push({
      id:uid(),
      type:'purchaseOrder',
      purchaseOrderId:nextOrder.id,
      supplierId:nextOrder.supplierId||'',
      supplierName:nextOrder.supplierName||'',
      items:Array.isArray(nextOrder.items)?nextOrder.items.map(i=>({productId:i.productId,productName:i.productName,qty:Number(i.qty)||0,orderedQty:Number(i.qty)||0,unitCost:0,totalCost:0})):[],
      totalCost:0,
      shortage:false,
      adminDeleted:true,
      deletedByEmployeeName:nextOrder.deletedByEmployeeName||'Администратор',
      timestamp:deletedAt
    });
  }
  criticalOperationBusy=true;
  try{await commitCriticalStorage('delete-purchase-order',{purchaseOrders:nextPurchaseOrders,receivings:nextReceivings});}
  catch(e){flash('Приёмка не удалена: '+(e?.message||e));return false;}
  finally{criticalOperationBusy=false;}
  state.purchaseOrders=nextPurchaseOrders;
  state.receivings=nextReceivings;
  try{closeModal();render();flash('Приёмка удалена администратором и перемещена в историю');}
  catch(e){console.error('Не удалось обновить экран после удаления приёмки:',e);}
  return true;
}

function purchaseUnitLabel(unit){return unit==='pack'?'упаковка':unit==='box'?'коробка':unit==='bottle'?'бутылка':unitLabel(unit);}
function requestedQuantityText(item){const unit=item.requestedUnit??item.stockUnit??stockUnit(getProduct(item.productId));const base=`${item.requestedQty??item.qty} ${purchaseUnitLabel(unit)}`;return ['bottle','pack','box'].includes(unit)&&item.packSize?`${base} × ${item.packSize} ${unitLabel(item.contentUnit||item.stockUnit||stockUnit(getProduct(item.productId)))}`:base;}
function purchaseUnitOptions(p,selected){
  const base=stockUnit(p),units=[...Object.keys(PRODUCT_UNITS).filter(u=>base&&PRODUCT_UNITS[u].kind===PRODUCT_UNITS[base]?.kind),'bottle','pack','box'];
  if(!base)units.unshift('');
  return units.map(u=>`<option value="${u}" ${u===selected?'selected':''}>${purchaseUnitLabel(u)}</option>`).join('');
}
function makePurchaseLine(p,qty,unit,packSize,contentUnit){
  qty=Number(qty);if(!Number.isFinite(qty)||qty<=0)throw new Error('Количество заказа должно быть больше нуля');
  const base=stockUnit(p),pack=['bottle','pack','box'].includes(unit),inside=contentUnit||base;
  let expected=null;
  if(pack){
    if(packSize!==''&&packSize!=null){
      const size=Number(packSize);if(!Number.isFinite(size)||size<=0)throw new Error('Количество в единице закупки должно быть больше нуля');
      expected=convertProductQty(qty*size,inside,base);
    }
  }else expected=convertProductQty(qty,unit,base);
  if(expected!==null&&(!Number.isFinite(expected)||expected<=0))throw new Error('Некорректное количество заказа');
  return {productId:p.id,productName:p.name,qty:expected??0,requestedQty:qty,requestedUnit:unit,stockUnit:base,contentUnit:inside,expectedQty:expected,packSize:pack&&packSize!==''&&packSize!=null?Number(packSize):null,timestamp:Date.now()};
}

function updatePurchaseRequest(productId){
  const row=[...document.querySelectorAll('[data-purchase-row]')].find(r=>r.dataset.purchaseRow===productId);if(!row)return;
  const qty=row.querySelector('[data-request-qty]').value,unit=row.querySelector('[data-request-unit]').value,size=row.querySelector('[data-request-size]').value,contentUnit=row.querySelector('[data-content-unit]')?.value||stockUnit(getProduct(productId));
  const idx=state.purchaseOrderCart.findIndex(x=>x.productId===productId);
  const draft={productId,requestedQty:qty,requestedUnit:unit,packSize:size,contentUnit};
  if(qty!==''){if(idx<0)state.purchaseOrderCart.push(draft);else state.purchaseOrderCart[idx]=draft;}else if(idx>=0)state.purchaseOrderCart.splice(idx,1);
  row.querySelectorAll('[data-pack-field]').forEach(el=>el.hidden=!['bottle','pack','box'].includes(unit));
  const note=row.querySelector('[data-request-note]');
  try{const line=makePurchaseLine(getProduct(productId),qty,unit,size,contentUnit);note.textContent=line.expectedQty===null?'Количество внутри уточняется при приёмке':`На склад поступит: ${line.expectedQty} ${unitLabel(line.stockUnit)}`;}catch(e){note.textContent=qty===''?'Укажите количество':e.message;}
  const button=document.getElementById('po-create');if(button)button.disabled=!state.purchaseOrderCart.length;
}

async function finalizePurchaseOrder(){
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  const supplier=getPurchaseOrderSupplier();if(!supplier){flash('Выберите поставщика');return false;}
  let nextProducts,nextPurchaseOrders,order;
  try{
    nextProducts=storageSnapshot(state.products);
    const items=state.purchaseOrderCart.map(x=>{
      const p=getProduct(x.productId);if(!p||p.type!=='simple')throw new Error('Товар недоступен: '+(p?.name||x.productId));
      const supplierProductIds=(supplier.productIds||[]).map(String);if(supplierProductIds.length&&!supplierProductIds.includes(String(p.id)))throw new Error('Товар «'+p.name+'» больше не привязан к выбранному поставщику');
      const line=makePurchaseLine(p,x.requestedQty??x.qty,x.requestedUnit??stockUnit(p),x.packSize??'',x.contentUnit??p.purchaseContentUnit??stockUnit(p)),nextProduct=nextProducts.find(item=>item.id===p.id);
      if(!nextProduct)throw new Error('Товар недоступен: '+p.name);
      nextProduct.purchaseUnit=line.requestedUnit;nextProduct.purchasePackSize=line.packSize??undefined;nextProduct.purchaseContentUnit=line.contentUnit;return line;
    });
    if(!items.length)throw new Error('Добавьте хотя бы один товар');
    order={id:uid(),requestVersion:1,supplierId:supplier.id,supplierName:supplier.name,items,status:'pending',timestamp:Date.now()};
    nextPurchaseOrders=storageSnapshot(state.purchaseOrders);nextPurchaseOrders.push(order);
    criticalOperationBusy=true;
    try{await commitCriticalStorage('create-purchase-order',{products:nextProducts,purchaseOrders:nextPurchaseOrders});}
    finally{criticalOperationBusy=false;}
  }catch(e){flash('Заказ не сформирован: '+(e?.message||e));return false;}
  state.products=nextProducts;state.purchaseOrders=nextPurchaseOrders;state.purchaseOrderCart=[];
  try{render();viewPurchaseOrder(order.id);}
  catch(e){console.error('Заказ сохранён, но не удалось открыть его карточку:',e);}
  return true;
}

function purchaseHistoryStatus(order){
  if(order.status==='deleted'||order.deletedAt)return {label:'Удалено администратором',style:'deleted'};
  if(order.status==='received')return order.shortage?{label:'Недовоз',style:'shortage'}:{label:'Принят',style:'received'};
  return {label:'Ожидается приёмка',style:'pending'};
}
function purchaseHistoryMarkup(){
  const history=state.purchaseOrders.slice().sort((a,b)=>b.timestamp-a.timestamp);
  return history.map(o=>{const status=purchaseHistoryStatus(o);return `<button class="purchase-history-row" onclick="viewPurchaseOrder(${escapeAttr(JSON.stringify(o.id))})"><span class="purchase-history-title"><strong>${escapeHtml(o.supplierName||'Поставщик')}</strong><small>${fmtDate(o.timestamp)} · ${(o.items||[]).length} позиций</small></span><span class="purchase-status ${status.style}">${status.label}</span></button>`;}).join('')||'<p class="pe-note supply-history-empty">Заказов пока нет.</p>';
}
function togglePurchaseHistory(){
  window._purchaseHistoryExpanded=!window._purchaseHistoryExpanded;
  const panel=document.getElementById('purchase-history-panel');panel.hidden=!window._purchaseHistoryExpanded;
  document.getElementById('purchase-history-button').setAttribute('aria-expanded',String(window._purchaseHistoryExpanded));
}
function openPurchaseQuantity(productId,field='qty'){
  if(!['qty','size'].includes(field))return;
  const row=[...document.querySelectorAll('[data-purchase-row]')].find(r=>r.dataset.purchaseRow===productId);if(!row)return;
  window._purchaseQuantity={productId,field,value:row.querySelector('[data-request-'+field+']').value,replace:true};
  showModal(`<div class="modal-title">${field==='size'?'В упаковке, '+unitLabel(stockUnit(getProduct(productId))):'Заказать'}</div><p class="pe-note">${escapeHtml(getProduct(productId)?.name||'Товар')}</p><output id="purchase-keypad-value" aria-live="polite">${escapeHtml(window._purchaseQuantity.value||'0')}</output><div class="purchase-keypad">${['1','2','3','4','5','6','7','8','9',',','0','⌫'].map(k=>`<button type="button" onclick="purchaseQuantityKey('${k}')" aria-label="${k==='⌫'?'Удалить цифру':k===','?'Десятичная запятая':k}">${k}</button>`).join('')}</div><div class="purchase-keypad-actions"><button class="btn btn-outline" onclick="purchaseQuantityKey('clear')">Очистить</button><button class="btn btn-primary" onclick="applyPurchaseQuantity()">Готово</button></div>`);
  document.querySelector('#modal-root .modal')?.classList.add('purchase-keypad-modal');
}
function purchaseQuantityKey(key){
  const draft=window._purchaseQuantity;if(!draft)return;
  if(key==='clear'){draft.value='';draft.replace=false;}
  else if(key==='⌫'){draft.value=draft.value.slice(0,-1);draft.replace=false;}
  else if(key===','){if(draft.replace)draft.value='';if(!draft.value.includes('.'))draft.value=(draft.value||'0')+'.';draft.replace=false;}
  else if(/^[0-9]$/.test(key)){
    if(draft.replace)draft.value='';draft.replace=false;
    if(draft.value.length<12)draft.value=draft.value==='0'?key:draft.value+key;
  }
  const output=document.getElementById('purchase-keypad-value');if(output)output.textContent=(draft.value||'0').replace('.',',');
}
function applyPurchaseQuantity(){
  const draft=window._purchaseQuantity;if(!draft)return;
  const row=[...document.querySelectorAll('[data-purchase-row]')].find(r=>r.dataset.purchaseRow===draft.productId);if(!row){closeModal();return;}
  row.querySelector(draft.field==='size'?'[data-request-size]':'[data-request-qty]').value=draft.value===''?'':String(Number(draft.value));
  updatePurchaseRequest(draft.productId);window._purchaseQuantity=null;closeModal();
}

function renderPurchaseOrdersScreen(){
  const supplier=getPurchaseOrderSupplier(),products=supplier?state.products.filter(p=>p.type==='simple'&&(supplier.productIds||[]).includes(p.id)).slice().sort((a,b)=>a.name.localeCompare(b.name,'ru')):[];
  return `<div class="screen content-screen ${state.tab==='purchaseOrders'?'active':''}"><div class="supply-stack"><div class="supply-stack-actions"><button class="btn btn-outline supply-history-button" id="purchase-history-button" aria-controls="purchase-history-panel" aria-expanded="${!!window._purchaseHistoryExpanded}" onclick="togglePurchaseHistory()"><svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>История</button><div id="purchase-history-panel" class="purchase-history-panel" ${window._purchaseHistoryExpanded?'':'hidden'}>${purchaseHistoryMarkup()}</div><button class="btn btn-primary supply-expand-button" aria-expanded="${!!window._purchaseExpanded}" onclick="togglePurchasePanel(this)">Новый заказ</button></div>
  <div id="purchase-expand-panel" class="supply-layout supply-focused" ${window._purchaseExpanded?'':'hidden'}><div class="card"><div class="field"><label for="po-supplier">Поставщик</label><select id="po-supplier" onchange="selectPurchaseOrderSupplier(this.value)"><option value="">Выберите поставщика</option>${state.suppliers.map(s=>`<option value="${escapeAttr(s.id)}" ${s.id===supplier?.id?'selected':''}>${escapeHtml(s.name)}</option>`).join('')}</select></div>
  ${products.map(p=>{const d=state.purchaseOrderCart.find(x=>x.productId===p.id)||{},u=d.requestedUnit??p.purchaseUnit??stockUnit(p),size=d.packSize??p.purchasePackSize??'',content=d.contentUnit??p.purchaseContentUnit??stockUnit(p);return `<div class="supply-request" data-purchase-row="${escapeAttr(p.id)}" oninput="updatePurchaseRequest(${escapeAttr(JSON.stringify(p.id))})" onchange="updatePurchaseRequest(${escapeAttr(JSON.stringify(p.id))})"><div class="supply-product"><strong>${escapeHtml(p.name)}</strong><small>Остаток: ${stockQtyText(p.stock)} ${unitLabel(stockUnit(p))}${p.minStock!=null?' · Минимум: '+stockQtyText(p.minStock):''}</small></div><div class="supply-request-fields"><div class="field"><label>Количество</label><input data-request-qty type="text" readonly inputmode="none" role="button" onclick="openPurchaseQuantity(${escapeAttr(JSON.stringify(p.id))})" value="${escapeAttr(String(d.requestedQty??d.qty??''))}" placeholder="0"></div><div class="field"><label>Единица заказа</label><select data-request-unit>${purchaseUnitOptions(p,u)}</select></div><div class="field" data-pack-field ${['bottle','pack','box'].includes(u)?'':'hidden'}><label>В единице</label><input data-request-size type="text" readonly inputmode="none" role="button" onclick="openPurchaseQuantity(${escapeAttr(JSON.stringify(p.id))},'size')" value="${escapeAttr(String(size))}" placeholder="0"></div><div class="field" data-pack-field ${['bottle','pack','box'].includes(u)?'':'hidden'}><label>Единица внутри</label><select data-content-unit>${productUnitOptions(stockUnit(p),content)}</select></div></div><small data-request-note class="pe-note"></small></div>`;}).join('')}
  ${supplier&&!products.length?'<p class="pe-note">Закрепите товары за поставщиком во вкладке «Поставщики».</p>':''}
  <div class="supply-order-footer"><button id="po-create" class="btn btn-primary" onclick="finalizePurchaseOrder()" ${state.purchaseOrderCart.length?'':'disabled'}>Сформировать заказ</button></div></div>
</div></div></div>`;
}

function togglePurchasePanel(button){
  window._purchaseExpanded=!window._purchaseExpanded;
  document.getElementById('purchase-expand-panel').hidden=!window._purchaseExpanded;
  button.setAttribute('aria-expanded',String(window._purchaseExpanded));
}

function getPurchaseOrderSupplier(){return state.suppliers.find(s=>s.id===state.purchaseOrderSupplierId)||null;}

function purchaseOrderText(order){
  const lines=[];
  lines.push(`Заказ поставщику: ${order.supplierName}`);
  lines.push(`Дата: ${fmtDate(order.timestamp)}`);
  lines.push('');
  lines.push(...order.items.map((i,idx)=>`${idx+1}. ${i.productName} — ${requestedQuantityText(i)}`));
  lines.push('');
  lines.push('Просьба подтвердить наличие и сроки поставки.');
  return lines.join('\n');
}
function selectPurchaseOrderSupplier(id){
  if(state.purchaseOrderCart.length&&id!==state.purchaseOrderSupplierId){flash('Сначала завершите текущий заказ');render();return;}
  state.purchaseOrderSupplierId=id||'';
  state.purchaseOrderCart=[];
  render();
}
function viewPurchaseOrder(id){
  const order=state.purchaseOrders.find(o=>o.id===id);if(!order)return;
  const deletedLabel=order.status==='deleted'?`<div class="badge deleted-admin supply-deleted-label">Удалено администратором</div>`:'';
  const text=purchaseOrderText(order),safeId=escapeAttr(JSON.stringify(order.id));
  showModal(`
    <div class="modal-title">Заказ поставщику</div>${deletedLabel}
    <div class="supply-modal-meta"><strong>${escapeHtml(order.supplierName)}</strong><br>${fmtDate(order.timestamp)}</div>
    <pre id="purchase-order-text" class="purchase-order-copy">${escapeHtml(text)}</pre>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="copyPurchaseOrder(${safeId})">Скопировать текст</button>
      <button class="btn btn-secondary" onclick="sharePurchaseOrder(${safeId})">Поделиться</button>
      <button class="btn btn-primary" onclick="closeModal()">Готово</button>
    </div>`,true);
}
async function copyPurchaseOrder(id){
  const order=state.purchaseOrders.find(o=>o.id===id);if(!order)return;
  const text=purchaseOrderText(order);
  try{await navigator.clipboard.writeText(text);}
  catch(e){const ta=document.createElement('textarea');ta.value=text;ta.className='clipboard-copy-buffer';document.body.appendChild(ta);ta.select();document.execCommand('copy');ta.remove();}
  flash('Текст заказа скопирован');
}
function sharePurchaseOrder(id){
  const order=state.purchaseOrders.find(o=>o.id===id);if(!order)return;
  const payload={
    id:order.id,
    supplierName:order.supplierName||'Поставщик',
    timestamp:order.timestamp||Date.now(),
    items:(order.items||[]).map(i=>({productName:i.productName||'',qty:Number(i.requestedQty??i.qty)||0,quantityText:requestedQuantityText(i)})),
    company:{legalName:state.company?.legalName||'',address:state.company?.address||'',deliveryAddress:state.company?.deliveryAddress||''}
  };
  if(window.webkit?.messageHandlers?.printer){window.webkit.messageHandlers.printer.postMessage({action:'sharePurchaseOrder',order:payload});return;}
  const text=purchaseOrderText(order);
  if(navigator.share)navigator.share({title:`Заказ поставщику — ${order.supplierName}`,text}).catch(()=>{});
  else copyPurchaseOrder(id);
}
