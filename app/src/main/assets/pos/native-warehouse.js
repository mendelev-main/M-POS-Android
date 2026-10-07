(function(global){
 'use strict';
 const core=global.MPosCore,originalReport=global.warehouseReport,originalRender=global.renderWarehousePage,originalClose=global.closeWarehousePage,originalGenerate=global.generateWarehouseReport,originalPDF=global.exportWarehousePDF,originalMonthly=global.maybeSendMonthlyWarehouseReport;
 if(!core?.Warehouse||typeof originalReport!=='function'||typeof originalRender!=='function')return;
 let model=null,generation=0,exportBusy=false,monthlyBusy=false;
 const disabled=()=>global.MPosNativeWarehouseEnabled===false;
 function present(report){const result=JSON.parse(JSON.stringify(report));result.generatedAt=fmtDate(result.generatedTimestamp);delete result.generatedTimestamp;result.rows.sort((a,b)=>a.name.localeCompare(b.name,'ru'));for(const doc of result.documents)doc.received=fmtDate(doc.timestamp);return result}
 async function read(from,to){const now=Date.now();warehouseRange(from,to);const result=await core.Warehouse.read({version:1,from,to,now,validationNow:now,zone:Intl.DateTimeFormat().resolvedOptions().timeZone});return present(result.report)}
 function using(report,callback){const previous=model;model=report;try{return callback()}finally{model=previous}}
 global.warehouseReport=function(from,to,now){if(disabled())return originalReport.apply(this,arguments);if(!model||model.from!==from||model.to!==to)throw Error('Складской отчёт ещё не загружен');return model};
 global.renderWarehousePage=async function(){
  if(disabled())return originalRender.apply(this,arguments);
  if(!currentShiftEmployeeIsAdmin()){flash('Доступен администратору');return false}
  const ticket=++generation,{from,to}=global._warehouseFilters;
  try{const report=await read(from,to);if(ticket!==generation)return false;if(!currentShiftEmployeeIsAdmin()){flash('Доступен администратору');return false}using(report,()=>originalRender.call(this));return true}
  catch(error){if(ticket===generation)flash('Не удалось сформировать складской отчёт: '+error.message);return false}
 };
 global.closeWarehousePage=function(){generation++;return originalClose.apply(this,arguments)};
 global.generateWarehouseReport=async function(){
  if(disabled())return originalGenerate.apply(this,arguments);
  if(!currentShiftEmployeeIsAdmin()){flash('Экспорт доступен администратору');return false}if(exportBusy)return false;
  exportBusy=true;const ticket=generation;
  try{
   const selected=[...document.querySelectorAll('.warehouse-report-section:checked')].map(el=>Number(el.value)),format=document.getElementById('warehouse-report-format').value;
   if(!['pdf','xlsx'].includes(format))throw Error('Выберите формат отчёта');
   const from=document.getElementById('warehouse-from').value,to=document.getElementById('warehouse-to').value,report=await read(from,to),payload=warehouseSelectedPayload(report,selected);
   if(!currentShiftEmployeeIsAdmin()){flash('Экспорт доступен администратору');return false}
   if(!global.webkit?.messageHandlers?.printer){flash('Экспорт доступен в приложении M POS');return false}
   global.webkit.messageHandlers.printer.postMessage({action:format==='xlsx'?'shareWarehouseExcel':'shareWarehouseReport',report:payload});
   if(ticket===generation){generation++;global._warehouseFilters={from,to};using(report,()=>originalRender());closeModal()}return true;
  }catch(error){flash(error.message);return false}finally{exportBusy=false}
 };
 global.exportWarehousePDF=async function(documentsOnly=false){
  if(disabled())return originalPDF.apply(this,arguments);
  if(!currentShiftEmployeeIsAdmin()){flash('Экспорт доступен администратору');return false}if(exportBusy)return false;
  exportBusy=true;const ticket=generation;
  try{
   const from=document.getElementById('warehouse-from')?.value||global._warehouseFilters.from,to=document.getElementById('warehouse-to')?.value||global._warehouseFilters.to,report=await read(from,to),payload=warehouseExportPayload(report,documentsOnly);
   if(!currentShiftEmployeeIsAdmin()){flash('Экспорт доступен администратору');return false}
   if(ticket===generation){generation++;global._warehouseFilters={from,to};using(report,()=>originalRender())}
   if(!global.webkit?.messageHandlers?.printer){flash('Экспорт PDF доступен в приложении M POS');return false}
   global.webkit.messageHandlers.printer.postMessage({action:'shareWarehouseReport',report:payload});return true;
  }catch(error){flash(error.message);return false}finally{exportBusy=false}
 };
 if(typeof originalMonthly==='function')global.maybeSendMonthlyWarehouseReport=async function(){
  if(disabled())return originalMonthly.apply(this,arguments);if(monthlyBusy)return false;
  const t=telegramConfigFromState();if(!t.enabled||!t.notifyMonthlyWarehouse||!t.botToken||!t.chatId||!currentShiftEmployeeIsAdmin()||!global.webkit?.messageHandlers?.telegram)return false;
  const period=previousCalendarMonthRange();if(t.lastMonthlyWarehouseSent===period.key)return false;monthlyBusy=true;
  try{const report=await read(period.from,period.to);using(report,()=>originalMonthly.call(this));return true}
  catch(error){console.error('Не удалось подготовить ежемесячный складской отчёт:',error);return false}finally{monthlyBusy=false}
 };
})(window);
