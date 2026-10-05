const LOYALTY_API_TIMEOUT_MS=5000;
function loyaltyInlineArg(value){return escapeAttr(JSON.stringify(String(value??'')));}
async function loyaltyApi(path,options={}){
 const n=networkConfigFromState(),controller=new AbortController(),externalSignal=options.signal;
 const relayAbort=()=>controller.abort();
 if(externalSignal){if(externalSignal.aborted)controller.abort();else externalSignal.addEventListener('abort',relayAbort,{once:true})}
 const timeout=setTimeout(()=>controller.abort(),LOYALTY_API_TIMEOUT_MS);
 try{
  const r=await fetch(n.backendUrl+path,{...options,signal:controller.signal,headers:{'Content-Type':'application/json','X-Device-Key':n.deviceKey,...(options.headers||{})},cache:'no-store'});
  const d=await r.json().catch(()=>null);if(!r.ok)throw new Error(d?.error||('HTTP '+r.status));return d;
 }catch(e){
  if(e?.name==='AbortError'&&!externalSignal?.aborted)throw new Error('Сервер не ответил вовремя');
  throw e;
 }finally{
  clearTimeout(timeout);externalSignal?.removeEventListener?.('abort',relayAbort);
 }
}
async function searchCustomers(q){
 const term=String(q||'').trim();if(term.length<2)return [];
 const data=await loyaltyApi('/api/customers/search?q='+encodeURIComponent(term));return data.customers||[];
}
async function loadCustomerLoyalty(customerId){
 if(!customerId){state.loyaltyPrograms=[];state.loyaltyRedemptions={};state.loyaltyCustomerId='';state.loyaltyLoadingCustomerId='';state.loyaltyLoadError='';return}
 const selectedCustomer=state.customer;
 state.loyaltyLoadingCustomerId=String(customerId);state.loyaltyLoadError='';if(typeof refreshOrderCustomerModal==='function')refreshOrderCustomerModal();
 try{
  const data=await loyaltyApi('/api/customers/'+encodeURIComponent(customerId)+'/loyalty');
  if(state.customer!==selectedCustomer||String(state.customer?.id)!==String(customerId))return;
  state.customer={...state.customer,id:data.customer.id,name:data.customer.name,phone:data.customer.normalized_phone};
  state.loyaltyPrograms=data.programs||[];state.loyaltyRedemptions={};state.loyaltyCustomerId=String(customerId);state.loyaltyLoadingCustomerId='';state.loyaltyLoadError='';saveCurrentOrderSession();render();if(typeof refreshOrderCustomerModal==='function')refreshOrderCustomerModal();
 }catch(e){
  if(state.customer===selectedCustomer&&String(state.customer?.id)===String(customerId)){state.loyaltyLoadingCustomerId='';state.loyaltyLoadError=String(e?.message||e);if(typeof refreshOrderCustomerModal==='function')refreshOrderCustomerModal()}
  throw e;
 }
}
function openOrderCustomer(){
 if(!state.customer?.id)return openCustomerPicker();
 renderOrderCustomerModal();
 const id=String(state.customer.id);if(state.loyaltyCustomerId!==id&&state.loyaltyLoadingCustomerId!==id)void loadCustomerLoyalty(id).catch(()=>{});
}
function renderOrderCustomerModal(){
 if(!state.customer?.id)return;
 showModal(`<div class="customer-profile-heading"><div class="customer-avatar" aria-hidden="true">${customerInitial(state.customer.name)}</div><div><div class="modal-title">${escapeHtml(state.customer.name||'Клиент')}</div><div class="customer-profile-phone">${escapeHtml(state.customer.phone||'')}</div></div></div><div class="loyalty-summary-section">${loyaltySummaryHtml()}</div><div class="modal-actions customer-profile-actions"><button class="btn btn-secondary" onclick="openCustomerPicker()">Сменить клиента</button><button class="btn btn-primary" onclick="closeModal();render()">Готово</button></div><button class="btn btn-secondary loyalty-remove-customer" onclick="removeOrderCustomer()">Убрать клиента из заказа</button>`,false);
 document.querySelector('#modal-root .modal')?.classList.add('customer-profile-modal');
}
function refreshOrderCustomerModal(){if(typeof document!=='undefined'&&document.querySelector?.('#modal-root .customer-profile-modal'))renderOrderCustomerModal()}
function retryCustomerLoyalty(){const id=state.customer?.id;if(id)void loadCustomerLoyalty(id).catch(()=>{})}
function setLoyaltyRedemption(programId,value){state.loyaltyRedemptions[programId]=Number(value)>0?1:0;saveCurrentOrderSession();render();renderOrderCustomerModal()}
function loyaltyReceiptSnapshot(allocation=loyaltyRewardAllocation()){
 const programs=[];
 for(const program of state.loyaltyPrograms||[]){const rewards=Math.max(0,Math.trunc(Number(state.loyaltyRedemptions?.[program.id])||0)),discount=Number(allocation.programDiscounts?.[program.id])||0;if(rewards&&discount>0)programs.push({id:String(program.id),name:String(program.name||'Программа лояльности'),rewards,discount:Math.round(discount*100)/100})}
 return {discount:Math.round((Number(allocation.discount)||0)*100)/100,programs};
}
function loyaltySummaryHtml(){
 if(!state.customer?.id)return '<div class="setting-sub">Выберите клиента, чтобы использовать программу лояльности.</div>';
 if(state.loyaltyLoadingCustomerId===String(state.customer.id))return '<div class="customer-loyalty-state"><strong>Загружаем программы</strong><span>Данные клиента обновляются…</span></div>';
 if(state.loyaltyLoadError)return '<div class="customer-loyalty-state is-error"><strong>Не удалось загрузить программы</strong><span>Проверьте интернет и повторите.</span><button class="btn btn-secondary" onclick="retryCustomerLoyalty()">Повторить</button></div>';
 if(!state.loyaltyPrograms.length)return '<div class="setting-sub">Для клиента пока нет активных программ.</div>';
 return state.loyaltyPrograms.map(p=>{const available=Number(p.rewards)>0;return `<div class="loyalty-summary-card"><div class="loyalty-summary-main"><div class="list-row-name">${escapeHtml(p.name)}</div><div class="list-row-sub">${available?'Накопление приостановлено до использования подарка':`Прогресс: ${Number(p.progress)||0} из ${Number(p.required_quantity)||0}`}</div></div><div class="loyalty-reward-count"><span>Подарок:</span><strong>${available?'доступен':'нет'}</strong></div>${available?`<select aria-label="Использовать подарок программы ${escapeAttr(p.name)}" onchange="setLoyaltyRedemption(${loyaltyInlineArg(p.id)},this.value)"><option value="0" ${!Number(state.loyaltyRedemptions?.[p.id])?'selected':''}>Не использовать</option><option value="1" ${Number(state.loyaltyRedemptions?.[p.id])===1?'selected':''}>Использовать подарок</option></select>`:''}</div>`}).join('');
}
async function openCustomerPicker(){
 ++customerSearchSeq;
 showModal(`<div class="customer-picker-heading"><button class="icon-btn customer-picker-close" type="button" aria-label="Закрыть" title="Закрыть" onclick="closeModal()"><span class="ui-icon ui-icon-close" aria-hidden="true"></span></button><div class="modal-title">Клиент заказа</div><button class="icon-btn customer-picker-add" type="button" aria-label="Добавить нового клиента" title="Добавить нового клиента" onclick="openCreateCustomer()"><span class="ui-icon ui-icon-add" aria-hidden="true"></span></button></div><div class="field customer-search-field"><label for="customer-search">Номер телефона</label><div class="customer-phone-field"><span>+375</span><input id="customer-search" type="tel" inputmode="numeric" pattern="[0-9]*" maxlength="9" autocomplete="off" enterkeyhint="search" placeholder="29 123 45 67" aria-label="Номер телефона после +375" oninput="customerSearchChanged(this.value)"></div></div><div id="customer-search-results" class="customer-picker-results"><div class="center-note">Введите минимум 4 цифры номера</div></div>`,false);
 document.querySelector('#modal-root .modal')?.classList.add('customer-picker-modal');
}
let customerSearchSeq=0;
function customerPhoneDigits(value){
 const raw=String(value||''),digits=raw.replace(/\D/g,'');
 return ((raw.trim().startsWith('+375')||digits.length===12&&digits.startsWith('375'))?digits.slice(3):digits).slice(0,9);
}
function customerInitial(name){return escapeHtml((String(name||'?').trim().charAt(0)||'?').toLocaleUpperCase('ru'))}
function customerResultCards(rows){return rows.map(x=>`<button class="customer-result-card" data-customer-id="${escapeAttr(x.id)}"><span class="customer-result-avatar" aria-hidden="true">${customerInitial(x.name)}</span><span class="customer-result-copy"><strong>${escapeHtml(x.name)}</strong><small>${escapeHtml(x.normalized_phone)}</small></span><span class="customer-result-action">Выбрать</span></button>`).join('')}
async function customerSearchChanged(value){
 const seq=++customerSearchSeq,el=document.getElementById('customer-search-results'),input=document.getElementById('customer-search');
 const digits=customerPhoneDigits(value);if(input)input.value=digits;if(!el)return;
 if(digits.length<4){el.innerHTML='<div class="center-note">Введите минимум 4 цифры номера</div>';return}
 const phone='+375'+digits;el.innerHTML='<div class="center-note">Поиск…</div>';
 try{
  const rows=(await searchCustomers(phone)).filter(x=>String(x.normalized_phone||'').replace(/\D/g,'').startsWith('375'+digits));
  if(seq!==customerSearchSeq||document.getElementById('customer-search-results')!==el)return;
  el.innerHTML=rows.length?customerResultCards(rows):'<div class="center-note">Клиент не найден. Можно создать нового.</div>';
  el.querySelectorAll('[data-customer-id]').forEach(button=>{button.onclick=()=>{const x=rows.find(x=>String(x.id)===button.dataset.customerId);if(x)selectCustomer(x.id,x.name,x.normalized_phone)}});
 }catch(e){if(seq===customerSearchSeq&&document.getElementById('customer-search-results')===el)el.innerHTML='<div class="center-note">Нет связи с сервером. Повторите поиск.</div>'}
}
function removeOrderCustomer(){
 ++customerSearchSeq;
 state.customer={name:'',phone:'',address:state.customer?.address||''};
 state.loyaltyPrograms=[];state.loyaltyRedemptions={};state.loyaltyCustomerId='';state.loyaltyLoadingCustomerId='';state.loyaltyLoadError='';
 saveCurrentOrderSession();closeModal();render();
}
async function selectCustomer(id,name,phone){
 state.customer={...state.customer,id,name,phone};
 state.loyaltyPrograms=[];state.loyaltyRedemptions={};state.loyaltyCustomerId='';state.loyaltyLoadingCustomerId=String(id);state.loyaltyLoadError='';
 const selectedCustomer=state.customer;
 saveCurrentOrderSession();closeModal();render();
 try{await loadCustomerLoyalty(id)}catch(e){if(state.customer===selectedCustomer){flash('Клиент выбран, но loyalty недоступна');render()}}
}
function openCreateCustomer(){showModal(`<div class="modal-title">Новый клиент</div><div class="field"><label>Имя</label><input id="new-customer-name"></div><div class="field"><label>Телефон</label><input id="new-customer-phone" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="off"></div><div class="modal-actions"><button class="btn btn-secondary" onclick="openCustomerPicker()">Назад</button><button class="btn btn-primary" onclick="createCustomerFromPos()">Создать</button></div>`,true)}
async function createCustomerFromPos(){try{const data=await loyaltyApi('/api/customers',{method:'POST',body:JSON.stringify({name:document.getElementById('new-customer-name')?.value,phone:document.getElementById('new-customer-phone')?.value})});await selectCustomer(data.customer.id,data.customer.name,data.customer.normalized_phone)}catch(e){flash(e.message||'Не удалось создать клиента')}}
async function publishPaidOrderLoyalty(order){
 if(!order?.customer?.id||order.loyaltySync?.status==='sending')return;
 order.loyaltySync={...(order.loyaltySync||{}),status:'sending',attemptedAt:Date.now()};
 try{await window.PrilavokCore.Storage.set('orders',state.orders)}catch(e){order.loyaltySync={...(order.loyaltySync||{}),status:'pending',error:'local-persist-before-send',at:Date.now()};markStorageBroken(e);return}
 try{const data=await loyaltyApi('/api/loyalty/sales',{method:'POST',body:JSON.stringify({orderId:order.id,customerId:order.customer.id,items:(order.items||[]).map(x=>({productId:x.productId,quantity:x.qty})),redemptions:order.loyaltyRedemptions||{},rewardAllocations:order.loyaltyRewardAllocations||{}})});order.loyaltySync={status:'synced',at:Date.now(),events:data.events||[]};await window.PrilavokCore.Storage.set('orders',state.orders);if(order.returnedAt&&order.loyaltyReversal?.status==='pending')await reverseOrderLoyalty(order)}
 catch(e){order.loyaltySync={status:'pending',error:String(e?.message||e),at:Date.now()};try{await window.PrilavokCore.Storage.set('orders',state.orders)}catch(storageError){markStorageBroken(storageError)}}
}
async function reverseOrderLoyalty(order){
 if(!order?.customer?.id||order.loyaltyReversal?.status==='sending'||order.loyaltyReversal?.status==='synced')return;
 order.loyaltyReversal={...(order.loyaltyReversal||{}),status:'sending',attemptedAt:Date.now()};
 try{await window.PrilavokCore.Storage.set('orders',state.orders)}catch(e){order.loyaltyReversal={...(order.loyaltyReversal||{}),status:'pending',error:'local-persist-before-send',at:Date.now()};markStorageBroken(e);return}
 try{await loyaltyApi('/api/loyalty/reversal',{method:'POST',body:JSON.stringify({orderId:order.id,customerId:order.customer.id})});order.loyaltyReversal={status:'synced',at:Date.now()};await window.PrilavokCore.Storage.set('orders',state.orders)}catch(e){order.loyaltyReversal={status:'pending',error:String(e?.message||e),at:Date.now()};try{await window.PrilavokCore.Storage.set('orders',state.orders)}catch(storageError){markStorageBroken(storageError)}}
}
async function settleReturnedOrderLoyalty(order){
 if(!order?.customer?.id||!order.returnedAt)return;
 if(order.loyaltySync?.status==='pending'){await publishPaidOrderLoyalty(order);return}
 if(order.loyaltySync?.status==='sending')return;
 if(order.loyaltySync?.status==='synced'&&order.loyaltyReversal?.status!=='synced')await reverseOrderLoyalty(order);
}

