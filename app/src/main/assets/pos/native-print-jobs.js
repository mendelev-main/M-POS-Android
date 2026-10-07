(function(global){
 'use strict';
 const bridge=global.webkit?.messageHandlers?.printer;if(!bridge||typeof global.__printerSettingsSnapshot!=='function')return;
 let sequence=0;const pending=new Map(),previousEvent=global.__nativePrinterEvent;
 global.__nativePrinterEvent=function(event){
  if(event?.type==='printAdmission'){
   const entry=pending.get(event.requestId);if(entry){pending.delete(event.requestId);clearTimeout(entry.timer);entry.resolve(event.ok===true&&event.count>0)};return;
  }
  if(typeof previousEvent==='function')previousEvent(event);
 };
 function route(trigger,order){
  const settings=global.__printerSettingsSnapshot();
  const input=JSON.parse(JSON.stringify({action:'routePrint',version:1,requestId:'print-'+Date.now()+'-'+(++sequence),trigger,order:order||{},printers:settings.printers,now:Date.now()}));
  let result;
  if(trigger==='shift-close')result=new Promise(resolve=>{const timer=setTimeout(()=>{pending.delete(input.requestId);global.flash?.('Статус задания печати неизвестен. Проверьте принтер перед повтором');resolve(false)},15000);pending.set(input.requestId,{resolve,timer})});
  try{if(bridge.postMessage(input)===false)throw Error('Нативная очередь печати недоступна');return result}
  catch(error){const entry=pending.get(input.requestId);if(entry){pending.delete(input.requestId);clearTimeout(entry.timer);entry.resolve(false)}global.flash?.('Не удалось передать задание печати');if(trigger!=='shift-close')throw error;return result}
 }
 for(const [name,trigger]of [['sendOrderToPrint','manual-receipt'],['sendKitchenOrderToPrint','manual-kitchen'],['printCompletedOrder','completed'],['printKitchenOrderNow','kitchen-now'],['printShiftCloseReceipt','shift-close']]){
  const original=global[name];if(typeof original!=='function')continue;
  global[name]=name==='sendOrderToPrint'?async function(order){if(global.MPosNativePrintJobsEnabled===false)return original.apply(this,arguments);route(trigger,order)}:function(order){if(global.MPosNativePrintJobsEnabled===false)return original.apply(this,arguments);return route(trigger,order)};
 }
})(window);
