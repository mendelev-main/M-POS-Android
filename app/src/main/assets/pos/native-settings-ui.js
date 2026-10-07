/* Native presentation of reviewed settings. Mounted DOM handlers retain business/auth ownership. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.settingsScreen;
  if(!bridge)return;
  if(global.MPosNativeSettingsUiEnabled===undefined)global.MPosNativeSettingsUiEnabled=true;
  let sequence=0,current=null,queued=false,scope=0,submission=null,dirty=true,activeProductJob=null,paymentOutstanding=0,paymentMessage='',paymentCart=null,surfaceObserver=null;
  let nodeSequence=0;const nodeKeys=new WeakMap();
  const nodeKey=node=>{if(!nodeKeys.has(node))nodeKeys.set(node,String(++nodeSequence));return nodeKeys.get(node)};
  const productRoot=()=>document.getElementById('product-editor-root')?.querySelector('.product-editor');
  const paymentEnabled=()=>global.MPosNativePaymentUiEnabled!==false;
  if(global.MPosNativePaymentUiEnabled===undefined)global.MPosNativePaymentUiEnabled=true;
  const paymentRoot=()=>document.getElementById('payment-page-root');
  const paymentSurface=root=>root?.id==='payment-page-root'||root?.dataset?.mposPaymentForm==='true';
  const pending=root=>paymentSurface(root)?paymentOutstanding>0||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||!!global.state?.busy:global._pmSaving===true;
  const productEnabled=()=>global.MPosNativeProductEditorEnabled!==false;
  if(global.MPosNativeProductEditorEnabled===undefined)global.MPosNativeProductEditorEnabled=true;
  const text=node=>String(node?.getAttribute?.('title')||node?.textContent||'').replace(/\s+/g,' ').trim().replace(/iPad/g,'Android').replace(/Safari/gi,'браузере');
  const enabled=()=>global.MPosNativeSettingsUiEnabled!==false;
  const recoveryPending=()=>global.MPosCore?.PlatformSettings?.blocked===true||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending);
  const theme=()=>document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light';
  const post=value=>bridge.postMessage(value);
  const modal=()=>document.querySelector('#modal-root .modal');
  const attached=node=>node?.isConnected!==false;
  function visible(node,root){for(let p=node;p&&p!==root;p=p.parentElement)if(p.hidden||p.style?.display==='none')return false;return node.type!=='hidden'}
  function restore(){surfaceObserver?.disconnect();surfaceObserver=null;if(current?.focus)for(const [node,focus]of current.focus)node.focus=focus;if(current?.root){current.root.style.opacity=current.opacity;current.root.style.pointerEvents=current.pointerEvents}current=null}
  function hide(){if(current)post({action:current.form?'formHide':'hide',token:current.token});restore()}
  function field(node,root,index){
    const parent=node.closest('.field'),row=node.closest('.setting-row'),label=node.closest('label');
    const name=text(parent?.querySelector('label')||row?.querySelector('.setting-title'))||label?.getAttribute('aria-label')||text(label)||node.getAttribute('aria-label')||node.id||'Поле';
    return{key:String(index),label:name,type:node.tagName==='SELECT'?'select':node.tagName==='TEXTAREA'?'multiline':node.type==='checkbox'?'checkbox':node.type==='password'?'password':node.type==='number'?'number':node.type==='tel'?'phone':'text',value:node.type==='checkbox'?!!node.checked:String(node.value??''),hint:node.placeholder||'',visible:visible(node,root),options:node.options?[...node.options].map(o=>({value:o.value,label:text(o)})):[],readOnly:node.readOnly||node.disabled,maxLength:Number(node.getAttribute('maxlength'))>0?Number(node.getAttribute('maxlength')):0};
  }
  function model(root,form,product=false){
    const actions=[],fields=[],items=[];let cancelButton=null;
    function walk(node,out){
      if(!node||node.nodeType!==1)return;
      if(node.matches('script,style,svg,input[type="hidden"],input[type="file"]'))return;
      if(product&&!visible(node,root))return;
      if(node.matches('.network-device-key')){out.push({kind:'text',text:text(node)});return}
      if(node.matches('input,select,textarea')){const i=fields.length;fields.push(node);out.push({kind:'field',...field(node,root,i),key:product?nodeKey(node):String(i),live:product});return}
      if(product&&node.matches('.pe-summary-row,.pe-price-block,.receipt-line,.receipt-total,.split-summary')){out.push({kind:'metric',label:text(node.children[0]),value:text(node.children[1]),primary:node.matches('.pe-price-block,.receipt-total,.split-summary-remaining')});return}
      if(product&&node.matches('#pf-image-preview')){const img=node.querySelector('img');out.push({kind:'image',source:global._pmLocalImageId&&!global._pmRemoveImage?'mpos-image://'+global._pmLocalImageId:img?.getAttribute('src')||'',label:text(node)||'Фото товара'});return}
      if(product&&node.matches('.split-count')){out.push({kind:'heading',text:'Количество платежей: '+text(node.querySelector('span'))});for(const child of node.children)if(child.tagName==='BUTTON')walk(child,out);return}
      if(product&&node.matches('.payment-change')){out.push({kind:'metric',label:text(node.closest('.field')?.querySelector('label'))||'Сдача',value:text(node)});return}
      if(product&&node.matches('.payment-total-big')){out.push({kind:'metric',label:'К оплате',value:text(node),primary:true});return}
      if(node.tagName==='BUTTON'||(product&&node.getAttribute('role')==='button')){
        if(paymentSurface(root)&&global.state?.paymentPage==='receipt'&&text(node)==='Готово')cancelButton=node;
        if(form&&(/^(Отмена|Закрыть|← Назад|Понятно)$/.test(text(node))||(paymentSurface(root)&&text(node)==='Вернуться'))){cancelButton=node;return}
        const i=actions.length;actions.push(node);
        out.push({kind:'button',key:product?nodeKey(node):String(i),label:(node.getAttribute('aria-label')||(node.matches('.payment-amount-display')?'Сумма · '+text(node):node.matches('.split-amount-display')?'Сумма части · '+text(node):node.parentElement?.matches('.split-count')?(text(node)==='+'?'Добавить часть оплаты':'Убрать часть оплаты'):text(node))||'Открыть')+(node.getAttribute('role')==='switch'?(node.getAttribute('aria-checked')==='true'?' · Включено':' · Выключено'):''),primary:node.classList.contains('btn-primary')||node.classList.contains('btn-cash')||(node.classList.contains('btn-card')&&!!node.closest('.modal')),selected:product&&(node.classList.contains('selected')||node.getAttribute('aria-pressed')==='true'),danger:node.classList.contains('danger')||/Удалить/.test(text(node)),disabled:node.disabled||node.getAttribute('aria-disabled')==='true',visible:visible(node,root)});return;
      }
      if(node.matches('.settings-card,.employee-settings-row,.list-row,.pe-card,.component-card,.modifier-option-card,.pe-summary,.payment-receipt,.payment-box,.split-payment')){const children=[];for(const child of node.children)walk(child,children);out.push({kind:'card',items:children});return}
      if(node.matches('.content-title,.settings-section-title,.settings-card-title,.network-card-title,.modal-title,h1,h2,h3,.component-builder-title,.payment-page-title,.payment-section-title,.payment-split-title,.split-payment-label,.receipt-payment-heading')){out.push({kind:'heading',text:text(node)});return}
      if(node.matches('.setting-sub,.settings-note,.list-row-name,.list-row-sub,.admin-badge,.center-note,.network-card-subtitle,.network-status,.settings-version,p,.pe-empty,.pe-note,.pe-badge,.pe-eyebrow,.pe-nav-caption,.component-card-name,.component-card-meta,.component-cost,.component-total,.component-empty,.component-builder-note,.modifier-input-suffix,.pe-summary-hint,.pe-summary-row,.pe-price-block,.pe-usage-facts,.product-image-empty,.modal-sub,.payment-order-label,.payment-order-meta,.payment-change,.split-payment-status,.payment-part-caption,.payment-part-amount,.payment-card-instruction,.payment-card-amount,.payment-offline-note,.receipt-line-detail,.receipt-order-label,.receipt-order-meta,.receipt-customer')){out.push({kind:'text',text:text(node)});return}
      // Labels are represented by their field descriptor, so do not duplicate field text.
      if(node.tagName==='LABEL'&&!node.querySelector('input,select,textarea'))return;
      for(const child of node.children)walk(child,out);
    }
    for(const child of root.children)walk(child,items);
    return{items,actions,fields,cancelButton};
  }
  function show(root,form,product=false){
    if(!root||!enabled())return;
    if(current?.root===root&&product){if(current.busy)return;if(current.theme!==theme()){hide();show(root,form,product);return}const data=model(root,form,true),signature=JSON.stringify([data.items,theme(),product&&pending(root)]);if(signature!==current.signature){for(const [node,focus]of current.focus)node.focus=focus;current.focus=data.fields.map(node=>[node,node.focus]);Object.assign(current,data,{signature});for(const [node]of current.focus)node.focus=()=>{};post({action:'formPatch',token:current.token,items:data.items,pending:pending(root),blocked:recoveryPending()})}return}
    if(current?.root===root){if(form)post({action:'formUpdate',token:current.token,fields:current.fields.map((f,i)=>field(f,root,i))});return}
    hide();const data=model(root,form,product),token='settings-ui-'+(++sequence);
    current={root,form,product,payment:paymentSurface(root),token,theme:theme(),signature:JSON.stringify([data.items,theme(),product&&pending(root)]),...data,focus:form?data.fields.map(node=>[node,node.focus]):[],opacity:root.style.opacity,pointerEvents:root.style.pointerEvents};
    const r=root.getBoundingClientRect();
    const payload={action:form?'formShow':'show',token,theme:theme(),expanded:product&&(root.classList.contains('product-editor')||root.id==='payment-page-root'),deferCancel:paymentSurface(root),hideCancel:paymentSurface(root)&&global.state?.paymentPage==='receipt'&&!!data.cancelButton,blocked:product&&recoveryPending(),pending:product&&pending(root),title:text(root.querySelector('.modal-title,.content-title,#pe-title'))||'Настройки',items:data.items,cancelLabel:text(data.cancelButton)||(data.fields.length?'Отмена':'Закрыть'),rect:{left:r.left,top:r.top,width:r.width,height:r.height},viewportWidth:global.innerWidth,viewportHeight:global.innerHeight};
    if(post(payload)===false){restore();return}
    if(root.id==='payment-page-root'){surfaceObserver=new MutationObserver(schedule);surfaceObserver.observe(root,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['hidden','disabled','aria-disabled']})}
    root.style.opacity='0';root.style.pointerEvents='none';
    if(form){document.activeElement?.blur?.();for(const [node]of current.focus)node.focus=()=>{}}
    dirty=false;
  }
  function update(){
    queued=false;
    if(!enabled()||!global.state?.loaded||document.hidden){hide();return}
    const dialog=modal();
    if(dialog){if(dialog.dataset.mposPaymentForm==='true'&&paymentEnabled())show(dialog,true,true);else if(dialog.dataset.mposProductForm==='true'&&productEnabled())show(dialog,true,true);else if(dialog.dataset.mposSettingsForm==='true')show(dialog,true);else hide();return}
    const payment=paymentRoot();if(payment){if(paymentEnabled())show(payment,true,true);else hide();return}
    const editor=productRoot();if(editor){if(productEnabled())show(editor,true,true);else hide();return}
    const page=document.getElementById('printer-page');
    if(page){show(page,true);return}
    if(['warehouse-root','receiving-page-root'].some(id=>document.getElementById(id)?.children.length)){hide();return}
    if(!['settings','network'].includes(global.state.tab)||global.state.loyaltyAdminScreen){hide();return}
    const root=document.querySelector('[data-mpos-settings-ui="'+global.state.tab+'"].active');if(!root){hide();return}
    if(current?.root===root&&!current.form){if(dirty&&!current.busy){hide();show(root,false);return}const r=root.getBoundingClientRect(),signature=JSON.stringify([r.left,r.top,r.width,r.height,theme()]);if(signature!==current.geometry){hide();show(root,false);if(current)current.geometry=signature}return}
    show(root,false);
  }
  function schedule(){if(!queued){queued=true;requestAnimationFrame(update)}}
  for(const [name,tab]of [['renderSettingsScreen','settings'],['renderNetworkScreen','network']]){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){return original.apply(this,args).replace('<div class="screen content-screen','<div data-mpos-settings-ui="'+tab+'" class="screen content-screen')};
  }
  const showModal=global.showModal;
  if(typeof showModal==='function')global.showModal=function(...args){const value=showModal.apply(this,args);{const node=modal();if(node&&scope===0&&global.state?.paymentPage&&paymentEnabled())node.dataset.mposPaymentForm='true';if(node&&scope===0&&productRoot()&&productEnabled())node.dataset.mposProductForm='true';if(node&&(scope||node.querySelector('#backup-import-password,#employee-delete-password,#ef-name')))node.dataset.mposSettingsForm='true'}schedule();return value};
  for(const name of ['openEmployeeModal','deleteEmployee','showEmployeeAdminInfo','showAdminOnlyInfo','openCompanyDetailsModal','openCompanyEditModal','openDeliveryRateModal','openDiscountModal','openBackendSettings','openTelegramSettings','openNotificationSettings','openBackupManager','prepareBackupImport','printerInfo']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){scope++;try{return original.apply(this,args)}finally{scope--;schedule()}};
  }
  for(const name of ['saveEmployee','confirmDeleteEmployee','savePrinterFromPage','testPrinterFromPage','deletePrinter','saveNotificationSettings','saveTelegramSettings','testTelegramConnection','testBackendConnection','confirmBackupImport','exportBackup','importBackup','syncMenuToBackend','testWebOrder','saveProductEditor','saveProduct','deleteProduct','confirmDelete','chooseModifierProduct']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){const result=original.apply(this,args);if(submission&&result&&typeof result.then==='function')submission.promises.push(result);return result};
  }
  for(const name of ['openProductModal','finishProductEditor','renderProductModal','renderTypeFields','refreshModifierEditor','renderComponentOptions','renderModifierProductPickerList','handleNativeProductImage','removeProductImage','updateProductEditorSummary']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){const result=original.apply(this,args);schedule();return result}}
  for(const name of ['confirmPaymentScreen','finalizePayment','completeSplitPayment','paySplitPart','openSplitPayment','adjustSplitCount','beginPaymentWithLoyaltyGuard','continuePaymentWithoutLoyalty']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){
      if(!paymentOutstanding){paymentMessage='';paymentCart=global.state?.cart}
      const result=original.apply(this,args);schedule();
      if(!result||typeof result.then!=='function')return result;
      paymentOutstanding++;
      const watched=Promise.resolve(result).finally(()=>{paymentOutstanding--;schedule();if(!paymentOutstanding&&current?.payment&&attached(current.root)&&paymentCart===global.state?.cart)post({action:'formResult',token:current.token,ok:false,error:!!paymentMessage,message:paymentMessage,blocked:recoveryPending()})});
      if(submission)submission.promises.push(watched);return watched;
    };
  }
  for(const name of ['renderPaymentScreen','renderSplitPayment','renderPaymentAmount','syncSplitPaymentUI','showPaymentReceipt','finishPaymentFlow','closePaymentPage','returnFromSplitPayment','setSplitMethod','setPaymentCash']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){try{return original.apply(this,args)}finally{schedule()}}}
  const close=global.closeModal;
  if(typeof close==='function')global.closeModal=function(...args){const result=close.apply(this,args);if(current?.form&&!attached(current.root))hide();schedule();return result};
  const flash=global.flash;
  if(typeof flash==='function')global.flash=function(message,...args){if(submission)submission.message=String(message??'');if(paymentOutstanding)paymentMessage=String(message??'');if(activeProductJob)activeProductJob.message=String(message??'');if(current?.busy&&current.job)current.job.message=String(message??'');return flash.call(this,message,...args)};
  function apply(values,change){
    const events=[];
    if(!values||!current)return;
    for(const [key,value]of Object.entries(values)){
      if(!/^\d+$/.test(key))continue;const node=current.product?current.fields.find(n=>nodeKey(n)===key):current.fields[Number(key)];if(!node||!attached(node)||node.disabled||node.readOnly)continue;
      const prior=node.type==='checkbox'?node.checked:node.value;
      if(node.type==='checkbox'){if(typeof value!=='boolean')continue;node.checked=value}
      else{if(typeof value!=='string')continue;node.value=node.type==='number'?value.replace(',','.'):value}
      if(change&&prior!==(node.type==='checkbox'?node.checked:node.value))events.push(node);
    }
    // Assign the complete draft first: unit/checkbox handlers may replace sibling fields.
    for(const node of events){if(!attached(node))continue;if(current?.product&&node.type!=='checkbox'&&node.tagName!=='SELECT')node.dispatchEvent(new Event('input',{bubbles:true}));if(attached(node))node.dispatchEvent(new Event('change',{bubbles:true}));}
  }
  global.__nativeSettingsAction=async payload=>{
    if(!enabled()||!current||payload?.token!==current.token||!attached(current.root)||current.busy)return;
    if(current.product&&pending(current.root))return;
    if(!current.form&&!['settings','network'].includes(global.state?.tab))return;
    if(payload.action==='cancel'&&current.payment){const snapshot=current,feedback={message:'',promises:[]};submission=feedback;try{apply(payload.fields,true);const button=snapshot.cancelButton;if(button&&attached(button)&&!button.disabled)button.click();else global.closeModal()}finally{submission=null;if(current===snapshot&&attached(snapshot.root))post({action:'formResult',token:snapshot.token,ok:false,error:!!feedback.message,message:feedback.message,blocked:recoveryPending()});schedule()}return}
    if(payload.action==='cancel'){if(!current.form)return;apply(payload.fields,current.product);const button=current.cancelButton;if(button&&attached(button)&&!button.disabled)button.click();else{const page=document.getElementById('printer-page');if(page)global.closePrinterPage();else global.closeModal()}hide();schedule();return}
    if(payload.action==='change'){if(current.product){const feedback={message:'',promises:[]};try{submission=feedback;apply(payload.fields,true);post({action:'formResult',token:current.token,ok:false,error:!!feedback.message,message:feedback.message,blocked:recoveryPending()})}catch(error){post({action:'formResult',token:current.token,ok:false,error:true,message:error?.message||'Не удалось изменить поле',blocked:recoveryPending()})}finally{submission=null}if(current?.product)show(current.root,true,true);schedule();return}apply(payload.fields,true);if(current?.form)post({action:'formUpdate',token:current.token,fields:current.fields.map((f,i)=>field(f,current.root,i))});schedule();return}
    if(payload.action!=='click'||!/^\d+$/.test(String(payload.key)))return;
    const button=current.product?current.actions.find(n=>nodeKey(n)===String(payload.key)):current.actions[Number(payload.key)];if(!button||!attached(button)||button.disabled||button.getAttribute('aria-disabled')==='true'||!visible(button,current.root))return;
    const snapshot=current,job={promises:[],message:'',session:global._pmSession,cart:global.state?.cart};if(snapshot.product)activeProductJob=job;snapshot.busy=true;snapshot.job=job;
    try{
      submission=job;apply(payload.fields,snapshot.product);
      // Input handlers may replace the list before this gesture reaches its button.
      if(!attached(button)||button.disabled||button.getAttribute('aria-disabled')==='true'||!visible(button,snapshot.root)){job.message='Список обновился. Выберите действие ещё раз.';if(current===snapshot)post({action:'formResult',token:snapshot.token,ok:false,error:false,message:job.message,blocked:recoveryPending()});return}
      // Only a button from this mounted reviewed form can act; no onclick source is evaluated.
      button.click();submission=null;
      for(let i=0;i<job.promises.length;i++)await job.promises[i];
      if(current===snapshot&&attached(snapshot.root))post({action:'formResult',token:snapshot.token,ok:false,error:/^(Введите|Проверьте|Не удалось|Ошибка|Неверн|Требуется|Перезапустите|Выберите)/i.test(job.message),message:job.message,blocked:recoveryPending()});
    }catch(error){if(current===snapshot)post({action:'formResult',token:snapshot.token,ok:false,error:true,message:error?.message||'Не удалось выполнить операцию',blocked:recoveryPending()})}
    finally{submission=null;snapshot.busy=false;snapshot.job=null;if(activeProductJob===job)activeProductJob=null;if(snapshot.product&&current!==snapshot&&current?.product&&attached(current.root)&&(snapshot.payment?job.cart===global.state?.cart:job.session===global._pmSession))post({action:'formResult',token:current.token,ok:false,error:/^(Введите|Проверьте|Не удалось|Ошибка|Неверн|Требуется|Перезапустите|Выберите)/i.test(job.message),message:job.message,blocked:recoveryPending()});schedule()}
  };
  function observe(){
    const observer=new MutationObserver(()=>{dirty=true;schedule()});
    for(const id of ['app','modal-root','product-editor-root']){const node=document.getElementById(id);if(node)observer.observe(node,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['hidden','disabled','aria-checked','aria-pressed']})}
    observer.observe(document.body,{childList:true});observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
    global.addEventListener('resize',()=>{if(current&&!current.form)hide();schedule()});document.addEventListener('visibilitychange',schedule);schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})(window);
