(function(global){
  'use strict';
  const core=global.MPosCore||(global.MPosCore={}),bridge=global.webkit?.messageHandlers?.workspace;
  if(!bridge)return;
  let revision=0,modal=null,warehouse=null,receiving=null,sequence=0;
  const identities=new WeakMap();let identity=0;
  const key=value=>{if(!value||typeof value!=='object')return value??null;if(!identities.has(value))identities.set(value,++identity);return identities.get(value);};
  function state(){return{version:1,enabled:global.MPosNativeSystemBackEnabled!==false,pendingImport:!!global._pendingBackupImport,modal:!!(modal||core.NativeOpenForm?.activeToken?.()||core.LayoutUi?.activeToken?.()||core.CartItemUi?.activeToken?.()||core.CartAddUi?.activeToken?.()),warehouse:!!warehouse,receiving:!!receiving};}
  function stamp(){return JSON.stringify([global.MPosNativeSystemBackEnabled!==false,key(global._pendingBackupImport),modal,warehouse,receiving,core.NativeOpenForm?.activeToken?.(),core.LayoutUi?.activeToken?.(),core.CartItemUi?.activeToken?.(),core.CartAddUi?.activeToken?.()]);}
  let published='';
  function changed(){const next=stamp();if(next===published)return;published=next;revision++;bridge.postMessage({action:'backState',revision,...state()});global.dispatchEvent?.(new CustomEvent('mpos-native-overlay-state'));}
  core.OverlayLifecycle=Object.freeze({changed,snapshot:()=>({...state(),revision,stamp:stamp()}),invalidate(){modal=warehouse=receiving=null;changed();}});
  const hook=(name,update)=>{const original=global[name];if(typeof original!=='function')return;global[name]=function(...args){const result=original.apply(this,args);
    const complete=value=>{if(value!==false){update();changed();}return value;};return result&&typeof result.then==='function'?result.then(complete):complete(result);};};
  hook('showModal',()=>{modal='modal-'+(++sequence)});
  hook('closeModal',()=>{modal=null});
  hook('renderPosFolderModal',()=>{modal=global._posFolderModal&&!(typeof state!=='undefined'&&state.editMode)?'folder-'+(++sequence):null});
  hook('renderWarehousePage',()=>{if(global.currentShiftEmployeeIsAdmin?.())warehouse='warehouse-'+(++sequence)});
  hook('closeWarehousePage',()=>{warehouse=null});
  hook('renderReceivingDocument',()=>{if(global._receivingDraft?.orderId)receiving='receiving-'+(++sequence)});
  hook('finishReceivingPage',()=>{receiving=null});
  hook('cancelBackupImport',()=>{});
  global.addEventListener('mpos-native-open-state',changed);changed();
})(window);
