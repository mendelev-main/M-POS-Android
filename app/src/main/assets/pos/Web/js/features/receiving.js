function invoiceReceivingItems(d){
  if(!d.lines.length)throw new Error('Добавьте товары');
  return d.lines.map(line=>{
    const p=getProduct(line.productId);if(!p||p.type!=='simple')throw new Error('Товар удалён или изменил тип');
    if(line.qtyInput===''||line.totalInput==='')throw new Error('Заполните количество и сумму каждой строки');
    const invoiceQty=Number(line.qtyInput),total=Number(line.totalInput);
    if(!Number.isFinite(invoiceQty)||invoiceQty<0||!Number.isFinite(total)||total<0||(invoiceQty===0&&total!==0))throw new Error('Проверьте количество и сумму: '+p.name);
    const isPack=['bottle','pack','box'].includes(line.unit);
    let qty;
    if(isPack){const size=Number(line.packSize);if(!Number.isFinite(size)||size<=0)throw new Error('Укажите количество внутри: '+p.name);qty=convertProductQty(invoiceQty*size,line.contentUnit||stockUnit(p),stockUnit(p));}
    else qty=convertProductQty(invoiceQty,line.unit,stockUnit(p));
    return {productId:p.id,productName:p.name,qty,unitCost:qty>0?total/qty:0,totalCost:total,stockUnit:stockUnit(p),invoiceQty,invoiceUnit:line.unit,packSize:isPack?Number(line.packSize):null,contentUnit:line.contentUnit||stockUnit(p),invoiceUnitPrice:invoiceQty>0?total/invoiceQty:0};
  });
}

function receivingStockUpdates(items){
  const updates=new Map();
  for(const item of items){
    const p=getProduct(item.productId);if(!p||p.type!=='simple')throw new Error('Товар приёмки недоступен');
    if(!Number.isFinite(item.qty)||item.qty<0||!Number.isFinite(item.totalCost)||item.totalCost<0)throw new Error('Некорректная строка приёмки');
    const old=updates.get(p.id)||{id:p.id,stock:Number(p.stock)||0,cost:Number(p.cost)||0};
    if(item.qty>0){
      const oldQty=Math.max(0,old.stock),newQty=oldQty+item.qty;
      const cost=p.noStockTracking?item.totalCost/item.qty:(oldQty*old.cost+item.totalCost)/newQty;
      if(!Number.isFinite(cost)||!Number.isFinite(old.stock+item.qty))throw new Error('Количество или стоимость слишком велики');
      updates.set(p.id,{id:p.id,stock:p.noStockTracking?old.stock:old.stock+item.qty,cost});
    }
  }
  return [...updates.values()];
}

function receivingDiscrepancy(order,items){
  if(!order)return false;
  return order.items.some(i=>{
    const expected=i.expectedQty===null?null:Number(i.expectedQty??i.qty),actual=items.filter(x=>x.productId===i.productId).reduce((s,x)=>s+x.qty,0);
    return expected===null?actual===0:Math.abs(actual-expected)>Number.EPSILON*16*Math.max(1,actual,expected);
  });
}

function confirmReceivingDocument(){
  if(window._receivingSaving)return false;
  receivingDocumentInput();const d=window._receivingDraft,order=d.orderId?state.purchaseOrders.find(o=>o.id===d.orderId):null;
  if(d.orderId&&(!order||['received','deleted'].includes(order.status))){flash('Заказ уже принят или удалён');return false;}
  try{
    const items=invoiceReceivingItems(d),updates=receivingStockUpdates(items);
    if(!items.some(i=>i.qty>0))throw new Error('Нет полученных товаров. Сохраните черновик, если поставка ещё ожидается');
    window._receivingPending={draft:JSON.parse(JSON.stringify(d)),items,updates,orderId:order?.id||null};
    const total=items.reduce((s,i)=>s+i.totalCost,0);
    showModal(`<div class="modal-title">Подтвердить приёмку?</div><p class="pe-note">${items.length} строк · ${money(total)}. Фактическое количество зачислится на склад, себестоимость пересчитается. ${order?'Заказ будет закрыт; недопоставленные позиции автоматически не переносятся.':''}</p><div class="supply-preview">${updates.map(u=>`<div>${escapeHtml(getProduct(u.id).name)}<b>${u.stock} ${unitLabel(stockUnit(getProduct(u.id)))} · ${Number(u.cost.toPrecision(8))} ${escapeHtml(state.currency)}/ед.</b></div>`).join('')}</div><div class="modal-actions"><button class="btn btn-secondary" onclick="renderReceivingDocument()">Вернуться к приёмке</button><button class="btn btn-primary" onclick="applyReceivingDocument()">Подтвердить приход</button></div>`,true);
    const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.onclick=null;
    return true;
  }catch(e){flash(e.message);return false;}
}

