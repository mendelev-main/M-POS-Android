function openProductModal(id){
  const editing=id?getProduct(id):null;
  if(id && !editing){flash('Товар не найден');return;}
  window._pmReturnScroll=document.querySelector('.screen.active')?.scrollTop||0;
  window._pmReturnFocus=document.activeElement;
  window._pmComponents=JSON.parse(JSON.stringify(editing?.components||[]));
  window._pmModifierGroups=JSON.parse(JSON.stringify(editing?.modifierGroups||[]));
  window._pmComponentCategory='Все';window._pmSearch='';
  window._pmStockUnlocked=currentShiftEmployeeIsAdmin();
  window._pmNoStockUnlocked=window._pmStockUnlocked;
  window._pmBaseUnit=editing?.stockUnit||'';window._pmUnit=editing?.stockDisplayUnit||window._pmBaseUnit;
  const factor=convertProductQty(1,window._pmBaseUnit,window._pmUnit);
  window._pmSimpleDraft={purchaseUnit:editing?.purchaseUnit??window._pmBaseUnit,packSize:editing?.purchasePackSize??'',contentUnit:editing?.purchaseContentUnit??window._pmBaseUnit,cost:editing?.cost==null?'':editing.cost/factor,stock:editing?.stock==null?'':convertProductQty(editing.stock,window._pmBaseUnit,window._pmUnit),minimum:editing?.minStock==null?'':convertProductQty(editing.minStock,window._pmBaseUnit,window._pmUnit),noStock:!!editing?.noStockTracking};
  window._pmType=editing?.type||'simple';window._pmEditingId=editing?.id||null;window._pmRecipeYield=editing?.recipeYield??1;window._pmRecipeUnit=editing?.recipeUnit||'piece';window._pmRecipeYieldManual=editing?.recipeYieldManual===true;
  window._pmImageData=null;window._pmLocalImageId=null;window._pmRemoveImage=false;window._pmOnline=editing?.availableOnline!==false;window._pmOnlineMenu=editing?.availableInOnlineMenu!==false;
  window._pmSaving=false;window._pmSession=(window._pmSession||0)+1;
  renderProductModal(editing,window._pmType,[],state.categoryOrder.slice());
  window._pmInitial=productEditorSnapshot();
  updateProductEditorSummary();
  document.getElementById('pe-back')?.focus();
}
function productEditorSnapshot(){
  const value=id=>document.getElementById(id)?.value??'';
  return JSON.stringify({purchaseUnit:value('pf-purchase-unit'),packSize:value('pf-pack-size'),purchaseContentUnit:value('pf-purchase-content-unit'),sku:value('pf-sku'),note:value('pf-note'),unit:window._pmUnit,minimum:value('pf-min-stock'),name:value('pf-name'),category:value('pf-category'),symbol:value('pf-symbol'),price:value('pf-price'),type:window._pmType,
    cost:window._pmType==='simple'?value('pf-cost'):null,stock:window._pmType==='simple'?value('pf-stock'):null,
    noStock:window._pmType==='simple'?!!document.getElementById('pf-no-stock')?.checked:false,
    components:window._pmType==='composite'?window._pmComponents:[],modifierGroups:window._pmModifierGroups||[],recipeYield:window._pmType==='composite'?Number(document.getElementById('pf-recipe-yield')?.value||window._pmRecipeYield||1):null,recipeUnit:window._pmType==='composite'?(document.getElementById('pf-recipe-unit')?.value||window._pmRecipeUnit||'piece'):null,onlineOrder:window._pmOnline,onlineMenu:window._pmOnlineMenu,description:value('pf-description'),image:window._pmImageData,removeImage:window._pmRemoveImage});
}
function productEditorDirty(){return productEditorSnapshot()!==window._pmInitial;}
function finishProductEditor(nextId=null){
  if(window._pmLocalImageId)window.webkit?.messageHandlers?.photoPicker?.postMessage({action:'remove',id:window._pmLocalImageId});
  document.getElementById('product-editor-root').innerHTML='';
  document.getElementById('app').inert=false;
  const screen=document.querySelector('.screen.active');if(screen)screen.scrollTop=window._pmReturnScroll||0;
  window._pmComponents=[];window._pmModifierGroups=[];window._pmImageData=null;window._pmEditingId=null;window._pmExitAction=null;
  if(nextId){openProductModal(nextId);return;}
  if(window._pmReturnFocus?.isConnected)window._pmReturnFocus.focus();
  else document.getElementById('products-search')?.focus({preventScroll:true});
}
function requestCloseProductEditor(nextId=null){
  if(window._pmSaving)return;
  if(!productEditorDirty()){finishProductEditor(nextId);return;}
  window._pmExitAction=()=>finishProductEditor(nextId);
  showModal(`<div class="modal-title">Сохранить изменения?</div><div class="pe-note">В карточке есть несохранённые изменения.</div><div class="pe-exit-actions"><button class="btn btn-primary" onclick="saveProductEditor(true)">Сохранить и продолжить</button><button class="btn btn-secondary" onclick="const action=window._pmExitAction;closeModal();action&&action()">Не сохранять</button><button class="btn btn-outline" onclick="closeModal()">Остаться в карточке</button></div>`);
}
async function saveProductEditor(continueNavigation=false){
  if(window._pmSaving)return;
  window._pmSaving=true;
  const button=document.getElementById('pe-save');button.disabled=true;button.textContent='Сохранение…';
  document.getElementById('pe-back').disabled=true;
  const body=document.querySelector('.pe-layout');body.inert=true;
  closeModal();
  try{
    const saved=await saveProduct(window._pmEditingId||'');
    if(saved){
      const action=continueNavigation?window._pmExitAction:null;
      if(action)action();else finishProductEditor();
    }
  }catch(e){flash('Не удалось сохранить товар: '+(e.message||'ошибка'));}
  finally{
    window._pmSaving=false;
    if(button.isConnected){button.disabled=false;button.textContent='Сохранить';document.getElementById('pe-back').disabled=false;body.inert=false;}
  }
}
function selectProductEditorSection(section){
  document.querySelectorAll('[data-pe-panel]').forEach(el=>{el.hidden=el.dataset.pePanel!==section;});
  document.querySelectorAll('[data-pe-tab]').forEach(el=>{el.classList.toggle('selected',el.dataset.peTab===section);el.setAttribute('aria-pressed',String(el.dataset.peTab===section));});
  document.querySelector('.pe-content').scrollTop=0;
}
function renderProductUsage(id){
  if(!id)return '<div class="pe-empty">Связи появятся после сохранения товара.</div>';
  const parents=state.products.filter(p=>p.id!==id && productDependsOn(p.id,id));
  const receipts=state.orders.filter(o=>(o.items||[]).some(i=>i.productId===id)||o.stockConsumption?.items?.some(i=>i.productId===id)).length;
  const parked=state.parked.filter(o=>(o.items||[]).some(i=>i.productId===id)).length;
  return `<p class="pe-note">Связи сохранённого товара. Изменение ингредиента влияет на рецептуры, в которых он используется.</p>
    <div class="pe-section-heading"><h2>Входит в состав</h2><span class="pe-badge">${parents.length}</span></div>
    ${parents.length?parents.map(p=>`<button type="button" class="pe-usage-row" onclick="requestCloseProductEditor(${escapeAttr(JSON.stringify(p.id))})"><span><strong>${escapeHtml(p.name)}</strong><small>${(p.components||[]).some(c=>c.productId===id)?'Прямой состав':'Через другие составные товары'}</small></span><span aria-hidden="true">→</span></button>`).join(''):'<div class="pe-empty">Этот товар пока не входит в другие рецептуры.</div>'}
    <div class="pe-usage-facts"><div><strong>${receipts}</strong><span>Чеков с товаром или его списанием</span></div><div><strong>${parked}</strong><span>Отложенных заказов с товаром</span></div></div>`;
}
function updateProductEditorSummary(){
  if(!document.getElementById('pe-summary-price'))return;
  const price=Number(document.getElementById('pf-price')?.value)||0;
  const cost=window._pmType==='simple'?(Number(document.getElementById('pf-cost')?.value)||0)/convertProductQty(1,window._pmUnit||'',window._pmBaseUnit||''):(window._pmComponents||[]).reduce((sum,c)=>sum+compositeCost(getProduct(c.productId))*Number(c.qty||0),0);
  document.getElementById('pe-summary-price').textContent=fullMoney(price);
  document.getElementById('pe-summary-cost').textContent=fullMoney(cost);
  document.getElementById('pe-summary-difference').textContent=fullMoney(price-cost);
  const title=document.getElementById('pf-name')?.value.trim();document.getElementById('pe-title').textContent=title||'Новый товар';
  document.getElementById('pe-kind').textContent=window._pmType==='simple'?'Простой товар':'Составной товар';
  const status=document.getElementById('pe-draft-state');status.textContent=window._pmInitial && productEditorDirty()?'Есть изменения':'Карточка товара';
  const minimum=document.getElementById('pf-min-stock'),stock=document.getElementById('pf-stock');
  if(window._pmType==='simple' && minimum?.value!=='' && minimum && stock && !document.getElementById('pf-no-stock')?.checked && Number(stock.value)<=Number(minimum.value))status.textContent+=' · Низкий остаток';
}
function renderProductModal(editing, type, simpleProducts, catList){
  document.getElementById('app').inert=true;
  document.getElementById('product-editor-root').innerHTML=`<section class="product-editor" aria-label="Карточка товара">
    <header class="pe-header"><button id="pe-back" class="btn btn-outline pe-back" onclick="requestCloseProductEditor()">← Назад</button><div class="pe-heading"><div id="pe-draft-state" class="pe-eyebrow">Карточка товара</div><h1 id="pe-title">${escapeHtml(editing?.name||'Новый товар')}</h1></div><button id="pe-save" class="btn btn-primary" onclick="saveProductEditor()">Сохранить</button></header>
    <div class="pe-layout"><nav class="pe-nav" aria-label="Разделы карточки"><div id="pe-kind" class="pe-nav-caption">${type==='simple'?'Простой товар':'Составной товар'}</div>
      ${[['main','Основное'],['stock','Состав и остатки'],['modifiers','Модификаторы'],['usage','Где используется'],['web','Онлайн-меню']].map(([id,label])=>`<button aria-pressed="${id==='main'}" data-pe-tab="${id}" class="${id==='main'?'selected':''}" onclick="selectProductEditorSection('${id}')">${label}</button>`).join('')}
      <div class="pe-nav-bottom">${editing?`<button class="btn product-modal-delete" onclick="deleteProduct(${escapeAttr(JSON.stringify(editing.id))})">Удалить товар</button>`:''}</div></nav>
    <div class="pe-content" oninput="updateProductEditorSummary()" onchange="updateProductEditorSummary()">
      <div class="pe-workspace"><main>
      <section class="pe-card" data-pe-panel="main"><div class="pe-section-heading"><h2>Основная информация</h2><span class="pe-badge">${editing?'Редактирование':'Создание'}</span></div><p class="pe-note">Название и категория помогают быстро найти товар на кассе.</p>
        <div class="field"><label for="pf-name">Название товара</label><input id="pf-name" value="${escapeAttr(editing?.name||'')}" placeholder="Например, Пицца Маргарита" autocomplete="off"></div>
        <div class="pe-two-fields"><div class="field"><label for="pf-category">Категория</label><select id="pf-category"><option value="">Без категории</option>${catList.map(c=>`<option value="${escapeAttr(c)}" ${editing?.category===c?'selected':''}>${escapeHtml(c)}</option>`).join('')}</select></div><div class="field"><label for="pf-symbol">Обозначение на плитке</label><input id="pf-symbol" value="${escapeAttr(editing?.tileSymbol||'')}" placeholder="До 3 символов"></div></div>
        <div class="field"><label for="pf-sku">Артикул</label><input id="pf-sku" maxlength="80" value="${escapeAttr(editing?.sku||'')}" placeholder="Внутренний код товара"></div>
        <div class="field"><label for="pf-note">Внутренняя заметка</label><textarea id="pf-note" class="product-note-input" maxlength="2000" rows="3">${escapeHtml(editing?.internalNote||'')}</textarea><p class="pe-note">Для сотрудников. Не выводится в чек или онлайн-меню.</p></div>
        <div class="field"><label>Тип товара</label><div class="radio-row"><button type="button" class="radio-opt product-type-switch ${type==='simple'?'selected':''}" data-type="simple" onclick="switchProductType('simple')">Простой</button><button type="button" class="radio-opt product-type-switch ${type==='composite'?'selected':''}" data-type="composite" onclick="switchProductType('composite')">Составной</button></div><p class="pe-note">Простой товар хранится на складе. Составной расходует ингредиенты по рецептуре.</p></div>
        <div class="field"><label for="pf-price">Цена продажи, ${escapeHtml(state.currency)}</label><input id="pf-price" type="number" min="0" step="0.01" inputmode="decimal" value="${editing?.price??''}" placeholder="0,00"></div>
        <div class="pe-photo"><div id="pf-image-preview" class="product-image-preview">${editing?.localImageId?`<img src="mpos-image://${escapeAttr(editing.localImageId)}" alt="Фото товара">`:editing?.imageUrl?`<img src="${escapeAttr(editing.imageUrl)}" alt="Фото товара">`:'<div class="product-image-empty">Фото товара</div>'}</div><div><h3>Фото товара</h3><p class="pe-note">Изображение для онлайн-меню.</p><button class="btn btn-outline" onclick="pickProductImage()">Выбрать фото</button><button class="btn btn-outline" onclick="removeProductImage();updateProductEditorSummary()">Убрать фото</button></div><input id="pf-image-input" type="file" accept="image/*" hidden onchange="handleProductImage(this)"></div>
      </section>
      <section class="pe-card" data-pe-panel="stock" hidden><div class="pe-section-heading"><h2>Состав и остатки</h2></div><div id="pf-type-fields"></div></section>
      <section class="pe-card" data-pe-panel="modifiers" hidden>
        <div class="pe-section-heading"><h2>Модификаторы</h2><button type="button" class="btn btn-outline" onclick="addModifierGroup()">+ Группа</button></div>
        <p class="pe-note">Варианты связаны с существующими товарами и списываются с их остатков.</p>
        <div id="pf-modifier-groups">${renderModifierGroups()}</div>
      </section>
      <section class="pe-card" data-pe-panel="usage" hidden><div class="pe-section-heading"><h2>Где используется</h2></div>${renderProductUsage(editing?.id)}</section>
      <section class="pe-card" data-pe-panel="web" hidden><div class="pe-section-heading"><h2>Онлайн</h2></div>
        <div class="web-setting-row"><div><strong>Онлайн заказ</strong><p class="pe-note">Товар доступен для добавления в заказ на сайте.</p></div><button id="pf-web-toggle" type="button" role="switch" aria-label="Онлайн заказ" aria-checked="${window._pmOnline}" aria-disabled="${!canEditProductWebSetting()}" class="toggle-switch ${window._pmOnline?'on':''}" ${canEditProductWebSetting()?'':'disabled'} onclick="toggleProductModalOnline();updateProductEditorSummary()"><span></span></button></div>
        <div class="web-setting-row"><div><strong>Онлайн меню</strong><p class="pe-note">Товар отображается в меню для просмотра.</p></div><button id="pf-menu-toggle" type="button" role="switch" aria-label="Онлайн меню" aria-checked="${window._pmOnlineMenu}" aria-disabled="${!canEditProductWebSetting()}" class="toggle-switch ${window._pmOnlineMenu?'on':''}" ${canEditProductWebSetting()?'':'disabled'} onclick="toggleProductModalMenu();updateProductEditorSummary()"><span></span></button></div>
        <div class="field"><label for="pf-description">Описание для онлайн-платформы</label><textarea id="pf-description" class="product-note-input" maxlength="2000" rows="4" placeholder="Состав, вкус или особенности товара" ${canEditProductWebSetting()?'':'disabled'}>${escapeHtml(editing?.description||'')}</textarea><p class="pe-note">Описание появится в карточке товара на сайте.</p></div>
        <p class="pe-note">${canEditProductWebSetting()?'Изменения отправляются вручную: Настройки → Сетевые настройки → Синхронизировать меню.':'Настройки онлайн-платформы может изменять только администратор при открытой им смене.'}</p></section>
      </main><aside class="pe-summary"><div class="pe-price-block"><span>Цена продажи</span><strong id="pe-summary-price"></strong></div><div class="pe-summary-row"><span>Себестоимость</span><strong id="pe-summary-cost"></strong></div><div class="pe-summary-row"><span>Цена − себестоимость</span><strong id="pe-summary-difference"></strong></div><p class="pe-note">Для составного товара себестоимость рассчитывается по ингредиентам.</p><div class="pe-summary-hint">Количество в составе указывается на одну продаваемую единицу товара.</div></aside></div>
    </div></div></section>`;
  renderTypeFields();toggleNoStockFields();updateProductEditorSummary();
}
