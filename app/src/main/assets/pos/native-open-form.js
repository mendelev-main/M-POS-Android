/* Native opening authority; JS only coordinates the compatibility screen and post-commit effects. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.shiftScreen;
  if(!bridge||typeof openShiftModal!=='function'||typeof submitOpenShift!=='function')return;
  if(global.MPosNativeOpenFormEnabled===undefined)global.MPosNativeOpenFormEnabled=true;
  let active=null,generation=0;
  const same=(a,b)=>{
    if(a===b)return true;
    if(!a||!b||typeof a!=='object'||typeof b!=='object'||Array.isArray(a)!==Array.isArray(b))return false;
    const keys=Object.keys(a);return keys.length===Object.keys(b).length&&keys.every(k=>Object.prototype.hasOwnProperty.call(b,k)&&same(a[k],b[k]));
  };
  const openOriginal=global.openShiftModal,closeOriginal=global.closeModal,showOriginal=global.showModal;
  function abandon(){if(active){active.password.value='';bridge.postMessage({action:'openFormHide',token:active.token});active=null;}}
  global.closeModal=function(...args){if(active?.busy)return false;abandon();return closeOriginal.apply(this,args);};
  global.showModal=function(...args){if(active?.nativeCommit&&active.busy)return false;abandon();return showOriginal.apply(this,args);};
  global.openShiftModal=function(){
    if(active?.busy)return;
    abandon();const result=openOriginal.apply(this,arguments);
    if(!global.MPosNativeOpenFormEnabled)return result;
    const select=document.getElementById('sf-employee'),password=document.getElementById('sf-admin-password');
    if(!select||!password||currentShift())return result;
    const token='native-open-form-'+(++generation),nativeCommit=global.MPosNativeShiftOpenCommandEnabled!==false;active={token,select,password,busy:false,nativeCommit};
    if(bridge.postMessage({action:'openFormShow',token,nativeCommit,currency:state.currency||'',theme:document.documentElement?.dataset?.theme||'light'})===false){active=null;return result;}
    const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.style.visibility='hidden';
    return result;
  };
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.NativeOpenForm=Object.freeze({
    activeToken(){return active?.token||null;},
    invalidate(){if(!active?.busy)abandon();},
    openNative(){
      if(!global.MPosNativeOpenFormEnabled||global.MPosNativeShiftOpenCommandEnabled===false){global.openShiftModal();return true;}
      if(active?.busy||!state.loaded||criticalOperationBusy||criticalStorageRecoveryPending||currentShift())return false;
      abandon();const token='native-open-form-'+(++generation);
      active={token,password:{value:''},busy:false,nativeCommit:true,nativeOnly:true};
      if(bridge.postMessage({action:'openFormShow',token,nativeCommit:true,currency:state.currency||'',theme:document.documentElement?.dataset?.theme||'light'})===false){active=null;return false;}
      return true;
    },
    async handleAction(payload){
      const form=active;if(!form||payload?.token!==form.token)return;
      if(payload.action==='fallback'){
        if(form.busy)return;global.MPosNativeOpenFormEnabled=false;abandon();
        if(form.nativeOnly){openOriginal();return;}
        const overlay=document.querySelector('#modal-root .modal-overlay');if(overlay)overlay.style.visibility='';return;
      }
      if(payload.action==='cancel'){if(!form.busy)global.closeModal();return;}
      if(payload.action==='prepare'){
        if(!form.nativeCommit||form.busy)return;
        if(!state.loaded||criticalOperationBusy||criticalStorageRecoveryPending||currentShift()||typeof payload.employeeId!=='string'||!state.employees.some(e=>e.id===payload.employeeId)){
          bridge.postMessage({action:'openFormResult',token:form.token,ok:false,blocked:!!criticalStorageRecoveryPending});return;
        }
        form.busy=true;criticalOperationBusy=true;
        form.employeeId=payload.employeeId;form.beforeShifts=JSON.stringify(state.shifts);form.beforeEmployees=JSON.stringify(state.employees);
        try{
          form.id=uid();form.openedAt=Date.now();
          if(bridge.postMessage({action:'openFormCommit',token:form.token,employeeId:form.employeeId,id:form.id,openedAt:form.openedAt,expectedShifts:state.shifts,expectedEmployees:state.employees})===false){
            form.busy=false;criticalOperationBusy=false;bridge.postMessage({action:'openFormResult',token:form.token,ok:false,blocked:false});
          }
        }catch(_){form.busy=false;criticalOperationBusy=false;criticalStorageRecoveryPending=true;bridge.postMessage({action:'openFormResult',token:form.token,ok:false,blocked:true});}
        return;
      }
      if(payload.action==='committed'){
        if(!form.nativeCommit||!form.busy)return;
        let ok=false,blocked=!!payload.blocked;
        try{
          if(payload.ok===true){
            if(JSON.stringify(state.shifts)!==form.beforeShifts||JSON.stringify(state.employees)!==form.beforeEmployees||!Array.isArray(payload.shifts)||payload.shifts.length!==state.shifts.length+1||payload.shift?.id!==form.id||payload.shift?.openedAt!==form.openedAt||payload.shift?.employeeId!==form.employeeId||payload.shift?.status!=='open'||!same(payload.shifts.slice(0,-1),JSON.parse(form.beforeShifts))||!same(payload.shifts.at(-1),payload.shift))throw Error('opening acknowledgement conflict');
            state.shifts=payload.shifts;ok=true;
          }else if(typeof payload.ok!=='boolean')throw Error('invalid opening acknowledgement');
        }catch(_){blocked=true;}
        if(blocked)criticalStorageRecoveryPending=true;
        form.busy=false;criticalOperationBusy=false;form.password.value='';
        bridge.postMessage({action:'openFormResult',token:form.token,ok,blocked});
        if(ok){
          global.closeModal();global.render();
          try{global.sendTelegramShiftOpened(payload.shift);global.maybeSendMonthlyWarehouseReport();}
          catch(_){global.flash?.('Смена открыта, но внешний отчёт не отправлен');}
        }else if(payload.message)global.flash?.(payload.message);
        return;
      }
      if(payload.action!=='submit'||form.busy)return;
      if(form.nativeCommit)return;
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
