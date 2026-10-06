/* Native employee/auth input; the existing verifier and lifecycle transaction stay authoritative. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.shiftScreen;
  if(!bridge||typeof openShiftModal!=='function'||typeof submitOpenShift!=='function')return;
  if(global.MPosNativeOpenFormEnabled===undefined)global.MPosNativeOpenFormEnabled=true;
  let active=null,generation=0;
  const openOriginal=global.openShiftModal,closeOriginal=global.closeModal,showOriginal=global.showModal;
  function abandon(){if(active){active.password.value='';bridge.postMessage({action:'openFormHide',token:active.token});active=null;}}
  global.closeModal=function(...args){if(active?.busy)return false;abandon();return closeOriginal.apply(this,args);};
  global.showModal=function(...args){abandon();return showOriginal.apply(this,args);};
  global.openShiftModal=function(){
    if(active?.busy)return;
    abandon();const result=openOriginal.apply(this,arguments);
    if(!global.MPosNativeOpenFormEnabled)return result;
    const select=document.getElementById('sf-employee'),password=document.getElementById('sf-admin-password');
    if(!select||!password||currentShift())return result;
    const token='native-open-form-'+(++generation);active={token,select,password,busy:false};
    if(bridge.postMessage({action:'openFormShow',token,currency:state.currency||''})===false){active=null;return result;}
    const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.style.visibility='hidden';
    return result;
  };
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.NativeOpenForm=Object.freeze({
    async handleAction(payload){
      const form=active;if(!form||payload?.token!==form.token)return;
      if(payload.action==='fallback'){
        if(form.busy)return;global.MPosNativeOpenFormEnabled=false;abandon();
        const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.style.visibility='';return;
      }
      if(payload.action==='cancel'){if(!form.busy)global.closeModal();return;}
      if(payload.action!=='submit'||form.busy)return;
      if(!state.loaded||criticalOperationBusy||currentShift()||typeof payload.employeeId!=='string'||typeof payload.password!=='string'||!state.employees.some(e=>e.id===payload.employeeId)){
        bridge.postMessage({action:'openFormResult',token:form.token,ok:false,blocked:false});return;
      }
      form.select.value=payload.employeeId;
      if(form.select.value!==payload.employeeId){bridge.postMessage({action:'openFormResult',token:form.token,ok:false,blocked:false});return;}
      form.password.value=payload.password;form.busy=true;let ok=false;
      try{ok=await global.submitOpenShift()===true;}
      catch(_){if(typeof markStorageBroken==='function')markStorageBroken('native opening form submission failed');}
      finally{form.password.value='';form.busy=false;}
      if(active===form){
        const blocked=typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending;
        bridge.postMessage({action:'openFormResult',token:form.token,ok,blocked});if(ok)global.closeModal();
      }
    }
  });
})(window);
