(function(global){
  'use strict';
  if(!global.MPosCore?.CatalogEdit)return;
  const enabled=()=>global.MPosNativeCatalogEditEnabled!==false;
  const keys=['products','categoryOrder','categoryColors','categorySymbols','categoryOnlineMenu','categoryOnlineOrder','layoutTiles','posNavigation'];
  const snapshot=()=>Object.fromEntries(keys.map(k=>[k,state[k]]));
  const stamp=()=>JSON.stringify([snapshot(),state.orders]);
  let pending=false;
  const busy=()=>global.MPosCore.NativeNavigation?.hasPending()||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.OrderContext?.hasPending()||global.MPosCore.CartOperations?.hasPending()||global.MPosCore.SplitPayments?.hasPending();
  const root=()=>document.getElementById('modal-root');
  const value=id=>document.getElementById(id)?.value||'';
  const form=()=>JSON.stringify([Array.from(root()?.querySelectorAll?.('input,select,textarea')||[],n=>[n.id,n.value,n.checked]),global._pmEditingId,global._pmType,global._pmComponents,global._pmModifierGroups,global._cmOnlineMenu,global._cmOnlineOrder]);
  async function decide(request,apply){
    if(pending||busy()){flash('Дождитесь завершения изменения данных');return;}
    pending=true;const before=stamp(),modal=root(),fields=form(),generation=global.MPosCore.OrderContext?.generation();
    const stale=()=>!enabled()||busy()||generation!==global.MPosCore.OrderContext?.generation()||before!==stamp()||modal!==root()||fields!==form();
    try{const result=await global.MPosCore.CatalogEdit.calculate(JSON.parse(JSON.stringify(request)));
      if(stale())return;
      if(typeof result?.allowed!=='boolean')throw Error('invalid catalog decision');
      if(!result.allowed){if(result.message)flash(result.message);return;}
      return await apply(result);
    }catch(error){if(!stale())flash('Не удалось проверить изменения: '+(error?.message||'ошибка'));}
    finally{pending=false;}
  }
  const originalSave=global.saveProduct;
  if(typeof originalSave==='function')global.saveProduct=function(editingId){
    if(!enabled())return originalSave.apply(this,arguments);
    // Until 081, preserve recipe-error precedence before type-change policy.
    const type=global._pmType,components=global._pmComponents||[];
    if(type==='composite'&&components.length&&value('pf-name').trim()){
      try{productIngredients({id:editingId||null,name:value('pf-name').trim(),type,components});}catch(error){flash(error.message);return;}
    }
    return decide({version:1,operation:'product',editingId:editingId??null,name:value('pf-name'),type,components},async()=>{if(global.MPosCore.ProductRecipes?.enabled()&&!await global.MPosCore.ProductRecipes.prepareModifiers(editingId))return;return originalSave(editingId);});
  };
  const originalCategory=global.saveCategory,originalChannel=global.toggleCategoryChannel,originalDelete=global.deleteCategory;
  function patch(result){
    const next=result.state;if(!next||keys.some(k=>!Object.prototype.hasOwnProperty.call(next,k)))throw Error('invalid category patch');
    for(const k of ['products','layoutTiles']){if(!Array.isArray(next[k])||next[k].length!==state[k].length||next[k].some(row=>!row||typeof row!=='object'||Array.isArray(row)))throw Error('invalid category rows');}
    if(!Array.isArray(next.categoryOrder)||next.categoryOrder.some(v=>typeof v!=='string'))throw Error('invalid category order');
    for(const k of ['categoryColors','categorySymbols','categoryOnlineMenu','categoryOnlineOrder'])if(!next[k]||typeof next[k]!=='object'||Array.isArray(next[k]))throw Error('invalid category map');
    if(result.navigationChanged&&(!Array.isArray(next.posNavigation?.categories)||next.posNavigation.categories.length!==state.posNavigation.categories.length||next.posNavigation.categories.some(row=>!row||typeof row!=='object')))throw Error('invalid navigation patch');
    // Preserve references used by editors and categoryOnline compatibility alias.
    for(const k of ['products','layoutTiles'])for(let i=0;i<state[k].length;i++)Object.assign(state[k][i],next[k][i]);
    state.categoryOrder.splice(0,state.categoryOrder.length,...next.categoryOrder);
    for(const k of ['categoryColors','categorySymbols','categoryOnlineMenu','categoryOnlineOrder']){for(const key of Object.keys(state[k]))delete state[k][key];Object.assign(state[k],next[k]);}
    if(result.navigationChanged){const rows=state.posNavigation.categories;for(let i=0;i<rows.length;i++)Object.assign(rows[i],next.posNavigation.categories[i]);}
    state.categoryOnline=state.categoryOnlineOrder;
  }
  if(typeof originalCategory==='function')global.saveCategory=function(oldName){
    if(!enabled())return originalCategory.apply(this,arguments);
    return decide({version:1,operation:'save',state:snapshot(),oldName:oldName??null,name:value('cf-name'),color:value('cf-color')||'#EEF1F5',symbol:limitTileSymbol(value('cf-symbol')),menu:global._cmOnlineMenu,order:global._cmOnlineOrder},result=>{
      if(typeof result.adding!=='boolean'||typeof result.navigationChanged!=='boolean')throw Error('invalid category flags');
      patch(result);if(result.navigationChanged)saveKey('posNavigation',state.posNavigation);if(!result.adding)saveKey('products',state.products);saveKey('layout',categoryLayoutSnapshot());closeModal();render();flash(result.adding?'Категория добавлена':'Категория изменена');
    });
  };
  if(typeof originalChannel==='function')global.toggleCategoryChannel=function(name,channel){
    if(!enabled())return originalChannel.apply(this,arguments);
    return decide({version:1,operation:'channel',state:snapshot(),name,channel},result=>{
      if(typeof result.enabled!=='boolean')throw Error('invalid channel flag');
      patch(result);saveKey('layout',categoryLayoutSnapshot());openCategoriesModal();flash(result.enabled?(channel==='menu'?'Категория добавлена в онлайн-меню':'Категория доступна для онлайн-заказа'):(channel==='menu'?'Категория скрыта из онлайн-меню':'Категория недоступна для онлайн-заказа'));
    });
  };
  if(typeof originalDelete==='function')global.deleteCategory=function(name){
    if(!enabled())return originalDelete.apply(this,arguments);
    return decide({version:1,operation:'deleteCheck',state:snapshot(),name},()=>requestDelete('category',name));
  };
})(window);
