(function(global){
  'use strict';
  const core=global.MPosCore,bridge=global.webkit?.messageHandlers?.workspace;
  if(!core?.WorkspaceNavigationLifecycle||!bridge)return;
  const enabled=()=>global.MPosNativeLayoutUiEnabled!==false&&global.MPosNativeWorkspaceNavigationEnabled!==false;
  const view=()=>({tab:state.tab,search:String(state.search||''),posPath:state.posPath??null,posFolder:state.posFolder||'',editMode:!!state.editMode});
  let current=null,queued=false,sequence=0,modal=null,form=null;
  let generation=0;
  const same=(a,b)=>a===b||!!a&&!!b&&typeof a==='object'&&typeof b==='object'&&Array.isArray(a)===Array.isArray(b)&&Object.keys(a).length===Object.keys(b).length&&Object.keys(a).every(k=>Object.hasOwn(b,k)&&same(a[k],b[k]));
  function hide(){generation++;if(current){bridge.postMessage({action:'layoutHide',token:current.token});current.node.style.opacity=current.opacity;current.node.style.pointerEvents=current.pointer;if(current.app)current.app.inert=current.inert;current=null;}modal=null;core.OverlayLifecycle?.changed();}
  function schedule(){if(!queued){queued=true;requestAnimationFrame(update);}}
  async function update(){
    queued=false;if(!enabled()||!state.loaded||state.tab!=='pos'||!state.editMode||state.busy||state.paymentPage||document.hidden||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)){hide();return;}
    const node=document.querySelector('#screen-pos .pos-left');if(!node){hide();return;}
    const stamp=JSON.stringify([core.WorkspaceNavigationLifecycle.generation(),view()]);
    if(current?.node===node&&current.stamp===stamp&&!form)return;
    const request=++generation;
    try{
      await core.WorkspaceNavigationLifecycle.toolbar();
      const model=await core.WorkspaceNavigation.execute({version:1,operation:'layoutView',expected:view(),parent:global._posFolderModal?.id||state.posFolder||''});
      if(request!==generation||stamp!==JSON.stringify([core.WorkspaceNavigationLifecycle.generation(),view()])||node!==document.querySelector('#screen-pos .pos-left')||!enabled()||document.hidden)return;
      if(model?.ok!==true||model.authoritative!==true||!Array.isArray(model.tiles)||typeof model.documentRevision!=='string')throw Error('Некорректная раскладка');
      hide();const token='layout-'+(++sequence),r=node.getBoundingClientRect(),app=document.getElementById('app');current={node,token,stamp,model,app,inert:app?.inert||false,opacity:node.style.opacity,pointer:node.style.pointerEvents};
      const packet={action:'layoutShow',token,model,theme:document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light',viewportWidth:global.innerWidth,viewportHeight:global.innerHeight,rect:{left:r.left,top:r.top,width:r.width,height:r.height},...(form||{})};form=null;
      if(bridge.postMessage(packet)===false){hide();return;}node.style.opacity='0';node.style.pointerEvents='none';
    }catch(error){if(request===generation){hide();global.flash?.(error?.message||'Не удалось открыть раскладку');}}
  }
  core.LayoutUi=Object.freeze({
    activeToken(){return modal;},
    back(){if(current)bridge.postMessage({action:'layoutBack',token:current.token});},
    invalidate:hide,
    action(payload){
      if(!current||payload.token!==current.token)return;
      if(payload.action==='fallback'){global.MPosNativeLayoutUiEnabled=false;hide();return;}
      if(payload.action==='modal'){modal=payload.active?current.token:null;if(current.app)current.app.inert=payload.active||current.inert;if(typeof payload.parent==='string')global._posFolderModal=payload.parent?{category:state.posPath,id:payload.parent}:null;core.OverlayLifecycle?.changed();return;}
      if(payload.action==='route'){
        const operation=payload.operation;if(!['toggleEdit','closeCategory'].includes(operation))return;
        Promise.resolve(operation==='toggleEdit'?global.toggleEditMode():global.closePosCategory()).finally(()=>{hide();schedule();});
      }
    },
    committed(payload){
      const result=payload.result;
      if(!result?.allowed)return;
      const active=current&&payload.token===current.token&&enabled()&&state.loaded&&state.tab==='pos'&&state.editMode&&
        JSON.stringify(view())===JSON.stringify(Object.fromEntries(Object.keys(view()).map(key=>[key,result.expected?.[key]])));
      const before=result.key==='layout'?state.layoutTiles:state.posNavigation;
      if(!active||!same(before,result.before)){
        if(typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
        global.flash?.('Раскладка сохранена. Перезапустите M POS для обновления данных');hide();return;
      }
      if(result.key==='layout')state.layoutTiles=result.document.tiles;else state.posNavigation=result.document;
      if(result.key==='posNavigation'&&global._posFolderModal&&!result.document.categories.some(c=>c.category===state.posPath&&c.items.some(i=>i.type==='folder'&&i.id===global._posFolderModal.id)))global._posFolderModal=null;
      bridge.postMessage({action:'layoutApplied',requestId:payload.requestId});
    }
  });
  const renderFolder=global.renderPosFolderModal;
  if(typeof renderFolder==='function')global.renderPosFolderModal=function(){
    if(!enabled()||!state.editMode)return renderFolder.apply(this,arguments);
    const root=document.getElementById('modal-root');if(root)root.replaceChildren();schedule();
  };
  for(const [name,kind]of [['openLayoutEditor','add'],['openPosFolderEditor','folder'],['openPosTileMove','move']]){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(id=''){if(!enabled()||!state.editMode)return original.apply(this,arguments);form={form:kind,id};schedule();};
  }
  function observe(){const observer=new MutationObserver(schedule);for(const id of ['app','modal-root']){const node=document.getElementById(id);if(node)observer.observe(node,{childList:true,subtree:true});}
    observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});global.addEventListener('resize',()=>{if(current)current.stamp='';schedule();});document.addEventListener('visibilitychange',schedule);schedule();}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})(window);
