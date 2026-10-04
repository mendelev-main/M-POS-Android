function productInlineArg(value){return escapeAttr(JSON.stringify(String(value??'')));}

// Persist quantities in the original stock unit; display conversions never rewrite history.
const PRODUCT_UNITS={piece:{label:'шт.',kind:'count',scale:1},kg:{label:'кг',kind:'mass',scale:1000},g:{label:'г',kind:'mass',scale:1},l:{label:'л',kind:'volume',scale:1000},ml:{label:'мл',kind:'volume',scale:1}};
function stockUnit(p){return p?.type==='simple'?(p.stockUnit||''):(p?.recipeUnit||'piece');}
function compositeRecipeYield(p){const value=Number(p?.recipeYield);return p?.type==='composite'&&Number.isFinite(value)&&value>0?value:1;}
function inferredCompositeYield(components=window._pmComponents||[]){
  if(!components.length)return null;
  const kinds=new Set();
  const rows=[];
  for(const c of components){
    const p=getProduct(c.productId);if(!p)return null;
    const unit=stockUnit(p),meta=PRODUCT_UNITS[unit];if(!meta)return null;
    kinds.add(meta.kind);rows.push({p,c,unit,meta});
  }
  if(kinds.size!==1)return null;
  const kind=[...kinds][0];if(kind==='count')return {qty:rows.reduce((sum,r)=>sum+(r.p.type==='composite'?Number(r.c.qty||0)*compositeRecipeYield(r.p):Number(r.c.qty||0)),0),unit:'piece'};
  const target=kind==='mass'?'g':'ml';
  let qty=0;
  for(const r of rows){
    const raw=r.p.type==='composite'?Number(r.c.qty||0)*compositeRecipeYield(r.p):Number(r.c.qty||0);
    qty+=convertProductQty(raw,r.p.type==='composite'?(r.p.recipeUnit||r.unit):r.unit,target);
  }
  return Number.isFinite(qty)&&qty>0?{qty:Number(qty.toPrecision(15)),unit:target}:null;
}
function syncInferredCompositeYield(){
  if(window._pmRecipeYieldManual)return;
  const inferred=inferredCompositeYield();
  if(inferred){window._pmRecipeYield=inferred.qty;window._pmRecipeUnit=inferred.unit;}
}
function componentBaseQty(product,qty,unit){
  qty=Number(qty);if(!Number.isFinite(qty)||qty<=0)throw new Error('Количество ингредиента должно быть больше нуля');
  const base=stockUnit(product);
  if(product?.type==='composite'){
    const recipeUnit=product.recipeUnit||'piece',yieldQty=compositeRecipeYield(product);
    const requested=convertProductQty(qty,unit||recipeUnit,recipeUnit);
    return requested/yieldQty;
  }
  return convertProductQty(qty,unit||base,base);
}
function unitLabel(unit){return PRODUCT_UNITS[unit]?.label||'ед. (не задана)';}
function convertProductQty(qty,from,to){
  if(from===to)return Number(qty);
  const a=PRODUCT_UNITS[from],b=PRODUCT_UNITS[to];
  if(!a||!b||a.kind!==b.kind)throw new Error('Несовместимые единицы измерения');
  const value=Number(qty)*a.scale/b.scale;
  if(!Number.isFinite(value))throw new Error('Некорректное количество');
  return Number(value.toPrecision(15));
}
function productUnitOptions(base,selected){
  const units=Object.keys(PRODUCT_UNITS).filter(u=>!base||PRODUCT_UNITS[u].kind===PRODUCT_UNITS[base]?.kind);
  return (!base?'<option value="">Не задана — прежний учёт</option>':'')+units.map(u=>`<option value="${u}" ${u===selected?'selected':''}>${unitLabel(u)}</option>`).join('');
}
function changeProductDisplayUnit(unit){
  const old=window._pmUnit||'',base=window._pmBaseUnit||'';
  try{
    if(base && !unit)throw new Error('Нельзя убрать назначенную единицу');
    if(old && unit){
      const stock=document.getElementById('pf-stock'),cost=document.getElementById('pf-cost'),minimum=document.getElementById('pf-min-stock');
      const factor=convertProductQty(1,old,unit);
      if(stock?.value!=='')stock.value=convertProductQty(stock.value,old,unit);
      if(cost?.value!=='')cost.value=Number((Number(cost.value)/factor).toPrecision(15));
      if(minimum?.value!=='')minimum.value=convertProductQty(minimum.value,old,unit);
    }
    window._pmUnit=unit;
    if(!window._pmBaseUnit && unit)window._pmBaseUnit=unit;
    captureSimpleProductDraft();renderTypeFields();
  }catch(e){document.getElementById('pf-unit').value=old;flash(e.message);}
}
function captureSimpleProductDraft(){
  window._pmSimpleDraft={purchaseUnit:document.getElementById('pf-purchase-unit')?.value,packSize:document.getElementById('pf-pack-size')?.value,contentUnit:document.getElementById('pf-purchase-content-unit')?.value,cost:document.getElementById('pf-cost')?.value??'',stock:document.getElementById('pf-stock')?.value??'',minimum:document.getElementById('pf-min-stock')?.value??'',noStock:!!document.getElementById('pf-no-stock')?.checked};
}
function componentDisplayUnit(c){const p=getProduct(c.productId),base=stockUnit(p);return c.displayUnit||base;}
function componentDisplayQty(c){const p=getProduct(c.productId),unit=componentDisplayUnit(c);if(p?.type==='composite')return convertProductQty(Number(c.qty||0)*compositeRecipeYield(p),p.recipeUnit||'piece',unit);return convertProductQty(c.qty,stockUnit(p),unit);}
function changeComponentDisplayUnit(idx,unit){
  const c=window._pmComponents[idx];if(!c)return;
  try{const p=getProduct(c.productId);convertProductQty(1,p?.type==='composite'?(p.recipeUnit||'piece'):stockUnit(p),unit);c.displayUnit=unit;renderTypeFields();}catch(e){flash(e.message);}
}
function saveProductConfiguration(p){
  for(const [id,key] of [['pf-sku','sku'],['pf-note','internalNote']]){
    const field=document.getElementById(id);if(!field)continue;
    const value=field.value.trim();if(value)p[key]=value;else delete p[key];
  }
  if(p.type!=='simple')return;
  const unit=window._pmUnit||'',base=window._pmBaseUnit||unit;
  if(base){
    if(p.stockUnit && p.stockUnit!==base)throw new Error('Базовая единица уже назначена');
    p.stock=convertProductQty(p.stock,unit,base);
    p.cost=Number(p.cost)/convertProductQty(1,unit,base);
    p.stockUnit=base;p.stockDisplayUnit=unit;
  }
  const field=document.getElementById('pf-min-stock');
  if(field){
    if(field.value==='')delete p.minStock;
    else{const value=Number(field.value);if(!Number.isFinite(value)||value<0)throw new Error('Минимальный остаток должен быть неотрицательным');p.minStock=convertProductQty(value,unit,base);}
  }
}