async function applyReceivingDocument(){
  const pending=window._receivingPending;if(!pending||window._receivingSaving)return false;
  if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
  const order=pending.orderId?state.purchaseOrders.find(o=>o.id===pending.orderId):null;
  if(pending.orderId&&(!order||['received','deleted'].includes(order.status))){flash('Заказ уже принят или удалён');return false;}
  try{
    // Recalculate against current stock, not the preview: POS may have sold items meanwhile.
    const items=invoiceReceivingItems(pending.draft),updates=receivingStockUpdates(items),now=Date.now(),shortage=receivingDiscrepancy(order,items);
    window._receivingSaving=true;
    criticalOperationBusy=true;
    const nextProducts=storageSnapshot(state.products),nextPurchaseOrders=storageSnapshot(state.purchaseOrders),nextReceivings=storageSnapshot(state.receivings);
    for(const u of updates){const p=nextProducts.find(product=>product.id===u.id);if(!p)throw new Error('Товар приёмки недоступен');p.stock=roundStockQty(u.stock);p.cost=u.cost;}
    const supplier=state.suppliers.find(s=>s.id===pending.draft.supplierId);
    const record={id:uid(),type:order?'purchaseOrder':'purchase',purchaseOrderId:order?.id||'',supplierId:order?.supplierId||supplier?.id||'',supplierName:order?.supplierName||supplier?.name||'',items,totalCost:Math.round(items.reduce((s,i)=>s+i.totalCost,0)*100)/100,shortage,invoiceNumber:pending.draft.invoiceNumber.trim(),invoiceDate:pending.draft.invoiceDate,timestamp:now};
    if(order){const nextOrder=nextPurchaseOrders.find(item=>item.id===order.id);if(!nextOrder)throw new Error('Заказ недоступен');nextOrder.status='received';nextOrder.receivedAt=now;nextOrder.receivedItems=items;nextOrder.shortage=shortage;nextOrder.receivingIncomplete=false;delete nextOrder.receivingDraft;delete nextOrder.receivingDraftV2;}
    nextReceivings.push(record);
    const writes={products:nextProducts,purchaseOrders:nextPurchaseOrders,receivings:nextReceivings};if(!order)writes.receivingDraft=null;
    await commitCriticalStorage('receiving',writes);
    state.products=nextProducts;state.purchaseOrders=nextPurchaseOrders;state.receivings=nextReceivings;
    void publishAvailability();
    if(!order)state.receivingCart=[];
    window._receivingPending=null;window._receivingDraft=null;window._receivingExpanded=false;finishReceivingPage();closeModal();render();flash('Приёмка подтверждена, себестоимость обновлена');
    return true;
  }catch(e){flash('Приёмка не выполнена: '+e.message);return false;}
  finally{window._receivingSaving=false;criticalOperationBusy=false;}
}

function openPurchaseOrderReceivingModal(id){return openReceivingDocument(id);}
function finalizePurchaseOrderReceiving(id){if(window._receivingDraft?.orderId===id)return confirmReceivingDocument();return false;}
function finalizeReceiving(){return openReceivingDocument();}
