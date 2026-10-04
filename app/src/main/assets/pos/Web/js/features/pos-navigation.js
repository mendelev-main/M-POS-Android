/* Local navigation folders: one level inside each product category, no inventory effects. */
function normalizePosNavigation(value){
  if(value?.version!==1||!Array.isArray(value.categories))return {version:1,categories:[]};
  const categories=[],seenCategories=new Set();
  for(const entry of value.categories){
    if(!entry||typeof entry.category!=='string'||!Array.isArray(entry.items)||seenCategories.has(entry.category))continue;
    seenCategories.add(entry.category);const ids=new Set(),items=[];
    for(const item of entry.items){
      if(!item||!['product','folder'].includes(item.type)||typeof item.id!=='string'||!item.id||ids.has(item.type+':'+item.id))continue;
      if(item.type==='folder'&&(typeof item.name!=='string'||!item.name.trim()))continue;
      ids.add(item.type+':'+item.id);items.push({type:item.type,id:item.id,...(item.type==='folder'?{name:item.name.trim().slice(0,80),parentId:''}:{parentId:typeof item.parentId==='string'?item.parentId:''})});
    }
    const folders=new Set(items.filter(i=>i.type==='folder').map(i=>i.id));for(const i of items)if(i.parentId&&!folders.has(i.parentId))i.parentId='';
    categories.push({category:entry.category,items});
  }
  return {version:1,categories};
}
function posCategoryItems(category,nav=state.posNavigation){
  const saved=normalizePosNavigation(nav).categories.find(e=>e.category===category)?.items||[];
  const products=state.products.filter(p=>productCategoryKey(p)===category).slice().sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0));
  const ids=new Set(products.map(p=>p.id)),items=saved.filter(i=>i.type==='folder'||ids.has(i.id));
  const placed=new Set(items.filter(i=>i.type==='product').map(i=>i.id));
  return [...items,...products.filter(p=>!placed.has(p.id)).map(p=>({type:'product',id:p.id,parentId:''}))];
}
function posVisibleCategoryItems(category=state.posPath){
  return posCategoryItems(category).filter(i=>i.parentId===(window._posFolderModal?.id||state.posFolder||''));
}
async function updatePosNavigation(category,change){
  if(window._posNavigationBusy)return false;
  window._posNavigationBusy=true;
  try{
    const next=normalizePosNavigation(state.posNavigation),items=posCategoryItems(category,next);
    change(items);
    const entry=next.categories.find(e=>e.category===category);
    if(entry)entry.items=items;else next.categories.push({category,items});
    await window.PrilavokCore.Storage.set('posNavigation',next);
    const folderContext=window._posFolderModal;
    state.posNavigation=next;closeModal();render();
    if(folderContext&&posCategoryItems(folderContext.category).some(i=>i.type==='folder'&&i.id===folderContext.id)){window._posFolderModal=folderContext;renderPosFolderModal();}
    return true;
  }catch(e){flash('Не удалось сохранить раскладку: '+(e.message||'ошибка'));return false;}
  finally{window._posNavigationBusy=false;}
}
function openPosFolder(id){
  if(!posCategoryItems(state.posPath).some(i=>i.type==='folder'&&i.id===id))return;
  state.posFolder='';state.search='';
  window._posFolderModal={category:state.posPath,id};renderPosFolderModal();
}
function renderProductTileSymbol(product){return product.tileSymbol?`<div class="tile-symbol">${escapeHtml(product.tileSymbol)}</div>`:'';}
function renderPosFolderModal(){
  const context=window._posFolderModal;if(!context)return;
  const items=posCategoryItems(context.category),folder=items.find(i=>i.type==='folder'&&i.id===context.id);
  if(!folder||state.posPath!==context.category){closeModal();return;}
  const editing=state.editMode,products=items.filter(i=>i.type==='product'&&i.parentId===folder.id).map(i=>getProduct(i.id)).filter(Boolean);
  const columns=Math.max(1,Math.min(4,products.length));
  document.getElementById('modal-root').innerHTML=`<div class="modal-overlay" onclick="if(event.target===this)closeModal()"><section class="modal pos-folder-modal" data-columns="${columns}" role="dialog" aria-modal="true" aria-label="${escapeAttr(folder.name)}"><header><h2>${escapeHtml(folder.name)}</h2><button class="btn btn-outline" onclick="closeModal()">Закрыть</button></header><div class="pos-folder-grid ${editing?'category-edit-grid':''}" id="pos-folder-grid">${products.map((p,index)=>{const avail=availableStock(p);return `<div class="layout-tile" data-tile-type="product" data-id="${escapeAttr(p.id)}" data-index="${index}"><div class="pcard ${!editing&&avail<=0?'disabled':''}" role="button" tabindex="0" aria-label="${escapeAttr(p.name)}">${editing?`<button class="navigation-action" onclick="event.stopPropagation();openPosTileMove(${escapeAttr(JSON.stringify(p.id))})">Переместить</button>`:''}${renderProductTileSymbol(p)}<div class="pcard-name">${escapeHtml(p.name)}</div><div class="pcard-bottom"><div><div class="pcard-price">${money(p.price)}</div><div class="pcard-stock">${avail===Infinity?'Остаток: ∞':'Остаток: '+stockQtyText(avail)+' '+unitLabel(stockUnit(p))}</div></div></div></div></div>`;}).join('')||'<p class="pe-note pos-folder-empty">В папке пока нет товаров.</p>'}</div></section></div>`;
  const grid=document.getElementById('pos-folder-grid');
  grid.addEventListener('click',handlePosGridClick);
  grid.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&!e.target.closest('button')){e.preventDefault();handlePosGridClick(e);}});
  if(editing)setupLayoutGridDrag(grid);
}
function openPosFolderEditor(id=''){
  if(!state.editMode||!state.posPath)return;
  const folder=posCategoryItems(state.posPath).find(i=>i.type==='folder'&&i.id===id);
  showModal(`<div class="modal-title">${folder?'Изменить папку':'Новая папка'}</div><div class="field"><label>Название</label><input id="pos-folder-name" maxlength="80" value="${escapeAttr(folder?.name||'')}"></div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="savePosFolder(${escapeAttr(JSON.stringify(id))})">Сохранить</button></div>${folder?`<button class="btn btn-danger-outline pos-folder-delete" onclick="requestRemovePosFolder(${escapeAttr(JSON.stringify(id))})">Удалить папку</button>`:''}`);
}
async function savePosFolder(id=''){
  if(!state.editMode||!state.posPath)return;
  const name=(document.getElementById('pos-folder-name')?.value||'').trim();
  if(!name||name.length>80){flash('Укажите название до 80 символов');return;}
  return updatePosNavigation(state.posPath,items=>{
    if(items.some(i=>i.type==='folder'&&i.id!==id&&i.name.toLocaleLowerCase('ru')===name.toLocaleLowerCase('ru')))throw Error('Папка с таким названием уже есть');
    if(id){const f=items.find(i=>i.type==='folder'&&i.id===id);if(!f)throw Error('Папка не найдена');f.name=name;}
    else items.push({type:'folder',id:uid(),name,parentId:''});
  });
}
function requestRemovePosFolder(id){
  if(!state.editMode)return;
  showModal(`<div class="modal-title">Удалить папку?</div><p>Товары вернутся в корень категории.</p><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-danger" onclick="removePosFolder(${escapeAttr(JSON.stringify(id))})">Удалить папку</button></div>`);
}
async function removePosFolder(id){
  if(!state.editMode||!state.posPath)return;
  const ok=await updatePosNavigation(state.posPath,items=>{const index=items.findIndex(i=>i.type==='folder'&&i.id===id);if(index<0)throw Error('Папка не найдена');items.splice(index,1);for(const i of items)if(i.parentId===id)i.parentId='';});
  if(ok&&state.posFolder===id){state.posFolder='';render();}
}
function openPosTileMove(id){
  if(!state.editMode||!state.posPath)return;
  const items=posCategoryItems(state.posPath),item=items.find(i=>i.type==='product'&&i.id===id);if(!item)return;
  showModal(`<div class="modal-title">Переместить товар</div><p>${escapeHtml(getProduct(id)?.name||'')}</p><div class="field"><label>Папка</label><select id="pos-move-folder"><option value="">Корень категории</option>${items.filter(i=>i.type==='folder').map(f=>`<option value="${escapeAttr(f.id)}" ${item.parentId===f.id?'selected':''}>${escapeHtml(f.name)}</option>`).join('')}</select></div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="movePosProduct(${escapeAttr(JSON.stringify(id))},document.getElementById('pos-move-folder').value)">Переместить</button></div>`);
}
function movePosProduct(id,folderId){
  if(!state.editMode||!state.posPath)return;
  return updatePosNavigation(state.posPath,items=>{
    if(folderId&&!items.some(i=>i.type==='folder'&&i.id===folderId))throw Error('Папка не найдена');
    const index=items.findIndex(i=>i.type==='product'&&i.id===id);if(index<0)throw Error('Товар не найден в категории');
    const [item]=items.splice(index,1);item.parentId=folderId||'';items.push(item);
  });
}
function reorderPosCategoryTile(type,id,targetIndex){
  if(!state.editMode||!state.posPath||state.search)return;
  const parent=window._posFolderModal?.id||state.posFolder||'';
  return updatePosNavigation(state.posPath,items=>{
    const visible=items.filter(i=>i.parentId===parent),from=visible.findIndex(i=>i.type===type&&i.id===id);if(from<0)return;
    const [item]=visible.splice(from,1);visible.splice(Math.max(0,Math.min(visible.length,targetIndex)),0,item);
    let index=0;for(let i=0;i<items.length;i++)if(items[i].parentId===parent)items[i]=visible[index++];
  });
}

