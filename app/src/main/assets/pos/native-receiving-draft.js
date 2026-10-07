(function(global){
 'use strict';
 const core=global.MPosCore,oldOpen=global.openReceivingDocument,oldSave=global.saveReceivingDraft;
 if(!core?.ReceivingDraft||typeof oldOpen!=='function'||typeof oldSave!=='function')return;
 function snapshot(value){return JSON.parse(JSON.stringify(value))}
 function ready(){if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw Error('Перезапустите M POS для восстановления данных')}
 function failed(error){if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;if(typeof markStorageBroken==='function')markStorageBroken(error)}
 global.openReceivingDocument=async function(orderId=null){
  if(global.MPosNativeReceivingDraftEnabled===false)return oldOpen.apply(this,arguments);
  if(global._receivingDraftOpening)return false;
  const order=orderId?state.purchaseOrders.find(o=>o.id===orderId):null;
  if(orderId&&(!order||['received','deleted'].includes(order.status))){flash('Заказ недоступен для приёмки');return false}
  global._receivingDraftOpening=true;
  try{
   ready();const result=await core.ReceivingDraft.execute(snapshot({version:1,operation:'open',orderId,expectedOrders:state.purchaseOrders,cart:state.receivingCart||[]}));
   if(result.changed)state.purchaseOrders=result.orders;
   if(order)global._receivingPageScroll=document.querySelector('.screen.active')?.scrollTop||0;
   global._receivingDraft=result.draft;renderReceivingDocument();return true;
  }catch(error){failed(error);flash('Не удалось сохранить начало приёмки: '+(error?.message||'ошибка сохранения'));return false}
  finally{global._receivingDraftOpening=false}
 };
 global.saveReceivingDraft=async function(){
  if(global.MPosNativeReceivingDraftEnabled===false)return oldSave.apply(this,arguments);
  if(global._receivingDraftSaveBusy||!global._receivingDraft)return false;
  receivingDocumentInput();const draft=snapshot(global._receivingDraft);
  global._receivingDraftSaveBusy=true;
  const buttons=[...(document.querySelectorAll?.('[data-receiving-draft-save]')||[])];buttons.forEach(button=>{button.disabled=true});
  try{
   ready();const result=await core.ReceivingDraft.execute(snapshot({version:1,operation:'save',draft,expectedOrders:state.purchaseOrders}));
   if(draft.orderId)state.purchaseOrders=result.orders;else global._receivingDraft=result.draft;
   global._receivingExpanded=false;
   if(draft.orderId){finishReceivingPage();global._receivingDraft=null}
   closeModal();render();flash('Черновик сохранён локально');return true;
  }catch(error){failed(error);flash('Не удалось сохранить черновик: '+(error?.message||'ошибка сохранения'));buttons.forEach(button=>{button.disabled=false});return false}
  finally{global._receivingDraftSaveBusy=false}
 };
})(window);
