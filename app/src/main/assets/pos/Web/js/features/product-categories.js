/* Product categories runtime. Compatibility globals are intentional during gradual migration. */
function openCategoriesModal(){
  syncCategoryOrder();
  const cats=state.categoryOrder.slice();
  showModal(`
    <div class="modal-title">Категории</div>
    <div class="center-note category-list-note">Добавляйте, переименовывайте, настраивайте цвет и удаляйте категории.</div>
    <div class="category-toolbar">
      <button class="btn btn-primary category-add-button" onclick="openCategoryModal(null)">+ Добавить категорию</button>
    </div>
    <div class="category-list">
      ${cats.length ? cats.map(c=>{
        const count=state.products.filter(p=>productCategoryKey(p)===c).length;
        return `<div class="category-row">
          <div class="category-main">
            <div class="category-name"><span class="category-swatch" style="--category-color:${state.categoryColors[c]||'#EEF1F5'}"></span>${escapeHtml(c)}</div>
            <div class="category-sub">${count} ${count===1?'товар':'товаров'}</div>
          </div>
          <button type="button" class="web-switch ${state.categoryOnlineMenu[c]!==false?'on':''}" role="switch" aria-label="Показывать категорию ${escapeAttr(c)} в онлайн-меню" aria-checked="${state.categoryOnlineMenu[c]!==false}" onclick="toggleCategoryChannel('${escapeAttr(c)}','menu')" title="Онлайн меню"><span></span><b>МЕНЮ</b></button>
          <button type="button" class="web-switch ${state.categoryOnlineOrder[c]!==false?'on':''}" role="switch" aria-label="Разрешить категорию ${escapeAttr(c)} в онлайн-заказе" aria-checked="${state.categoryOnlineOrder[c]!==false}" onclick="toggleCategoryChannel('${escapeAttr(c)}','order')" title="Онлайн заказ"><span></span><b>ЗАКАЗ</b></button>
          <button type="button" class="icon-btn" aria-label="Изменить категорию ${escapeAttr(c)}" onclick="openCategoryModal('${escapeAttr(c)}')"><span class="ui-icon ui-icon-edit" aria-hidden="true"></span></button>
          <button type="button" class="icon-btn danger" aria-label="Удалить категорию ${escapeAttr(c)}" onclick="deleteCategory('${escapeAttr(c)}')"><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button>
        </div>`;
      }).join('') : '<div class="center-note">Категорий пока нет.</div>'}
    </div>
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Закрыть</button>
    </div>
  `);
}

function openCategoryModal(oldName){
  const returnToCategoryList = true;
  const editing = oldName !== null && oldName !== undefined;
  const colors = [
    '#EEF1F5','#DDEBFF','#DFF5E8','#FFF0C7','#FFE0E0',
    '#E9DDFB','#DDF4F4','#F5E1D3','#E5E5E5','#DCE6F7'
  ];
  const selectedColor = state.categoryColors[oldName] || colors[0];
  window._cmOnlineMenu = editing ? state.categoryOnlineMenu[oldName] !== false : true;
  window._cmOnlineOrder = editing ? state.categoryOnlineOrder[oldName] !== false : true;
  showModal(`
    <div class="modal-title">${editing?'Изменить категорию':'Новая категория'}</div>
    <div class="field"><label>Название категории</label>
      <input type="text" id="cf-name" value="${editing?escapeAttr(oldName):''}" placeholder="Например, Напитки">
    </div>
    <div class="field"><label>Обозначение на плитке</label>
      <input type="text" id="cf-symbol" value="${editing?escapeAttr(state.categorySymbols[oldName]||''):''}" placeholder="До 3 символов: ☕ / К / КФ" autocomplete="off">
      <div class="center-note category-field-note">До 3 символов. Они будут отображаться по центру плитки в рабочей зоне.</div>
    </div>
    <div class="field"><label>Цвет категории</label>
      <div class="category-color-picker">
        ${colors.map(color=>`<button type="button" class="category-color-option ${selectedColor===color?'selected':''}" style="--category-color:${color}" data-color="${color}" onclick="selectCategoryColor('${color}')" aria-label="Выбрать цвет"></button>`).join('')}
      </div>
      <input type="hidden" id="cf-color" value="${selectedColor}">
    </div>
    <div class="field">
      <div class="web-setting-row">
        <div><div class="category-setting-title">Онлайн меню</div><div class="center-note category-web-note">Показывать категорию в меню для просмотра.</div></div>
        <button type="button" id="cf-menu-toggle" class="toggle-switch ${editing&&state.categoryOnlineMenu[oldName]===false?'':'on'}" role="switch" aria-label="Показывать категорию в онлайн-меню" aria-checked="${editing&&state.categoryOnlineMenu[oldName]===false?'false':'true'}" onclick="toggleCategoryModalChannel('menu')"><span></span></button>
      </div>
      <div class="web-setting-row">
        <div><div class="category-setting-title">Онлайн заказ</div><div class="center-note category-web-note">Разрешить товары категории для онлайн-заказа.</div></div>
        <button type="button" id="cf-order-toggle" class="toggle-switch ${editing&&state.categoryOnlineOrder[oldName]===false?'':'on'}" role="switch" aria-label="Разрешить категорию в онлайн-заказе" aria-checked="${editing&&state.categoryOnlineOrder[oldName]===false?'false':'true'}" onclick="toggleCategoryModalChannel('order')"><span></span></button>
      </div>
    </div>
    ${editing?`<div class="center-note category-rename-note">При переименовании категория изменится у всех товаров, которые к ней относятся.</div>`:''}
    <div class="modal-actions">
      <button class="btn btn-secondary" onclick="closeModal()">Отмена</button>
      <button class="btn btn-primary" onclick="saveCategory(${editing?`'${escapeAttr(oldName)}'`:'null'})">Сохранить</button>
    </div>
  `);
}
function selectCategoryColor(color){
  const input=document.getElementById('cf-color');
  if(input) input.value=color;
  document.querySelectorAll('.category-color-option').forEach(b=>b.classList.toggle('selected',b.dataset.color===color));
}

