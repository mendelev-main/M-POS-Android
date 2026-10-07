(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.workspace;
  if(!bridge)return;
  if(global.MPosNativeWorkspaceEnabled===undefined)global.MPosNativeWorkspaceEnabled=true;
  let current=null,sequence=0,scheduled=false,lastSignature='',awaiting=null,capture=null,message='';
  const text=node=>String(node?.textContent||'').replace(/\s+/g,' ').trim();
  const blocked=()=>typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending;
  function restore(){if(current)for(const [node,opacity,pointer]of current.styles){node.style.opacity=opacity;node.style.pointerEvents=pointer}current=null;lastSignature=''}
  function hide(){if(current)bridge.postMessage({action:'hide',token:current.token});restore()}
  const rect=node=>{const r=node.getBoundingClientRect();return{left:r.left,top:r.top,width:r.width,height:r.height}};
  function position(node,index,columns){
    const s=global.getComputedStyle(node),start=value=>/^\d+$/.test(value)?Number(value)-1:null,span=value=>/^span \d+$/.test(value)?Number(value.slice(5)):1;
    const column=start(s.gridColumnStart),row=start(s.gridRowStart);
    return{column:column??index%columns,row:row??Math.floor(index/columns),columnSpan:span(s.gridColumnEnd),rowSpan:span(s.gridRowEnd)};
  }
  function build(root,folder){
    const actions=[],bind=(node,kind='button',extra={})=>{const key=String(actions.length);actions.push({node,kind,...extra});return key};
    const button=node=>({key:bind(node),label:node.getAttribute('aria-label')||text(node),primary:node.classList.contains('btn-primary'),disabled:node.disabled});
    const grid=folder?folder.querySelector('.pos-folder-grid'):root.querySelector('.product-grid');
    const columns=Math.max(1,global.getComputedStyle(grid).gridTemplateColumns.split(' ').filter(Boolean).length||4);
    const tiles=[...grid.querySelectorAll('.layout-tile')].map((node,i)=>{
      const card=node.querySelector('.pcard');
      return{key:bind(card,'tile'),name:text(card.querySelector('.pcard-name')),price:text(card.querySelector('.pcard-price')),stock:text(card.querySelector('.pcard-stock')),symbol:text(card.querySelector('.tile-symbol')),type:node.dataset.tileType||'product',disabled:card.classList.contains('disabled'),color:card.style.getPropertyValue('--category-color'),...position(node,i,columns)};
    });
    const panel=root.querySelector('.cart-panel');
    const lines=[...panel.querySelectorAll('.cart-row')].map(node=>({key:bind(node,'cart'),removeKey:bind(node,'remove',{id:node.dataset.cartId}),name:text(node.querySelector('.cart-row-name')),amount:text(node.querySelector('.cart-row-linetotal')),details:[...node.querySelectorAll('.cart-row-sub')].map(text).join('\n')}));
    return{actions,model:{blocked:blocked(),columns,tiles,title:folder?text(folder.querySelector('h2')):text(root.querySelector('.zone-title-btn')),context:String(global.state.posPath||'')+'|'+String(global._posFolderModal?.id||global.state.posFolder||''),folder:!!folder,closeKey:folder?bind(folder.querySelector('header button')):null,
      toolbar:folder?[]:[...root.querySelectorAll('.pos-left .pos-toolbar button,.no-shift-banner button')].map(button),notice:folder?'':text(root.querySelector('.no-shift-banner span')),
      empty:text(grid.querySelector('.empty-hint,.pos-folder-empty'))||'В папке пока нет товаров.',cartTitle:text(panel.querySelector('.cart-order-title')),metadata:text(panel.querySelector('.order-meta')),lines,
      cartHeaderButtons:[...panel.querySelectorAll('.cart-head button,.order-meta button')].map(button),cartButtons:[...panel.querySelectorAll('.cart-foot button')].map(button),totals:[...panel.querySelectorAll('.total-row')].map(node=>({label:text(node.querySelector('.label')),value:text(node.querySelector('.value'))})),cartEmpty:text(panel.querySelector('.cart-empty'))}};
  }
  function update(){
    scheduled=false;
    if(!global.MPosNativeWorkspaceEnabled||!global.state?.loaded||global.state.tab!=='pos'||global.state.editMode||global.state.paymentPage||document.hidden){hide();return}
    if(document.getElementById('printer-page')||['warehouse-root','receiving-page-root'].some(id=>document.getElementById(id)?.children.length)){hide();return}
    const root=document.getElementById('screen-pos'),modal=document.querySelector('#modal-root .modal'),folder=modal?.classList.contains('pos-folder-modal')?modal:null;
    if(!root||!root.classList.contains('active')||modal&&!folder){hide();return}
    let built;try{built=build(root,folder)}catch(_){global.MPosNativeWorkspaceEnabled=false;hide();return}
    const payload={action:'show',theme:document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light',viewportWidth:global.innerWidth,viewportHeight:global.innerHeight,rect:rect(root),model:built.model};
    const signature=JSON.stringify(payload);
    if(current?.root===root&&current?.folder===folder&&signature===lastSignature&&current.actions.length===built.actions.length&&current.actions.every((entry,i)=>entry.node===built.actions[i].node&&entry.kind===built.actions[i].kind&&entry.id===built.actions[i].id))return;
    const old=current,same=old?.root===root&&old?.folder===folder;const previousStyles=same?old.styles:null;if(old&&!same)restore();
    const styles=previousStyles||[root,...(folder?[folder.closest('.modal-overlay')]:[])].map(node=>[node,node.style.opacity,node.style.pointerEvents]);
    const token='workspace-'+(++sequence);current={root,folder,token,actions:built.actions,styles};payload.token=token;lastSignature=signature;
    if(bridge.postMessage(payload)===false){restore();return}
    for(const [node]of styles){node.style.opacity='0';node.style.pointerEvents='none'}
  }
  function schedule(){if(!scheduled){scheduled=true;requestAnimationFrame(update)}}
  global.__nativeWorkspaceAction=async payload=>{
    if(!current||payload?.token!==current.token)return;
    if(payload.action==='fallback'){global.MPosNativeWorkspaceEnabled=false;hide();return}
    if(payload.action!=='click')return;
    if(awaiting)return;
    if(!global.MPosNativeWorkspaceEnabled||!global.state.loaded||global.state.tab!=='pos'||global.state.editMode||global.state.paymentPage||document.hidden||blocked()){bridge.postMessage({action:'result',token:current.token,blocked:blocked(),message:blocked()?'Перезапустите M POS для восстановления заказа':''});return;}
    const modal=document.querySelector('#modal-root .modal');if(modal&&modal!==current.folder)return;
    const entry=current.actions[Number(payload.key)];if(!/^\d+$/.test(String(payload.key))||!entry||entry.node?.isConnected===false||entry.node?.disabled||entry.node?.classList.contains('disabled'))return;
    const token=current.token;awaiting=token;message='';const promises=[];capture=promises;
    try{
      let result;
      if(entry.kind==='tile')result=global.handlePosGridClick({target:entry.node,preventDefault(){}});
      else if(entry.kind==='cart')result=global.handleCartRowClick({target:entry.node,preventDefault(){}},entry.node.dataset.cartId);
      else if(entry.kind==='remove'){
        if(!global.state.cart.some(i=>String(global.cartItemKey(i))===entry.id))return;
        result=global.removeFromCart(entry.id);
      }else result=entry.node.click();
      capture=null;await Promise.resolve(result);for(const promise of promises)await promise;
    }catch(_){message='Не удалось выполнить действие заказа';global.flash?.(message)}
    finally{capture=null;awaiting=null;bridge.postMessage({action:'result',token:current?.token||token,blocked:blocked(),message});schedule()}
  };
  const originalFlash=global.flash;if(typeof originalFlash==='function')global.flash=function(value,...args){if(awaiting)message=String(value??'');return originalFlash.call(this,value,...args)};
  for(const name of ['addToCart','addConfiguredCartItem','removeFromCart','parkOrder','markCurrentWebOrderReady','openPaymentModal','openPosCategory','closePosCategory','openPosFolder','toggleEditMode']){
    const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){const result=original.apply(this,args);if(capture&&result&&typeof result.then==='function')capture.push(result);return result};
  }
  function observe(){
    const observer=new MutationObserver(schedule);
    for(const id of ['app','modal-root']){const node=document.getElementById(id);if(node)observer.observe(node,{childList:true,subtree:true,characterData:true})}
    observer.observe(document.body,{childList:true});
    observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
    global.addEventListener('resize',()=>{lastSignature='';schedule()});document.addEventListener('visibilitychange',schedule);schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})(window);
