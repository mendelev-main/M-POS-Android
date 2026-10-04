/* Inventory runtime. Compatibility globals are intentional during gradual migration. */
function inventoryDateOnly(value){if(!value)return null;const d=value instanceof Date?new Date(value):new Date(String(value).length===10?value+'T00:00:00':value);if(Number.isNaN(d.getTime()))return null;return new Date(d.getFullYear(),d.getMonth(),d.getDate());}
function inventoryPeriodStart(date=new Date(),frequency=state.inventoryConfig?.frequency){
 const d=inventoryDateOnly(date)||inventoryDateOnly(new Date());
 if(frequency==='weekly'){const day=(d.getDay()+6)%7;const start=new Date(d);start.setDate(d.getDate()-day);return start;}
 if(frequency==='quarterly')return new Date(d.getFullYear(),Math.floor(d.getMonth()/3)*3,1);
 return new Date(d.getFullYear(),d.getMonth(),1);
}
function inventoryPeriodEnd(start,frequency=state.inventoryConfig?.frequency){
 const s=inventoryDateOnly(start);if(!s)return null;
 if(frequency==='weekly'){const e=new Date(s);e.setDate(e.getDate()+6);return e;}
 if(frequency==='quarterly')return new Date(s.getFullYear(),s.getMonth()+3,0);
 return new Date(s.getFullYear(),s.getMonth()+1,0);
}
function inventoryWindow(){
 const c=state.inventoryConfig||{};if(!c.enabled||!c.productIds?.length)return null;
 const frequency=['weekly','monthly','quarterly'].includes(c.frequency)?c.frequency:'monthly',today=inventoryDateOnly(new Date()),start=inventoryPeriodStart(today,frequency),windowEnd=new Date(start);windowEnd.setDate(start.getDate()+6);
 const periodEnd=inventoryPeriodEnd(start,frequency);if(windowEnd>periodEnd)windowEnd.setTime(periodEnd.getTime());
 const last=inventoryDateOnly(c.lastCompletedAt),completed=!!(last&&last>=start&&last<=periodEnd);
 return {frequency,start,end:windowEnd,periodEnd,today,active:today>=start&&today<=windowEnd&&!completed,completed};
}
function inventoryNextDate(){
 const w=inventoryWindow();if(!w)return null;if(w.active)return w.start;
 const next=new Date(w.start);
 if(w.frequency==='weekly')next.setDate(next.getDate()+7);
 else if(w.frequency==='quarterly')next.setMonth(next.getMonth()+3);
 else next.setMonth(next.getMonth()+1);
 return next;
}
function inventoryDaysUntil(){const w=inventoryWindow();if(w?.active)return 0;const n=inventoryNextDate();return n?Math.ceil((n-inventoryDateOnly(new Date()))/86400000):null;}
function renderInventoryTopbarReminder(){const w=inventoryWindow();if(!state.inventoryDraft&&!w?.active)return '';return '<button class="tab inventory-reminder '+(state.tab==='inventoryWork'?'active':'')+'" onclick="openInventoryWork()">Инвентаризация</button>';}
function inventoryTrackedProducts(){const ids=new Set(state.inventoryConfig?.productIds||[]);return state.products.filter(p=>ids.has(p.id)&&productTracksStock(p));}
function setInventoryFrequency(v){state.inventoryConfig.frequency=['weekly','monthly','quarterly'].includes(v)?v:'monthly';render();}
function filterInventoryProducts(value){
 const q=String(value||'').trim().toLocaleLowerCase('ru');document.querySelectorAll('[data-inventory-product-row]').forEach(row=>{row.hidden=!!q&&!String(row.dataset.inventoryProductName||'').includes(q);});
}
function saveInventorySettings(){
 const productIds=[...document.querySelectorAll('[data-inventory-product]:checked')].map(x=>x.value);
 if(!productIds.length){flash('Выберите хотя бы один товар');return;}
 const frequency=['weekly','monthly','quarterly'].includes(state.inventoryConfig.frequency)?state.inventoryConfig.frequency:'monthly';
 state.inventoryConfig={...state.inventoryConfig,enabled:true,frequency,productIds};
 delete state.inventoryConfig.monthDay;delete state.inventoryConfig.customDates;
 saveKey('inventoryConfig',state.inventoryConfig);render();flash('Настройки инвентаризации сохранены');
}
function startInventory(type='scheduled'){
 const ps=inventoryTrackedProducts();if(!ps.length){flash('Нет товаров для инвентаризации');return false;}
 state.inventoryDraft={id:uid(),type,startedAt:Date.now(),scheduledDate:type==='adhoc'?Date.now():(inventoryWindow()?.start||inventoryDateOnly(new Date())).getTime(),items:ps.map(p=>({productId:p.id,name:p.name,unit:stockUnit(p),expected:roundStockQty(Number(p.stock)||0),actual:null}))};
 saveKey('inventoryDraft',state.inventoryDraft);return true;
}
function openInventoryWork(){if(!state.inventoryDraft&&!startInventory('scheduled'))return;state.tab='inventoryWork';render();}
function startAdhocInventory(){
 if(state.inventoryDraft){state.tab='inventoryWork';render();flash('Сначала завершите текущую инвентаризацию');return;}
 if(!startInventory('adhoc'))return;state.tab='inventoryWork';render();
}
function updateInventoryActual(id,v){const x=state.inventoryDraft?.items.find(i=>i.productId===id);if(!x||x.fixedAt)return;const n=Number(String(v).replace(',','.'));x.actual=Number.isFinite(n)&&n>=0?roundStockQty(n):null;saveKey('inventoryDraft',state.inventoryDraft);}
async function fixInventoryItem(id){
 if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
 const d=state.inventoryDraft,x=d?.items.find(i=>i.productId===id);if(!x||x.fixedAt)return false;
 const actual=Number(x.actual);if(x.actual===null||!Number.isFinite(actual)||actual<0){flash('Введите корректный остаток товара');return false;}
 const p=getProduct(x.productId);if(!productTracksStock(p)){flash('Для товара недоступен учёт остатков');return false;}
 const nextProducts=storageSnapshot(state.products),nextDraft=storageSnapshot(d),nextProduct=nextProducts.find(item=>item.id===p.id),nextItem=nextDraft.items.find(item=>item.productId===id);
 nextItem.expected=roundStockQty(Number(nextProduct.stock)||0);nextItem.actual=roundStockQty(actual);nextItem.difference=roundStockQty(nextItem.actual-nextItem.expected);nextItem.fixedAt=Date.now();nextProduct.stock=nextItem.actual;
 criticalOperationBusy=true;
 try{await commitCriticalStorage('inventory-fix',{products:nextProducts,inventoryDraft:nextDraft})}
 catch(e){flash('Остаток не зафиксирован: '+(e?.message||e));return false}
 finally{criticalOperationBusy=false}
 state.products=nextProducts;state.inventoryDraft=nextDraft;void publishAvailability();render();flash('Остаток зафиксирован');return true;
}
function requestCancelInventory(){
 if(!state.inventoryDraft)return;
 if(!currentShiftEmployeeIsAdmin()){flash('Отменить инвентаризацию может только администратор');return;}
 showModal(`<div class="modal-title">Отменить инвентаризацию?</div>
   <div class="center-note inventory-cancel-note">Текущая инвентаризация будет отменена. Введённые и зафиксированные в ней данные не будут сохранены.</div>
   <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Нет, продолжить</button><button class="btn btn-danger" onclick="confirmCancelInventory()">Да, отменить</button></div>`,true);
}
function confirmCancelInventory(){
 if(!currentShiftEmployeeIsAdmin()){closeModal();flash('Отменить инвентаризацию может только администратор');return;}
 if(!state.inventoryDraft){closeModal();return;}
 state.inventoryDraft=null;
 saveKey('inventoryDraft',null);
 closeModal();
 state.tab='inventory';
 render();
 flash('Инвентаризация отменена');
}
function pauseInventory(){saveKey('inventoryDraft',state.inventoryDraft);state.tab='pos';render();flash('Инвентаризация сохранена. Можно продолжить позже');}
function completeInventory(){
 const d=state.inventoryDraft;if(!d)return;if(d.items.some(x=>!x.fixedAt)){flash('Сначала зафиксируйте остаток каждого товара');return;}
 showInventorySummary();
}
function inventorySummaryData(){
 const d=state.inventoryDraft;if(!d)return {rows:[],loss:0,shortages:0,surpluses:0};
 let loss=0,shortages=0,surpluses=0;
 const rows=d.items.map(x=>{const p=getProduct(x.productId),difference=roundStockQty(Number(x.difference||0)),cost=Number(p?.cost)||0,lossAmount=difference<0?Math.abs(difference)*cost:0;if(difference<0)shortages++;if(difference>0)surpluses++;loss+=lossAmount;return {...x,difference,cost,lossAmount};});
 return {rows,loss,shortages,surpluses};
}
function showInventorySummary(){
 const d=state.inventoryDraft;if(!d)return;
 const summary=inventorySummaryData();
 const changed=summary.rows.filter(x=>x.difference!==0);
 showModal(`<div class="modal-title">Сводка инвентаризации</div>
   <div class="grid-3 inventory-summary-stats">
     <div class="stat-box"><div class="label">Недостачи</div><div class="value">${summary.shortages}</div></div>
     <div class="stat-box"><div class="label">Излишки</div><div class="value">${summary.surpluses}</div></div>
     <div class="stat-box"><div class="label">Примерный убыток</div><div class="value">${money(summary.loss)}</div></div>
   </div>
   <div class="inventory-summary-list">
     ${changed.length?changed.map(x=>`<div class="list-row"><div class="inventory-row-main"><div class="list-row-name">${escapeHtml(x.name)}</div><div class="setting-sub">Было: ${stockQtyText(x.expected)} ${unitLabel(x.unit)} · Стало: ${stockQtyText(x.actual)} ${unitLabel(x.unit)}</div></div><div class="inventory-summary-delta"><div class="inventory-summary-difference ${x.difference<0?'is-shortage':'is-surplus'}">${x.difference>0?'+':''}${stockQtyText(x.difference)} ${unitLabel(x.unit)}</div>${x.lossAmount>0?`<div class="setting-sub">≈ −${money(x.lossAmount)}</div>`:''}</div></div>`).join(''):'<div class="center-note">Расхождений нет</div>'}
   </div>
   <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Вернуться</button><button class="btn btn-primary" onclick="confirmCompleteInventory()">Завершить инвентаризацию</button></div>`,true);
}
async function confirmCompleteInventory(){
 if(criticalOperationBusy){flash('Сохранение операции ещё не завершено');return false;}
 const d=state.inventoryDraft;if(!d)return false;
 if(!Array.isArray(d.items)||!d.items.length||d.items.some(x=>!x.fixedAt)){flash('Сначала зафиксируйте остаток каждого товара');return false;}
 const completedAt=Date.now(),summary=inventorySummaryData(),nextProducts=storageSnapshot(state.products),nextHistory=storageSnapshot(state.inventoryHistory),nextConfig=storageSnapshot(state.inventoryConfig);
 nextHistory.unshift({...storageSnapshot(d),completedAt,estimatedLoss:summary.loss,items:storageSnapshot(d.items)});if(d.type!=='adhoc')nextConfig.lastCompletedAt=completedAt;
 criticalOperationBusy=true;
 try{await commitCriticalStorage('inventory-complete',{products:nextProducts,inventoryHistory:nextHistory,inventoryConfig:nextConfig,inventoryDraft:null})}
 catch(e){flash('Инвентаризация не завершена: '+(e?.message||e));return false}
 finally{criticalOperationBusy=false}
 state.products=nextProducts;state.inventoryHistory=nextHistory;state.inventoryConfig=nextConfig;state.inventoryDraft=null;void publishAvailability();state.tab='pos';closeModal();render();flash('Инвентаризация завершена');return true;
}
function renderInventoryScreen(){
 const c=state.inventoryConfig||{};if(!['weekly','monthly','quarterly'].includes(c.frequency))c.frequency='monthly';
 const tracked=state.products.filter(productTracksStock).sort((a,b)=>a.name.localeCompare(b.name,'ru')),w=inventoryWindow(),next=inventoryNextDate();
 const rows=tracked.map(p=>`<label data-inventory-product-row data-inventory-product-name="${escapeAttr(String(p.name||'').toLocaleLowerCase('ru'))}" class="list-row inventory-product-row"><input data-inventory-product class="inventory-product-checkbox" type="checkbox" value="${escapeAttr(p.id)}" ${(c.productIds||[]).includes(p.id)?'checked':''}><div class="inventory-row-main"><div class="list-row-name">${escapeHtml(p.name)}</div><div class="setting-sub">Остаток: ${stockQtyText(p.stock)} ${unitLabel(stockUnit(p))}</div></div></label>`).join('');
 const scheduleText=w?.active?`Окно инвентаризации открыто до ${w.end.toLocaleDateString('ru-RU')} включительно.`:w?.completed?'Инвентаризация в текущем периоде завершена.':next?`Следующее окно начнётся ${next.toLocaleDateString('ru-RU')}.`:'После сохранения настроек окно будет открываться автоматически.';
 return `<div class="screen content-screen ${state.tab==='inventory'?'active':''}"><div class="content-head"><div class="inventory-screen-heading"><button class="btn btn-secondary inventory-back-button" onclick="state.settingsAdminPanel=true;setTab('settings')">← Назад</button><div class="content-title">Инвентаризация</div></div></div><div class="inventory-scroll">
 <div class="card inventory-card-spaced"><div class="inventory-card-title">График</div><div class="setting-sub inventory-schedule-description">${scheduleText} На проведение даётся 7 дней с начала периода.</div><div class="inventory-next-date">Следующая инвентаризация: ${next?next.toLocaleDateString('ru-RU'):'—'}</div>
 <div class="inventory-frequency-grid"><button class="btn ${c.frequency==='weekly'?'btn-primary':'btn-secondary'}" onclick="setInventoryFrequency('weekly')">Раз в неделю</button><button class="btn ${c.frequency==='monthly'?'btn-primary':'btn-secondary'}" onclick="setInventoryFrequency('monthly')">Раз в месяц</button><button class="btn ${c.frequency==='quarterly'?'btn-primary':'btn-secondary'}" onclick="setInventoryFrequency('quarterly')">Раз в квартал</button></div><button class="btn btn-secondary inventory-full-action" onclick="startAdhocInventory()">Внеплановая инвентаризация</button></div>
 <div class="card inventory-card-spaced"><div class="inventory-card-title">Товары для инвентаризации</div><div class="setting-sub inventory-products-description">Выберите товары, которые нужно пересчитывать. Товары с остатком ∞ исключены.</div><input class="inventory-search" type="search" placeholder="Поиск товара" oninput="filterInventoryProducts(this.value)"><div class="inventory-product-list">${rows||'<div class="center-note">Нет товаров с учётом остатков</div>'}</div><button class="btn btn-primary inventory-save-button" onclick="saveInventorySettings()">Сохранить настройки</button></div>
 <div class="card"><div class="inventory-history-head"><div class="inventory-card-title">История</div></div>${state.inventoryHistory.length?state.inventoryHistory.slice(0,50).map(r=>`<div class="list-row"><div class="inventory-row-main"><div class="list-row-name">${fmtDate(r.completedAt)}${r.type==='adhoc'?' · Внеплановая':''}</div><div class="setting-sub">${r.items.length} товаров · расхождений: ${r.items.filter(x=>Number(x.difference)!==0).length} · недостач: ${r.items.filter(x=>Number(x.difference)<0).length}</div></div></div>`).join(''):'<div class="center-note">Инвентаризаций пока не было</div>'}</div></div></div>`;
}