function switchProductType(t){
  const existing=getProduct(window._pmEditingId);
  if(existing && existing.type!==t && (existing.stockUnit || state.products.some(p=>p.id!==existing.id && productDependsOn(p.id,existing.id)))){flash('Тип товара с единицами или связями нельзя менять: создайте отдельный товар');return;}

  if(window._pmType==='simple')captureSimpleProductDraft();
  window._pmType = t;
  document.querySelectorAll('.product-type-switch').forEach(btn=>{
    btn.classList.toggle('selected', btn.dataset.type===t);
  });
  renderTypeFields();
}
function renderTypeFields(){
  const el = document.getElementById('pf-type-fields');
  const search=document.getElementById('comp-search');if(search)window._pmSearch=search.value;
  if(!el) return;
  const editing = window._pmEditingId ? getProduct(window._pmEditingId) : null;
  if(window._pmType === 'simple'){
    el.innerHTML = `
      <div class="field"><label for="pf-unit">Единица ввода остатка и себестоимости</label><select id="pf-unit" onchange="changeProductDisplayUnit(this.value)" ${!window._pmStockUnlocked?'disabled':''}>${productUnitOptions(window._pmBaseUnit,window._pmUnit)}</select>
      <p class="pe-note">${editing?.stockUnit?'При смене кг ↔ г или л ↔ мл значения карточки пересчитываются.':'Первое назначение единицы сохраняет текущие числа: остаток 5 станет 5 выбранных единиц. Проверьте существующие рецептуры.'} Приёмки и продажи используют базовую единицу: ${unitLabel(window._pmBaseUnit)}. Цена продажи не пересчитывается.</p></div>
      <div class="grid-2">
        <div class="field"><label>Себестоимость за 1 ${unitLabel(window._pmUnit)}</label>
          <input type="number" id="pf-cost" ${editing?'readonly aria-readonly="true"':''} inputmode="decimal" value="${escapeAttr(String(window._pmSimpleDraft?.cost??''))}" placeholder="0">
        </div>
        <div class="field"><label>${editing?'Текущий остаток':'Начальный остаток'}, ${unitLabel(window._pmUnit)}</label>
          <input type="number" id="pf-stock" inputmode="decimal" value="${escapeAttr(String(window._pmSimpleDraft?.stock??''))}" placeholder="0" ${!window._pmStockUnlocked?'disabled':''}>
          ${!window._pmStockUnlocked ? `<div class="center-note configuration-access-note">Данные конфигурации доступны только администратору.</div>` : ''}
        </div>
      </div>
      <p class="pe-note">${editing?'Себестоимость изменяется только при приёмке по средневзвешенной стоимости.':'Укажите начальную себестоимость. После создания её будут изменять приёмки.'}</p>
      <div class="pe-card configuration-card purchase-configuration-card"><strong>Как обычно закупаем</strong><p class="pe-note">Эти значения автоматически подставятся в следующий заказ, но их всегда можно изменить.</p><div class="pe-two-fields"><div class="field"><label for="pf-purchase-unit">Единица закупки</label><select id="pf-purchase-unit" onchange="captureSimpleProductDraft();renderTypeFields()">${purchaseUnitOptions({...editing,type:'simple',stockUnit:window._pmBaseUnit},window._pmSimpleDraft?.purchaseUnit??editing?.purchaseUnit??window._pmBaseUnit)}</select></div><div class="field"><label for="pf-pack-size">Количество внутри</label><input id="pf-pack-size" type="number" min="0" step="any" inputmode="decimal" value="${escapeAttr(String(window._pmSimpleDraft?.packSize??editing?.purchasePackSize??''))}" placeholder="Например, 12"></div></div><div class="field"><label for="pf-purchase-content-unit">Единица внутри</label><select id="pf-purchase-content-unit">${productUnitOptions(window._pmBaseUnit,window._pmSimpleDraft?.contentUnit??editing?.purchaseContentUnit??window._pmBaseUnit)}</select></div></div>
      <div class="field"><label for="pf-min-stock">Минимальный остаток, ${unitLabel(window._pmUnit)}</label><input id="pf-min-stock" type="number" min="0" step="any" inputmode="decimal" value="${escapeAttr(String(window._pmSimpleDraft?.minimum??''))}" placeholder="Не задан"><p class="pe-note">Подсказка в карточке; не блокирует продажи и не создаёт заказ поставщику.</p></div>
      <div class="field no-stock-field">
        <label class="no-stock-toggle ${window._pmNoStockUnlocked?'can-edit':''}">
          <input class="no-stock-checkbox" type="checkbox" id="pf-no-stock" ${window._pmSimpleDraft?.noStock?'checked':''} ${window._pmNoStockUnlocked?'':'disabled'} onchange="toggleNoStockFields();updateProductEditorSummary()">
          <span>Не вести учет остатков</span>
        </label>
        <div class="center-note configuration-access-note">${window._pmNoStockUnlocked ? 'Для этого товара остаток не будет уменьшаться при продаже и не будет учитываться в стоимости остатков.' : 'Данные конфигурации доступны только администратору.'}</div>
      </div>
    `;
  } else {
    syncInferredCompositeYield();
    const editingId = window._pmEditingId;
    const componentProducts = state.products.filter(p=>p.id!==editingId).slice().sort((a,b)=>a.name.localeCompare(b.name,'ru'));
    const calculatedCost = (window._pmComponents||[]).reduce((sum,c)=>{
      const p=getProduct(c.productId);
      return sum + (p ? compositeCost(p)*Number(c.qty||0) : 0);
    },0);
    const categoriesForComponents = ['Все', ...Array.from(new Set(componentProducts.map(p=>p.category||'Без категории'))).sort((a,b)=>a.localeCompare(b,'ru'))];
    const selectedRows = (window._pmComponents||[]).map((c,idx)=>{
      const p=getProduct(c.productId);
      if(!p) return `<div class="component-card"><span>Ингредиент не найден</span><button class="btn btn-outline" onclick="removeComponent(${idx})">Убрать из состава</button></div>`;
      const unitCost=compositeCost(p),displayUnit=componentDisplayUnit(c),displayQty=componentDisplayQty(c),lineCost=compositeCostForQuantity(p,displayQty,displayUnit);
      return `<div class="component-card">
        <div class="component-card-main">
          <div class="component-card-name">${escapeHtml(p.name)}</div>
          <div class="component-card-meta">${escapeHtml(p.category||'Без категории')} · ${p.type==='composite'?money(unitCost)+' за '+compositeRecipeYield(p)+' '+unitLabel(p.recipeUnit||'piece'):money(unitCost)+' за 1 '+unitLabel(stockUnit(p))}</div>
        </div>
        <div id="component-cost-${idx}" class="component-cost">${money(lineCost)}</div>
        <div class="component-qty">
          <input type="number" inputmode="decimal" aria-label="Количество: ${escapeAttr(p.name)}" min="0.001" step="0.001" value="${displayQty}" oninput="setComponentQtyLive(${idx},this.value)" onchange="setComponentQty(${idx},this.value)">
          <select class="component-unit-select" aria-label="Единица ингредиента" onchange="changeComponentDisplayUnit(${idx},this.value)">${stockUnit(p)?productUnitOptions(stockUnit(p),displayUnit):'<option value="">Ед. не задана</option>'}</select>
        </div>
        <button type="button" class="icon-btn danger" aria-label="Удалить компонент" onclick="removeComponent(${idx})"><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button>
      </div>`;
    }).join('');
    el.innerHTML = `
      <div class="pe-card configuration-card recipe-yield-card"><strong>Выход готового состава</strong><div class="pe-two-fields recipe-yield-fields"><div class="field"><label>Вес / объём</label><input id="pf-recipe-yield" type="number" min="0.001" step="any" inputmode="decimal" value="${escapeAttr(String(window._pmRecipeYield??editing?.recipeYield??1))}" oninput="window._pmRecipeYieldManual=true;window._pmRecipeYield=Number(this.value)||1;updateProductEditorSummary()"></div><div class="field"><label>Единица</label><select id="pf-recipe-unit" onchange="window._pmRecipeYieldManual=true;window._pmRecipeUnit=this.value;renderTypeFields()">${Object.keys(PRODUCT_UNITS).map(u=>`<option value="${u}" ${u===(window._pmRecipeUnit||editing?.recipeUnit||'piece')?'selected':''}>${unitLabel(u)}</option>`).join('')}</select></div></div></div>
      <div class="component-builder">
        <div class="component-builder-head">
          <div>
            <div class="component-builder-title">Состав товара</div>
            <div class="component-builder-note">Расход на одну продаваемую позицию. Выберите г/кг или мл/л рядом с количеством. Если единица не задана, сначала настройте карточку ингредиента.</div>
          </div>
          <div id="component-total-cost" class="component-total">Себестоимость: ${fullMoney(calculatedCost)}</div>
        </div>
        <div class="component-selected">
          ${selectedRows || '<div class="component-empty">Состав пока пуст.<br>Найдите товар ниже и нажмите «Добавить».</div>'}
        </div>
        <div class="component-add-panel">
          <div class="field component-add-field">
            <label>Добавить в состав</label>
            <div class="component-search">
              <input type="search" id="comp-search" placeholder="Поиск товара или ингредиента..." oninput="renderComponentOptions()">
              <button type="button" class="component-search-clear" aria-label="Очистить поиск" onclick="clearComponentSearch()"><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button>
            </div>
          </div>
          <div class="component-category-tabs">
            ${categoriesForComponents.map(c=>`<button type="button" class="component-category-tab ${(window._pmComponentCategory||'Все')===c?'selected':''}" onclick="setComponentCategory(${escapeAttr(JSON.stringify(c))})">${escapeHtml(c)}</button>`).join('')}
          </div>
          <div id="component-options" class="component-options"></div>
        </div>
      </div>
`;
    document.getElementById('comp-search').value=window._pmSearch||'';
    renderComponentOptions();
  }
  toggleNoStockFields();updateProductEditorSummary();
}
function addComponent(productId, qty){
  const search=(document.getElementById('comp-search')?.value||'');
  const id=productId || document.getElementById('comp-select')?.value;
  if(!id) return;
  const draft={id:window._pmEditingId||null,name:'редактируемый товар',type:'composite',components:[...window._pmComponents,{productId:id,qty:1}]};
  try{ productIngredients(draft); }catch(e){ flash(e.message); return; }
  const value=qty===undefined ? parseFloat(document.getElementById('comp-qty')?.value) : parseFloat(qty);
  const finalQty=Number.isFinite(value)&&value>0?value:1,p=getProduct(id);
  const normalized=p?.type==='composite'?componentBaseQty(p,finalQty,p.recipeUnit||'piece'):finalQty;
  const existing=window._pmComponents.find(c=>c.productId===id);
  if(existing) existing.qty=Number(existing.qty||0)+normalized;
  else window._pmComponents.push({productId:id,qty:normalized});
  renderTypeFields();
  setTimeout(()=>{const i=document.getElementById('comp-search'); if(i){i.value=search; renderComponentOptions(); i.focus();}},0);
}
function changeComponentQty(idx,delta){
  const c=window._pmComponents[idx];
  if(!c) return;
  c.qty=Math.max(0.001, Number(c.qty||0)+delta);
  renderTypeFields();
}
function refreshCompositeEditorCalculations(){
  clearCompositeCostCache();
  if(!window._pmRecipeYieldManual){
    const inferred=inferredCompositeYield();
    if(inferred){
      window._pmRecipeYield=inferred.qty;window._pmRecipeUnit=inferred.unit;
      const yieldInput=document.getElementById('pf-recipe-yield');if(yieldInput)yieldInput.value=String(inferred.qty);
      const yieldUnit=document.getElementById('pf-recipe-unit');if(yieldUnit)yieldUnit.value=inferred.unit;
    }
  }
  let total=0;
  (window._pmComponents||[]).forEach((c,idx)=>{
    const p=getProduct(c.productId);if(!p)return;
    const line=compositeCost(p)*Number(c.qty||0);total+=line;
    const cost=document.getElementById('component-cost-'+idx);if(cost)cost.textContent=money(line);
  });
  const totalEl=document.getElementById('component-total-cost');if(totalEl)totalEl.textContent='Себестоимость: '+fullMoney(total);
  updateProductEditorSummary();
}
function setComponentQtyLive(idx,value){
  const qty=parseFloat(value),c=window._pmComponents[idx];if(!c||!Number.isFinite(qty)||qty<=0)return;
  const p=getProduct(c.productId);
  try{c.qty=componentBaseQty(p,qty,componentDisplayUnit(c));refreshCompositeEditorCalculations();}catch(e){}
}
function setComponentQty(idx,value){
  const qty=parseFloat(value);
  if(!Number.isFinite(qty)||qty<=0){flash('Количество должно быть больше нуля');renderTypeFields();return;}
  const c=window._pmComponents[idx],p=getProduct(c?.productId);if(!c||!p)return;
  try{c.qty=componentBaseQty(p,qty,componentDisplayUnit(c));refreshCompositeEditorCalculations();}catch(e){flash(e.message);renderTypeFields();}
}
function removeComponent(idx){
  window._pmComponents.splice(idx,1);
  renderTypeFields();
}
function setComponentCategory(category){
  window._pmComponentCategory=category;
  renderTypeFields();
}
function clearComponentSearch(){
  const input=document.getElementById('comp-search');
  if(input) input.value='';
  renderComponentOptions();
  setTimeout(()=>document.getElementById('comp-search')?.focus(),20);
}
function renderComponentOptions(){
  const box=document.getElementById('component-options');
  if(!box) return;
  const query=(document.getElementById('comp-search')?.value||'').trim().toLowerCase();
  const category=window._pmComponentCategory||'Все';
  const editingId=window._pmEditingId;
  const selected=new Set((window._pmComponents||[]).map(c=>c.productId));
  const products=state.products.filter(p=>p.id!==editingId &&
    (!query || p.name.toLowerCase().includes(query) || (p.category||'Без категории').toLowerCase().includes(query)) &&
    (category==='Все' || (p.category||'Без категории')===category)
  ).slice().sort((a,b)=>a.name.localeCompare(b.name,'ru'));
  if(!products.length){
    box.innerHTML='<div class="component-empty">Нет подходящих товаров.</div>';
    return;
  }
  box.innerHTML=products.map(p=>`<button type="button" class="component-option" onclick="addComponent(${productInlineArg(p.id)},1)">
    <div class="component-option-info">
      <div class="component-option-name">${escapeHtml(p.name)}</div>
      <div class="component-option-meta">${escapeHtml(p.category||'Без категории')} · себестоимость ${money(compositeCost(p))}${selected.has(p.id)?' · уже добавлен':''}</div>
    </div>
    <span class="component-option-add">${selected.has(p.id)?'+ ещё':'Добавить'}</span>
  </button>`).join('');
}

