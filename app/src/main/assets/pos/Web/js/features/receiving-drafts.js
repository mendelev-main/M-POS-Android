function receivingDraftForOrder(order,orderId){
  const saved=order?.receivingDraftV2;
  const draft=saved?storageSnapshot(saved):{version:1,orderId:orderId||null,supplierId:order?.supplierId||'',invoiceNumber:'',invoiceDate:'',lines:(order?.items||state.receivingCart||[]).map(i=>({productId:i.productId,qtyInput:order?.receivingDraft?.[i.productId]?.qty??(!order?i.qty:(i.expectedQty===null?'':(i.requestedQty??i.expectedQty??i.qty??''))),unit:order?(i.requestedUnit??i.stockUnit??stockUnit(getProduct(i.productId))):(i.stockUnit??stockUnit(getProduct(i.productId))),packSize:order?(i.packSize??''):'',contentUnit:order?(i.contentUnit??i.stockUnit??stockUnit(getProduct(i.productId))):stockUnit(getProduct(i.productId)),priceInput:'',totalInput:order?.receivingDraft?.[i.productId]?.totalCost??(!order?i.totalCost:''),priceMode:'total'}))};
  draft.orderId=orderId||null;
  return draft;
}

async function openReceivingDocument(orderId=null){
  if(window._receivingDraftOpening)return false;
  const order=orderId?state.purchaseOrders.find(o=>o.id===orderId):null;
  if(orderId&&(!order||['received','deleted'].includes(order.status))){flash('Заказ недоступен для приёмки');return false;}
  window._receivingDraftOpening=true;
  let attemptedStorage=false;
  try{
    if(!order){
      const saved=await loadKey('receivingDraft',null);
      const draft=saved?storageSnapshot(saved):receivingDraftForOrder(null,null);
      draft.orderId=null;
      window._receivingDraft=draft;
      renderReceivingDocument();
      return true;
    }

    const draft=receivingDraftForOrder(order,orderId);
    if(!order.receivingDraftV2 || !order.receivingIncomplete){
      const nextPurchaseOrders=storageSnapshot(state.purchaseOrders);
      const nextOrder=nextPurchaseOrders.find(item=>item.id===orderId);
      if(!nextOrder||['received','deleted'].includes(nextOrder.status))throw new Error('Заказ недоступен');
      nextOrder.receivingDraftV2=storageSnapshot(draft);
      nextOrder.receivingIncomplete=true;
      attemptedStorage=true;
      await window.PrilavokCore.Storage.set('purchaseOrders',nextPurchaseOrders);
      state.purchaseOrders=nextPurchaseOrders;
    }
    window._receivingPageScroll=document.querySelector('.screen.active')?.scrollTop||0;
    window._receivingDraft=draft;
    renderReceivingDocument();
    return true;
  }catch(e){
    if(attemptedStorage)markStorageBroken(e);
    flash('Не удалось сохранить начало приёмки: '+(e?.message||'ошибка сохранения'));
    return false;
  }finally{
    window._receivingDraftOpening=false;
  }
}

async function saveReceivingDraft(){
  if(window._receivingDraftSaveBusy||!window._receivingDraft)return false;
  receivingDocumentInput();
  const draft=storageSnapshot(window._receivingDraft);
  window._receivingDraftSaveBusy=true;
  let attemptedStorage=false;
  const buttons=[...(document.querySelectorAll?.('[data-receiving-draft-save]')||[])];buttons.forEach(button=>{button.disabled=true;});
  try{
    if(draft.orderId){
      const nextPurchaseOrders=storageSnapshot(state.purchaseOrders);
      const nextOrder=nextPurchaseOrders.find(o=>o.id===draft.orderId);
      if(!nextOrder||['received','deleted'].includes(nextOrder.status))throw new Error('Заказ недоступен');
      nextOrder.receivingDraftV2=storageSnapshot(draft);
      nextOrder.receivingIncomplete=true;
      attemptedStorage=true;
      await window.PrilavokCore.Storage.set('purchaseOrders',nextPurchaseOrders);
      state.purchaseOrders=nextPurchaseOrders;
    }else{
      attemptedStorage=true;
      await window.PrilavokCore.Storage.set('receivingDraft',draft);
      window._receivingDraft=draft;
    }
    window._receivingExpanded=false;
    if(draft.orderId){finishReceivingPage();window._receivingDraft=null;}
    closeModal();render();flash('Черновик сохранён локально');
    return true;
  }catch(e){
    if(attemptedStorage)markStorageBroken(e);
    flash('Не удалось сохранить черновик: '+(e?.message||'ошибка сохранения'));
    buttons.forEach(button=>{button.disabled=false;});
    return false;
  }finally{
    window._receivingDraftSaveBusy=false;
  }
}
