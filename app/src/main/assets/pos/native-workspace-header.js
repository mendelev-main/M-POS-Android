(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.workspace,lifecycle=global.MPosCore?.WorkspaceNavigationLifecycle;
  if(!bridge||!lifecycle?.header)return;
  if(global.MPosNativeWorkspaceHeaderEnabled===undefined)global.MPosNativeWorkspaceHeaderEnabled=true;
  const view=()=>({tab:state.tab,search:String(state.search||''),posPath:state.posPath??null,posFolder:state.posFolder||'',editMode:!!state.editMode});
  const enabled=()=>global.MPosNativeWorkspaceHeaderEnabled!==false&&global.MPosNativeWorkspaceNavigationEnabled!==false;
  const covered=()=>global.MPosCore?.NativeOpenForm?.activeToken?.()||global.MPosCore?.LayoutUi?.activeToken?.()||document.querySelector('#modal-root .modal-overlay')||state.paymentPage||document.querySelector('#product-editor-root .product-editor')||document.getElementById('printer-page')||['warehouse-root','receiving-page-root'].some(id=>document.getElementById(id)?.children.length);
  let current=null,cache=null,scheduled=false,sequence=0,lastSignature='';
  function hide(){cache=null;lastSignature='';if(current){bridge.postMessage({action:'headerHide',token:current.token});for(const [node,opacity,pointer]of current.styles){node.style.opacity=opacity;node.style.pointerEvents=pointer;}current=null;}}
  const key=()=>JSON.stringify([lifecycle.generation(),view(),state.shifts]);
  function rect(node){const r=node.getBoundingClientRect();return{left:r.left,top:r.top,width:r.width,height:r.height};}
  const nodes=root=>({brand:root.querySelector('.brand'),tabs:root.querySelector('.tabs'),settings:root.querySelector('.settings-topbar-btn'),
    ...(lifecycle.shiftHeader&&global.MPosCore?.NativeOpenForm?.openNative?{shift:root.querySelector('.shift-pill')}:{})});
  async function update(){
    scheduled=false;
    if(!enabled()||typeof state==='undefined'||!state.loaded||document.hidden||covered()){hide();return;}
    const root=document.querySelector('.topbar');if(!root){hide();return;}const frames=nodes(root);
    if(Object.values(frames).some(node=>!node||node.isConnected===false)){hide();return;}
    // Keep the source horizontal scroller when its auxiliary controls are outside the viewport.
    if(Object.values(frames).some(node=>{const r=rect(node);return r.left<0||r.left+r.width>global.innerWidth||r.top<0||r.top+r.height>global.innerHeight;})){hide();return;}
    const before=key();let navigation;
    try{
      if(cache?.key!==before)cache={key:before,promise:Promise.all([lifecycle.header(),frames.shift?lifecycle.shiftHeader():null])
        .then(([header,shift])=>shift?{...header,buttons:[...header.buttons,shift.shift],shift:shift.shift,shiftExpected:shift.expected}:header)};navigation=await cache.promise;
      if(!enabled()||!state.loaded||document.hidden||covered()){hide();return;}
      if(before!==key()||root!==document.querySelector('.topbar')||Object.entries(frames).some(([name,node])=>nodes(root)[name]!==node)){schedule();return;}
      if(!navigation?.expected||!Array.isArray(navigation.buttons)||!navigation.buttons.length)throw Error('Invalid header');
      if(frames.shift&&(typeof navigation.shift?.open!=='boolean'||typeof navigation.shift.label!=='string'||typeof navigation.shift.revision!=='string'||!navigation.shiftExpected))throw Error('Invalid shift header');
    }catch(_error){if(before===key()&&root===document.querySelector('.topbar'))hide();else schedule();return;}
    const groups=Object.fromEntries(Object.entries(frames).map(([name,node])=>[name,rect(node)]));
    if(Object.values(groups).some(r=>![r.left,r.top,r.width,r.height].every(Number.isFinite)||r.width<=0||r.height<=0||r.left<0||r.left+r.width>global.innerWidth||r.top<0||r.top+r.height>global.innerHeight)){hide();return;}
    const packet={action:'headerShow',theme:document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light',viewportWidth:global.innerWidth,viewportHeight:global.innerHeight,groups,navigation};
    const signature=JSON.stringify(packet);
    if(signature===lastSignature&&current?.root===root&&Object.values(frames).every(node=>current.styles.some(([old])=>old===node)))return;
    if(current&&current.root!==root)hide();
    const styles=current?.styles||Object.values(frames).map(node=>[node,node.style.opacity,node.style.pointerEvents]);
    const token='header-'+(++sequence);current={root,token,styles,navigation,generation:lifecycle.generation(),shiftStamp:JSON.stringify(state.shifts)};lastSignature=signature;
    if(bridge.postMessage({...packet,token})===false){hide();return;}
    for(const [node]of styles){node.style.opacity='0';node.style.pointerEvents='none';}
  }
  function schedule(){if(!scheduled){scheduled=true;requestAnimationFrame(update);}}
  global.__nativeWorkspaceHeaderFallback=payload=>{if(payload?.token===current?.token){global.MPosNativeWorkspaceHeaderEnabled=false;hide();}};
  global.__nativeWorkspaceHeaderResult=async payload=>{
    const token=payload?.token,result=payload?.result;
    const valid=current&&token===current.token&&enabled()&&state.loaded&&!document.hidden&&!covered()&&current.root===document.querySelector('.topbar')&&current.root.isConnected!==false&&Object.values(nodes(current.root)).every(node=>node&&node.isConnected!==false&&current.styles.some(([old])=>old===node))
      &&current.generation===lifecycle.generation()&&current.shiftStamp===JSON.stringify(state.shifts)&&JSON.stringify(view())===JSON.stringify(Object.fromEntries(Object.keys(view()).map(name=>[name,current.navigation.expected[name]])));
    let applied=false;cache=null;
    try{
      if(!result?.ok){if(valid)global.flash?.(result?.message||'Не удалось переключить раздел');return;}
      if(!valid)return;
      if(payload.kind==='shift'){
        if(!current.navigation.shift)throw Error('Missing shift header');
        if(result.effect==='openShift'){
          if(current.navigation.shift.open||global.MPosCore.NativeOpenForm.openNative()!==true)throw Error('Shift opening unavailable');
          applied=true;hide();return;
        }
        if(result.effect!=='render'||!current.navigation.shift.open||result.snapshot?.tab!=='shift')throw Error('Invalid shift transition');
      }
      if(!current.navigation.buttons.some(button=>button.tab===result.snapshot?.tab))throw Error('Invalid header transition');
      state.tab=result.snapshot.tab;applied=true;global.render();
    }catch(_error){if(valid)global.flash?.('Не удалось переключить раздел');}
    finally{
      if(result?.ok&&!applied&&result.headerToken)await global.MPosCore.WorkspaceNavigation.execute({version:1,operation:'discardHeaderTab',headerToken:result.headerToken}).catch(()=>{});
      bridge.postMessage({action:'headerResult',requestToken:token});schedule();
    }
  };
  for(const name of ['setTab','onSearch','closeModal']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){cache=null;const result=original.apply(this,args);if(result&&typeof result.then==='function')result.then(()=>{cache=null;schedule();},()=>{cache=null;schedule();});else schedule();return result;};
  }
  function observe(){
    const observer=new MutationObserver(schedule);
    for(const id of ['app','modal-root','product-editor-root','payment-page-root']){const node=document.getElementById(id);if(node)observer.observe(node,{childList:true,subtree:true});}
    observer.observe(document.body,{childList:true});observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
    global.addEventListener('resize',()=>{lastSignature='';schedule();});document.addEventListener('visibilitychange',schedule);document.addEventListener('scroll',schedule,true);schedule();
    global.addEventListener('mpos-native-open-state',schedule);
    global.addEventListener('mpos-native-overlay-state',schedule);
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})(window);