function ensureLayoutPositions(){
  let changed=false;
  if(state.layoutTiles.length>20){ state.layoutTiles=state.layoutTiles.slice(0,20); changed=true; }
  const cols = 5;
  const occupied=new Set();
  state.layoutTiles.forEach((t,i)=>{
    if(!t || typeof t!=='object') return;
    if(Number.isInteger(t.col) && Number.isInteger(t.row) && t.col>=0 && t.col<cols && t.row>=0){
      const key=t.col+':'+t.row;
      if(!occupied.has(key)){ occupied.add(key); return; }
    }
    let n=0;
    while(occupied.has((n%cols)+':'+Math.floor(n/cols))) n++;
    t.col=n%cols; t.row=Math.floor(n/cols); occupied.add(t.col+':'+t.row); changed=true;
  });
  if(changed) saveKey('layout',categoryLayoutSnapshot());
  return changed;
}
function tilePositionStyle(t){
  if(!Number.isInteger(t.col) || !Number.isInteger(t.row)) return '';
  return `grid-column:${t.col+1};grid-row:${t.row+1};`;
}
function renderPosScreen(shift){
  syncCategoryOrder();
  ensureLayoutPositions();
  const editing = state.editMode;
  const q = state.search.trim().toLowerCase();
  const currentCategory = state.posPath;

  let tiles = [];
  if(currentCategory){
    const all=posCategoryItems(currentCategory);
    if(state.posFolder&&!all.some(i=>i.type==='folder'&&i.id===state.posFolder))state.posFolder='';
    tiles=all.filter(i=>q ? i.type==='product'&&getProduct(i.id)?.name.toLowerCase().includes(q) : i.parentId===(state.posFolder||'')).map(i=>({...i,product:i.type==='product'?getProduct(i.id):null}));
  } else {
    tiles = state.layoutTiles.map((t,index)=>{
      if(t.type==='product'){
        const p=getProduct(t.id);
        return p ? {type:'product', id:t.id, product:p, index, tile:t} : null;
      }
      if(t.type==='category' && state.categoryOrder.includes(t.id)) return {type:'category', id:t.id, index, tile:t};
      return null;
    }).filter(Boolean);
    if(q) tiles=tiles.filter(t=>t.type==='product' && t.product.name.toLowerCase().includes(q));
  }

  const tileHtml = tiles.map((t, index)=>{
    const pos = !currentCategory ? tilePositionStyle(t.tile) : '',posAttr=pos?` style="${pos}"`:'';
    if(t.type==='folder')return `<div class="layout-tile category-tile folder-tile" data-tile-type="folder" data-id="${escapeAttr(t.id)}" data-index="${index}"><div class="pcard ${editing?'edit-tile':''}" role="button" tabindex="0">${editing?`<button class="navigation-action" onclick="event.stopPropagation();openPosFolderEditor(${escapeAttr(JSON.stringify(t.id))})">Изменить</button>`:''}<div class="pcard-name">${escapeHtml(t.name)}</div></div></div>`;
    if(t.type==='category'){
      const categoryColor = state.categoryColors[t.id] || '#EEF1F5';
      return `<div class="layout-tile category-tile" data-tile-type="category" data-id="${escapeAttr(t.id)}" data-index="${t.index ?? index}"${posAttr}>
        <div class="pcard ${editing?'edit-tile':''}" data-action="category" role="button" tabindex="0" style="--category-color:${categoryColor}">
          ${editing&&!currentCategory?`<button type="button" class="tile-remove" onclick="event.stopPropagation();removeLayoutTile(${t.index})">×</button>`:''}
          ${state.categorySymbols[t.id]?`<div class="tile-symbol">${escapeHtml(state.categorySymbols[t.id])}</div>`:''}
          <div class="pcard-name">${escapeHtml(t.id)}</div>
          <div class="pcard-bottom"><div><div class="pcard-stock">Категория</div></div></div>
        </div>
      </div>`;
    }
    const p=t.product;
    const avail=availableStock(p);
    const disabled=avail<=0;
    return `<div class="layout-tile" data-tile-type="product" data-id="${p.id}" data-index="${t.index ?? index}"${posAttr}>
      <div class="pcard ${disabled&&!editing?'disabled':''} ${editing?'edit-tile':''}" data-action="product" role="button" tabindex="0">
        ${editing&&!currentCategory?`<button type="button" class="tile-remove" onclick="event.stopPropagation();removeLayoutTile(${t.index})">×</button>`:''}
        ${editing&&currentCategory?`<button class="navigation-action" onclick="event.stopPropagation();openPosTileMove(${escapeAttr(JSON.stringify(p.id))})">Переместить</button>`:''}
        ${renderProductTileSymbol(p)}
        <div class="pcard-name">${escapeHtml(p.name)}</div>
        <div class="pcard-bottom"><div><div class="pcard-price">${money(p.price)}</div><div class="pcard-stock ${p.noStockTracking?'':(avail<=3?'low':'')}">${p.type==='simple' ? (p.noStockTracking ? 'Остаток: ∞' : 'Остаток: '+stockQtyText(avail)+' '+unitLabel(stockUnit(p))) : (avail===Infinity?'Остаток: ∞':'Доступно: '+avail)}</div></div></div>
      </div>
    </div>`;
  }).join('');

  const parkCount=state.parked.length;
  const folderName=posCategoryItems(currentCategory).find(i=>i.type==='folder'&&i.id===state.posFolder)?.name;
  const title=currentCategory ? escapeHtml(currentCategory+(folderName?' / '+folderName:'')) : 'Рабочая зона';
  return `<div class="screen ${state.tab==='pos'?'active':''}" id="screen-pos">
    <div class="pos-left">
      ${!shift ? `<div class="no-shift-banner"><span>Чтобы принимать оплату, откройте кассовую смену.</span><button onclick="openShiftModal()">Открыть смену</button></div>` : ''}
      <div class="pos-toolbar">
        <div class="pos-toolbar-left">
          ${currentCategory ? `<button class="pos-back" onclick="closePosCategory()">← Назад</button>` : ''}
          <div class="zone-title-btn" title="${title}">${title}</div>
        </div>
        <div class="pos-actions-left">
          <button class="park-btn demand-btn ${state.demandOverload?'active':''}" onclick="setDemandOverload(!state.demandOverload)" ${editing?'disabled':''} title="Сообщить гостям о повышенном спросе">${state.demandOverload?'Повышенный спрос':'Обычная загрузка'}</button>
          <button class="park-btn ${editing?'chip active':''}" onclick="toggleEditMode()" title="Настроить рабочую зону">${editing?'Готово':'Раскладка'}</button>
          <button class="park-btn" onclick="openParkedModal()" ${editing?'disabled':''}>Отложенные ${parkCount?`<span class="park-badge">${parkCount}</span>`:''}</button>
        </div>
      </div>
      <div class="product-grid pos-workspace-grid ${editing ? (currentCategory?'category-edit-grid':'layout-edit-grid') : ''}" id="sections-wrap">
        ${tileHtml || `<div class="empty-hint pos-grid-empty">${currentCategory?'В этой категории пока нет товаров.':'Рабочая зона пуста. Нажмите «Раскладка», чтобы добавить плитки.'}</div>`}
      </div>
      ${editing&&currentCategory&&!state.posFolder?`<button class="btn btn-primary pos-create-folder" onclick="openPosFolderEditor()">Создать папку</button>`:''}
      ${editing && !currentCategory ? `<div class="pos-layout-actions"><button class="btn btn-primary" onclick="openLayoutEditor()">＋ Изменить содержимое</button></div>` : ''}
    </div>
    ${renderCartPanel(shift)}
  </div>`;
}

