/* Kotlin owns the shift summary; approved forms retain business actions. */
(function(){
  const bridge=window.webkit?.messageHandlers?.shiftScreen;
  if(!bridge || typeof renderShiftScreen!=='function') return;
  if(window.MPosNativeShiftScreenEnabled===undefined) window.MPosNativeShiftScreenEnabled=true;
  let renderRevision=0;
  const original=renderShiftScreen;
  renderShiftScreen=function(...args){renderRevision++;return original.apply(this,args).replace('<div class="screen content-screen','<div data-mpos-shift-screen class="screen content-screen');};
  let queued=false,lastKey='',visible=false,nextId=0;
  const ids=new WeakMap();
  function identity(value){if(!value||typeof value!=='object')return 0;if(!ids.has(value))ids.set(value,++nextId);return ids.get(value);}
  function hide(){lastKey='';if(visible){visible=false;bridge.postMessage({action:'hide'});}}
  function update(){
    queued=false;
    if(!window.MPosNativeShiftScreenEnabled||!state.loaded||state.tab!=='shift'||document.querySelector('#modal-root .modal-overlay')){hide();return;}
    const root=document.querySelector('[data-mpos-shift-screen]');
    if(!root){hide();return;}
    const r=root.getBoundingClientRect(),width=window.innerWidth,height=window.innerHeight;
    if(![r.left,r.top,r.width,r.height,width,height].every(Number.isFinite)||r.width<=0||r.height<=0||width<=0||height<=0){hide();return;}
    const theme=document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light';
    const payload={theme,action:'show',rect:{left:r.left,top:r.top,width:r.width,height:r.height},viewportWidth:width,viewportHeight:height,currency:state.currency||'',establishmentName:state.company?.establishmentName||''};
    const key=JSON.stringify([payload,identity(state.shifts),identity(state.orders),renderRevision]);
    if(key===lastKey)return;
    lastKey=key;visible=true;bridge.postMessage(payload);
  }
  function schedule(){if(!queued){queued=true;requestAnimationFrame(update);}}
  window.__mposShiftScreenAction=function(payload){
    const name=payload?.action;
    if(!['open','deposit','withdrawal','close','report','fallback'].includes(name))return;
    if(name==='fallback'){window.MPosNativeShiftScreenEnabled=false;hide();return;}
    if(!window.MPosNativeShiftScreenEnabled||!state.loaded||state.tab!=='shift'||state.busy||document.querySelector('#modal-root .modal-overlay')){schedule();return;}
    if(['deposit','withdrawal','close'].includes(name)&&currentShift()?.id!==payload.shiftId){flash('Смена изменилась. Обновите экран.','err');lastKey='';schedule();return;}
    lastKey='';visible=false;
    try{
      const result=name==='open'?openShiftModal():name==='close'?openCloseShiftModal():name==='report'?viewShiftModal(payload.shiftId):openCashMovementModal(name);
      Promise.resolve(result).catch(()=>{flash('Не удалось открыть форму смены','err');schedule();});
    }catch(_){flash('Не удалось открыть форму смены','err');}
    schedule();
  };
  function observe(){
    const observer=new MutationObserver(schedule);
    for(const id of ['app','modal-root']){const node=document.getElementById(id);if(node)observer.observe(node,{childList:true,subtree:true});}
    observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
    window.addEventListener('resize',()=>{lastKey='';schedule();});
    document.addEventListener('visibilitychange',schedule);
    schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})();