function normalizeModifierGroup(group){const options=Array.isArray(group?.options)?group.options:[],max=Math.max(1,Number(group?.max)||1),min=Math.max(0,Math.min(max,Number(group?.min)||0));return {id:group?.id||uid(),name:String(group?.name||''),min,max,options:options.map(o=>({id:o.id||uid(),productId:o.productId||'',posName:String(o.posName||''),qty:Number(o.qty)||1,priceDelta:Number(o.priceDelta)||0}))};}
function validateModifierGroups(groups,editingId){for(const raw of groups||[]){const g=normalizeModifierGroup(raw);if(!g.name.trim())throw new Error('Укажите название каждой группы модификаторов');if(g.min>g.max||g.min>g.options.length)throw new Error('Проверьте минимум и максимум группы: '+g.name);if(!g.options.length)throw new Error('Добавьте варианты в группу: '+g.name);const ids=new Set();for(const o of g.options){if(!o.productId||o.productId===editingId||!getProduct(o.productId))throw new Error('Выберите существующий товар для каждого модификатора');if(ids.has(o.productId))throw new Error('Товар повторяется в группе: '+g.name);ids.add(o.productId);if(!Number.isFinite(Number(o.qty))||Number(o.qty)<=0)throw new Error('Количество модификатора должно быть больше нуля');}}}
function modifierOptionUnitText(productId){const p=getProduct(productId);return p?unitLabel(stockUnit(p)||p.unit||'')||'ед.':'ед.';}
function renderModifierGroups(){
  const groups=window._pmModifierGroups||[];
  if(!groups.length)return '<div class="pe-empty">Модификаторов пока нет. Нажмите «Добавить группу».</div>';
  return groups.map((raw,gi)=>{
    const g=normalizeModifierGroup(raw);window._pmModifierGroups[gi]=g;
    const opts=g.options.map((o,oi)=>{
      const p=getProduct(o.productId),unit=modifierOptionUnitText(o.productId);
      return '<div class="modifier-option-card"><div class="field modifier-product-field"><label>Товар</label><button type="button" class="modifier-product-button" onclick="openModifierProductPicker('+gi+','+oi+')"><span>'+escapeHtml(p?.name||'Выберите товар')+'</span><span class="ui-icon ui-icon-search" aria-hidden="true"></span></button></div><div class="field modifier-pos-name-field"><label>Название в POS</label><input value="'+escapeAttr(o.posName||'')+'" placeholder="'+escapeAttr(p?.name||'Название для кассы')+'" oninput="setModifierOptionTextField('+gi+','+oi+',\'posName\',this.value)"></div><div class="field modifier-qty-field"><label>Количество для списания</label><div class="modifier-input-wrap"><input type="number" min="0.001" step="0.001" value="'+o.qty+'" onchange="setModifierOptionField('+gi+','+oi+',\'qty\',this.value)"><span class="modifier-input-suffix">'+escapeHtml(unit)+'</span></div></div><div class="field modifier-price-field"><label>Доплата к цене</label><div class="modifier-input-wrap"><input type="number" step="0.01" value="'+o.priceDelta+'" onchange="setModifierOptionField('+gi+','+oi+',\'priceDelta\',this.value)"><span class="modifier-input-suffix">BYN</span></div></div><button type="button" class="icon-btn danger modifier-delete-button" aria-label="Удалить вариант" onclick="removeModifierOption('+gi+','+oi+')"><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button></div>';
    }).join('');
    const mode=g.min>0?(g.max===1?'Обязательная группа · выбрать 1':'Обязательная группа · выбрать от '+g.min+' до '+g.max):(g.max===1?'Необязательная группа · выбрать до 1':'Необязательная группа · выбрать до '+g.max);
    return '<div class="pe-card modifier-group-card"><div class="pe-two-fields"><div class="field"><label>Название группы</label><input value="'+escapeAttr(g.name)+'" placeholder="Например, Начинка" oninput="setModifierGroupField('+gi+',\'name\',this.value)"></div><div class="modifier-limits"><div class="field"><label>Минимум вариантов</label><input type="number" min="0" step="1" value="'+g.min+'" onchange="setModifierGroupField('+gi+',\'min\',this.value)"></div><div class="field"><label>Максимум вариантов</label><input type="number" min="1" step="1" value="'+g.max+'" onchange="setModifierGroupField('+gi+',\'max\',this.value)"></div></div></div><p class="pe-note">'+escapeHtml(mode)+'</p>'+(opts||'<div class="component-empty">Добавьте первый вариант.</div>')+'<div class="modifier-group-actions"><button type="button" class="btn btn-outline" onclick="addModifierOption('+gi+')">Добавить вариант</button><button type="button" class="btn btn-outline" onclick="removeModifierGroup('+gi+')">Удалить группу</button></div></div>';
  }).join('');
}
function openModifierProductPicker(groupIndex,optionIndex){window._modifierPicker={groupIndex,optionIndex};showModal('<div class="modifier-product-picker"><div class="modal-title">Выберите товар</div><div class="modal-sub">Найдите товар, который будет использоваться как модификатор.</div><div class="field modifier-picker-search"><label>Поиск</label><input id="modifier-product-search" placeholder="Название товара" oninput="renderModifierProductPickerList(this.value)"></div><div id="modifier-product-list" class="modifier-product-list"></div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button></div></div>',true);renderModifierProductPickerList('');setTimeout(()=>document.getElementById('modifier-product-search')?.focus(),50);}
function renderModifierProductPickerList(query){const target=window._modifierPicker;if(!target)return;const currentProductId=window._pmEditingId,q=String(query||'').trim().toLocaleLowerCase('ru'),products=state.products.filter(p=>p.id!==currentProductId&&(!q||String(p.name||'').toLocaleLowerCase('ru').includes(q))).slice().sort((a,b)=>a.name.localeCompare(b.name,'ru'));const el=document.getElementById('modifier-product-list');if(!el)return;el.innerHTML=products.length?products.map(p=>'<button type="button" class="modifier-product-choice" onclick="chooseModifierProduct('+productInlineArg(p.id)+')"><strong>'+escapeHtml(p.name)+'</strong><span>'+escapeHtml(p.category||'Без категории')+' · '+escapeHtml(unitLabel(stockUnit(p)||p.unit||'')||'ед.')+'</span></button>').join(''):'<div class="pe-empty">Товары не найдены.</div>';}
function chooseModifierProduct(productId){const target=window._modifierPicker;if(!target)return;setModifierOptionProduct(target.groupIndex,target.optionIndex,productId);window._modifierPicker=null;closeModal();refreshModifierEditor();}
function refreshModifierEditor(){const el=document.getElementById('pf-modifier-groups');if(el)el.innerHTML=renderModifierGroups();updateProductEditorSummary();}
function addModifierGroup(){(window._pmModifierGroups||(window._pmModifierGroups=[])).push({id:uid(),name:'',min:1,max:1,options:[]});refreshModifierEditor();}
function removeModifierGroup(i){window._pmModifierGroups.splice(i,1);refreshModifierEditor();}
function setModifierGroupField(i,k,v){const g=window._pmModifierGroups[i];if(!g)return;if(k==='name')g.name=v;else g[k]=Math.max(k==='max'?1:0,Math.floor(Number(v)||0));if(g.min>g.max)g.min=g.max;updateProductEditorSummary();}
function addModifierOption(i){const g=window._pmModifierGroups[i];if(g){g.options.push({id:uid(),productId:'',posName:'',qty:1,priceDelta:0});refreshModifierEditor();}}
function removeModifierOption(i,j){window._pmModifierGroups[i]?.options.splice(j,1);refreshModifierEditor();}
function setModifierOptionProduct(i,j,v){const o=window._pmModifierGroups[i]?.options[j];if(o)o.productId=v;}
function setModifierOptionField(i,j,k,v){const o=window._pmModifierGroups[i]?.options[j];if(!o)return;const n=Number(v);o[k]=k==='qty'?(Number.isFinite(n)&&n>0?n:1):(Number.isFinite(n)?n:0);}function setModifierOptionTextField(i,j,k,v){const o=window._pmModifierGroups[i]?.options[j];if(o)o[k]=String(v||'');}
function setModifierOptionDefault(i,j,v){const g=window._pmModifierGroups[i],o=g?.options[j];if(!o)return;if(g.max===1&&v)g.options.forEach(x=>x.default=false);o.default=v;refreshModifierEditor();}
function toggleNoStockFields(){
  const noStock=!!document.getElementById('pf-no-stock')?.checked;
  const stock=document.getElementById('pf-stock');
  if(stock) stock.disabled=noStock || (window._pmEditingId && !window._pmStockUnlocked);
}