function toggleEditMode(){
  state.editMode=!state.editMode;
  state.search='';
  render();
  if(state.editMode) setTimeout(setupLayoutGridDrag,50);
}
function openPosCategory(category){
  const name=String(category||'').trim();
  if(!name) return;
  state.posFolder='';
  state.posPath=name;
  state.search='';
  state.editMode=false;
  render();
}
function closePosCategory(){ if(window._posFolderModal){closeModal();return;} if(state.posFolder){state.posFolder='';state.search='';render();return;} state.posPath=null; state.search=''; state.editMode=false; render(); }
function handlePosGridClick(e){
  if(Date.now()<(window._posSuppressClickUntil||0))return;
  const tile=e.target.closest('.layout-tile');
  if(!tile) return;
  if(e.target.closest('.tile-remove')) return;
  const type=tile.dataset.tileType;
  const id=tile.dataset.id;
  if(state.editMode){if(state.posPath){if(type==='folder')openPosFolder(id);else if(type==='product')openPosTileMove(id);}return;}
  if(type==='folder'){openPosFolder(id);return;}
  // На главной рабочей зоне категория открывает вложенный экран,
  // товар сразу добавляется в текущий заказ.
  if(!state.posPath){
    if(type==='category') openPosCategory(id);
    else if(type==='product') addToCart(id);
    return;
  }
  // Внутри категории отображаются обычные товарные плитки.
  // Они должны добавляться в текущий заказ так же, как товары на главном экране.
  if(type==='product') addToCart(id);
}
function openLayoutEditor(){
  const products=state.products.slice().sort((a,b)=>a.name.localeCompare(b.name,'ru'));
  const cats=state.categoryOrder.slice();
  showModal(`<div class="modal-title">Настройка рабочей зоны</div>
    <div class="center-note layout-editor-note">Нажмите «＋», чтобы добавить плитку. После добавления можно закрыть окно и расставить плитки по сетке.</div>
    <div class="layout-list">
      <div class="layout-editor-heading">Категории</div>
      ${cats.map(c=>`<div class="layout-add-row"><div class="name">${escapeHtml(c)}<div class="sub">Открывает товары категории</div></div><button type="button" class="layout-add-btn" data-layout-add-type="category" data-layout-add-id="${escapeAttr(c)}">＋</button></div>`).join('') || '<div class="center-note">Категорий пока нет.</div>'}
      <div class="layout-editor-heading products">Товары</div>
      ${products.map(p=>`<div class="layout-add-row"><div class="name">${escapeHtml(p.name)}<div class="sub">${escapeHtml(productCategoryKey(p))} · ${money(p.price)}</div></div><button type="button" class="layout-add-btn" data-layout-add-type="product" data-layout-add-id="${escapeAttr(p.id)}">＋</button></div>`).join('') || '<div class="center-note">Товаров пока нет.</div>'}
    </div>
    <div class="modal-actions"><button type="button" class="btn btn-primary" id="layout-editor-done">Готово</button></div>`, true);
  document.querySelectorAll('[data-layout-add-type]').forEach(btn=>btn.addEventListener('click',()=>addLayoutTile(btn.dataset.layoutAddType,btn.dataset.layoutAddId)));
  document.getElementById('layout-editor-done')?.addEventListener('click',()=>{ closeModal(); render(); setTimeout(setupLayoutGridDrag,50); });
}
function addLayoutTile(type,id){
  if(!type || !id) return;
  if(state.layoutTiles.length>=20){ flash('В рабочей зоне можно разместить максимум 20 плиток'); return; }
  state.layoutTiles.push({type,id});
  ensureLayoutPositions();
  saveKey('layout',categoryLayoutSnapshot());
  render();
  openLayoutEditor();
}
function removeLayoutTile(index){
  state.layoutTiles.splice(Number(index),1);
  saveKey('layout',categoryLayoutSnapshot());
  render();
  setTimeout(setupLayoutGridDrag,50);
}

