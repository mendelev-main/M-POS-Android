/* Native counted-cash input retains approved closure and post-commit output flow. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.shiftScreen;
  if(!bridge||typeof openCloseShiftModal!=='function'||typeof submitCloseShift!=='function')return;
  if(global.MPosNativeCloseFormEnabled===undefined)global.MPosNativeCloseFormEnabled=true;
  let active=null,generation=0;
  const openOriginal=global.openCloseShiftModal,closeOriginal=global.closeModal,showOriginal=global.showModal;
  function abandon(){if(active){bridge.postMessage({action:'closeFormHide',token:active.token});active=null;}}
  global.closeModal=function(...args){if(active?.busy)return false;abandon();return closeOriginal.apply(this,args);};
  global.showModal=function(...args){abandon();return showOriginal.apply(this,args);};
  global.openCloseShiftModal=function(){
    if(active?.busy)return;
    abandon();const result=openOriginal.apply(this,arguments);
    if(!global.MPosNativeCloseFormEnabled)return result;
    const shift=currentShift(),counted=document.getElementById('sf-counted');
    if(!shift||!counted)return result;
    const token='native-close-form-'+(++generation);
    active={token,shiftId:shift.id,counted,busy:false};
    if(bridge.postMessage({action:'closeFormShow',token,shiftId:shift.id,currency:state.currency||'',theme:document.documentElement?.dataset?.theme||'light'})===false){active=null;return result;}
    const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.style.visibility='hidden';
    return result;
  };
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.NativeCloseForm=Object.freeze({
    async handleAction(payload){
      const form=active;if(!form||payload?.token!==form.token)return;
      if(payload.action==='fallback'){
        if(form.busy)return;
        global.MPosNativeCloseFormEnabled=false;abandon();
        const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.style.visibility='';
        return;
      }
      if(payload.action==='cancel'){if(!form.busy)global.closeModal();return;}
      if(payload.action!=='submit'||form.busy)return;
      if(payload.type!=='close'||payload.shiftId!==form.shiftId||currentShift()?.id!==form.shiftId||!state.loaded||criticalOperationBusy||typeof payload.amount!=='number'||!Number.isFinite(payload.amount)||payload.amount<0){
        bridge.postMessage({action:'closeFormResult',token:form.token,ok:false,blocked:false});return;
      }
      form.busy=true;form.counted.value=String(payload.amount);let ok=false;
      try{ok=await global.submitCloseShift()===true;}
      catch(_){if(typeof markStorageBroken==='function')markStorageBroken('native close form submission failed');}
      finally{form.busy=false;}
      if(active===form){
        const blocked=typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending;
        bridge.postMessage({action:'closeFormResult',token:form.token,ok,blocked});
        if(ok)global.closeModal();
      }
    }
  });
})(window);
