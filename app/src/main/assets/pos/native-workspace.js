(function(global){
  'use strict';
  if(global.MPosNativeWorkspaceReadModelsEnabled===false)return;
  const core=global.MPosCore,bridge=global.webkit?.messageHandlers?.workspace;
  if(!core?.WorkspaceNavigationLifecycle||!bridge)return;
  if(global.MPosNativeWorkspaceEnabled===undefined)global.MPosNativeWorkspaceEnabled=true;
  let current=null,scheduled=false,sequence=0,generation=0,awaiting=false,capture=null,cartForm=null,addForm=null;
  let nativeWrites=0,uncertainWrite=false,deferredSessionSave=false;
  const restartMessage='Не удалось подтвердить сохранение. Перезапустите приложение перед повтором.';
  const sourceSessionSave=global.saveCurrentOrderSession,sourceCartOperations=core.CartOperations;
  core.CartOperations=Object.freeze({hasPending:()=>nativeWrites>0||uncertainWrite||!!sourceCartOperations?.hasPending?.()});
  if(typeof sourceSessionSave==='function')global.saveCurrentOrderSession=function(...args){
    if(nativeWrites||uncertainWrite){deferredSessionSave=true;return;}
    return sourceSessionSave.apply(this,args);
  };
  async function cartMutation(request){
    const cart=state.cart,items=JSON.stringify(cart),runtime=core.WorkspaceNavigationLifecycle.generation();
    nativeWrites++;let known=false;
    try{
      const result=await request();
      if(!result?.ok||!result.authoritative||typeof result.allowed!=='boolean')throw Error('Invalid native cart write');
      known=true;
      if(result.allowed){
        if(!Array.isArray(result.items))throw Error('Invalid persisted items');
        if(runtime!==core.WorkspaceNavigationLifecycle.generation()){deferredSessionSave=false;}
        else if(cart===state.cart&&items===JSON.stringify(state.cart)&&state.loaded){
          state.cart=result.items;result.projected=true;
          if(result.resetState){
            const keys=['orderLabel','orderType','deliveryFee','deliveryTariffSelected','customer','loyaltyPrograms','loyaltyRedemptions','_splitPayments','_splitCount','_splitPaymentTotalCents','loyaltyCustomerId','loyaltyLoadingCustomerId','loyaltyLoadError','orderComment','currentOrderSource','currentWebOrderId','currentWebOrderStatus'];
            if(Object.keys(result.resetState).some(key=>!keys.includes(key))||keys.some(key=>!Object.hasOwn(result.resetState,key)))throw Error('Invalid cart reset');
            Object.assign(state,result.resetState);global.__currentOrderKitchenPrinted=false;global.__currentOrderPrintedItems=[];
          }
        }else{uncertainWrite=true;global.flash?.(restartMessage);}
      }
      return result;
    }catch(error){uncertainWrite=true;global.flash?.(restartMessage);throw error;}
    finally{nativeWrites--;if(known&&!uncertainWrite&&nativeWrites===0&&deferredSessionSave){deferredSessionSave=false;sourceSessionSave?.call(global);}schedule();}
  }
  const enabled=()=>global.MPosNativeWorkspaceEnabled&&global.MPosNativeWorkspaceReadModelsEnabled!==false;
  const view=()=>({tab:state.tab,search:String(state.search||''),posPath:state.posPath??null,posFolder:state.posFolder||'',editMode:!!state.editMode});
  const blocked=()=>awaiting||!!cartForm||!!addForm||!!core.CartOperations?.hasPending?.()||!!state.busy||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)||!!core.NativeOpenForm?.activeToken?.();
  function order(){return{items:state.cart,discounts:state.discounts||[],loyaltyPrograms:state.loyaltyPrograms||[],loyaltyRedemptions:state.loyaltyRedemptions||{},orderType:state.orderType,deliveryFee:state.deliveryFee,deliveryTariffSelected:state.deliveryTariffSelected,deliveryRates:state.deliveryRates||[],customer:state.customer,orderLabel:state.orderLabel,orderComment:state.orderComment,source:state.currentOrderSource,webOrderId:state.currentWebOrderId,webOrderStatus:state.currentWebOrderStatus,currency:state.currency,demandOverload:!!state.demandOverload};}
  const stamp=()=>JSON.stringify([core.WorkspaceNavigationLifecycle.generation(),view(),order(),state.products,state.shifts,state.parked,global._posFolderModal,core.OverlayLifecycle?.snapshot().stamp]);
  const context=()=>JSON.stringify([state.posPath,state.posFolder,global._posFolderModal]);
  function cancelCartForm(){if(!cartForm)return;bridge.postMessage({action:'cartItemHide',token:cartForm.token});cartForm=null;core.OverlayLifecycle?.changed();if(current)bridge.postMessage({action:'result',token:current.token,blocked:blocked()});schedule();}
  function cancelAddForm(){if(!addForm)return;bridge.postMessage({action:'cartAddHide',token:addForm.token});addForm=null;core.OverlayLifecycle?.changed();if(current)bridge.postMessage({action:'result',token:current.token,blocked:blocked()});schedule();}
  function hide(){cancelCartForm();cancelAddForm();generation++;if(current){bridge.postMessage({action:'hide',token:current.token});for(const [node,opacity,pointer]of current.styles){node.style.opacity=opacity;node.style.pointerEvents=pointer;}current=null;}}
  function schedule(){if(!scheduled){scheduled=true;requestAnimationFrame(update);}}
  const rect=node=>{const r=node.getBoundingClientRect();return{left:r.left,top:r.top,width:r.width,height:r.height};};
  function geometry(root,folder){
    const grid=folder?.querySelector('.pos-folder-grid')||root.querySelector('.product-grid'),panel=root.querySelector('.cart-panel');
    if(!grid||!panel)throw Error('Workspace bounds unavailable');
    const gridStyle=global.getComputedStyle(grid),screenStyle=global.getComputedStyle(root),number=(value,fallback)=>Number.isFinite(parseFloat(value))?parseFloat(value):fallback;
    return{columns:Math.max(1,gridStyle.gridTemplateColumns.split(' ').filter(Boolean).length),presentation:{cartWidth:panel.getBoundingClientRect().width,padding:number(screenStyle.paddingLeft,16),gap:number(screenStyle.columnGap,16),vertical:screenStyle.flexDirection==='column',gridGap:number(gridStyle.columnGap,12),rowHeight:number(gridStyle.gridTemplateRows?.split(' ')[0],155)}};
  }
  function covered(){return document.getElementById('printer-page')||['warehouse-root','receiving-page-root'].some(id=>document.getElementById(id)?.children.length);}
  function normal(){return enabled()&&state.loaded&&state.tab==='pos'&&!state.editMode&&!state.paymentPage&&!document.hidden&&!covered();}
  async function update(){
    scheduled=false;if(!normal()){hide();return;}if(cartForm){if(cartForm.stamp!==stamp())cancelCartForm();else return;}
    if(addForm){if(addForm.stamp!==stamp())cancelAddForm();else return;}
    const root=document.getElementById('screen-pos'),modal=document.querySelector('#modal-root .modal'),folder=modal?.classList.contains('pos-folder-modal')?modal:null;
    if(!root||!root.classList.contains('active')||modal&&!folder){hide();return;}
    const before=stamp();if(current?.root===root&&current.folder===folder&&current.stamp===before)return;
    const request=++generation;
    try{
      const layout=geometry(root,folder);await core.WorkspaceNavigationLifecycle.toolbar();
      const liveScope=current?.root===root&&current.folder===folder&&current.context===context()?current.scope:undefined;
      const result=await core.WorkspaceNavigation.execute({version:1,operation:'workspaceView',expected:view(),folderModal:global._posFolderModal??null,columns:layout.columns,order:order(),blocked:blocked(),...(liveScope?{liveScope}:{})});
      if(request!==generation||before!==stamp()||root!==document.getElementById('screen-pos')||modal!==document.querySelector('#modal-root .modal')||!normal())return;
      const model=result?.model;
      if(result?.ok!==true||result.authoritative!==true||!Array.isArray(model?.tiles)||!Array.isArray(model.lines)||!model.actions||!model.navigation?.expected)throw Error('Invalid native workspace model');
      const old=current,same=old?.root===root&&old.folder===folder;
      if(old&&!same)hide();
      const styles=same?old.styles:[root,...(folder?[folder.closest('.modal-overlay')]:[])].map(node=>[node,node.style.opacity,node.style.pointerEvents]);
      const token='workspace-'+(++sequence);current={root,folder,token,styles,stamp:before,context:context(),scope:liveScope||model.catalogScope,model};
      const payload={action:'show',token,theme:document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light',viewportWidth:global.innerWidth,viewportHeight:global.innerHeight,rect:rect(root),retainedUpdates:global.MPosNativeWorkspaceRetainedViewsEnabled!==false,model:{...model,sourceLayout:global.MPosNativeWorkspaceSourceLayoutEnabled!==false,presentation:layout.presentation}};
      if(bridge.postMessage(payload)===false){hide();return;}
      for(const [node]of styles){node.style.opacity='0';node.style.pointerEvents='none';}
    }catch(_error){if(request===generation)hide();}
  }
  global.__nativeWorkspaceNavigationResult=async payload=>{
    const result=payload?.result,valid=current&&payload.token===current.token&&normal()&&!blocked()&&current.stamp===stamp();let applied=false;
    try{
      if(!result?.ok){if(valid)global.flash?.(result?.message||'Не удалось выполнить переход');return;}
      if(!valid)return;
      if(!result.patch||Object.keys(result.patch).some(k=>!['posPath','posFolder','search','editMode'].includes(k))||!['render','renderFolder','closeModal'].includes(result.effect))throw Error('Invalid navigation');
      if(result.effect==='renderFolder'&&(!result.folderModal||result.folderModal.category!==state.posPath||typeof result.folderModal.id!=='string'))throw Error('Invalid folder');
      Object.assign(state,result.patch);applied=true;
      if(result.effect==='closeModal')global.closeModal();else if(result.effect==='renderFolder'){global._posFolderModal=result.folderModal;global.renderPosFolderModal();}else global.render();
    }catch(_error){if(valid)global.flash?.('Не удалось выполнить переход');}
    finally{if(result?.ok&&!applied&&result.proposalToken)await core.WorkspaceNavigation.execute({version:1,operation:'discardRoute',proposalToken:result.proposalToken}).catch(()=>{});bridge.postMessage({action:'result',token:current?.token||payload.token,blocked:blocked()});schedule();}
  };
  async function openCartLine(id){
    if(global.MPosNativeCartItemFormEnabled===false||typeof core.WorkspaceNavigation.cartItem!=='function')return global.openCartItemModal(id);
    if(global._posFolderModal){global.closeModal();const closed=stamp();await update();if(closed!==stamp()||!normal()||!current)return;}
    const form={token:'cart-item-'+(++sequence),id,expected:view(),pending:false};cartForm=form;core.OverlayLifecycle?.changed();form.stamp=stamp();
    if(current)bridge.postMessage({action:'result',token:current.token,blocked:true});
    try{
      const result=await core.WorkspaceNavigation.cartItem({version:1,operation:'cartItemView',expected:form.expected,id,items:state.cart,discounts:state.discounts||[],currency:state.currency});
      if(cartForm!==form)return;
      if(form.stamp!==stamp()||!normal()){cancelCartForm();return;}
      if(result?.ok!==true||!result.authoritative||!result.model?.sessionRevision)throw Error('Invalid native cart item');
      form.model=result.model;bridge.postMessage({action:'cartItemShow',token:form.token,theme:document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light',model:result.model});
    }catch(_error){if(cartForm===form){cancelCartForm();global.flash?.('Не удалось открыть позицию заказа. Повторите попытку.');}}
  }
  core.CartItemUi=Object.freeze({activeToken:()=>cartForm?.token||null,back(){if(!cartForm?.pending)cancelCartForm();},async action(payload){
    const form=cartForm;if(!form||payload?.token!==form.token||form.pending)return;
    if(payload.action==='cartItemCancel'){cancelCartForm();return;}
    if(payload.action!=='cartItemSave'||!form.model)return;
    if(uncertainWrite){bridge.postMessage({action:'cartItemResult',token:form.token,message:restartMessage});return;}
    if(!normal()||form.stamp!==stamp()){cancelCartForm();return;}
    form.pending=true;let message='';
    try{
      const result=await cartMutation(()=>core.WorkspaceNavigation.cartItem({version:1,operation:'cartItemCommit',expected:form.expected,id:form.id,sessionRevision:form.model.sessionRevision,quantity:payload.quantity,comment:payload.comment,discountId:payload.discountId}));
      if(cartForm!==form){if(result.projected)global.render();return;}
      if(!result?.ok||!result.authoritative||typeof result.allowed!=='boolean')throw Error('Invalid cart save result');
      if(!result.allowed){message=result.message||'Недостаточно остатка';return;}
      if(!result.projected||!normal()){cancelCartForm();return;}
      if(!Array.isArray(result.items))throw Error('Invalid persisted cart');
      state.cart=result.items;cancelCartForm();global.render();
    }catch(_error){message=uncertainWrite?restartMessage:'Не удалось сохранить позицию. Закройте форму и откройте её снова.';}
    finally{form.pending=false;if(cartForm===form)bridge.postMessage({action:'cartItemResult',token:form.token,message});schedule();}
  }});
  function projectAdd(result){
    if(!Array.isArray(result.items)||!result.animation)throw Error('Invalid persisted add');
    state.cart=result.items;global.__cartAnimation=result.animation;cancelAddForm();global.closeModal?.();global.render();
  }
  async function addProduct(id){
    if(global.MPosNativeCartAddEnabled===false||typeof core.WorkspaceNavigation.cartAdd!=='function')return global.addToCart(id);
    const session=global.currentOrderSessionSnapshot();delete session.updatedAt;
    const form={token:'cart-add-'+(++sequence),id,expected:view(),session,pending:false};const before=stamp();
    const result=await core.WorkspaceNavigation.cartAdd({version:1,operation:'cartAddView',expected:form.expected,id,session,currency:state.currency});
    if(before!==stamp()||!normal())return;
    if(result?.supported===false)return global.addToCart(id);
    if(!result?.ok||!result.authoritative||typeof result.allowed!=='boolean')throw Error('Invalid add read');
    if(!result.allowed){global.flash?.(result.message||'Не удалось добавить товар');return;}
    form.model=result.model;
    if(!form.model?.sessionRevision||!form.model.catalogRevision||!Array.isArray(form.model.groups))throw Error('Invalid add model');
    if(form.model.groups.length||form.model.manual){
      if(global._posFolderModal){global.closeModal();const closed=stamp();await update();if(closed!==stamp()||!normal()||!current)return;}
      addForm=form;core.OverlayLifecycle?.changed();form.stamp=stamp();
      bridge.postMessage({action:'cartAddShow',token:form.token,theme:document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light',model:form.model});
    }else{
      const saved=await cartMutation(()=>core.WorkspaceNavigation.cartAdd({version:1,operation:'cartAddCommit',expected:form.expected,id,session,sessionRevision:form.model.sessionRevision,catalogRevision:form.model.catalogRevision,selections:[]}));
      if(saved.allowed&&!saved.projected)return;
      if(!saved?.ok||!saved.authoritative||typeof saved.allowed!=='boolean')throw Error('Invalid configured add');
      if(saved.allowed){if(normal())projectAdd(saved);else global.render();}else global.flash?.(saved.message||'Недостаточно остатка');
    }
  }
  core.CartAddUi=Object.freeze({activeToken:()=>addForm?.token||null,back(){if(!addForm?.pending)cancelAddForm();},async action(payload){
    const form=addForm;if(!form||payload?.token!==form.token||form.pending)return;
    if(payload.action==='cartAddCancel'){cancelAddForm();return;}
    if(payload.action!=='cartAddSave')return;
    if(uncertainWrite){bridge.postMessage({action:'cartAddResult',token:form.token,message:restartMessage});return;}
    if(!normal()||form.stamp!==stamp()){cancelAddForm();return;}
    form.pending=true;let message='';
    try{
      const result=await cartMutation(()=>core.WorkspaceNavigation.cartAdd({version:1,operation:'cartAddCommit',expected:form.expected,id:form.id,session:form.session,sessionRevision:form.model.sessionRevision,catalogRevision:form.model.catalogRevision,selections:payload.selections,...(form.model.manual?{manualInput:payload.manualInput}:{})}));
      if(addForm!==form){if(result.projected)global.render();return;}
      if(!result?.ok||!result.authoritative||typeof result.allowed!=='boolean')throw Error('Invalid configured add');
      if(!result.allowed){message=result.message||'Недостаточно остатка';return;}
      if(!result.projected||!normal()){cancelAddForm();return;}
      projectAdd(result);
    }catch(_error){message=uncertainWrite?restartMessage:'Не удалось добавить товар. Закройте форму и откройте её снова.';}
    finally{form.pending=false;if(addForm===form)bridge.postMessage({action:'cartAddResult',token:form.token,message});schedule();}
  }});
  const close=global.closeModal;if(typeof close==='function')global.closeModal=function(...args){if(cartForm){core.CartItemUi.back();return;}if(addForm){core.CartAddUi.back();return;}return close.apply(this,args);};
  const sourceRemove=global.removeFromCart,sourceQuantity=global.changeQty;
  async function editCart(id,operation,delta){
    if(uncertainWrite){global.flash?.(restartMessage);return;}
    if(global.MPosNativeCartEditEnabled===false||typeof core.WorkspaceNavigation.cartEdit!=='function')return operation==='cartRemoveCommit'?sourceRemove(id):sourceQuantity(id,delta);
    const session=global.currentOrderSessionSnapshot();delete session.updatedAt;
    const result=await cartMutation(()=>core.WorkspaceNavigation.cartEdit({version:1,operation,expected:view(),id,session,...(operation==='cartQuantityCommit'?{delta}:{})}));
    if(result.allowed){if(result.projected)global.render();}else global.flash?.(result.message||'Недостаточно остатка');
  }
  if(typeof sourceQuantity==='function')global.changeQty=function(id,delta){if(core.CartOperations.hasPending()){global.flash?.('Дождитесь завершения изменения корзины');return;}return normal()&&current?editCart(id,'cartQuantityCommit',delta):sourceQuantity.apply(this,arguments);};
  if(typeof sourceRemove==='function')global.removeFromCart=function(id){if(core.CartOperations.hasPending()){global.flash?.('Дождитесь завершения изменения корзины');return;}return normal()&&current?editCart(id,'cartRemoveCommit'):sourceRemove.apply(this,arguments);};
  const commands={addProduct,editCartLine:openCartLine,removeCartLine:id=>editCart(id,'cartRemoveCommit'),parkOrder:()=>global.parkOrder(),readyOrder:()=>global.markCurrentWebOrderReady(),payment:()=>global.openPaymentModal(),customer:()=>global.openOrderCustomer(),orderSettings:()=>global.openOrderSettings(),parked:()=>global.openParkedModal(),demand:()=>global.setDemandOverload(!state.demandOverload),openShift:()=>global.openShiftModal()};
  global.__nativeWorkspaceAction=async payload=>{
    if(!current||payload?.token!==current.token)return;
    if(payload.action==='fallback'){global.MPosNativeWorkspaceEnabled=false;hide();return;}
    if(payload.action!=='click'||awaiting)return;
    const token=current.token,command=current.model.actions[payload.key];
    if(!normal()||blocked()||current.stamp!==stamp()||!command||!Object.hasOwn(commands,command.operation)){bridge.postMessage({action:'result',token,blocked:blocked()});schedule();return;}
    awaiting=true;let message='';const promises=[];capture=promises;
    try{const result=commands[command.operation](command.value);capture=null;await result;for(const promise of promises)await promise;}catch(_error){message=uncertainWrite?restartMessage:'Не удалось выполнить действие заказа';global.flash?.(message);}
    finally{capture=null;awaiting=false;bridge.postMessage({action:'result',token:current?.token||token,blocked:blocked(),message});schedule();}
  };
  core.WorkspaceReadUi=Object.freeze({invalidate:hide,refresh:schedule});
  for(const name of ['addToCart','addConfiguredCartItem','changeQty','removeFromCart','parkOrder','markCurrentWebOrderReady','openPaymentModal','openPosCategory','closePosCategory','openPosFolder','toggleEditMode','setTab','onSearch','closeModal']){
    const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){const result=original.apply(this,args);if(result&&typeof result.then==='function'){if(capture)capture.push(result);result.then(schedule,schedule);}else schedule();return result;};
  }
  function observe(){const observer=new MutationObserver(schedule);for(const id of ['app','modal-root']){const node=document.getElementById(id);if(node)observer.observe(node,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['hidden']});}observer.observe(document.body,{childList:true});observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});global.addEventListener('resize',()=>{if(current)current.stamp='';schedule();});document.addEventListener('visibilitychange',schedule);global.addEventListener('mpos-native-overlay-state',schedule);global.addEventListener('mpos-native-open-state',()=>{if(current)current.stamp='';schedule();});schedule();}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})(window);