let layoutDragState=null;
function setupLayoutGridDrag(grid=document.getElementById('sections-wrap')){
  if(!grid || !state.editMode) return;
  if(grid.dataset.dragReady==='1') return;
  grid.dataset.dragReady='1';
  grid.addEventListener('pointerdown',onLayoutPointerDown);
  grid.addEventListener('pointermove',onLayoutPointerMove);
  grid.addEventListener('pointerup',onLayoutPointerUp);
  grid.addEventListener('pointercancel',onLayoutPointerUp);
}
function gridMetrics(grid){
  const cs=getComputedStyle(grid), cols=cs.gridTemplateColumns.split(' ').length;
  const rect=grid.getBoundingClientRect();
  const gap=parseFloat(cs.columnGap)||0;
  const cellW=(rect.width-(parseFloat(cs.paddingLeft)||0)-(parseFloat(cs.paddingRight)||0)-gap*(cols-1))/cols;
  const rows=cs.gridTemplateRows.split(' ').filter(Boolean);
  const rowH=rows.length ? parseFloat(rows[0]) : 160;
  const rowGap=parseFloat(cs.rowGap)||12;
  return {cols,cellW,rowH,rowGap,padLeft:parseFloat(cs.paddingLeft)||0,padTop:parseFloat(cs.paddingTop)||0};
}
function pointerToCell(grid,x,y){
  const m=gridMetrics(grid), r=grid.getBoundingClientRect();
  const localX=x-r.left-m.padLeft+grid.scrollLeft;
  const localY=y-r.top-m.padTop+grid.scrollTop;
  return {col:Math.max(0,Math.min(m.cols-1,Math.floor(localX/(m.cellW+(parseFloat(getComputedStyle(grid).columnGap)||0))))),row:Math.max(0,Math.floor(localY/(m.rowH+m.rowGap)))};
}
function ensureGridCell(grid,col,row,exceptIndex){
  return !state.layoutTiles.some((t,i)=>i!==exceptIndex && t.col===col && t.row===row);
}
function nearestFreeCell(grid,col,row,exceptIndex){
  const m=gridMetrics(grid);
  for(let d=0;d<30;d++){
    for(let rr=Math.max(0,row-d);rr<=row+d;rr++) for(let cc=Math.max(0,col-d);cc<=Math.min(m.cols-1,col+d);cc++){
      if(Math.abs(cc-col)+Math.abs(rr-row)!==d) continue;
      if(ensureGridCell(grid,cc,rr,exceptIndex)) return {col:cc,row:rr};
    }
  }
  return {col:Math.min(m.cols-1,col),row:row+1};
}
function onLayoutPointerDown(e){
  if(!state.editMode) return;
  if(e.target.closest('button')) return;
  const tile=e.target.closest('.layout-tile');
  const grid=tile?.closest('.pos-folder-grid')||document.getElementById('sections-wrap');
  if(!tile || !grid) return;
  const index=Number(tile.dataset.index);
  const rect=tile.getBoundingClientRect();
  layoutDragState={category:state.posPath,folder:window._posFolderModal?.id||state.posFolder||'',tile,index,startX:e.clientX,startY:e.clientY,lastX:e.clientX,lastY:e.clientY,pointerId:e.pointerId,dragging:false,rect,grid};
  try{tile.setPointerCapture(e.pointerId);}catch(_){ }
}
function onLayoutPointerMove(e){
  const d=layoutDragState;
  if(!d || d.pointerId!==e.pointerId) return;
  d.lastX=e.clientX; d.lastY=e.clientY;
  const dx=e.clientX-d.startX,dy=e.clientY-d.startY;
  if(!d.dragging && Math.hypot(dx,dy)<6) return;
  if(!d.dragging){
    d.dragging=true;
    d.tile.classList.add('drag-placeholder');
    const clone=d.tile.cloneNode(true);
    clone.classList.add('layout-drag-clone');
    clone.style.setProperty('--drag-w',d.rect.width+'px');
    clone.style.setProperty('--drag-h',d.rect.height+'px');
    clone.style.left=(e.clientX-d.rect.width/2)+'px'; clone.style.top=(e.clientY-d.rect.height/2)+'px';
    document.body.appendChild(clone); d.clone=clone;
  }
  e.preventDefault();
  if(d.clone){d.clone.style.left=(e.clientX-d.rect.width/2)+'px';d.clone.style.top=(e.clientY-d.rect.height/2)+'px';}
  const cell=pointerToCell(d.grid,e.clientX,e.clientY);
  d.target=cell;
  d.grid.querySelectorAll('.layout-cell-highlight').forEach(x=>x.remove());
  const m=gridMetrics(d.grid), gap=parseFloat(getComputedStyle(d.grid).columnGap)||0, rowGap=parseFloat(getComputedStyle(d.grid).rowGap)||0;
  const h=document.createElement('div'); h.className='layout-cell-highlight';
  h.style.left=(m.padLeft+cell.col*(m.cellW+gap))+'px'; h.style.top=(m.padTop+cell.row*(m.rowH+rowGap))+'px';
  h.style.width=m.cellW+'px'; h.style.height=m.rowH+'px'; d.grid.appendChild(h);
}
function onLayoutPointerUp(e){
  const d=layoutDragState;
  if(!d || d.pointerId!==e.pointerId) return;
  if(d.dragging){
    e.preventDefault();
    const target=d.target || pointerToCell(d.grid,e.clientX,e.clientY);
    window._posSuppressClickUntil=Date.now()+350;
    if(e.type!=='pointercancel'){
      if(d.category){
        if(state.posPath===d.category&&(window._posFolderModal?.id||state.posFolder||'')===d.folder)reorderPosCategoryTile(d.tile.dataset.tileType,d.tile.dataset.id,target.row*gridMetrics(d.grid).cols+target.col);
      }else{
        const cell=nearestFreeCell(d.grid,target.col,target.row,d.index),old=state.layoutTiles[d.index];
        if(old){old.col=cell.col;old.row=cell.row;}
        saveKey('layout',categoryLayoutSnapshot());
      }
    }
    if(d.clone)d.clone.remove();
    d.grid.querySelectorAll('.layout-cell-highlight').forEach(x=>x.remove());
    render();
    setTimeout(setupLayoutGridDrag,50);
  }
  try{d.tile.releasePointerCapture(e.pointerId);}catch(_){ }
  layoutDragState=null;
}
function onSearch(v){
  state.search=String(v||'');
  const grid=document.getElementById('sections-wrap');
  if(!grid)return;
  const q=state.search.trim().toLocaleLowerCase('ru');
  grid.querySelectorAll('.layout-tile').forEach(tile=>{
    if(!q){tile.hidden=false;return;}
    if(tile.dataset.tileType!=='product'){tile.hidden=true;return;}
    const p=getProduct(tile.dataset.id);
    tile.hidden=!(p&&String(p.name||'').toLocaleLowerCase('ru').includes(q));
  });
}