function retryPendingLoyalty(){for(const order of state.orders||[]){if(!order?.customer?.id)continue;if(order.returnedAt)void settleReturnedOrderLoyalty(order);else if(order?.loyaltySync?.status==='pending')void publishPaidOrderLoyalty(order)}}

function loyaltyRewardAllocation(){
  const units=[];
  for(const [lineIndex,item] of (state.cart||[]).entries())for(let unitIndex=0;unitIndex<Math.max(0,Math.trunc(Number(item.qty)||0));unitIndex++)units.push({id:lineIndex+':'+unitIndex,productId:String(item.productId),price:Math.max(0,Number(item.price)||0)});
  const used=new Set(),allocations={},programDiscounts={};let discount=0;
  for(const program of state.loyaltyPrograms||[]){
    const requested=Number(state.loyaltyRedemptions?.[program.id])>0?1:0;if(!requested)continue;
    const allowed=new Set((program.loyalty_reward_products||[]).map(x=>String(x.product_id)));
    const selected=units.filter(unit=>!used.has(unit.id)&&allowed.has(unit.productId)).sort((a,b)=>a.price-b.price).slice(0,requested);
    if(selected.length!==requested)continue;
    allocations[program.id]=[];programDiscounts[program.id]=0;
    for(const unit of selected){used.add(unit.id);discount+=unit.price;programDiscounts[program.id]+=unit.price;const row=allocations[program.id].find(x=>x.productId===unit.productId);if(row)row.quantity++;else allocations[program.id].push({productId:unit.productId,quantity:1})}
  }
  Object.keys(programDiscounts).forEach(id=>{programDiscounts[id]=Math.round(programDiscounts[id]*100)/100});
  return {discount:Math.round(discount*100)/100,allocations,programDiscounts};
}
function loyaltyRewardDiscount(){
  return loyaltyRewardAllocation().discount;
}

