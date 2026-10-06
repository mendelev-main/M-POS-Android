/* Native cash inputs reuse the reviewed submission and atomic Kotlin command. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.shiftScreen;
  if(!bridge||typeof openCashMovementModal!=='function'||typeof submitCashMovement!=='function')return;
  if(global.MPosNativeCashFormsEnabled===undefined)global.MPosNativeCashFormsEnabled=true;
  let active=null,generation=0;
  const openOriginal=global.openCashMovementModal,closeOriginal=global.closeModal,showOriginal=global.showModal;
  function abandon(){if(active){bridge.postMessage({action:'cashFormHide',token:active.token});active=null;}}
  global.closeModal=function(...args){if(active?.busy)return false;abandon();return closeOriginal.apply(this,args);};
  global.showModal=function(...args){abandon();return showOriginal.apply(this,args);};
  global.openCashMovementModal=function(type){
    if(active?.busy)return;
    abandon();
    const result=openOriginal.apply(this,arguments);
    if(!global.MPosNativeCashFormsEnabled||!['deposit','withdrawal'].includes(type))return result;
    const shift=currentShift(),amount=document.getElementById('cash-movement-amount'),note=document.getElementById('cash-movement-note');
    if(!shift||!amount||!note)return result;
    const token='native-cash-form-'+(++generation);
    active={token,type,shiftId:shift.id,busy:false,amount,note};
    if(bridge.postMessage({action:'cashFormShow',token,type,shiftId:shift.id,theme:document.documentElement?.dataset?.theme||'light'})===false){active=null;return result;}
    // Keep real legacy fields as compatibility inputs; native dialog owns visible editing.
    const overlay=document.querySelector('#modal-root .modal-overlay');
    if(overlay)overlay.style.visibility='hidden';
    amount.blur?.();
    return result;
  };
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.NativeCashForms=Object.freeze({
    async handleAction(payload){
      const form=active;
      if(!form||payload?.token!==form.token)return;
      if(payload.action==='cancel'){if(!form.busy)global.closeModal();return;}
      if(payload.action!=='submit'||form.busy)return;
      if(payload.type!==form.type||payload.shiftId!==form.shiftId||currentShift()?.id!==form.shiftId||!state.loaded||criticalOperationBusy){
        bridge.postMessage({action:'cashFormResult',token:form.token,ok:false,blocked:false});return;
      }
      if(typeof payload.amount!=='number'||!Number.isFinite(payload.amount)||payload.amount<=0||typeof payload.note!=='string'){
        bridge.postMessage({action:'cashFormResult',token:form.token,ok:false,blocked:false});return;
      }
      form.busy=true;form.amount.value=String(payload.amount);form.note.value=payload.note;
      let ok=false;
      try{ok=await global.submitCashMovement(form.type)===true;}
      catch(_){if(typeof markStorageBroken==='function')markStorageBroken('native cash form submission failed');}
      finally{form.busy=false;}
      // Successful legacy submission closes the modal. Make that close effective after busy guard.
      if(active===form){
        const blocked=typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending;
        bridge.postMessage({action:'cashFormResult',token:form.token,ok,blocked});
        if(ok)global.closeModal();
      }
    }
  });
})(window);