function renderInventoryWorkScreen(){
 const d=state.inventoryDraft;
 if(!d)return `<div class="screen content-screen ${state.tab==='inventoryWork'?'active':''}"><div class="center-note inventory-empty-state">Нет активной инвентаризации.</div></div>`;
 const isAdmin=currentShiftEmployeeIsAdmin(),fixed=d.items.filter(x=>!!x.fixedAt).length;
 const rows=d.items.map(x=>{
   const unit=unitLabel(x.unit),done=!!x.fixedAt,p=getProduct(x.productId),currentExpected=done?roundStockQty(Number(x.expected)||0):roundStockQty(Number(p?.stock)||0),diff=roundStockQty(Number(x.difference||0)),diffText=diff>0?'+'+stockQtyText(diff):stockQtyText(diff);
   const before=done?`<div class="setting-sub inventory-item-expected">Расчётный остаток: ${stockQtyText(currentExpected)} ${unit}</div><div class="inventory-item-difference ${diff<0?'is-shortage':diff>0?'is-surplus':'is-balanced'}">Разница: ${diffText} ${unit}</div>`:(isAdmin?`<div class="setting-sub">Учётный остаток: ${stockQtyText(currentExpected)} ${unit}</div>`:'');
   const control=done?`<div class="inventory-fixed-summary"><div class="setting-sub">Зафиксированный остаток</div><div class="inventory-fixed-value">${stockQtyText(x.actual)} ${unit}</div></div>`:`<div class="inventory-entry"><div class="setting-sub inventory-entry-label">Остаток, ${escapeHtml(unit)}</div><div class="inventory-entry-controls"><input class="inventory-actual-input" type="number" min="0" step="0.001" inputmode="decimal" value="${x.actual===null?'':escapeAttr(String(x.actual))}" placeholder="0" onchange="updateInventoryActual('${escapeAttr(x.productId)}',this.value)"><button class="btn btn-primary inventory-fix-button" onclick="fixInventoryItem('${escapeAttr(x.productId)}')">Фиксировать</button></div></div>`;
   return `<div class="list-row inventory-work-row"><div class="inventory-row-main"><div class="list-row-name">${escapeHtml(x.name)}</div>${before}</div>${control}</div>`;
 }).join('');
 return `<div class="screen content-screen ${state.tab==='inventoryWork'?'active':''}">
   <div class="content-head inventory-work-head"><div><div class="content-title">${d.type==='adhoc'?'Внеплановая инвентаризация':'Инвентаризация'}</div><div class="setting-sub">Зафиксировано: ${fixed} из ${d.items.length}</div></div>${isAdmin?'<button class="btn btn-danger inventory-cancel-button" onclick="requestCancelInventory()">Отменить инвентаризацию</button>':''}</div>
   <div class="inventory-work-layout">
     <div class="card inventory-work-list">${rows||'<div class="center-note">Нет товаров для пересчёта</div>'}</div>
     <div class="inventory-work-actions">
       <button class="btn btn-secondary inventory-work-action" onclick="pauseInventory()">Продолжить позже</button>
       <button class="btn btn-primary inventory-work-action" onclick="completeInventory()">Завершить инвентаризацию</button>
     </div>
   </div>
 </div>`;
}