async function openCustomersAdmin(){
 try{const rows=await searchCustomers('375');showModal(`<div class="modal-title">Клиенты</div><div class="field"><label>Поиск</label><input id="admin-customer-search" oninput="adminCustomerSearch(this.value)" placeholder="Имя или телефон"></div><div id="admin-customers-list">${customerAdminRows(rows)}</div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Закрыть</button></div>`,true)}catch(e){flash('Не удалось загрузить клиентов')}
}
function customerAdminRows(rows){return (rows||[]).map(x=>`<button class="list-row loyalty-customer-row" onclick="openCustomerAdminCard(${loyaltyInlineArg(x.id)})"><div><div class="list-row-name">${escapeHtml(x.name)}</div><div class="list-row-sub">${escapeHtml(x.normalized_phone)}${x.telegram_user_id?' · Telegram ✓':''}</div></div></button>`).join('')||'<div class="center-note">Клиенты не найдены</div>'}
async function adminCustomerSearch(q){const el=document.getElementById('admin-customers-list');if(!el)return;try{el.innerHTML=customerAdminRows(await searchCustomers(q))}catch(e){el.textContent='Ошибка загрузки'}}
async function openCustomerAdminCard(id){
 try{
  const [d,h,o]=await Promise.all([loyaltyApi('/api/customers/'+encodeURIComponent(id)+'/loyalty'),loyaltyApi('/api/customers/'+encodeURIComponent(id)+'/ledger'),loyaltyApi('/api/customers/'+encodeURIComponent(id)+'/orders')]);
  showModal(`<div class="modal-title">${escapeHtml(d.customer.name)}</div><div class="settings-note">${escapeHtml(d.customer.normalized_phone)}${d.customer.telegram_user_id?' · Telegram привязан':''}</div><div class="loyalty-customer-programs">${(d.programs||[]).map(p=>`<div class="list-row"><div class="loyalty-customer-program-main"><div class="list-row-name">${escapeHtml(p.name)}</div><div class="list-row-sub">${Number(p.rewards)>0?'Подарок доступен · накопление приостановлено':`Прогресс ${p.progress}/${p.required_quantity}`}</div></div><button class="btn btn-secondary loyalty-adjust-button" onclick="openLoyaltyAdjustment(${loyaltyInlineArg(id)},${loyaltyInlineArg(p.id)},${loyaltyInlineArg(p.name)})">Корректировка</button></div>`).join('')||'<div class="center-note">Нет активных программ</div>'}</div><div class="loyalty-customer-section-title">Покупки</div><div class="loyalty-customer-scroll loyalty-customer-orders">${(o.orders||[]).map(x=>`<div class="list-row"><div><div class="list-row-name">${escapeHtml(x.external_id||x.id)} · ${Number(x.total||0).toFixed(2)} BYN</div><div class="list-row-sub">${escapeHtml(x.status||'')} · ${(x.order_items||[]).map(i=>escapeHtml(i.product_name)+' ×'+Number(i.quantity||0)).join(', ')}</div></div></div>`).join('')||'<div class="center-note">Покупок пока нет</div>'}</div><div class="loyalty-customer-section-title">История</div><div class="loyalty-customer-scroll loyalty-customer-ledger">${(h.ledger||[]).map(x=>`<div class="list-row"><div><div class="list-row-name">${escapeHtml(x.loyalty_programs?.name||x.operation_type)}</div><div class="list-row-sub">${escapeHtml(x.operation_type)} · прогресс ${Number(x.progress_delta)>=0?'+':''}${x.progress_delta} · подарок ${Number(x.reward_delta)>=0?'+':''}${x.reward_delta}${x.metadata?.reason?' · '+escapeHtml(x.metadata.reason):''}</div></div></div>`).join('')||'<div class="center-note">Операций пока нет</div>'}</div><div class="modal-actions"><button class="btn btn-secondary" onclick="openCustomersAdmin()">Назад</button></div>`,true);
 }catch(e){flash('Не удалось открыть клиента')}
}
function openLoyaltyAdjustment(customerId,programId,programName){showModal(`<div class="modal-title">Корректировка · ${escapeHtml(programName)}</div><div class="field"><label>Прогресс (+/-)</label><input id="la-progress" type="number" value="0"></div><div class="field"><label>Подарок (+1 / -1)</label><input id="la-rewards" type="number" min="-1" max="1" value="0"></div><div class="field"><label>Причина</label><input id="la-reason" placeholder="Обязательная причина"></div><div class="field"><label>Пароль администратора</label><input id="la-admin-password" type="password" autocomplete="off" placeholder="Введите пароль"></div><div class="modal-actions"><button class="btn btn-secondary" onclick="openCustomerAdminCard(${loyaltyInlineArg(customerId)})">Назад</button><button class="btn btn-primary" onclick="saveLoyaltyAdjustment(${loyaltyInlineArg(customerId)},${loyaltyInlineArg(programId)})">Сохранить</button></div>`,true)}
async function saveLoyaltyAdjustment(customerId,programId){if(!currentShiftEmployeeIsAdmin())return flash('Требуются права администратора');const shift=currentShift(),employee=state.employees.find(e=>e.id===shift?.employeeId);try{await loyaltyApi('/api/customers/'+encodeURIComponent(customerId)+'/loyalty-adjustment',{method:'POST',body:JSON.stringify({programId,progressDelta:Number(document.getElementById('la-progress')?.value),rewardDelta:Number(document.getElementById('la-rewards')?.value),reason:document.getElementById('la-reason')?.value,adminEmployeeId:employee?.id||'',adminEmployeeName:employee?.name||'',adminPassword:document.getElementById('la-admin-password')?.value||''})});flash('Корректировка сохранена');openCustomerAdminCard(customerId)}catch(e){flash(e.message||'Не удалось сохранить корректировку')}}
async function openLoyaltyAdmin(){return openLoyaltyAdminScreen()}
function loyaltyProgramForm(p=null){
  const earning=new Set((p?.loyalty_earning_products||[]).map(x=>String(x.product_id)));
  const rewards=new Set((p?.loyalty_reward_products||[]).map(x=>String(x.product_id)));
  const products=(state.products||[]).map(x=>({...x,id:String(x.id)}));
  for(const id of new Set([...earning,...rewards]))if(!products.some(x=>x.id===id))products.push({id,name:'Товар отсутствует в локальном каталоге',category:'ID: '+id});
  products.sort((a,b)=>String(a.name).localeCompare(String(b.name),'ru'));
  const panel=(kind,title,selected)=>`<section class="lp-panel"><div class="lp-panel-head"><strong>${title}</strong><span id="lp-${kind}-count" class="lp-count">Выбрано: ${selected.size}</span></div><input class="lp-search" aria-label="Поиск: ${title}" placeholder="Название или категория" oninput="filterLoyaltyProducts('${kind}',this.value)"><div class="lp-products" id="lp-${kind}-list">${products.map(x=>`<label class="lp-product"><input type="checkbox" class="loyalty-${kind}-product" value="${escapeAttr(x.id)}" ${selected.has(x.id)?'checked':''} onchange="updateLoyaltyProductCount('${kind}')"><span><b>${escapeHtml(x.name)}</b><small>${escapeHtml(x.category||'Без категории')}</small></span></label>`).join('')}<div class="center-note lp-empty" ${products.length?'hidden':''}>Товары не найдены</div></div></section>`;
  showModal(`<div class="lp-heading"><div><div class="modal-title">${p?'Изменить программу':'Новая программа'}</div><div class="settings-note">Условия накопления и товары для подарка</div></div></div><div class="lp-body"><section class="lp-panel"><div class="field"><label for="lp-name">Название программы</label><input id="lp-name" value="${escapeAttr(p?.name||'')}" placeholder="Например, каждый шестой кофе в подарок"></div><div class="lp-rules"><div class="field"><label for="lp-required">Купить товаров</label><input id="lp-required" type="number" inputmode="numeric" min="1" step="1" value="${Number(p?.required_quantity)||5}"></div><div class="field"><label>Награда</label><div class="settings-note">Один подарок. Новое накопление начнётся после его использования.</div></div></div></section><div class="lp-columns">${panel('earning','Товары для накопления',earning)}${panel('reward','Товары в подарок',rewards)}</div><div class="settings-note">Изменения применяются к будущим продажам. История начислений сохраняется.</div></div><div class="lp-actions"><button class="btn btn-secondary" onclick="closeModal()">Отмена</button><button id="lp-save" data-active="${p?.is_active!==false}" class="btn btn-primary" onclick="saveLoyaltyProgram(${loyaltyInlineArg(p?.id||'')})">${p?'Сохранить изменения':'Создать программу'}</button></div>`,true);
  document.querySelector('#modal-root .modal')?.classList.add('loyalty-editor');
}
function filterLoyaltyProducts(kind,value){
  const list=document.getElementById('lp-'+kind+'-list');if(!list)return;
  const query=String(value||'').trim().toLocaleLowerCase('ru');let visible=0;
  list.querySelectorAll('.lp-product').forEach(row=>{row.hidden=!row.textContent.toLocaleLowerCase('ru').includes(query);if(!row.hidden)visible++});
  list.querySelector('.lp-empty').hidden=visible>0;
}
function updateLoyaltyProductCount(kind){
  const el=document.getElementById('lp-'+kind+'-count');if(el)el.textContent='Выбрано: '+document.querySelectorAll('.loyalty-'+kind+'-product:checked').length;
}
function openLoyaltyProgramCreate(){loyaltyProgramForm()}
function openLoyaltyProgramEdit(id){const p=(window.__loyaltyAdminPrograms||[]).find(x=>String(x.id)===String(id));if(p)loyaltyProgramForm(p)}
async function saveLoyaltyProgram(id=''){
  const button=document.getElementById('lp-save');if(!button||button.disabled)return;
  const earningProductIds=[...document.querySelectorAll('.loyalty-earning-product:checked')].map(x=>x.value);
  const rewardProductIds=[...document.querySelectorAll('.loyalty-reward-product:checked')].map(x=>x.value);
  const body={isActive:button.dataset.active!=='false',name:String(document.getElementById('lp-name')?.value||'').trim(),requiredQuantity:Number(document.getElementById('lp-required')?.value),rewardQuantity:1,earningProductIds,rewardProductIds};
  if(!body.name)return flash('Введите название программы');
  if(!Number.isSafeInteger(body.requiredQuantity)||body.requiredQuantity<1)return flash('Количество должно быть целым числом больше нуля');
  if(!earningProductIds.length||!rewardProductIds.length)return flash('Выберите товары для начисления и подарка');
  button.disabled=true;const label=button.textContent;button.textContent='Сохранение…';
  try{
    await loyaltyApi(id?'/api/loyalty/programs/'+encodeURIComponent(id):'/api/loyalty/programs',{method:id?'PUT':'POST',body:JSON.stringify(body)});
    if(document.getElementById('lp-save')===button)closeModal();
    flash(id?'Программа сохранена':'Программа создана');openLoyaltyAdminScreen();
  }catch(e){flash(e.message||'Не удалось сохранить программу')}
  finally{button.disabled=false;button.textContent=label}
}
function confirmDeleteLoyaltyProgram(id,name){showModal(`<div class="modal-title">Удалить программу?</div><div class="settings-note">Программа «${escapeHtml(name)}» будет удалена. Если по ней уже есть история начислений, удаление будет запрещено — такую программу можно только выключить.</div><div class="modal-actions"><button class="btn btn-secondary" onclick="openLoyaltyAdmin()">Отмена</button><button class="btn btn-danger" onclick="deleteLoyaltyProgram(${loyaltyInlineArg(id)})">Удалить</button></div>`,true)}
async function deleteLoyaltyProgram(id){try{await loyaltyApi('/api/loyalty/programs/'+encodeURIComponent(id),{method:'DELETE'});flash('Программа удалена');openLoyaltyAdminScreen()}catch(e){flash(e.message||'Не удалось удалить программу')}}
async function toggleLoyaltyProgram(id,isActive){try{await loyaltyApi('/api/loyalty/programs/'+encodeURIComponent(id)+'/active',{method:'PATCH',body:JSON.stringify({isActive})});flash(isActive?'Программа включена':'Программа выключена');openLoyaltyAdminScreen()}catch(e){flash(e.message||'Не удалось изменить программу')}}


