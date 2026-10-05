/* WEB orders runtime. Compatibility globals are intentional during gradual migration. */
function normalizeWebOrder(raw){return {id:raw.id,external_id:raw.external_id,status:raw.status||'new',order_type:raw.order_type||'На месте',customer_id:raw.customer_id||'',customer_name:raw.customer_name||'',phone:raw.phone||'',address:raw.address||'',comment:raw.comment||'',total:Number(raw.total||0),delivery_fee:Number(raw.delivery_fee||0),created_at:raw.created_at||new Date().toISOString(),order_items:Array.isArray(raw.order_items)?raw.order_items:[]};}
function ownerLiveReportSnapshot(){
 const shift=currentShift();
 if(!shift)return {generatedAt:Date.now(),shiftOpen:false,currency:state.currency||'BYN',orders:0,revenue:0,cash:0,card:0,categories:[],products:[]};
 const totals=shiftTotals(shift.id),categoryMap={},productMap={};
 for(const order of totals.activeOrders||[]){for(const item of order.items||[]){const quantity=Math.max(0,Number(item.qty)||0),price=Math.max(0,Number(item.price)||0),product=getProduct(item.productId),category=String(product?.category||item.category||'Без категории'),name=String(item.name||item.productName||product?.name||'Товар'),revenue=quantity*price;if(!categoryMap[category])categoryMap[category]={name:category,quantity:0,revenue:0};if(!productMap[name])productMap[name]={name,quantity:0,revenue:0};categoryMap[category].quantity+=quantity;categoryMap[category].revenue+=revenue;productMap[name].quantity+=quantity;productMap[name].revenue+=revenue;}}
 const sortRows=map=>Object.values(map).sort((a,b)=>b.revenue-a.revenue||b.quantity-a.quantity||a.name.localeCompare(b.name,'ru'));
 return {generatedAt:Date.now(),shiftOpen:true,shiftOpenedAt:Number(shift.openedAt)||0,employeeName:String(shift.employeeName||''),currency:state.currency||'BYN',orders:Number(totals.count)||0,revenue:Number(totals.total)||0,cash:Number(totals.cash)||0,card:Number(totals.card)||0,categories:sortRows(categoryMap),products:sortRows(productMap)};
}
async function respondOwnerLiveReport(requestId){
 const id=String(requestId||'');if(!/^[A-Za-z0-9_-]{20,100}$/.test(id))return false;
 const network=networkConfigFromState();if(!network.backendUrl||!network.deviceKey)return false;
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),5000);
 try{const response=await fetch(network.backendUrl.replace(/\/+$/,'')+'/api/device/live-report/'+encodeURIComponent(id),{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':network.deviceKey},body:JSON.stringify({report:ownerLiveReportSnapshot()}),cache:'no-store',signal:controller.signal});return response.ok}catch(_error){return false}finally{clearTimeout(timeout)}
}
function startWebOrderEvents(){const n=networkConfigFromState();if(!n.deviceKey||!n.backendUrl||!window.EventSource)return;try{state.webEventsSource?.close();}catch(e){}const source=new EventSource(n.backendUrl.replace(/\/+$/,'')+'/api/orders/events?deviceKey='+encodeURIComponent(n.deviceKey));state.webEventsSource=source;source.onmessage=async(ev)=>{try{const d=JSON.parse(ev.data||'{}');if(d.type==='owner_live_report_request'){await respondOwnerLiveReport(d.requestId);return}if(d.type!=='orders')return;const oldList=state.webEvents.slice();const oldIds=new Set(oldList.map(x=>x.id));const incoming=(d.orders||[]).map(normalizeWebOrder);const merged=[...incoming];oldList.forEach(x=>{if(!merged.some(y=>y.id===x.id))merged.push(x);});const next=merged.filter(x=>x.status==='new').sort((a,b)=>new Date(b.created_at)-new Date(a.created_at));const changed=next.length!==oldList.length || next.some((x,i)=>{const y=oldList[i];return !y || y.id!==x.id || y.status!==x.status || x.total!==y.total || x.updated_at!==y.updated_at || x.customer_id!==y.customer_id;});const newOrders=incoming.filter(x=>x.status==='new'&&!oldIds.has(x.id));if(changed){try{await window.PrilavokCore.Storage.set('webEvents',next)}catch(e){markStorageBroken(e);return}}state.webEvents=next;if(newOrders.length){flash('🔔 Новый заказ с сайта');render();}}catch(e){}};source.onerror=()=>{};}
function webOrderDate(o){return fmtDate(new Date(o.created_at).getTime());}
function openWebEventsModal(){const list=state.webEvents.slice().sort((a,b)=>new Date(b.created_at)-new Date(a.created_at));showModal(`<div class="modal-title">События</div>${list.length?list.map(o=>`<div class="list-row web-event-row" onclick="openWebOrder('${escapeAttr(o.id)}')"><div class="web-event-main"><div class="list-row-name">Заказ с сайта ${escapeHtml(o.external_id||'')}</div><div class="list-row-sub">${escapeHtml(o.customer_name||'Клиент')} · ${fullMoney(o.total)} · ${webOrderDate(o)}</div></div><span class="ui-icon ui-icon-chevron" aria-hidden="true"></span></div>`).join(''):`<div class="center-note">Новых событий нет</div>`}<div class="modal-actions"><button class="btn btn-secondary modal-full-width-action" onclick="closeModal()">Закрыть</button></div>`);}
const WEB_READY_ESTIMATES=[['5m','5 мин'],['15m','15 мин'],['30m','30 мин'],['40m','40 мин'],['60plus','Больше часа']];
let selectedWebReadyEstimate='',selectedWebReadyOrderId='';
function validWebReadyEstimate(value){return WEB_READY_ESTIMATES.some(([v])=>v===value)||/^at:([01]\d|2[0-3]):[0-5]\d$/.test(value)}
function editWebReadyTime(value){
 const estimate='at:'+value;
 selectedWebReadyEstimate=validWebReadyEstimate(estimate)?estimate:'custom';
 const accept=document.getElementById('web-order-accept');
 if(accept)accept.disabled=!validWebReadyEstimate(selectedWebReadyEstimate);
}

function selectWebReadyEstimate(value,id){if(!WEB_READY_ESTIMATES.some(([v])=>v===value))return;selectedWebReadyEstimate=value;openWebOrder(id);}
function openWebOrder(id){
  const scrollTop=selectedWebReadyOrderId===id?(document.querySelector('.web-order-body')?.scrollTop||0):0;
  if(selectedWebReadyOrderId!==id){selectedWebReadyEstimate='';selectedWebReadyOrderId=id;}
  const o=state.webEvents.find(x=>x.id===id);if(!o)return;
  const custom=selectedWebReadyEstimate==='custom'||selectedWebReadyEstimate.startsWith('at:');
  const estimates=WEB_READY_ESTIMATES.map(([value,label])=>`<button class="btn ${selectedWebReadyEstimate===value?'btn-primary':'btn-secondary'}" aria-pressed="${selectedWebReadyEstimate===value}" onclick="selectWebReadyEstimate('${value}','${escapeAttr(o.id)}')">${label}</button>`).join('');
  showModal(`<header class="web-order-heading"><div class="web-order-eyebrow">Заказ с сайта</div><div class="modal-title">${escapeHtml(o.external_id||'Новый заказ')}</div><div class="web-order-customer"><strong>${escapeHtml(o.customer_name||'Клиент')}</strong>${o.phone?`<span>${escapeHtml(o.phone)}</span>`:''}</div></header><div class="web-order-body"><section class="web-order-section web-order-products"><div class="web-order-section-head"><h3>Состав заказа</h3><span class="web-order-type">${escapeHtml(o.order_type||'На месте')}</span></div><div class="web-order-items">${(o.order_items||[]).map(i=>`<div class="web-order-item"><div><strong>${escapeHtml(i.product_name)}</strong><span>${Number(i.quantity||0)} × ${fullMoney(Number(i.price||0))}</span></div><b>${fullMoney(Number(i.price||0)*Number(i.quantity||0))}</b></div>`).join('')}</div>${o.delivery_fee?`<div class="web-order-delivery"><span>Доставка</span><strong>${fullMoney(o.delivery_fee)}</strong></div>`:''}<div class="web-order-total"><span>Итого</span><strong>${fullMoney(o.total)}</strong></div></section><div class="web-order-details">${o.address?`<section class="web-order-section"><h3>Адрес доставки</h3><div class="web-order-address">${escapeHtml(o.address)}</div></section>`:''}${o.comment?`<section class="web-order-section web-order-comment"><h3>Комментарий клиента</h3><div class="web-customer-comment">${escapeHtml(o.comment)}</div></section>`:''}<section class="web-order-section"><h3>Время готовности</h3><div class="web-ready-grid">${estimates}<button class="btn ${custom?'btn-primary':'btn-secondary'}" aria-pressed="${custom}" onclick="selectedWebReadyEstimate='custom';openWebOrder('${escapeAttr(o.id)}')">Будет готово к</button></div>${custom?`<div class="web-ready-custom"><label for="web-ready-time">Укажите время<span>По времени заведения</span></label><input id="web-ready-time" type="time" value="${selectedWebReadyEstimate.startsWith('at:')?selectedWebReadyEstimate.slice(3):''}" oninput="editWebReadyTime(this.value)" onchange="editWebReadyTime(this.value)"></div>`:''}</section></div></div><footer class="web-order-actions"><button class="btn btn-secondary" onclick="selectedWebReadyEstimate='';openWebEventsModal()">Назад</button><button id="web-order-accept" class="btn btn-primary" ${validWebReadyEstimate(selectedWebReadyEstimate)?'':'disabled'} onclick="acceptWebOrder('${escapeAttr(o.id)}',selectedWebReadyEstimate)">Взять в работу</button></footer>`,true);
  document.querySelector('#modal-root .modal')?.classList.add('web-order-modal');
  const body=document.querySelector('.web-order-body');if(body)body.scrollTop=scrollTop;
}
let webAcceptBusy=false,webAcceptanceRecoveryBusy=false,legacyWebReadyBusy=false;
const legacyWebReadyPrompted=new Set();
function openLegacyWebReadyPrompt(id,record){
  if(legacyWebReadyPrompted.has(id)||document.getElementById('modal-root')?.innerHTML.trim())return false;
  legacyWebReadyPrompted.add(id);
  const encoded=encodeURIComponent(id),label=record?.parked?.receiptDisplayNumber||record?.parked?.orderLabel||id;
  const estimates=WEB_READY_ESTIMATES.map(([value,text])=>`<button class="btn btn-secondary" onclick="saveLegacyWebReadyEstimate(decodeURIComponent('${encoded}'),'${value}')">${text}</button>`).join('');
  showModal(`<div class="modal-title">Укажите время готовности</div><div class="settings-note web-recovery-note">Заказ ${escapeHtml(label)} уже сохранён в Отложенных. Выберите время, чтобы завершить подтверждение на сайте.</div><div class="web-ready-grid">${estimates}</div><div class="web-ready-custom"><label for="legacy-web-ready-time">Будет готово к<span>По времени заведения</span></label><input id="legacy-web-ready-time" type="time" onchange="saveLegacyWebReadyEstimate(decodeURIComponent('${encoded}'),'at:'+this.value)"></div><div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">Позже</button></div>`);
  return true;
}
async function saveLegacyWebReadyEstimate(id,readyEstimate){
  if(legacyWebReadyBusy||!validWebReadyEstimate(readyEstimate)){if(readyEstimate)flash('Укажите корректное время готовности');return false;}
  legacyWebReadyBusy=true;
  try{
    const journal=await loadKey('webOrderAcceptances',{}),record=journal?.[id];
    if(!record||record.stage!=='local'||!record.parked)throw new Error('Запись приёма заказа не найдена');
    if(!state.parked.some(p=>p.webOrderId===record.parked.webOrderId))throw new Error('Заказ не найден в Отложенных');
    record.readyEstimate=readyEstimate;record.readyEstimateUpdatedAt=Date.now();
    try{await window.PrilavokCore.Storage.set('webOrderAcceptances',journal)}catch(e){markStorageBroken(e);throw e}
    const confirmed=await confirmWebAcceptance(id,record,journal);
    if(confirmed){const cleaned=await removeConfirmedWebEvents(journal);closeModal();render();flash(cleaned?'Время сохранено, заказ подтверждён':'Заказ подтверждён. Событие будет убрано после восстановления хранилища');}
    else{closeModal();flash('Время сохранено. Подтверждение повторится после восстановления связи');}
    return confirmed;
  }catch(e){flash('Не удалось сохранить время готовности: '+(e?.message||e));return false;}
  finally{legacyWebReadyBusy=false;}
}
function webOrderCartItems(o){
  if(!Array.isArray(o.order_items)||!o.order_items.length)throw new Error('В заказе нет товаров');
  return o.order_items.map(i=>{
    // IDs are authoritative. Name fallback is only for legacy rows without an ID.
    const matches=i.external_product_id?[getProduct(i.external_product_id)].filter(Boolean):state.products.filter(p=>p.name===i.product_name);
    if(matches.length!==1)throw new Error('Товар не найден или неоднозначен: '+i.product_name);
    const p=matches[0],qty=Number(i.quantity),price=Number(i.price??p.price);
    if(!Number.isFinite(qty)||qty<=0||!Number.isFinite(price)||price<0)throw new Error('Некорректная позиция: '+p.name);
    return {productId:p.id,name:p.name,price,qty,comment:i.comment||'',category:p.category||''};
  });
}
async function confirmWebAcceptance(id,record,journal){
  if(record?.stage==='confirmed')return true;
  const n=networkConfigFromState();if(!n.backendUrl||!n.deviceKey)return false;
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),5000);
  try{
    const r=await fetch(n.backendUrl.replace(/\/+$/,'')+'/api/orders/'+encodeURIComponent(id)+'/accept',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':n.deviceKey},body:JSON.stringify({readyEstimate:record.readyEstimate||''}),cache:'no-store',signal:controller.signal});
    const d=await r.json().catch(()=>null);if(!r.ok)throw new Error(d?.error||('HTTP '+r.status));
    record.stage='confirmed';record.confirmedAt=Date.now();await window.PrilavokCore.Storage.set('webOrderAcceptances',journal);return true;
  }catch(e){return false;}finally{clearTimeout(timeout);}
}
async function removeConfirmedWebEvents(journal){
  const confirmed=new Set(Object.entries(journal||{}).filter(([,record])=>record?.stage==='confirmed').map(([id])=>id));
  if(!confirmed.size)return true;
  const events=state.webEvents.filter(event=>!confirmed.has(event?.id));if(events.length===state.webEvents.length)return true;
  try{await window.PrilavokCore.Storage.set('webEvents',events);state.webEvents=events;return true}catch(e){markStorageBroken(e);return false}
}
async function recoverWebAcceptanceJournal(){
  if(webAcceptanceRecoveryBusy)return false;
  webAcceptanceRecoveryBusy=true;
  try{
  const journal=await loadKey('webOrderAcceptances',{});
  const records=Object.entries(journal||{});
  let changed=false;
  for(const [id,record] of records){
    if(!record?.parked)continue;
    const local=state.parked.find(p=>p.webOrderId===record.parked.webOrderId);
    if(record.stage==='prepared'&&local){record.stage='local';changed=true;}
  }
  if(changed){try{await window.PrilavokCore.Storage.set('webOrderAcceptances',journal);}catch(e){markStorageBroken(e);return;}}
  // ACK recovery is best-effort and only happens for records already durably committed locally.
  for(const [id,record] of records.filter(([,r])=>r?.stage==='local')){
    const local=state.parked.some(p=>p.webOrderId===record.parked?.webOrderId);
    if(local){
      if(!validWebReadyEstimate(record.readyEstimate||'')){openLegacyWebReadyPrompt(id,record);continue;}
      await confirmWebAcceptance(id,record,journal);
    }
  }
  await removeConfirmedWebEvents(journal);
  return true;
  }finally{webAcceptanceRecoveryBusy=false;}
}
async function acceptWebOrder(id,readyEstimate=''){
  if(webAcceptBusy)return;
  const o=state.webEvents.find(x=>x.id===id);if(!o)return;
  if(!validWebReadyEstimate(readyEstimate)){flash('Выберите примерное время готовности');return;}
  webAcceptBusy=true;
  try{
    const n=networkConfigFromState();
    if(!n.backendUrl||!n.deviceKey)throw new Error('Проверьте сетевые настройки');
    let readError=false;
    const journal=await window.PrilavokCore.Storage.get('webOrderAcceptances',{},()=>{readError=true;});
    if(readError||!journal||typeof journal!=='object'||Array.isArray(journal))throw new Error('Не удалось прочитать журнал приёма');
    if(!journal[id]){
      const cartItems=webOrderCartItems(o);checkedStockConsumption(cartItems);
      const existing=state.parked.find(p=>p.webOrderId===id);
      const rawType=String(o.order_type||'').trim().toLocaleLowerCase('ru');
      const orderType=(rawType==='delivery'||rawType==='доставка')?'Доставка':((rawType==='pickup'||rawType==='самовывоз'||rawType==='с собой')?'С собой':(o.order_type||'На месте'));
      const acceptedAt=Date.now();
      journal[id]={readyEstimate,stage:existing?'local':'prepared',parked:existing||{id:uid(),receiptDisplayNumber:'#'+String(o.external_id||o.id||'—'),items:cartItems,total:Number(o.total||0),subtotal:Number(o.total||0)-Number(o.delivery_fee||0),deliveryFee:Number(o.delivery_fee||0),deliveryTariffSelected:false,orderLabel:'Веб '+(o.external_id||''),orderType,customer:{id:o.customer_id||'',name:o.customer_name||'',phone:o.phone||'',address:o.address||''},comment:o.comment||'',source:'web',webOrderId:o.id,webOrderStatus:'accepted',timestamp:acceptedAt,createdAt:acceptedAt,kitchenPrinted:false,printedItems:[]}};
      await window.PrilavokCore.Storage.set('webOrderAcceptances',journal);
    }
    const record=journal[id];
    if(record.stage!=='confirmed'){record.readyEstimate=readyEstimate;await window.PrilavokCore.Storage.set('webOrderAcceptances',journal);}
    if(record.stage==='prepared'){
      checkedStockConsumption(record.parked.items);
      const parked=state.parked.some(p=>p.webOrderId===id)?state.parked.slice():[...state.parked,record.parked];
      await window.PrilavokCore.Storage.set('parked',parked);
      record.stage='local';await window.PrilavokCore.Storage.set('webOrderAcceptances',journal);
      state.parked=parked;
    }
    // WEB-заказ печатаем на кухню один раз в момент "Взять в работу".
    // Снимок printedItems сохраняет идемпотентность и позволяет позже допечатать только добавленные позиции.
    if(!record.parked.kitchenPrinted){
      record.parked.items=(record.parked.items||[]).map(item=>({...item,category:item.category||getProduct(item.productId)?.category||''}));
      record.parked.receiptDisplayNumber=record.parked.receiptDisplayNumber||('#'+String(o.external_id||o.id||'—'));
      record.parked.timestamp=Number(record.parked.timestamp)||Number(record.parked.createdAt)||Date.now();
      const deltaItems=kitchenPrintDelta(record.parked.items,record.parked.printedItems||[]);
      if(deltaItems.length && typeof window.printKitchenOrderNow==='function'){
        window.printKitchenOrderNow({...record.parked,items:deltaItems});
        record.parked.kitchenPrinted=true;
        record.parked.printedItems=kitchenPrintedSnapshot(record.parked.items);
        const parkedIndex=state.parked.findIndex(p=>p.webOrderId===id);
        if(parkedIndex>=0){
          state.parked[parkedIndex]=JSON.parse(JSON.stringify(record.parked));
          await window.PrilavokCore.Storage.set('parked',state.parked);
        }
        await window.PrilavokCore.Storage.set('webOrderAcceptances',journal);
      }
    }
    if(record.stage!=='confirmed'){
      const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),30000);
      try{
        const r=await fetch(n.backendUrl.replace(/\/+$/,'')+'/api/orders/'+encodeURIComponent(id)+'/accept',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':n.deviceKey},body:JSON.stringify({readyEstimate}),cache:'no-store',signal:controller.signal});
        const d=await r.json().catch(()=>null);if(!r.ok)throw new Error(d?.error||('HTTP '+r.status));
      }finally{clearTimeout(timeout);}
      record.stage='confirmed';await window.PrilavokCore.Storage.set('webOrderAcceptances',journal);
    }
    const events=state.webEvents.filter(x=>x.id!==id);
    await window.PrilavokCore.Storage.set('webEvents',events);state.webEvents=events;
    selectedWebReadyEstimate='';closeModal();render();flash('Заказ взят в работу и добавлен в Отложенные');
  }catch(e){render();flash('Не удалось подтвердить заказ: '+(e?.message||'Ошибка сети')+'. Если заказ уже в Отложенных, повторное нажатие не создаст копию.');}
  finally{webAcceptBusy=false;}
}

