/* Warehouse reporting runtime. Compatibility globals are intentional during gradual migration. */
/* Read-only inventory reporting. Historical balances are reconstructed, never persisted. */
function warehouseRange(from,to){
  const parse=value=>{if(!/^\d{4}-\d{2}-\d{2}$/.test(value||''))throw new Error('Укажите даты периода');const [y,m,d]=value.split('-').map(Number),date=new Date(y,m-1,d);if(localDateString(date)!==value)throw new Error('Некорректная дата');return date;};
  const start=parse(from),last=parse(to);if(start>last)throw new Error('Начало периода позже окончания');
  if(last>new Date(new Date().getFullYear(),new Date().getMonth(),new Date().getDate()))throw new Error('Дата окончания не может быть в будущем');
  const end=new Date(last);end.setDate(end.getDate()+1);return {start:start.getTime(),end:end.getTime()};
}
function warehousePreset(preset){
  const to=new Date(),from=new Date(to);if(preset==='7')from.setDate(from.getDate()-6);else if(preset==='month')from.setDate(1);else if(preset==='quarter'){from.setMonth(Math.floor(from.getMonth()/3)*3,1);}
  window._warehouseFilters={from:localDateString(from),to:localDateString(to)};renderWarehousePage();
}
function warehouseReport(from,to,now=Date.now()){
  const range=warehouseRange(from,to),map=new Map(),documents=[],warnings={legacySales:0,legacyReturns:0,invalid:0,unknownCosts:0};
  function row(id,name,unit){
    if(!map.has(id)){
      const p=getProduct(id),simple=p?.type==='simple',cost=simple&&Number.isFinite(Number(p.cost))?Number(p.cost):null;
      map.set(id,{id,name:p?.name||name||('Удалённый товар · '+id),unit:simple?stockUnit(p):(unit||''),cost,current:simple&&!p.noStockTracking&&Number.isFinite(Number(p.stock))?Number(p.stock):null,minStock:simple?p.minStock:null,supplierNames:[],incoming:0,incomingValue:0,outgoing:0,returned:0,afterStart:0,afterEnd:0});
    }return map.get(id);
  }
  state.products.filter(p=>p.type==='simple').forEach(p=>row(p.id,p.name,stockUnit(p)));
  function event(id,name,unit,qty,time,kind,value=0){
    if(!id||!Number.isFinite(qty)||qty<0||!Number.isFinite(time)||time>now||!Number.isFinite(value)){warnings.invalid++;return;}
    const r=row(id,name,unit);let amount=qty;
    try{if(unit!==undefined&&unit!==r.unit)amount=convertProductQty(qty,unit,r.unit);}catch(e){warnings.invalid++;return;}
    const delta=kind==='outgoing'?-amount:amount;
    if(time>=range.start)r.afterStart+=delta;if(time>=range.end)r.afterEnd+=delta;
    if(time>=range.start&&time<range.end){r[kind]+=amount;if(kind==='incoming')r.incomingValue+=value;return r;}
  }
  for(const receipt of state.receivings){
    if(receipt.adminDeleted)continue;
    const time=Number(receipt.timestamp),items=Array.isArray(receipt.items)?receipt.items:[receipt];
    if(time>=range.start&&time<range.end&&time<=now)documents.push({id:receipt.id,supplier:receipt.supplierName||'Не указан',number:receipt.invoiceNumber||'—',date:receipt.invoiceDate||'—',received:fmtDate(time),timestamp:time,total:Number(receipt.totalCost??items.reduce((s,i)=>s+Number(i.totalCost??Number(i.qty)*Number(i.unitCost)),0)),shortage:!!receipt.shortage});
    for(const item of items){
      const received=event(item.productId,item.productName,item.stockUnit,Number(item.qty),time,'incoming',Number(item.totalCost??Number(item.qty)*Number(item.unitCost)));
      const supplier=receipt.supplierName||'Не указан';
      if(received&&Number(item.qty)>0&&!received.supplierNames.includes(supplier))received.supplierNames.push(supplier);
    }
  }
  let recordedSalesCost=0,missingSalesCosts=0;
  for(const order of state.orders){
    const saleTime=Number(order.timestamp),returnTime=Number(order.returnedAt||0),saleIn=saleTime>=range.start&&saleTime<range.end&&saleTime<=now;
    if(saleIn){for(const item of order.items||[]){if(item.cost==null||!Number.isFinite(Number(item.cost)))missingSalesCosts++;else recordedSalesCost+=Number(item.cost)*Number(item.qty||0);}}
    const snap=order.stockConsumption;
    if(!snap||snap.version!==1||!Array.isArray(snap.items)){
      if(saleTime>=range.start&&saleTime<=now)warnings.legacySales++;
      if(returnTime>=range.start&&returnTime<=now)warnings.legacyReturns++;
      continue;
    }
    if(snap.items.some(i=>!i||!i.productId||!Number.isFinite(Number(i.qty))||Number(i.qty)<0)||new Set(snap.items.map(i=>i.productId)).size!==snap.items.length){warnings.invalid++;continue;}
    for(const item of snap.items){
      event(item.productId,null,undefined,Number(item.qty),saleTime,'outgoing');
      if(returnTime)event(item.productId,null,undefined,Number(item.qty),returnTime,'returned');
    }
  }
  const rows=[...map.values()].map(r=>{
    const start=r.current===null?null:r.current-r.afterStart,end=r.current===null?null:r.current-r.afterEnd;
    if(r.cost===null&&(r.outgoing||r.returned))warnings.unknownCosts++;
    return {...r,start,end,outgoingValue:r.cost===null?null:r.outgoing*r.cost,returnedValue:r.cost===null?null:r.returned*r.cost,currentValue:r.current===null||r.cost===null?null:r.current*r.cost,endValue:end===null||r.cost===null?null:end*r.cost};
  }).sort((a,b)=>a.name.localeCompare(b.name,'ru'));
  const suppliers=new Map();for(const doc of documents){const entry=suppliers.get(doc.supplier)||{name:doc.supplier,count:0,total:0,shortages:0};entry.count++;entry.total+=Number.isFinite(doc.total)?doc.total:0;if(doc.shortage)entry.shortages++;suppliers.set(doc.supplier,entry);}
  return {from,to,generatedAt:fmtDate(now),rows,documents:documents.sort((a,b)=>b.timestamp-a.timestamp),suppliers:[...suppliers.values()],warnings,recordedSalesCost,missingSalesCosts,
    incomingValue:rows.reduce((s,r)=>s+r.incomingValue,0),outgoingValue:rows.reduce((s,r)=>s+(r.outgoingValue||0),0),returnedValue:rows.reduce((s,r)=>s+(r.returnedValue||0),0),currentValue:rows.reduce((s,r)=>s+(r.currentValue||0),0),endValue:rows.reduce((s,r)=>s+(r.endValue||0),0),
    low:rows.filter(r=>r.current!==null&&r.minStock!=null&&r.current<=r.minStock).length,idle:rows.filter(r=>r.current>0&&r.outgoing===0).length};
}
function warehouseNumber(value){return value===null?'—':Number(value.toFixed(6)).toLocaleString('ru-RU',{maximumFractionDigits:6});}
function warehouseQuantityTotals(report,key){
  const sums=new Map();for(const r of report.rows){if(r[key]==null||!r[key])continue;const base=r.unit==='g'?'kg':r.unit==='ml'?'l':r.unit;const q=convertProductQty(r[key],r.unit,base);sums.set(base,(sums.get(base)||0)+q);}
  return [...sums].map(([unit,qty])=>warehouseNumber(qty)+' '+unitLabel(unit)).join(' · ')||'0';
}
function warehouseNotes(report){
  const w=report.warnings;
  return ['Упрощённый учёт. Одна строка на товар; поставщики указаны по приёмкам за период. Списание и остаток общие для товара, без распределения по поставщикам. Списание показано до вычета возвратов; возвраты учтены в остатке.',
    'Даты. В отчёт входят приёмки, продажи и возвраты за выбранные дни. Дата документа не влияет на отбор. Списания показывают расход при продаже, без порчи и ручных корректировок.',
    'Остатки. «Сейчас» — текущий склад. Начало и конец периода рассчитаны по известным движениям. Ручные изменения и неполная история могут влиять на точность.',
    'Суммы. Поступления — фактические суммы приёмок. Списания, возвраты и остатки на конец оценены по текущей себестоимости. НДС отдельно не рассчитан: ставки не сохранены.',
    `Неполная история с начала периода до сегодня. Чеков без сохранённого состава списания: ${w.legacySales}; возвратов без состава: ${w.legacyReturns}. Их количества не восстановлены. Некорректных движений: ${w.invalid}; товаров без оценки стоимости: ${w.unknownCosts}.`,
    'Прочерк означает, что остаток неизвестен или его учёт отключён. Поступления таких товаров показывают закупленное количество.'];
}
function warehouseSimpleSection(report){
  const rows=report.rows.filter(r=>r.incoming||r.outgoing||r.returned||(r.end!==null&&r.end!==0));
  const excelRows=rows.map(r=>[(r.supplierNames||[]).slice().sort((a,b)=>a.localeCompare(b,'ru')).join(', ')||'—',r.name,unitLabel(r.unit),r.incoming,r.outgoing,r.end]);
  return {title:'Упрощённый учёт',headers:['Поставщики за период','Товар','Ед.','Получено','Списано','Остаток на конец*'],excelRows,rows:excelRows.map(row=>row.map((v,i)=>i<3?v:warehouseNumber(v)))};
}
function warehouseExportPayload(report,documentsOnly=false){
  const amount=v=>v===null?'—':money(v),q=warehouseNumber;
  const sections=[];
  if(!documentsOnly){
    sections.push(warehouseSimpleSection(report));
    sections.push({title:'Сводка',headers:['Показатель','Количество','Сумма'],rows:[['Поступления',warehouseQuantityTotals(report,'incoming'),amount(report.incomingValue)],['Списания при продаже · оценка',warehouseQuantityTotals(report,'outgoing'),amount(report.outgoingValue)],['Возвраты на склад · оценка',warehouseQuantityTotals(report,'returned'),amount(report.returnedValue)],['Текущий остаток',warehouseQuantityTotals(report,'current'),amount(report.currentValue)],['Остаток на конец · расчёт',warehouseQuantityTotals(report,'end'),amount(report.endValue)]]});
    sections.push({title:'Движения и остатки',headers:['Товар / ед.','Начало*','Приход','Расход','Возврат','Конец*','Сейчас','Приход, '+state.currency,'Расход*, '+state.currency,'Конец*, '+state.currency],rows:report.rows.map(r=>[r.name+' / '+unitLabel(r.unit),q(r.start),q(r.incoming),q(r.outgoing),q(r.returned),q(r.end),q(r.current),amount(r.incomingValue),amount(r.outgoingValue),amount(r.endValue)])});
    sections.push({title:'Поставщики',headers:['Поставщик','Приёмок','Сумма','Расхождений'],rows:report.suppliers.map(s=>[s.name,String(s.count),amount(s.total),String(s.shortages)])});
  }
  sections.push({title:'Реестр полученных документов',headers:['Поставщик','Номер','Дата документа','Дата приёмки','Сумма','Статус'],rows:report.documents.map(d=>[d.supplier,d.number,d.date,d.received,Number.isFinite(d.total)?amount(d.total):'—',d.shortage?'Расхождение':'Принят'])});
  return {title:documentsOnly?'Реестр приёмок':'Складской учёт',period:report.from+' — '+report.to,generatedAt:report.generatedAt,company:state.company?.establishmentName||'',notes:warehouseNotes(report),sections};
}
function openWarehousePage(){
  if(!currentShiftEmployeeIsAdmin()){flash('Складской учёт доступен администратору');return;}
  if(!window._warehouseFilters){const to=new Date(),from=new Date(to);from.setDate(from.getDate()-6);window._warehouseFilters={from:localDateString(from),to:localDateString(to)};}
  renderWarehousePage();
}
function closeWarehousePage(){document.getElementById('warehouse-root').innerHTML='';document.getElementById('app').inert=false;}
function applyWarehouseDates(){
  const from=document.getElementById('warehouse-from').value,to=document.getElementById('warehouse-to').value;
  try{warehouseRange(from,to);window._warehouseFilters={from,to};renderWarehousePage();}catch(e){flash(e.message);}
}
function renderWarehouseTable(section){
  return `<div class="warehouse-table-scroll"><table><thead><tr>${section.headers.map(h=>`<th>${escapeHtml(h)}</th>`).join('')}</tr></thead><tbody>${section.rows.map(row=>`<tr>${row.map(cell=>`<td>${escapeHtml(String(cell))}</td>`).join('')}</tr>`).join('')||`<tr><td colspan="${section.headers.length}">Нет данных за период</td></tr>`}</tbody></table></div>`;
}
function renderWarehousePage(){
  if(!currentShiftEmployeeIsAdmin()){flash('Доступен администратору');return;}
  const f=window._warehouseFilters,report=warehouseReport(f.from,f.to),payload=warehouseExportPayload(report);
  document.getElementById('app').inert=true;
  document.getElementById('warehouse-root').innerHTML=`<section class="warehouse-page"><header><button class="btn btn-outline warehouse-back" onclick="closeWarehousePage()">← Назад</button><h1>Складской учёт</h1><button class="btn btn-primary" onclick="openWarehouseReportModal()">Сформировать отчёт</button></header><main>
  <div class="card"><div class="analytics-filters">
    <div class="analytics-period-head"><div class="analytics-period-title">Период складского учёта</div></div>
    <div class="analytics-period-layout">
      <div class="analytics-date-fields">
        <div class="analytics-filter"><label for="warehouse-from">От</label><input id="warehouse-from" type="date" value="${f.from}" onchange="applyWarehouseDates()"></div>
        <div class="analytics-filter"><label for="warehouse-to">До</label><input id="warehouse-to" type="date" value="${f.to}" onchange="applyWarehouseDates()"></div>
      </div>
      <div class="analytics-presets-wrap"><div class="analytics-presets-label">Быстрый период</div><div class="analytics-presets">
        ${[['7','7 дней'],['month','Месяц'],['quarter','Квартал']].map(([key,label])=>{const to=new Date(),from=new Date(to);if(key==='7')from.setDate(from.getDate()-6);else if(key==='month')from.setDate(1);else from.setMonth(Math.floor(from.getMonth()/3)*3,1);const active=f.from===localDateString(from)&&f.to===localDateString(to);return `<button class="btn ${active?'analytics-preset-active':''}" onclick="warehousePreset('${key}')">${label}</button>`;}).join('')}
      </div></div>
    </div>
  </div></div>
  <p class="pe-note">${f.from} — ${f.to} · Текущий склад: ${report.generatedAt}. Месяц и квартал — с начала текущего календарного периода.</p>
  <section class="card warehouse-simple"><h2>Упрощённый учёт</h2>${renderWarehouseTable(payload.sections[0])}</section>
  <div class="analytics-kpis">${[['Поступления',report.incomingValue],['Списания · оценка',report.outgoingValue],['Возвраты · оценка',report.returnedValue],['Остаток сейчас',report.currentValue]].map(([label,value])=>`<div class="analytics-kpi"><div class="k-label">${label}</div><div class="k-value">${money(value)}</div></div>`).join('')}</div>

  ${payload.sections.slice(1).map(section=>`<section class="card"><h2>${escapeHtml(section.title)}</h2>${renderWarehouseTable(section)}</section>`).join('')}<details class="card warehouse-notes"><summary>Как читать отчёт</summary>${payload.notes.map(note=>`<p class="pe-note">${escapeHtml(note)}</p>`).join('')}</details></main></section>`;
}
function openWarehouseReportModal(){
  if(!currentShiftEmployeeIsAdmin()){flash('Экспорт доступен администратору');return;}
  showModal(`<h2>Формирование отчёта</h2><p class="pe-note">За выбранный на странице период</p><div class="warehouse-report-options">${['Упрощённый учёт','Сводка','Движения и остатки','Поставщики','Реестр приёмок'].map((label,i)=>`<label><input type="checkbox" class="warehouse-report-section" value="${i}" checked><span>${label}</span></label>`).join('')}</div><div class="field"><label for="warehouse-report-format">Формат отчёта</label><select id="warehouse-report-format"><option value="pdf">PDF</option><option value="xlsx">Excel (.xlsx)</option></select></div><p class="pe-note">Пояснения к расчётам включаются всегда.</p><div class="modal-actions"><button class="btn btn-outline" onclick="closeModal()">Отмена</button><button class="btn btn-primary" onclick="generateWarehouseReport()">Сформировать</button></div>`);
}
function warehouseSelectedPayload(report,selected){
  const payload=warehouseExportPayload(report),finite=value=>Number.isFinite(value)?value:null;
  const numericRows=[
    payload.sections[0].excelRows,
    payload.sections[1].rows.map((row,i)=>[row[0],row[1],finite([report.incomingValue,report.outgoingValue,report.returnedValue,report.currentValue,report.endValue][i])]),
    report.rows.map(r=>[r.name+' / '+unitLabel(r.unit),r.start,r.incoming,r.outgoing,r.returned,r.end,r.current,r.incomingValue,r.outgoingValue,r.endValue].map((v,i)=>i===0?v:finite(v))),
    report.suppliers.map(s=>[s.name,s.count,finite(s.total),s.shortages]),
    report.documents.map(d=>[d.supplier,d.number,d.date,d.received,finite(d.total),d.shortage?'Расхождение':'Принят'])
  ];
  payload.sections=payload.sections.map((section,i)=>({...section,excelHeaders:section.headers.map(h=>h==='Сумма'?h+', '+state.currency:h),excelRows:numericRows[i]})).filter((_,i)=>selected.includes(i));
  if(!payload.sections.length)throw new Error('Выберите хотя бы один раздел отчёта');
  return payload;
}
function generateWarehouseReport(){
  if(!currentShiftEmployeeIsAdmin()){flash('Экспорт доступен администратору');return;}
  try{
    const selected=[...document.querySelectorAll('.warehouse-report-section:checked')].map(el=>Number(el.value));
    const format=document.getElementById('warehouse-report-format').value;
    if(!['pdf','xlsx'].includes(format))throw new Error('Выберите формат отчёта');
    const from=document.getElementById('warehouse-from').value,to=document.getElementById('warehouse-to').value;
    const payload=warehouseSelectedPayload(warehouseReport(from,to),selected);
    if(!window.webkit?.messageHandlers?.printer){flash('Экспорт доступен в приложении на iPad');return;}
    window.webkit.messageHandlers.printer.postMessage({action:format==='xlsx'?'shareWarehouseExcel':'shareWarehouseReport',report:payload});
    window._warehouseFilters={from,to};renderWarehousePage();closeModal();
  }catch(e){flash(e.message);}
}

function exportWarehousePDF(documentsOnly=false){
  if(!currentShiftEmployeeIsAdmin()){flash('Экспорт доступен администратору');return;}
  const from=document.getElementById('warehouse-from')?.value||window._warehouseFilters.from,to=document.getElementById('warehouse-to')?.value||window._warehouseFilters.to;
  try{
    const report=warehouseReport(from,to),payload=warehouseExportPayload(report,documentsOnly);
    window._warehouseFilters={from,to};renderWarehousePage();
    if(!window.webkit?.messageHandlers?.printer){flash('Экспорт PDF доступен в приложении на iPad');return;}
    window.webkit.messageHandlers.printer.postMessage({action:'shareWarehouseReport',report:payload});
  }catch(e){flash(e.message);}
}