function openLoyaltyAdminScreen(){if(!state.settingsAdminPanel && !state.loyaltyAdminScreen)return showAdminOnlyInfo('Программа лояльности');state.settingsAdminPanel=false;state.loyaltyAdminScreen=true;state.loyaltyAdminSection=state.loyaltyAdminSection||'programs';render();loadLoyaltyAdminScreen()}
function closeLoyaltyAdminScreen(){state.loyaltyAdminScreen=false;state.settingsAdminPanel=true;render()}
async function loadLoyaltyAdminScreen(){try{if(state.loyaltyAdminSection==='clients'){window.__loyaltyAdminCustomers=await searchCustomers('375')}else{const d=await loyaltyApi('/api/loyalty/programs');window.__loyaltyAdminPrograms=d.programs||[]}render()}catch(e){flash('Не удалось загрузить данные программы лояльности')}}
function setLoyaltyAdminSection(section){state.loyaltyAdminSection=section;loadLoyaltyAdminScreen()}
function renderLoyaltyAdminScreen(){
  const section=state.loyaltyAdminSection||'programs',programs=window.__loyaltyAdminPrograms||[],clients=window.__loyaltyAdminCustomers||[];
  const cards=programs.map(p=>`<article class="loyalty-program-card"><div class="loyalty-program-heading"><h2>${escapeHtml(p.name)}</h2><span class="loyalty-status ${p.is_active?'is-active':''}">${p.is_active?'Активна':'Выключена'}</span></div><div class="loyalty-rule"><div><strong>${Number(p.required_quantity)||0}</strong><span>купить товаров</span></div><span class="loyalty-rule-arrow" aria-hidden="true">→</span><div><strong>1</strong><span>получить в подарок</span></div></div><div class="loyalty-card-meta"><span>Для накопления: ${(p.loyalty_earning_products||[]).length}</span><span>В подарок: ${(p.loyalty_reward_products||[]).length}</span></div><div class="loyalty-card-actions"><button class="btn btn-primary" onclick="openLoyaltyProgramEdit(${loyaltyInlineArg(p.id)})">Изменить</button><button class="btn btn-secondary" onclick="toggleLoyaltyProgram(${loyaltyInlineArg(p.id)},${!p.is_active})">${p.is_active?'Выключить':'Включить'}</button><button class="btn btn-secondary loyalty-delete" onclick="confirmDeleteLoyaltyProgram(${loyaltyInlineArg(p.id)},${loyaltyInlineArg(p.name)})">Удалить</button></div></article>`).join('');
  return `<div class="screen content-screen loyalty-page ${state.tab==='settings'?'active':''}"><div class="loyalty-page-inner"><header class="loyalty-page-header"><button class="btn btn-secondary" onclick="closeLoyaltyAdminScreen()">← Назад</button><h1>Программа лояльности</h1></header><nav class="loyalty-sections" aria-label="Разделы лояльности"><button class="${section==='programs'?'selected':''}" aria-pressed="${section==='programs'}" onclick="setLoyaltyAdminSection('programs')">Программы</button><button class="${section==='clients'?'selected':''}" aria-pressed="${section==='clients'}" onclick="setLoyaltyAdminSection('clients')">Клиенты</button></nav>${section==='programs'?`<div class="loyalty-section-toolbar"><span>Всего программ: ${programs.length}</span><button class="btn btn-primary" onclick="openLoyaltyProgramCreate()">Новая программа</button></div><div class="loyalty-program-grid">${cards||'<div class="loyalty-empty"><strong>Пока нет программ</strong><span>Создайте первую программу для ваших гостей.</span></div>'}</div>`:`<section class="loyalty-clients-panel"><div class="loyalty-client-search"><label for="loyalty-client-query">Найти клиента</label><input id="loyalty-client-query" type="search" placeholder="Имя или телефон" oninput="loyaltyAdminCustomerSearch(this.value)"></div><div class="loyalty-client-columns"><span>Клиент</span><span>Профиль</span></div><div id="loyalty-admin-customers">${customerAdminRows(clients)}</div></section>`}</div></div>`;
}
async function loyaltyAdminCustomerSearch(q){const el=document.getElementById('loyalty-admin-customers');if(!el)return;try{el.innerHTML=customerAdminRows(await searchCustomers(q))}catch(e){el.textContent='Ошибка загрузки'}}
