function openSupplierModal(id=''){
  const supplier = state.suppliers.find(s=>s.id===id);
  const editing = !!supplier;
  const selected = new Set(Array.isArray(supplier?.productIds) ? supplier.productIds : []);
  const products = state.products.filter(p=>p.type==='simple').slice().sort((a,b)=>a.name.localeCompare(b.name,'ru'));
  showModal(`
    <div class="modal-title">${editing?'Товары поставщика':'Новый поставщик'}</div>
    <div class="field">
      <label>Название поставщика</label>
      <input id="sf-name" type="text" value="${escapeAttr(supplier?.name||'')}" placeholder="Например, ООО Поставщик">
    </div>
    <div class="supplier-section-title">Товары на поставщике</div>
    <div class="settings-note supplier-products-note">Выберите товары, которые обычно закупаются у этого поставщика. В приёмке они будут показываться первыми.</div>
    <div class="field supplier-search-field"><input id="supplier-product-search" type="search" placeholder="Поиск товара или категории" autocomplete="off" oninput="filterSupplierProducts(this.value)"></div>
    <div id="supplier-product-list" class="supplier-product-list">
      ${products.length ? products.map(p=>`<label class="supplier-product-option" data-search="${escapeAttr((p.name+' '+(p.category||'')).toLocaleLowerCase('ru'))}">
        <input class="supplier-product-check" type="checkbox" value="${escapeAttr(p.id)}" ${selected.has(p.id)?'checked':''}>
        <span class="supplier-product-name">${escapeHtml(p.name)}</span>
        <span class="list-row-sub">${escapeHtml(p.category||'Без категории')}</span>
      </label>`).join('') : `<div class="center-note">Сначала создайте простые товары.</div>`}
      <div id="supplier-product-search-empty" class="center-note" hidden>Товары не найдены.</div>
    </div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
      <button id="supplier-save-confirm" class="btn btn-primary" onclick="saveSupplier('${escapeAttr(id)}')">${editing?'Сохранить':'Создать'}</button>
    </div>`, true);
}

function filterSupplierProducts(value){
  const query=String(value||'').trim().toLocaleLowerCase('ru');
  const options=[...document.querySelectorAll('#supplier-product-list .supplier-product-option')];
  let visible=0;
  options.forEach(row=>{const show=!query||(row.dataset.search||'').includes(query);row.hidden=!show;if(show)visible++;});
  const empty=document.getElementById('supplier-product-search-empty');if(empty)empty.hidden=!(options.length&&!visible);
}

function deleteSupplier(id){
  if(!canEditCompanySettings()){
    flash('Удалять поставщиков может только администратор при открытой им смене');
    return;
  }
  const supplier=state.suppliers.find(s=>s.id===id);
  if(!supplier) return;
  showModal(`
    <div class="modal-title">Удаление поставщика</div>
    <div class="center-note supplier-delete-note">Удалить поставщика «${escapeHtml(supplier.name)}» из списка?</div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
      <button id="supplier-delete-confirm" class="btn btn-danger" onclick="confirmDeleteSupplier('${escapeAttr(id)}')">Удалить</button>
    </div>`, true);
}

async function confirmDeleteSupplier(id){
  if(window._supplierDeleteBusy)return false;
  if(!canEditCompanySettings()){
    closeModal();
    flash('Удалять поставщиков может только администратор при открытой им смене');
    return false;
  }
  if(!state.suppliers.some(s=>s.id===id))return false;
  const next=state.suppliers.filter(s=>s.id!==id);
  window._supplierDeleteBusy=true;
  const button=document.getElementById('supplier-delete-confirm');if(button)button.disabled=true;
  try{
    await window.PrilavokCore.Storage.set('suppliers',next);
    state.suppliers=next;
    closeModal();
    render();
    flash('Поставщик удалён');
    return true;
  }catch(e){
    markStorageBroken(e);
    flash('Не удалось удалить поставщика: '+(e?.message||'ошибка сохранения'));
    if(button)button.disabled=false;
    return false;
  }finally{
    window._supplierDeleteBusy=false;
  }
}

async function saveSupplier(id=''){
  if(window._supplierSaveBusy)return false;
  const name=(document.getElementById('sf-name')?.value||'').trim();
  if(!name){flash('Введите название поставщика');return false;}
  const productIds=[...document.querySelectorAll('.supplier-product-check:checked')].map(x=>x.value);
  const existingIndex=id ? state.suppliers.findIndex(x=>x.id===id) : -1;
  if(id && existingIndex<0){flash('Поставщик не найден');return false;}
  const next=storageSnapshot(state.suppliers);
  if(existingIndex>=0){
    next[existingIndex].name=name;
    next[existingIndex].productIds=productIds;
  }else{
    next.push({id:uid(),name,productIds});
  }
  window._supplierSaveBusy=true;
  const button=document.getElementById('supplier-save-confirm');if(button)button.disabled=true;
  try{
    await window.PrilavokCore.Storage.set('suppliers',next);
    state.suppliers=next;
    closeModal();
    render();
    flash(id?'Поставщик сохранён':'Поставщик создан');
    return true;
  }catch(e){
    markStorageBroken(e);
    flash('Не удалось сохранить поставщика: '+(e?.message||'ошибка сохранения'));
    if(button)button.disabled=false;
    return false;
  }finally{
    window._supplierSaveBusy=false;
  }
}
