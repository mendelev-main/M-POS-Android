/* Product catalog runtime. Compatibility globals are intentional during gradual migration. */
/* ============================= PRODUCTS SCREEN ============================= */
/* Name/category-only CSV import. Additive, local, and explicitly confirmed. */
function cleanImportedProductName(value){
  return String(value||'').normalize('NFC').replace(/[\u200B-\u200D\uFEFF\uFE0F❄]/g,'').replace(/^[\s*#]+/,'').replace(/[\[\]]/g,' ').replace(/\bNo\s*(\d+)/g,'№ $1').replace(/\s+/g,' ').trim();
}
function importedProductPrice(value){
  const raw=String(value??'').trim();
  if(!raw||raw.toLocaleLowerCase('ru')==='изменяемая')return null;
  if(!/^\d+(?:[.,]\d{1,2})?$/.test(raw))throw new Error('Некорректная цена: '+raw);
  const price=Number(raw.replace(',','.'));
  if(!Number.isFinite(price)||price>Number.MAX_SAFE_INTEGER/100)throw new Error('Цена слишком велика');
  return price;
}
function parseProductCSV(text){
  text=String(text).replace(/^\uFEFF/,'');
  const first=text.split(/\r?\n/,1)[0],delimiter=first.includes(';')&&!first.includes(',')?';':',';
  const rows=[];let row=[],cell='',quoted=false,closed=false;
  for(let i=0;i<text.length;i++){
    const c=text[i];
    if(quoted){if(c==='"'){if(text[i+1]==='"'){cell+='"';i++;}else{quoted=false;closed=true;}}else cell+=c;continue;}
    if(c==='"'){if(cell||closed)throw new Error('Некорректные кавычки в CSV');quoted=true;continue;}
    if(c===delimiter){row.push(cell);cell='';closed=false;continue;}
    if(c==='\r'||c==='\n'){if(c==='\r'&&text[i+1]==='\n')i++;row.push(cell);if(row.some(v=>v.trim()))rows.push(row);row=[];cell='';closed=false;continue;}
    if(closed)throw new Error('Лишний текст после кавычек в CSV');cell+=c;
  }
  if(quoted)throw new Error('Незакрытые кавычки в CSV');
  row.push(cell);if(row.some(v=>v.trim()))rows.push(row);
  if(rows.length<2)throw new Error('В файле нет товаров');
  const headers=rows.shift().map(v=>v.trim().normalize('NFC').toLowerCase());
  const nameIndex=headers.indexOf('название'),categoryIndex=headers.indexOf('категория'),priceIndex=headers.includes('цена')?headers.indexOf('цена'):headers.indexOf('цена [project]');
  if(nameIndex<0)throw new Error('Нужна колонка «Название»');
  if(new Set(headers).size!==headers.length)throw new Error('Повторяющиеся заголовки CSV');
  if(rows.length>5000)throw new Error('За один импорт можно добавить до 5000 товаров');
  return rows.map((r,i)=>{if(r.length!==headers.length)throw new Error('Неверное число колонок в строке '+(i+2));return {name:r[nameIndex],category:categoryIndex<0?'':r[categoryIndex],price:priceIndex<0?null:importedProductPrice(r[priceIndex])};});
}
function planProductImport(entries){
  const key=v=>String(v||'').normalize('NFC').trim().replace(/\s+/g,' ').toLocaleLowerCase('ru');
  const cats=new Map();for(const c of [...state.categoryOrder,...state.products.map(p=>p.category)])if(c&&c!=='Без категории')cats.set(key(c),c);
  const counts=new Map();for(const e of entries){const n=key(cleanImportedProductName(e.name));counts.set(n,(counts.get(n)||0)+1);}
  const known=new Set(state.products.map(p=>key(cleanImportedProductName(p.name)))),add=[],skipped=[];
  for(const entry of entries){
    let name=cleanImportedProductName(entry.name);
    if(counts.get(key(name))>1&&/^сырь[её]$/i.test(String(entry.category).normalize('NFC').trim()))name+=' (сырьё)';
    if(!name)throw new Error('Найден товар без названия');
    if(name.length>200)throw new Error('Название длиннее 200 символов: '+name.slice(0,40));
    if(known.has(key(name))){skipped.push(name);continue;}known.add(key(name));
    add.push({name,category:cats.get(key(entry.category))||'',price:importedProductPrice(entry.price)});
  }
  return {add,skipped};
}
function openProductImport(){
  showModal(`<h2>Импорт товаров</h2><p>Добавить товары, цены и совпадающие категории из CSV.</p><p class="pe-note">Остатки и состав заполните вручную. Пустые и изменяемые цены нужно назначить после импорта. Существующие товары сохранятся.</p><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="chooseProductCSV()">Выбрать CSV</button></div><button class="btn btn-outline product-import-backup" onclick="closeModal();importBackup()">Восстановить резервную копию JSON</button>`);
}
function chooseProductCSV(){
  const input=document.createElement('input');input.type='file';input.accept='.csv,text/csv';
  input.onchange=()=>{const file=input.files?.[0];if(!file)return;if(file.size>5*1024*1024){flash('Файл больше 5 МБ');return;}
    const reader=new FileReader();reader.onerror=()=>flash('Не удалось прочитать файл');reader.onload=()=>{try{window._productImportEntries=parseProductCSV(reader.result);previewProductImport();}catch(e){flash(e.message);}};reader.readAsText(file,'UTF-8');};input.click();
}
function previewProductImport(){
  const plan=planProductImport(window._productImportEntries||[]);
  window._productImportPreview=JSON.stringify(plan.add);
  showModal(`<h2>Проверка импорта</h2><p>Добавить: <b>${plan.add.length}</b> · Уже есть или повторяются: <b>${plan.skipped.length}</b></p><p class="pe-note">Цены берутся из файла. Пустая или изменяемая цена — 0 до ручного заполнения. Себестоимость и остаток — 0; публикация в WEB выключена. Единицы и состав не назначены.</p><div class="product-import-preview">${plan.add.map(p=>`<div class="product-import-row"><b>${escapeHtml(p.name)}</b><div class="pe-note">${escapeHtml(p.category||'Без категории')} · ${p.price===null?'Цена не задана — заполните вручную':money(p.price)}</div></div>`).join('')}${plan.skipped.length?`<details><summary>Пропущенные товары</summary>${plan.skipped.map(n=>`<p>${escapeHtml(n)}</p>`).join('')}</details>`:''}</div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button id="product-import-confirm" class="btn btn-primary" ${plan.add.length?'':'disabled'} onclick="confirmProductImport()">Добавить товары</button></div>`,true);
}
async function confirmProductImport(){
  if(window._productImportBusy)return;
  const plan=planProductImport(window._productImportEntries||[]);
  if(JSON.stringify(plan.add)!==window._productImportPreview){previewProductImport();flash('Список обновился — проверьте импорт ещё раз');return;}
  if(!plan.add.length)return;
  window._productImportBusy=true;
  const app=document.getElementById('app'),wasInert=app.inert;app.inert=true;
  const button=document.getElementById('product-import-confirm');if(button)button.disabled=true;
  try{
    const start=Math.max(0,...state.products.map(p=>Number(p.sortOrder)||0));
    const added=plan.add.map((p,i)=>({id:uid(),name:p.name,category:p.category,type:'simple',price:p.price??0,cost:0,stock:0,noStockTracking:false,availableOnline:false,availableInOnlineMenu:false,description:'',imageUrl:'',tileSymbol:'',sortOrder:start+i+1}));
    const next=[...state.products,...added];
    try{await window.PrilavokCore.Storage.set('products',next);}
    catch(e){markStorageBroken(e);flash('Импорт не сохранён: '+(e.message||'ошибка хранилища'));if(button)button.disabled=false;return;}
    state.products=next;window._productImportEntries=null;window._productImportPreview=null;
    closeModal();render();flash('Добавлено товаров: '+added.length);
  }finally{app.inert=wasInert;window._productImportBusy=false;}
}

function productSearchText(value){return String(value||'').normalize('NFC').toLocaleLowerCase('ru').replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
function productMatchesSearch(product,query){return !query||(productSearchText(product.name)+' '+productSearchText(productCategoryKey(product))).includes(query);}
function sortProductsBy(key){
  if(!['name','category','type','price','cost','web','stock'].includes(key))return;
  const previous=window._productsSort||{key:'name',direction:1};
  window._productsSort={key,direction:previous.key===key?-previous.direction:1};
  render();
}
function sortedProductRows(){
  const {key,direction}=window._productsSort||{key:'name',direction:1};
  const value=p=>{
    if(key==='category')return productCategoryKey(p);
    if(key==='type')return p.type==='composite'?'Составной':'Простой';
    if(key==='web')return p.availableOnline!==false?1:0;
    if(key==='stock'){const n=p.noStockTracking?Infinity:availableStock(p);return Number.isFinite(n)?n:null;}
    if(key==='price'||key==='cost'){const n=Number(key==='cost'?compositeCost(p):p.price);return Number.isFinite(n)?n:null;}
    return p.name;
  };
  return state.products.map(p=>({p,value:value(p)})).sort((a,b)=>{
    if(a.value===null&&b.value!==null)return 1;
    if(b.value===null&&a.value!==null)return -1;
    const order=a.value===null?0:typeof a.value==='number'?a.value-b.value:String(a.value).localeCompare(String(b.value),'ru',{sensitivity:'base',numeric:true});
    return order*direction||a.p.name.localeCompare(b.p.name,'ru',{sensitivity:'base',numeric:true});
  }).map(row=>row.p);
}
function productSortHeader(key,label){
  const sort=window._productsSort||{key:'name',direction:1},active=sort.key===key;
  return `<button type="button" class="products-sort-header ${active?'selected':''}" onclick="sortProductsBy('${key}')" aria-label="${label}: ${active?(sort.direction===1?'по возрастанию':'по убыванию'):'сортировать'}">${label}<span class="product-sort-mark ${active?(sort.direction===1?'asc':'desc'):''}" aria-hidden="true"></span></button>`;
}

function renderProductsScreen(){
  syncCategoryOrder();
  const cats = state.categoryOrder.slice();
  const query = productSearchText(state.productsSearch);
  const sortedProducts = sortedProductRows();
  const filteredProducts = sortedProducts.filter(p=>productMatchesSearch(p,query));
  const rows = sortedProducts.map(p=>{
    const avail = availableStock(p);
    const typeLabel = p.type==='composite'?'Составной':'Простой';
    const stockLabel = avail===Infinity ? 'Не учитывается' : stockQtyText(avail)+' '+unitLabel(stockUnit(p))+(p.type==='simple' && p.minStock!=null && avail<=p.minStock?' · низкий':'');
    return `
    <div class="products-table-row" data-product-search="${escapeAttr(productSearchText(p.name)+' '+productSearchText(productCategoryKey(p)))}" ${productMatchesSearch(p,query)?'':'hidden'} role="button" tabindex="0" onclick="openProductModal(${productInlineArg(p.id)})" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();openProductModal(${productInlineArg(p.id)})}" aria-label="Открыть товар ${escapeAttr(p.name)}">
      <div class="products-table-main">
        <div class="products-table-name">${escapeHtml(p.name)}</div>
        <div class="products-table-sub">${escapeHtml(productCategoryKey(p))}</div>
        <div class="products-mobile-meta">
          <span class="badge ${p.type==='composite'?'type-composite':''}">${typeLabel}</span>
          <span class="badge">${p.noStockTracking?'Остаток: ∞':'Остаток '+stockLabel}</span>
        </div>
      </div>
      <div class="products-table-cell products-table-muted">${escapeHtml(productCategoryKey(p))}</div>
      <div class="products-table-cell"><span class="badge ${p.type==='composite'?'type-composite':''}">${typeLabel}</span></div>
      <div class="products-table-cell">${money(p.price)}</div>
      <div class="products-table-cell">${money(compositeCost(p))}</div>
      <div class="products-table-cell products-web-cell"><button type="button" class="web-switch ${p.availableOnline!==false?'on':''}" role="switch" aria-label="Онлайн заказ для товара ${escapeAttr(p.name)}" aria-checked="${p.availableOnline!==false}" onclick="event.stopPropagation();toggleProductOnline(${productInlineArg(p.id)})" title="Онлайн заказ"><span></span><b>Заказ</b></button></div>
      <div class="products-table-cell products-stock">${p.noStockTracking?'—':stockLabel}</div>
    </div>`;
  }).join('');

  return `
  <div class="screen content-screen ${state.tab==='products'?'active':''}">
    <div class="content-head">
      <div class="content-title">Товары</div>
      <div class="products-head-actions">
        <button class="btn btn-secondary products-head-action" onclick="openProductImport()">Импорт</button>
        <button class="btn btn-secondary products-head-action" onclick="openCategoriesModal()">Категории</button>
        <button class="btn btn-primary products-head-action products-add-action" onclick="openProductModal(null)">Добавить товар</button>
      </div>
    </div>
    <div class="card products-search-card">
      <div class="field products-search-field">
        <label>Поиск товаров</label>
        <div class="products-search-wrap">
          <input id="products-search" class="products-search-input" type="search" value="${escapeAttr(state.productsSearch||'')}" placeholder="Название или категория" oninput="filterProductsScreen()" onsearch="filterProductsScreen()">
          <button type="button" class="icon-btn products-search-clear" onclick="clearProductsSearch()" aria-label="Очистить поиск" ${query?'':'hidden'}><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button>
        </div>
      </div>
    </div>
    <div class="card">
      <div class="section-mini-head"><div class="products-section-title">Товары</div><span class="badge">${query?filteredProducts.length+' / ':''}${state.products.length}</span></div>
      <div id="products-search-table" class="products-table" ${filteredProducts.length?'':'hidden'}>
        <div class="products-table-head">${[['name','Товар'],['category','Категория'],['type','Тип'],['price','Цена'],['cost','Себестоимость'],['web','Онлайн заказ'],['stock','Остаток']].map(([key,label])=>productSortHeader(key,label)).join('')}</div>
        ${rows}
      </div><div id="products-search-empty" class="center-note products-search-empty" ${filteredProducts.length?'hidden':''}>${state.products.length?'По вашему запросу товары не найдены.':'Пока нет товаров. Добавьте первый.'}</div>
    </div>
  </div>`;
}

function filterProductsScreen(){
  const input=document.getElementById('products-search');
  state.productsSearch=input?.value||'';
  const card=input?.closest('.content-screen');
  if(!card) return render();
  const query=productSearchText(state.productsSearch);
  const rows=[...card.querySelectorAll('.products-table-row')];
  let visible=0;
  rows.forEach(row=>{
    const text=row.dataset.productSearch;
    const show=!query || text.includes(query);
    row.hidden=!show;
    if(show) visible++;
  });
  const table=document.getElementById('products-search-table'),empty=document.getElementById('products-search-empty');
  if(table)table.hidden=!visible;
  if(empty)empty.hidden=!!visible;
  const badge=card.querySelector('.section-mini-head .badge');
  if(badge) badge.textContent=query?`${visible} / ${state.products.length}`:String(state.products.length);
  const clear=card.querySelector('[aria-label="Очистить поиск"]');
  if(clear) clear.hidden=!query;
}
function clearProductsSearch(){
  state.productsSearch='';
  const input=document.getElementById('products-search');
  if(input){ input.value=''; input.focus(); }
  filterProductsScreen();
}
