function productDependsOn(productId, targetId, seen=new Set()){
  if(productId===targetId) return true;
  if(seen.has(productId)) return false;
  seen.add(productId);
  const p=getProduct(productId);
  if(!p || p.type!=='composite') return false;
  return (p.components||[]).some(c=>productDependsOn(c.productId,targetId,seen));
}

async function saveProduct(editingId){
  const name = document.getElementById('pf-name').value.trim();
  const category = document.getElementById('pf-category').value.trim();
  const price = parseFloat(document.getElementById('pf-price').value) || 0;
  const tileSymbol = limitTileSymbol(document.getElementById('pf-symbol')?.value||'');
  const noStockTracking = !!document.getElementById('pf-no-stock')?.checked;
  const canEditOnline = canEditProductWebSetting();
  const onlineDescription = document.getElementById('pf-description')?.value.trim()||'';
  if(!name){ flash('Введите название'); return; }
  const type = window._pmType;
  if(type === 'composite' && window._pmComponents.length === 0){ flash('Добавьте хотя бы один товар в состав'); return; }
  if(type==='composite'){
    const draft={id:editingId||null,name,type,components:window._pmComponents,recipeYield:Number(document.getElementById('pf-recipe-yield')?.value||window._pmRecipeYield||1),recipeUnit:document.getElementById('pf-recipe-unit')?.value||window._pmRecipeUnit||'piece'};
    try{ productIngredients(draft); }catch(e){ flash(e.message); return; }
  }
  if(editingId && getProduct(editingId)?.type!==type && hasUnreturnedStockConsumption(editingId)){
    flash('Нельзя изменить тип: товар нужен для возврата ранее проданных чеков'); return;
  }
  const existing=getProduct(editingId);
  if(existing && existing.type!==type && (existing.stockUnit || state.products.some(p=>p.id!==existing.id && productDependsOn(p.id,existing.id)))){flash('Тип товара с единицами или связями нельзя менять: создайте отдельный товар');return;}
  const btn = document.querySelector('#modal-root .modal-actions .btn-primary');
  if(btn){ btn.disabled=true; btn.textContent='Сохраняем…'; }
  try{
    let p;
    const originalProduct=editingId?JSON.stringify(getProduct(editingId)):null;
    if(editingId){
      p = JSON.parse(originalProduct);
      p.name = name; p.category = category; p.price = price; p.type = type;
      p.tileSymbol = tileSymbol;
      p.noStockTracking = noStockTracking;
      p.availableOnline = canEditOnline ? window._pmOnline !== false : getProduct(editingId)?.availableOnline !== false;
      p.availableInOnlineMenu = canEditOnline ? window._pmOnlineMenu !== false : getProduct(editingId)?.availableInOnlineMenu !== false;
      if(canEditOnline)p.description = onlineDescription;
      if(window._pmRemoveImage) p.imageUrl = '';
      if(type==='simple'){
        p.cost = parseFloat(document.getElementById('pf-cost').value) || 0;
        p.stock = parseFloat(document.getElementById('pf-stock').value) || 0;
        delete p.components;
      } else {
        p.components = window._pmComponents;
        p.recipeYield=Number(document.getElementById('pf-recipe-yield')?.value||window._pmRecipeYield||1);p.recipeUnit=document.getElementById('pf-recipe-unit')?.value||window._pmRecipeUnit||'piece';p.recipeYieldManual=!!window._pmRecipeYieldManual;
        if(!Number.isFinite(p.recipeYield)||p.recipeYield<=0)throw new Error('Выход состава должен быть больше нуля');
        delete p.cost; delete p.stock;
      }
    } else {
      const catKey = category.trim() || 'Без категории';
      const siblingOrders = state.products.filter(x=>productCategoryKey(x)===catKey).map(x=>x.sortOrder||0);
      const nextOrder = siblingOrders.length ? Math.max(...siblingOrders)+1 : 0;
      p = {id: uid(), name, category, price, type, tileSymbol, noStockTracking, sortOrder: nextOrder, availableOnline: canEditOnline ? window._pmOnline !== false : false, availableInOnlineMenu: canEditOnline ? window._pmOnlineMenu !== false : false, description:canEditOnline?onlineDescription:'', imageUrl:''};
      if(type==='simple'){
        p.cost = parseFloat(document.getElementById('pf-cost')?.value) || 0;
        p.stock = parseFloat(document.getElementById('pf-stock')?.value) || 0;
      } else {
        p.components = window._pmComponents;
        p.recipeYield=Number(document.getElementById('pf-recipe-yield')?.value||window._pmRecipeYield||1);p.recipeUnit=document.getElementById('pf-recipe-unit')?.value||window._pmRecipeUnit||'piece';p.recipeYieldManual=!!window._pmRecipeYieldManual;
        if(!Number.isFinite(p.recipeYield)||p.recipeYield<=0)throw new Error('Выход состава должен быть больше нуля');
      }
    }
    validateModifierGroups(window._pmModifierGroups||[],editingId||p.id);
    p.modifierGroups=(window._pmModifierGroups||[]).map(normalizeModifierGroup);
    saveProductConfiguration(p);
    if(editingId && p.type==='simple')p.cost=Number(getProduct(editingId)?.type==='simple'?getProduct(editingId).cost:compositeCost(getProduct(editingId)))||0;
    if(p.type==='simple'){
      const purchaseUnit=document.getElementById('pf-purchase-unit'),packSize=document.getElementById('pf-pack-size'),contentUnit=document.getElementById('pf-purchase-content-unit');
      if(purchaseUnit){p.purchaseUnit=purchaseUnit.value;p.purchaseContentUnit=contentUnit?.value||p.stockUnit;const raw=packSize?.value||'';if(raw!==''){if(!Number.isFinite(Number(raw))||Number(raw)<=0)throw new Error('Объём упаковки должен быть больше нуля');p.purchasePackSize=Number(raw);}else delete p.purchasePackSize;}
    }
    const previousLocalImageId=editingId?getProduct(editingId)?.localImageId||'':'',selectedLocalImageId=window._pmLocalImageId||'';
    if(selectedLocalImageId){p.localImageId=selectedLocalImageId;p.imageUploadPending=true}
    if(window._pmRemoveImage){delete p.localImageId;delete p.imageUploadPending}
    if(editingId && JSON.stringify(getProduct(editingId))!==originalProduct)throw new Error('Товар изменился во время сохранения. Откройте карточку заново');
    const nextProducts=storageSnapshot(state.products);if(editingId)nextProducts[nextProducts.findIndex(x=>x.id===editingId)]=p;else nextProducts.push(p);
    await window.PrilavokCore.Storage.set('products',nextProducts);state.products=nextProducts;window._pmLocalImageId=null;syncCategoryOrder();void publishAvailability();
    if(previousLocalImageId && (window._pmRemoveImage || (selectedLocalImageId&&selectedLocalImageId!==previousLocalImageId)))window.webkit?.messageHandlers?.photoPicker?.postMessage({action:'remove',id:previousLocalImageId});
    let imageData=window._pmImageData;
    if(!imageData&&p.imageUploadPending&&p.localImageId)imageData=await readNativeProductImage(p.localImageId);
    let photoError='';
    if(imageData){
      try{
        const n=networkConfigFromState();if(!n.backendUrl || !n.deviceKey)throw new Error('нет подключения к backend');
        const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),10000);
        let r;try{r=await fetch(n.backendUrl.replace(/\/+$/,'')+'/api/media/upload',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':n.deviceKey},body:JSON.stringify({productId:p.id,dataUrl:imageData}),cache:'no-store',signal:controller.signal})}finally{clearTimeout(timeout)}
        const d=await r.json().catch(()=>null);if(!r.ok)throw new Error(r.status===413?'фото слишком большое для сервера':(d?.error||('HTTP '+r.status)));
        const latestProducts=storageSnapshot(state.products),latest=latestProducts.find(x=>x.id===p.id);if(latest&&latest.localImageId===p.localImageId){latest.imageUrl=d.url||'';delete latest.imageUploadPending;await window.PrilavokCore.Storage.set('products',latestProducts);state.products=latestProducts}
      }catch(e){photoError=e?.name==='AbortError'?'backend не ответил за 10 секунд':(e?.message||'ошибка upload')}
    }
    closeModal();render();flash(photoError?'Товар сохранён локально. Фото ожидает повторной загрузки: '+photoError:'Товар сохранён');
    return true;
  }catch(e){
    if(btn){ btn.disabled=false; btn.textContent='Сохранить'; }
    flash('Не удалось сохранить товар: '+(e?.message||'ошибка'));
  }
}