async function markCurrentWebOrderReady(){
  const id=state.currentWebOrderId;
  if(!id || state.currentOrderSource!=='web') return;
  const n=networkConfigFromState();
  if(!n.backendUrl||!n.deviceKey){flash('Проверьте сетевые настройки');return;}
  try{
    const r=await fetch(n.backendUrl.replace(/\/+$/,'')+'/api/orders/'+encodeURIComponent(id)+'/ready',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':n.deviceKey},body:'{}',cache:'no-store'});
    const d=await r.json().catch(()=>null); if(!r.ok)throw new Error(d?.error||('HTTP '+r.status));
    state.currentWebOrderStatus='ready'; saveCurrentOrderSession(); render(); flash('Веб-заказ отмечен как готовый');
  }catch(e){flash('Не удалось отметить заказ готовым: '+(e?.message||'Ошибка сети'));}
}

function testWebOrder(){const n=networkConfigFromState();fetch(n.backendUrl.replace(/\/+$/,'')+'/api/orders/test',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':n.deviceKey},body:'{}',cache:'no-store'}).then(async r=>{const d=await r.json().catch(()=>null);if(!r.ok)throw new Error(d?.error||('HTTP '+r.status));flash('Тестовый заказ отправлен');}).catch(()=>flash('Не удалось создать тестовый заказ'));}