function requestNoStockUnlock(){
  showModal(`
    <div class="modal-title">Доступ администратора</div>
    <div class="modal-sub">Только администратор может включить или отключить учет остатков для товара.</div>
    <div class="field"><label>Пароль администратора</label>
      <input type="password" id="no-stock-password" inputmode="text" autocomplete="off" placeholder="Введите пароль">
    </div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal();renderProductModal(getProduct(window._pmEditingId),window._pmType,window._pmSimpleProducts,state.categoryOrder.slice())">Отмена</button>
      <button class="btn btn-primary" onclick="confirmNoStockUnlock()">Разрешить</button>
    </div>
  `);
  setTimeout(()=>document.getElementById('no-stock-password')?.focus(),50);
}
function confirmNoStockUnlock(){
  const value=document.getElementById('no-stock-password')?.value||'';
  if(value!=='Rom23061998'){ flash('Неверный пароль'); return; }
  window._pmNoStockUnlocked=true;
  closeModal();
  const editing=getProduct(window._pmEditingId);
  renderProductModal(editing,window._pmType,window._pmSimpleProducts,state.categoryOrder.slice());
  flash('Доступ администратора разрешен');
}

function requestStockUnlock(){
  if(!window._pmEditingId) return;
  showModal(`
    <div class="modal-title">Изменение остатка</div>
    <div class="field"><label>Пароль</label>
      <input type="password" id="stock-password" inputmode="text" autocomplete="off" placeholder="Введите пароль">
    </div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal(); renderProductModal(getProduct(window._pmEditingId),window._pmType,window._pmSimpleProducts,state.categoryOrder.slice())">Отмена</button>
      <button class="btn btn-primary" onclick="confirmStockUnlock()">Разрешить</button>
    </div>
  `);
  setTimeout(()=>document.getElementById('stock-password')?.focus(),50);
}
function confirmStockUnlock(){
  const value=document.getElementById('stock-password')?.value||'';
  if(value!=='Rom23061998'){ flash('Неверный пароль'); return; }
  window._pmStockUnlocked=true;
  closeModal();
  const editing=getProduct(window._pmEditingId);
  renderProductModal(editing,window._pmType,window._pmSimpleProducts,state.categoryOrder.slice());
  flash('Изменение остатка разрешено');
}