function canEditProductWebSetting(){
  return currentShiftEmployeeIsAdmin();
}
function toggleProductOnline(id){
  if(!canEditProductWebSetting()){
    flash('Настройку WEB может изменять только администратор при открытой им смене');
    return;
  }
  const p=getProduct(id); if(!p) return;
  p.availableOnline = p.availableOnline===false;
  saveKey('products',state.products); render();
  flash(p.availableOnline?'Товар доступен для онлайн-заказа':'Товар недоступен для онлайн-заказа');
}
function toggleProductModalOnline(){
  if(!canEditProductWebSetting()){
    flash('Настройку WEB может изменять только администратор при открытой им смене');
    return;
  }
  window._pmOnline = !window._pmOnline;
  const b=document.getElementById('pf-web-toggle');
  if(b){ b.classList.toggle('on',window._pmOnline); b.setAttribute('aria-checked',String(window._pmOnline)); }
}
function toggleProductModalMenu(){
  if(!canEditProductWebSetting()){
    flash('Настройку WEB может изменять только администратор при открытой им смене');
    return;
  }
  window._pmOnlineMenu = !window._pmOnlineMenu;
  const b=document.getElementById('pf-menu-toggle');
  if(b){ b.classList.toggle('on',window._pmOnlineMenu); b.setAttribute('aria-checked',String(window._pmOnlineMenu)); }
}
