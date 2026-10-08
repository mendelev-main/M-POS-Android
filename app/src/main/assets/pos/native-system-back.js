(function(global){
  'use strict';
  const core=global.MPosCore||(global.MPosCore={});
  const identities=new WeakMap();let identity=0,sequence=0,pending=null;
  function key(value){if(value&&typeof value==='object'){if(!identities.has(value))identities.set(value,++identity);return identities.get(value);}return String(value??'');}
  function read(){
    const imported=global._pendingBackupImport;
    const modal=document.querySelector('.modal-overlay');
    const nativeOpening=core.NativeOpenForm?.activeToken?.();
    const warehouse=document.getElementById('warehouse-root')?.firstElementChild;
    const receiving=document.getElementById('receiving-page-root')?.firstElementChild;
    return{version:1,pendingImport:!!imported,modal:!!modal||!!nativeOpening,warehouse:!!warehouse,receiving:!!receiving,
      stamp:JSON.stringify([key(imported),key(modal),key(warehouse),key(receiving),nativeOpening||null])};
  }
  const effects={CANCEL_IMPORT:()=>global.cancelBackupImport(),CLOSE_MODAL:()=>global.closeModal(),
    CLOSE_WAREHOUSE:()=>global.closeWarehousePage(),FINISH_RECEIVING:()=>global.finishReceivingPage(),BACKGROUND:()=>{}};
  core.SystemBack=Object.freeze({
    capture(){if(global.MPosNativeSystemBackEnabled===false)return null;const snapshot=read();pending={...snapshot,token:++sequence};const {stamp,...payload}=pending;return JSON.stringify(payload);},
    apply(input){
      if(!pending||input?.token!==pending.token)return false;
      const snapshot=pending;pending=null;
      if(global.MPosNativeSystemBackEnabled===false||snapshot.stamp!==read().stamp||!Object.hasOwn(effects,input.action))return false;
      try{effects[input.action]();return true;}catch(_error){return false;}
    }
  });
})(window);