// Keep tile labels Unicode-safe. Emoji such as 👨‍🍳, ❤️ and flags can contain
// several UTF-16 code units while representing a single visible character.
function limitTileSymbol(value, maxSymbols=3){
  const text=(value||'').trim();
  if(!text) return '';
  try{
    if(typeof Intl!=='undefined' && Intl.Segmenter){
      const segmenter=new Intl.Segmenter(undefined,{granularity:'grapheme'});
      return Array.from(segmenter.segment(text), part=>part.segment).slice(0,maxSymbols).join('');
    }
  }catch(_){}
  // Fallback keeps Unicode code points intact on older WebKit.
  return Array.from(text).slice(0,maxSymbols).join('');
}

function saveCategory(oldName){
  const input=document.getElementById('cf-name');
  const name=(input?.value||'').trim();
  if(!name){ flash('Введите название категории'); return; }
  const duplicate=state.categoryOrder.some(c=>c.toLowerCase()===name.toLowerCase() && c!==oldName);
  if(duplicate){ flash('Такая категория уже существует'); return; }
  const color=document.getElementById('cf-color')?.value || '#EEF1F5';
  const symbol=limitTileSymbol(document.getElementById('cf-symbol')?.value||'');
  if(oldName===null || oldName===undefined){
    state.categoryOrder.push(name);
    state.categoryColors[name]=color;
    state.categoryOnlineMenu[name]=window._cmOnlineMenu !== false;
    state.categoryOnlineOrder[name]=window._cmOnlineOrder !== false;
    if(symbol) state.categorySymbols[name]=symbol; else delete state.categorySymbols[name];
  } else {
    if(oldName!==name){const entry=state.posNavigation.categories.find(e=>e.category===oldName);if(entry){entry.category=name;saveKey('posNavigation',state.posNavigation);}}
    const idx=state.categoryOrder.indexOf(oldName);
    if(idx>=0) state.categoryOrder[idx]=name;
    state.products.forEach(p=>{ if(productCategoryKey(p)===oldName) p.category=name; });
    state.layoutTiles.forEach(t=>{ if(t.type==='category' && t.id===oldName) t.id=name; });
    if(oldName!==name) {
      delete state.categoryColors[oldName]; delete state.categorySymbols[oldName];
      state.categoryOnlineMenu[name]=state.categoryOnlineMenu[oldName] !== false;
      state.categoryOnlineOrder[name]=state.categoryOnlineOrder[oldName] !== false;
      delete state.categoryOnlineMenu[oldName]; delete state.categoryOnlineOrder[oldName]; delete state.categoryOnline[oldName];
    }
    state.categoryColors[name]=color;
    state.categoryOnlineMenu[name]=window._cmOnlineMenu !== false;
    state.categoryOnlineOrder[name]=window._cmOnlineOrder !== false;
    if(symbol) state.categorySymbols[name]=symbol; else delete state.categorySymbols[name];
    saveKey('products',state.products);
  }
  state.categoryOnline=state.categoryOnlineOrder;
  saveKey('layout',categoryLayoutSnapshot());
  closeModal(); render(); flash(oldName===null||oldName===undefined?'Категория добавлена':'Категория изменена');
}
function toggleCategoryChannel(name,channel){
  const map=channel==='menu'?state.categoryOnlineMenu:state.categoryOnlineOrder;
  map[name]=map[name]===false;
  state.categoryOnline=state.categoryOnlineOrder;
  saveKey('layout',categoryLayoutSnapshot());
  openCategoriesModal();
  flash(map[name]?(channel==='menu'?'Категория добавлена в онлайн-меню':'Категория доступна для онлайн-заказа'):(channel==='menu'?'Категория скрыта из онлайн-меню':'Категория недоступна для онлайн-заказа'));
}
function toggleCategoryModalChannel(channel){
  const key=channel==='menu'?'_cmOnlineMenu':'_cmOnlineOrder';
  window[key]=!window[key];
  const b=document.getElementById(channel==='menu'?'cf-menu-toggle':'cf-order-toggle');
  if(b){ b.classList.toggle('on',window[key]); b.setAttribute('aria-checked',String(window[key])); }
}
// Compatibility wrappers for cached markup and gradual module migration.
function toggleCategoryOnline(name){ toggleCategoryChannel(name,'order'); }
function toggleCategoryModalOnline(){ toggleCategoryModalChannel('order'); }

function deleteCategory(name){
  const used=state.products.some(p=>productCategoryKey(p)===name);
  if(used){ flash('Нельзя удалить категорию: в ней есть товары'); return; }
  if(!state.categoryOrder.includes(name)) return;
  requestDelete('category', name);
}
