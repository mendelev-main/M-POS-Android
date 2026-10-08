/* Native presentation of reviewed settings. Mounted DOM handlers retain business/auth ownership. */
(function(global){
  'use strict';
  const appState=()=>typeof state!=='undefined'?state:global.state;
  const bridge=global.webkit?.messageHandlers?.settingsScreen;
  if(!bridge)return;
  if(global.MPosNativeSettingsUiEnabled===undefined)global.MPosNativeSettingsUiEnabled=true;
  let sequence=0,current=null,queued=false,scope=0,submission=null,dirty=true,activeProductJob=null,paymentOutstanding=0,paymentMessage='',paymentCart=null,surfaceObserver=null,receiptScope=0,receiptOutstanding=0,domainScope=0,domainOutstanding=0,warehouseScope=0,warehouseOutstanding=0,analyticsScope=0,analyticsDateToken=null,hallScope=0,hallOutstanding=0;
  let nodeSequence=0;const nodeKeys=new WeakMap();
  const nodeKey=node=>{if(!nodeKeys.has(node))nodeKeys.set(node,String(++nodeSequence));return nodeKeys.get(node)};
  const productRoot=()=>document.getElementById('product-editor-root')?.querySelector('.product-editor');
  const paymentEnabled=()=>global.MPosNativePaymentUiEnabled!==false;
  if(global.MPosNativePaymentUiEnabled===undefined)global.MPosNativePaymentUiEnabled=true;
  const paymentRoot=()=>document.getElementById('payment-page-root');
  const paymentSurface=root=>root?.id==='payment-page-root'||root?.dataset?.mposPaymentForm==='true';
  const pending=root=>hallSurface(root)?hallOutstanding>0||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending):analyticsSurface(root)?false:warehouseSurface(root)?warehouseOutstanding>0||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||!!global._receivingDraftOpening||!!global._receivingDraftSaveBusy||!!global._receivingSaving:domainSurface(root)?domainOutstanding>0||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy):receiptSurface(root)?receiptOutstanding>0||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy):paymentSurface(root)?paymentOutstanding>0||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||!!appState()?.busy:global._pmSaving===true;
  const receiptEnabled=()=>global.MPosNativeReceiptUiEnabled!==false;
  if(global.MPosNativeReceiptUiEnabled===undefined)global.MPosNativeReceiptUiEnabled=true;
  const receiptSurface=root=>root?.dataset?.mposReceiptForm==='true'||root?.dataset?.mposSettingsUi==='receipts';
  const domainEnabled=()=>global.MPosNativeCustomerUiEnabled!==false;
  if(global.MPosNativeCustomerUiEnabled===undefined)global.MPosNativeCustomerUiEnabled=true;
  const domainSurface=root=>root?.dataset?.mposCustomerForm==='true'||root?.dataset?.mposSettingsUi==='loyalty';
  const domainWrapped=new WeakSet();
  function installDomainOperations(){
    for(const name of ['confirmParkOrderLabel','parkOrderNow','resumeParked','deleteParked','selectCustomer','removeOrderCustomer','createCustomerFromPos','saveLoyaltyAdjustment','saveLoyaltyProgram','deleteLoyaltyProgram','toggleLoyaltyProgram','openCustomersAdmin','openCustomerAdminCard']){
      const original=global[name];if(typeof original!=='function'||domainWrapped.has(original))continue;
      const wrapped=function(...args){domainScope++;let result;try{result=original.apply(this,args)}finally{domainScope--;schedule()}if(!result||typeof result.then!=='function')return result;domainOutstanding++;const watched=Promise.resolve(result).finally(()=>{domainOutstanding--;schedule()});if(submission)submission.promises.push(watched);return watched};
      domainWrapped.add(wrapped);global[name]=wrapped;
    }
  }
  const warehouseEnabled=()=>global.MPosNativeWarehouseUiEnabled!==false;
  if(global.MPosNativeWarehouseUiEnabled===undefined)global.MPosNativeWarehouseUiEnabled=true;
  const warehouseTabs=['purchaseOrders','receiving','inventory','inventoryWork'];
  const warehouseSurface=root=>root?.dataset?.mposWarehouseForm==='true'||warehouseTabs.includes(root?.dataset?.mposSettingsUi)||!!root?.matches('.warehouse-page,.receiving-page');
  const warehouseWrapped=new WeakSet();
  function installWarehouseOperations(){
    for(const name of ['saveSupplier','confirmDeleteSupplier','finalizePurchaseOrder','deletePurchaseOrderAsAdmin','openReceivingDocument','saveReceivingDraft','applyReceivingDocument','fixInventoryItem','confirmCompleteInventory','copyPurchaseOrder','generateWarehouseReport','exportWarehousePDF','renderWarehousePage']){
      const original=global[name];if(typeof original!=='function'||warehouseWrapped.has(original))continue;
      const wrapped=function(...args){warehouseScope++;let result;try{result=original.apply(this,args)}finally{warehouseScope--;schedule()}if(!result||typeof result.then!=='function')return result;const blocking=name!=='renderWarehousePage';if(blocking)warehouseOutstanding++;const watched=Promise.resolve(result).finally(()=>{if(blocking)warehouseOutstanding--;schedule()});if(submission)submission.promises.push(watched);return watched};
      warehouseWrapped.add(wrapped);global[name]=wrapped;
    }
  }
  const analyticsEnabled=()=>global.MPosNativeAnalyticsUiEnabled!==false;
  if(global.MPosNativeAnalyticsUiEnabled===undefined)global.MPosNativeAnalyticsUiEnabled=true;
  const analyticsSurface=root=>root?.dataset?.mposAnalyticsForm==='true'||root?.dataset?.mposSettingsUi==='analytics'||root?.getAttribute?.('data-mpos-native-analytics')!=null;
  const analyticsUi=global.MPosCore?(global.MPosCore.AnalyticsUi||= {}):{};
  const hallEnabled=()=>global.MPosNativeHallUiEnabled!==false;
  if(global.MPosNativeHallUiEnabled===undefined)global.MPosNativeHallUiEnabled=true;
  const hallSurface=root=>root?.dataset?.mposHallForm==='true'||root?.dataset?.mposSettingsUi==='bookings';
  const hallWrapped=new WeakSet();
  function installHallOperations(){
    for(const name of ['createHallTable','confirmDeleteHallTable','rotateHallTable','saveHallTableEdits','saveNewBooking','saveEditedBooking','cancelBooking','hallPointerEnd']){
      const original=global[name];if(typeof original!=='function'||hallWrapped.has(original))continue;
      const wrapped=function(...args){hallScope++;let result;try{result=original.apply(this,args)}finally{hallScope--;schedule()}if(!result||typeof result.then!=='function')return result;hallOutstanding++;const watched=Promise.resolve(result).finally(()=>{hallOutstanding--;schedule()});if(submission)submission.promises.push(watched);return watched};hallWrapped.add(wrapped);global[name]=wrapped;
    }
  }
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
    const parent=node.closest('.field,.loyalty-client-search,.analytics-filter,.inventory-entry,.booking-field'),row=node.closest('.setting-row'),label=node.closest('label');
    const name=text(parent?.querySelector('label,.inventory-entry-label')||row?.querySelector('.setting-title'))||label?.getAttribute('aria-label')||text(label)||node.getAttribute('aria-label')||node.placeholder||node.id||'Поле';
    return{key:String(index),label:name,type:node.tagName==='SELECT'?'select':node.tagName==='TEXTAREA'?'multiline':node.type==='checkbox'?'checkbox':node.type==='password'?'password':node.type==='number'?'number':node.type==='tel'?'phone':node.type==='date'?'date':node.type==='time'?'time':'text',value:node.type==='checkbox'?!!node.checked:String(node.value??''),hint:node.placeholder||'',visible:visible(node,root),options:node.options?[...node.options].map(o=>({value:o.value,label:text(o)})):[],readOnly:node.readOnly||node.disabled,maxLength:Number(node.getAttribute('maxlength'))>0?Number(node.getAttribute('maxlength')):0};
  }
  function model(root,form,product=false){
    const actions=[],fields=[],items=[];let cancelButton=null;
    function walk(node,out){
      if(!node||node.nodeType!==1)return;
      if((domainSurface(root)||warehouseSurface(root)||analyticsSurface(root)||hallSurface(root))&&node.getAttribute('aria-hidden')==='true')return;
      if(node.matches('script,style,svg,input[type="hidden"],input[type="file"]'))return;
      if(product&&!visible(node,root))return;
      if(node.matches('.network-device-key')){out.push({kind:'text',text:text(node)});return}
      if(hallSurface(root)&&node.id==='bookingsMap'){out.push({kind:'hallMap',editing:!!appState().hallEditMode,tables:[...node.querySelectorAll('.hall-table')].map(table=>{actions.push(table);const saved=appState().hallTables.find(t=>t.id===table.dataset.id);return{key:nodeKey(table),name:text(table.querySelector('.hall-table-name')),status:text(table.querySelector('.hall-table-meta')),shape:saved?.shape,rotation:Number(saved?.rotation)||0,x:Number(saved?.x)||0,y:Number(saved?.y)||0,selected:table.classList.contains('selected'),booked:table.classList.contains('booked')}}),empty:text(node.querySelector('.bookings-map-hint'))});return}
      if(hallSurface(root)&&node.matches('.bookings-layout')){const columns=[];for(const child of node.children){const children=[];walk(child,children);columns.push({kind:'card',scrollKey:child.matches('.bookings-map-card')?'hall-map':'hall-bookings-'+String(appState().selectedHallTableId||'')+'-'+String(appState().bookingDate||''),items:children})}out.push({kind:'columns',items:columns});return}
      if(hallSurface(root)&&node.matches('.booking-card')){actions.push(node);const items=[{kind:'button',key:nodeKey(node),label:text(node.querySelector('.booking-card-name'))+' · '+text(node.querySelector('.booking-card-meta')),receiptRow:true}];const note=node.querySelector('.booking-card-note');if(note)items.push({kind:'text',text:text(note)});for(const button of node.querySelectorAll('button'))walk(button,items);out.push({kind:'card',items});return}
      if(hallSurface(root)&&node.matches('.booking-guests-stepper')){out.push({kind:'heading',text:'Количество гостей: '+text(node.querySelector('span'))});for(const button of node.querySelectorAll('button'))walk(button,out);return}
      if(hallSurface(root)&&node.matches('.booking-side-title')){out.push({kind:'heading',text:text(node)});return}
      if(analyticsSurface(root)&&node.matches('.hbar-list')){out.push({kind:'bars',scrollKey:text(node.closest('.analytics-card')?.querySelector('.analytics-title'))+'-'+String(appState()?.analyticsFrom||'')+'-'+String(appState()?.analyticsTo||''),rows:[...node.querySelectorAll('.hbar-row')].map(row=>{const value=row.querySelector('.hbar-value'),fill=row.querySelector('.hbar-fill'),width=Number.parseFloat(fill?.style?.width||fill?.getAttribute('style')?.match(/width:\s*([^;]+)/)?.[1]||'0');return{name:text(row.querySelector('.hbar-name')),value:value?.classList.contains('hbar-value-hidden')?'':text(value),width:Number.isFinite(width)?width:0}})});return}
      if(analyticsSurface(root)&&node.matches('.analytics-kpi')){out.push({kind:'card',items:[{kind:'metric',label:text(node.querySelector('.k-label')),value:text(node.querySelector('.k-value'))}]});return}
      if(analyticsSurface(root)&&node.matches('.analytics-kpis,.analytics-grid-2,.analytics-date-fields,.analytics-period-modal-grid')){const children=[];for(const child of node.children)walk(child,children);out.push({kind:'grid',columns:node.matches('.analytics-kpis')?3:2,minCellWidth:node.matches('.analytics-grid-2')?380:240,items:children});return}
      if(analyticsSurface(root)&&node.matches('.analytics-title,.analytics-period-title')){out.push({kind:'heading',text:text(node)});return}
      if(analyticsSurface(root)&&node.matches('.analytics-card,.card')){const children=[];for(const child of node.children)walk(child,children);out.push({kind:'card',items:children});return}
      if(warehouseSurface(root)&&node.matches('input[role="button"]')){const i=actions.length;actions.push(node);out.push({kind:'button',key:nodeKey(node),label:(text(node.closest('.field')?.querySelector('label'))||'Количество')+' · '+String(node.value||node.placeholder||'0'),disabled:node.disabled,visible:visible(node,root)});return}
      if(warehouseSurface(root)&&node.matches('.purchase-keypad')){const keys=[];for(const child of node.children)walk(child,keys);out.push({kind:'grid',columns:3,items:keys});return}
      if(warehouseSurface(root)&&node.tagName==='TABLE'){out.push({kind:'table',scrollKey:text(node.closest('section')?.querySelector('h2'))+'-'+String(global._warehouseFilters?.from||'')+'-'+String(global._warehouseFilters?.to||''),headers:[...node.querySelectorAll('thead th')].map(text),rows:[...node.querySelectorAll('tbody tr')].map(row=>[...row.querySelectorAll('td')].map(text))});return}
      if(warehouseSurface(root)&&node.tagName==='DETAILS'){const summary=node.querySelector('summary');if(summary){actions.push(summary);out.push({kind:'button',key:nodeKey(summary),label:text(summary),selected:!!node.open})}if(node.open)for(const child of node.children)if(child!==summary)walk(child,out);return}
      if(warehouseSurface(root)&&node.matches('.analytics-kpi,.stat-box')){out.push({kind:'metric',label:text(node.children[0]),value:text(node.children[1])});return}
      if(warehouseSurface(root)&&node.id==='ttn-total'){out.push({kind:'metric',label:'Итого',value:text(node),primary:true});return}
      if(warehouseSurface(root)&&node.matches('.supply-product')){out.push({kind:'heading',text:text(node.querySelector('strong'))});const note=node.querySelector('small');if(note)out.push({kind:'text',text:text(note)});return}
      if(node.matches('input,select,textarea')){const i=fields.length;fields.push(node);out.push({kind:'field',...field(node,root,i),key:product?nodeKey(node):String(i),live:product});return}
      if(receiptSurface(root)&&node.matches('.receipts-layout')){const columns=[];for(const child of node.children){const children=[];walk(child,children);columns.push({kind:'card',items:children,scrollKey:child.matches('.receipts-list-panel')?'receipts-list-'+text(root.querySelector('.receipts-list-panel .modal-actions')):'receipt-detail-'+String(appState()?.selectedReceiptId||'')})}out.push({kind:'columns',items:columns});return}
      if(product&&node.matches('.pe-summary-row,.pe-price-block,.receipt-line,.receipt-total,.split-summary')){out.push({kind:'metric',label:text(node.children[0]),value:text(node.children[1]),primary:node.matches('.pe-price-block,.receipt-total,.split-summary-remaining')});return}
      if(product&&node.matches('#pf-image-preview')){const img=node.querySelector('img');out.push({kind:'image',source:global._pmLocalImageId&&!global._pmRemoveImage?'mpos-image://'+global._pmLocalImageId:img?.getAttribute('src')||'',label:text(node)||'Фото товара'});return}
      if(product&&node.matches('.split-count')){out.push({kind:'heading',text:'Количество платежей: '+text(node.querySelector('span'))});for(const child of node.children)if(child.tagName==='BUTTON')walk(child,out);return}
      if(receiptSurface(root)&&node.matches('.receipt-return-amount')){out.push({kind:'metric',label:text(node.closest('.field')?.querySelector('label'))||'Сумма возврата',value:text(node),primary:true});return}
      if(product&&node.matches('.payment-change')){out.push({kind:'metric',label:text(node.closest('.field')?.querySelector('label'))||'Сдача',value:text(node)});return}
      if(product&&node.matches('.payment-total-big')){out.push({kind:'metric',label:'К оплате',value:text(node),primary:true});return}
      if(node.tagName==='BUTTON'||(product&&node.getAttribute('role')==='button')){
        if(!form&&((warehouseSurface(root)&&(text(node)==='← Назад'||node.getAttribute('data-receiving-draft-save')!==null))||(domainSurface(root)&&text(node)==='← Назад'))&&!cancelButton)cancelButton=node;
        if((paymentSurface(root)&&appState()?.paymentPage==='receipt'||domainSurface(root)||(warehouseSurface(root)&&!root.querySelector('#purchase-keypad-value')))&&text(node)==='Готово')cancelButton=node;
        if(form&&(/^(Отмена|Закрыть|← Назад|Понятно)$/.test(text(node))||(paymentSurface(root)&&text(node)==='Вернуться')||(warehouseSurface(root)&&(/^(Вернуться|Вернуться к приёмке|Нет, продолжить|Сохранить и выйти)$/.test(text(node))))||(domainSurface(root)&&(/^(Назад|Закрыть)$/.test(text(node))||node.getAttribute('aria-label')==='Закрыть')))){cancelButton=node;return}
        const i=actions.length;actions.push(node);
        out.push({kind:'button',description:node.getAttribute('aria-label')||'',receiptRow:node.matches('.receipts-list-row,.purchase-history-row'),key:product?nodeKey(node):String(i),label:(node.closest('.purchase-keypad')?text(node):node.matches('.purchase-history-row')?[text(node.querySelector('strong')),text(node.querySelector('small')),text(node.querySelector('.purchase-status'))].filter(Boolean).join('\n'):node.matches('.customer-result-card')?[text(node.querySelector('strong')),text(node.querySelector('small')),'Выбрать'].join(' · '):node.matches('.receipts-list-row')?[text(node.querySelector('.receipts-list-number'))+' · '+text(node.querySelector('.receipts-list-amount')),text(node.querySelector('.receipts-list-meta')),text(node.querySelector('.receipts-list-statusline'))].filter(Boolean).join('\n'):node.getAttribute('aria-label')||(node.matches('.payment-amount-display')?'Сумма · '+text(node):node.matches('.split-amount-display')?'Сумма части · '+text(node):node.parentElement?.matches('.split-count')?(text(node)==='+'?'Добавить часть оплаты':'Убрать часть оплаты'):text(node))||'Открыть')+(node.getAttribute('role')==='switch'?(node.getAttribute('aria-checked')==='true'?' · Включено':' · Выключено'):''),style:node.classList.contains('btn-card')?'card':node.classList.contains('btn-cash')?'cash':node.classList.contains('btn-secondary')?'secondary':node.classList.contains('btn-outline')?'outline':'default',primary:node.classList.contains('booking-primary')||node.classList.contains('btn-primary')||node.classList.contains('btn-cash')||(node.classList.contains('btn-card')&&!!node.closest('.modal')),selected:product&&(node.classList.contains('selected')||node.getAttribute('aria-pressed')==='true'||node.classList.contains('analytics-preset-active')),danger:node.classList.contains('booking-danger')||node.classList.contains('danger')||node.classList.contains('btn-danger')||node.classList.contains('receipt-action-return')||/Удалить/.test(text(node)),disabled:node.disabled||node.getAttribute('aria-disabled')==='true',visible:visible(node,root)});return;
      }
      if(node.matches('.settings-card,.employee-settings-row,.list-row,.pe-card,.component-card,.modifier-option-card,.pe-summary,.payment-receipt,.payment-box,.split-payment,.loyalty-summary-card,.loyalty-program-card,.lp-panel,.loyalty-clients-panel')||(warehouseSurface(root)&&node.matches('.card,.supply-request,.supply-invoice-row'))){const children=[];for(const child of node.children)walk(child,children);out.push({kind:'card',items:children});return}
      if(node.matches('.content-title,.settings-section-title,.settings-card-title,.network-card-title,.modal-title,h1,h2,h3,.component-builder-title,.payment-page-title,.payment-section-title,.payment-split-title,.split-payment-label,.receipt-payment-heading,.receipts-list-heading,.receipt-detail-title')){out.push({kind:'heading',text:text(node)});return}
      if(node.matches('.setting-sub,.settings-note,.list-row-name,.list-row-sub,.admin-badge,.center-note,.network-card-subtitle,.network-status,.settings-version,p,.pe-empty,.pe-note,.pe-badge,.pe-eyebrow,.pe-nav-caption,.component-card-name,.component-card-meta,.component-cost,.component-total,.component-empty,.component-builder-note,.modifier-input-suffix,.pe-summary-hint,.pe-summary-row,.pe-price-block,.pe-usage-facts,.product-image-empty,.modal-sub,.payment-order-label,.payment-order-meta,.payment-change,.split-payment-status,.payment-part-caption,.payment-part-amount,.payment-card-instruction,.payment-card-amount,.payment-offline-note,.receipt-line-detail,.receipt-order-label,.receipt-order-meta,.receipt-customer,.receipts-list-count,.receipt-return-banner,.receipt-return-sticker,.receipt-return-meta,.receipt-return-amount')){out.push({kind:'text',text:text(node)});return}
      if(receiptSurface(root)&&node.tagName==='SPAN'){out.push({kind:'text',text:text(node)});return}
      if((domainSurface(root)||warehouseSurface(root)||analyticsSurface(root)||hallSurface(root))&&!node.children.length&&text(node)&&node.tagName!=='LABEL'){out.push({kind:'text',text:text(node)});return}
      // Labels are represented by their field descriptor, so do not duplicate field text.
      if(node.tagName==='LABEL'&&!node.querySelector('input,select,textarea'))return;
      for(const child of node.children)walk(child,out);
    }
    for(const child of root.children)walk(child,items);
    return{items,actions,fields,cancelButton};
  }
  const geometry=root=>{const r=root.getBoundingClientRect();return{rect:{left:r.left,top:r.top,width:r.width,height:r.height},viewportWidth:global.innerWidth,viewportHeight:global.innerHeight}};
  function show(root,form,product=false){
    if(!root||!attached(root)||!enabled())return;
    if(current?.root===root&&product){if(current.busy)return;if(current.theme!==theme()){hide();show(root,form,product);return}const data=model(root,form,true),signature=JSON.stringify([data.items,theme(),product&&pending(root),!form&&geometry(root)]);if(signature!==current.signature){for(const [node,focus]of current.focus)node.focus=focus;current.focus=data.fields.map(node=>[node,node.focus]);Object.assign(current,data,{signature});for(const [node]of current.focus)node.focus=()=>{};post({action:'formPatch',token:current.token,items:data.items,pending:pending(root),blocked:recoveryPending(),...(!form?geometry(root):{})})}return}
    if(current?.root===root){if(form)post({action:'formUpdate',token:current.token,fields:current.fields.map((f,i)=>field(f,root,i))});return}
    hide();const data=model(root,form,product),token='settings-ui-'+(++sequence);
    current={root,form,product,receipt:receiptSurface(root),domain:domainSurface(root),warehouse:warehouseSurface(root),analytics:analyticsSurface(root),hall:hallSurface(root),payment:paymentSurface(root),token,theme:theme(),signature:JSON.stringify([data.items,theme(),product&&pending(root),!form&&geometry(root)]),...data,focus:form?data.fields.map(node=>[node,node.focus]):[],opacity:root.style.opacity,pointerEvents:root.style.pointerEvents};
    const r=root.getBoundingClientRect();
    const payload={action:form?'formShow':'show',token,theme:theme(),expanded:product&&(root.classList.contains('product-editor')||root.id==='payment-page-root'||receiptSurface(root)||domainSurface(root)||warehouseSurface(root)||analyticsSurface(root)||hallSurface(root)),deferCancel:paymentSurface(root)||((domainSurface(root)||warehouseSurface(root)||analyticsSurface(root)||hallSurface(root))&&(form||!!data.cancelButton)),hideCancel:!!data.cancelButton&&((paymentSurface(root)&&appState()?.paymentPage==='receipt')||((domainSurface(root)||warehouseSurface(root)||analyticsSurface(root)||hallSurface(root))&&text(data.cancelButton)==='Готово')),blocked:product&&recoveryPending(),pending:product&&pending(root),title:text(root.querySelector('.modal-title,.content-title,#pe-title'))||(warehouseSurface(root)?({'purchaseOrders':'Заказы поставщикам','receiving':'Приёмка','inventory':'Инвентаризация','inventoryWork':'Инвентаризация'}[root.dataset.mposSettingsUi]||'Склад'):analyticsSurface(root)?'Аналитика продаж':hallSurface(root)?'Зал и бронирования':'Настройки'),items:data.items,cancelLabel:text(data.cancelButton)||(data.fields.length?'Отмена':'Закрыть'),rect:{left:r.left,top:r.top,width:r.width,height:r.height},viewportWidth:global.innerWidth,viewportHeight:global.innerHeight};
    if(post(payload)===false){restore();return}
    if(root.id==='payment-page-root'){surfaceObserver=new MutationObserver(schedule);surfaceObserver.observe(root,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['hidden','disabled','aria-disabled']})}
    root.style.opacity='0';root.style.pointerEvents='none';
    if(form){document.activeElement?.blur?.();for(const [node]of current.focus)node.focus=()=>{}}
    dirty=false;
  }
  function update(){
    queued=false;installDomainOperations();installWarehouseOperations();installHallOperations();
    if(!enabled()||!appState()?.loaded||document.hidden){hide();return}
    const dialog=modal();
    if(dialog){if(dialog.dataset.mposWarehouseForm==='true'&&warehouseEnabled())show(dialog,true,true);else if(dialog.dataset.mposHallForm==='true'&&hallEnabled())show(dialog,true,true);else if(dialog.dataset.mposAnalyticsForm==='true'&&analyticsEnabled())show(dialog,true,true);else if(dialog.dataset.mposCustomerForm==='true'&&domainEnabled())show(dialog,true,true);else if(dialog.dataset.mposReceiptForm==='true'&&receiptEnabled())show(dialog,true,true);else if(dialog.dataset.mposPaymentForm==='true'&&paymentEnabled())show(dialog,true,true);else if(dialog.dataset.mposProductForm==='true'&&productEnabled())show(dialog,true,true);else if(dialog.dataset.mposSettingsForm==='true')show(dialog,true);else hide();return}
    const payment=paymentRoot();if(payment){if(paymentEnabled())show(payment,true,true);else hide();return}
    const editor=productRoot();if(editor){if(productEnabled())show(editor,true,true);else hide();return}
    if(appState().loyaltyAdminScreen){const loyalty=document.querySelector('[data-mpos-settings-ui="loyalty"].active');if(loyalty&&domainEnabled()){show(loyalty,false,true);return}hide();return}
    if(appState().tab==='receipts'){const receipts=document.querySelector('[data-mpos-settings-ui="receipts"].active');if(receipts&&receiptEnabled()){show(receipts,false,true);return}hide();return}
    const page=document.getElementById('printer-page');
    if(page){show(page,true);return}
    for(const selector of ['#receiving-page-root .receiving-page','#warehouse-root .warehouse-page']){const surface=document.querySelector(selector);if(surface){if(warehouseEnabled())show(surface,false,true);else hide();return}}
    if(warehouseTabs.includes(appState().tab)){const root=document.querySelector('[data-mpos-settings-ui="'+appState().tab+'"].active');if(root&&warehouseEnabled()){show(root,false,true);return}hide();return}
    if(appState().tab==='bookings'){const root=document.querySelector('[data-mpos-settings-ui="bookings"].active');if(root&&hallEnabled()){show(root,false,true);return}hide();return}
    if(appState().tab==='analytics'){const root=document.querySelector('[data-mpos-native-analytics].active')||document.querySelector('[data-mpos-settings-ui="analytics"].active');if(root&&analyticsEnabled()){show(root,false,true);return}hide();return}
    if(!['settings','network'].includes(appState().tab)||appState().loyaltyAdminScreen){hide();return}
    const root=document.querySelector('[data-mpos-settings-ui="'+appState().tab+'"].active');if(!root){hide();return}
    if(current?.root===root&&!current.form){if(dirty&&!current.busy){hide();show(root,false);return}const r=root.getBoundingClientRect(),signature=JSON.stringify([r.left,r.top,r.width,r.height,theme()]);if(signature!==current.geometry){hide();show(root,false);if(current)current.geometry=signature}return}
    show(root,false);
  }
  function schedule(){if(!queued){queued=true;requestAnimationFrame(update)}}
  for(const [name,tab]of [['renderSettingsScreen','settings'],['renderNetworkScreen','network'],['renderReceiptsScreen','receipts'],['renderLoyaltyAdminScreen','loyalty'],['renderPurchaseOrdersScreen','purchaseOrders'],['renderReceivingScreen','receiving'],['renderInventoryScreen','inventory'],['renderInventoryWorkScreen','inventoryWork'],['renderAnalyticsScreen','analytics'],['renderBookingsScreen','bookings']]){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){return original.apply(this,args).replace('<div class="screen '+(tab==='bookings'?'bookings-screen':'content-screen'),'<div data-mpos-settings-ui="'+tab+'" class="screen '+(tab==='bookings'?'bookings-screen':'content-screen'))};
  }
  const showModal=global.showModal;
  if(typeof showModal==='function')global.showModal=function(...args){const value=showModal.apply(this,args);{const node=modal();if(node&&hallEnabled()&&hallScope)node.dataset.mposHallForm='true';if(node&&analyticsEnabled()&&(analyticsScope||node.querySelector('#analytics-period-from')))node.dataset.mposAnalyticsForm='true';if(node&&warehouseEnabled()&&(warehouseScope||node.querySelector('#sf-name,#supplier-delete-confirm,#purchase-keypad-value,#ttn-confirm,.warehouse-report-section')))node.dataset.mposWarehouseForm='true';if(node&&domainEnabled()&&(domainScope||node.querySelector('#admin-customer-search,.loyalty-customer-section-title,#la-progress,#lp-save')))node.dataset.mposCustomerForm='true';if(node&&receiptEnabled()&&(receiptScope||node.querySelector('.receipt-return-amount')&&text(node.querySelector('.modal-title'))==='Возврат выполнен'))node.dataset.mposReceiptForm='true';if(node&&scope===0&&appState()?.paymentPage&&paymentEnabled())node.dataset.mposPaymentForm='true';if(node&&scope===0&&productRoot()&&productEnabled())node.dataset.mposProductForm='true';if(node&&(scope||node.querySelector('#backup-import-password,#employee-delete-password,#ef-name')))node.dataset.mposSettingsForm='true'}schedule();return value};
  for(const name of ['openHallEditMenu','openCreateHallTableModal','openHallTableEditModal','deleteHallTable','startBookingForSelected','editBooking']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){hallScope++;try{return original.apply(this,args)}finally{hallScope--;schedule()}}}
  for(const name of ['changeBookingGuests','changeEditBookingGuests','refreshBookingTimeSummary']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){const result=original.apply(this,args);schedule();return result}}
  for(const name of ['openAnalyticsPeriodModal']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){analyticsScope++;try{return original.apply(this,args)}finally{analyticsScope--;schedule()}}}
  for(const name of ['openSupplierModal','deleteSupplier','openDeletePurchaseOrderModal','openPurchaseQuantity','viewPurchaseOrder','viewReceivingModal','renderReceivingDocument','confirmReceivingDocument','openWarehouseReportModal','requestCancelInventory','showInventorySummary']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){warehouseScope++;try{return original.apply(this,args)}finally{warehouseScope--;schedule()}}}
  for(const name of ['filterSupplierProducts','filterInventoryProducts','purchaseQuantityKey','applyPurchaseQuantity','updatePurchaseRequest','togglePurchaseHistory','togglePurchasePanel','toggleReceivingHistory','toggleReceivingPanel','updateInvoiceLine','addInvoiceProduct','removeInvoiceLine','finishReceivingPage','closeWarehousePage']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){const result=original.apply(this,args);schedule();if(result&&typeof result.then==='function'){if(submission)submission.promises.push(result);Promise.resolve(result).then(schedule,schedule)}return result}}
  for(const name of ['parkOrder','openParkedModal','openOrderCustomer','renderOrderCustomerModal','openCustomerPicker','openCreateCustomer','openLoyaltyAdjustment','loyaltyProgramForm','confirmDeleteLoyaltyProgram']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){domainScope++;try{return original.apply(this,args)}finally{domainScope--;schedule()}}}
  for(const name of ['customerSearchChanged','adminCustomerSearch','loyaltyAdminCustomerSearch','loadCustomerLoyalty','loadLoyaltyAdminScreen','filterLoyaltyProducts','updateLoyaltyProductCount']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){const result=original.apply(this,args);schedule();if(result&&typeof result.then==='function')Promise.resolve(result).then(schedule,schedule);return result}}
  for(const name of ['viewReceiptModal','openReturnConfirm']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){receiptScope++;try{return original.apply(this,args)}finally{receiptScope--;schedule()}}}
  const returnOperation=global.processFullReturn;
  if(typeof returnOperation==='function')global.processFullReturn=function(...args){receiptScope++;let result;try{result=returnOperation.apply(this,args)}finally{receiptScope--;schedule()}if(!result||typeof result.then!=='function')return result;receiptOutstanding++;const watched=Promise.resolve(result).finally(()=>{receiptOutstanding--;schedule()});if(submission)submission.promises.push(watched);return watched};
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
      if(!paymentOutstanding){paymentMessage='';paymentCart=appState()?.cart}
      const result=original.apply(this,args);schedule();
      if(!result||typeof result.then!=='function')return result;
      paymentOutstanding++;
      const watched=Promise.resolve(result).finally(()=>{paymentOutstanding--;schedule();if(!paymentOutstanding&&current?.payment&&attached(current.root)&&paymentCart===appState()?.cart)post({action:'formResult',token:current.token,ok:false,error:!!paymentMessage,message:paymentMessage,blocked:recoveryPending()})});
      if(submission)submission.promises.push(watched);return watched;
    };
  }
  for(const name of ['renderPaymentScreen','renderSplitPayment','renderPaymentAmount','syncSplitPaymentUI','showPaymentReceipt','finishPaymentFlow','closePaymentPage','returnFromSplitPayment','setSplitMethod','setPaymentCash']){const original=global[name];if(typeof original!=='function')continue;global[name]=function(...args){try{return original.apply(this,args)}finally{schedule()}}}
  const close=global.closeModal;
  if(typeof close==='function')global.closeModal=function(...args){const result=close.apply(this,args);if(current?.form&&!attached(current.root))hide();schedule();return result};
  const flash=global.flash;
  if(typeof flash==='function')global.flash=function(message,...args){if(submission)submission.message=String(message??'');if(paymentOutstanding)paymentMessage=String(message??'');if(activeProductJob)activeProductJob.message=String(message??'');if(current?.job)current.job.message=String(message??'');return flash.call(this,message,...args)};
  function apply(values,change){
    const events=[],intentions=new Map(),warehouse=!!current?.warehouse;
    if(!values||!current)return;
    for(const [key,value]of Object.entries(values)){
      if(!/^\d+$/.test(key))continue;const node=current.product?current.fields.find(n=>nodeKey(n)===key):current.fields[Number(key)];if(!node||!attached(node)||node.disabled||node.readOnly)continue;
      const prior=node.type==='checkbox'?node.checked:node.value;
      if(node.type==='checkbox'){if(typeof value!=='boolean')continue;node.checked=value}
      else{if(typeof value!=='string')continue;let normalized=node.type==='number'?value.replace(',','.'):value;if(warehouse&&node.type==='number'&&/^[-+]?\d+\.$/.test(normalized))normalized=normalized.slice(0,-1);node.value=normalized}
      if(change&&prior!==(node.type==='checkbox'?node.checked:node.value)){events.push(node);intentions.set(node,node.type==='checkbox'?node.checked:node.value);}
    }
    // Assign the complete draft first: unit/checkbox handlers may replace sibling fields.
    if(warehouse)events.sort((a,b)=>Number(a.tagName==='SELECT'||a.type==='checkbox')-Number(b.tagName==='SELECT'||b.type==='checkbox'));
    for(const node of events){if(!attached(node))continue;if(warehouse){if(node.type==='checkbox')node.checked=intentions.get(node);else node.value=intentions.get(node)}if(current?.product&&node.type!=='checkbox'&&node.tagName!=='SELECT')node.dispatchEvent(new Event('input',{bubbles:true}));if(attached(node))node.dispatchEvent(new Event('change',{bubbles:true}));}
  }
  global.__nativeSettingsAction=async payload=>{
    installDomainOperations();installWarehouseOperations();installHallOperations();
    if(payload?.action==='dateEditing'&&payload.editing===false&&payload.token===analyticsDateToken){analyticsDateToken=null;analyticsUi.dateEditing=false;analyticsUi.resumeDate?.();return}
    if(!enabled()||!current||payload?.token!==current.token||!attached(current.root)||current.busy)return;
    if(payload.action==='dateEditing'){if(current.analytics&&payload.editing===true&&current.fields.some(node=>nodeKey(node)===String(payload.key)&&node.type==='date')){analyticsDateToken=current.token;analyticsUi.dateEditing=true}return}
    if(current.product&&pending(current.root))return;
    if(!current.form&&!current.warehouse&&!current.analytics&&!current.hall&&!['settings','network','receipts'].includes(appState()?.tab))return;
    if(payload.action==='cancel'&&(current.payment||current.domain||current.warehouse||current.analytics||current.hall)){const snapshot=current,feedback={message:'',promises:[]};submission=feedback;snapshot.busy=true;snapshot.job=feedback;try{apply(payload.fields,true);const button=snapshot.cancelButton;if(button&&attached(button)&&!button.disabled)button.click();else global.closeModal();submission=null;for(let i=0;i<feedback.promises.length;i++)await feedback.promises[i]}catch(error){feedback.message=error?.message||'Не удалось вернуться'}finally{submission=null;snapshot.busy=false;snapshot.job=null;if(current===snapshot&&attached(snapshot.root))post({action:'formResult',token:snapshot.token,ok:false,error:!!feedback.message,message:feedback.message,blocked:recoveryPending()});schedule()}return}
    if(payload.action==='cancel'){if(!current.form)return;apply(payload.fields,current.product);const button=current.cancelButton;if(button&&attached(button)&&!button.disabled)button.click();else{const page=document.getElementById('printer-page');if(page)global.closePrinterPage();else global.closeModal()}hide();schedule();return}
    if(payload.action==='change'&&current.warehouse){const snapshot=current,epoch=(snapshot.fieldEpoch||0)+1,feedback={message:'',promises:[]};snapshot.fieldEpoch=epoch;snapshot.job=feedback;try{submission=feedback;apply(payload.fields,true);submission=null;for(let i=0;i<feedback.promises.length;i++)await feedback.promises[i];if(current===snapshot&&snapshot.fieldEpoch===epoch&&attached(snapshot.root))post({action:'formResult',token:snapshot.token,ok:false,error:!!feedback.message,message:feedback.message,blocked:recoveryPending()})}catch(error){if(current===snapshot&&snapshot.fieldEpoch===epoch)post({action:'formResult',token:snapshot.token,ok:false,error:true,message:error?.message||'Не удалось изменить поле',blocked:recoveryPending()})}finally{submission=null;if(snapshot.job===feedback)snapshot.job=null;schedule()}return}
    if(payload.action==='change'){if(current.product){const feedback={message:'',promises:[]};try{submission=feedback;apply(payload.fields,true);post({action:'formResult',token:current.token,ok:false,error:!!feedback.message,message:feedback.message,blocked:recoveryPending()})}catch(error){post({action:'formResult',token:current.token,ok:false,error:true,message:error?.message||'Не удалось изменить поле',blocked:recoveryPending()})}finally{submission=null}if(current?.product)show(current.root,current.form,true);schedule();return}apply(payload.fields,true);if(current?.form)post({action:'formUpdate',token:current.token,fields:current.fields.map((f,i)=>field(f,current.root,i))});schedule();return}
    const move=payload.action==='hallMove'&&current.hall&&appState().hallEditMode;
    if((payload.action!=='click'&&!move)||!/^\d+$/.test(String(payload.key)))return;
    const button=current.product?current.actions.find(n=>nodeKey(n)===String(payload.key)):current.actions[Number(payload.key)];if(!button||!attached(button)||button.disabled||button.getAttribute('aria-disabled')==='true'||!visible(button,current.root))return;
    const snapshot=current,job={promises:[],message:'',session:global._pmSession,cart:appState()?.cart};if(snapshot.product)activeProductJob=job;snapshot.busy=true;snapshot.job=job;
    try{
      submission=job;apply(payload.fields,snapshot.product);
      // Input handlers may replace the list before this gesture reaches its button.
      if(!attached(button)||button.disabled||button.getAttribute('aria-disabled')==='true'||!visible(button,snapshot.root)){job.message='Список обновился. Выберите действие ещё раз.';if(current===snapshot)post({action:'formResult',token:snapshot.token,ok:false,error:false,message:job.message,blocked:recoveryPending()});return}
      // Only a button from this mounted reviewed form can act; no onclick source is evaluated.
      if(move){
        if(!button.matches('.hall-table')||!Number.isFinite(payload.x)||!Number.isFinite(payload.y))return;
        const map=document.getElementById('bookingsMap'),table=appState().hallTables.find(t=>t.id===button.dataset.id);if(!map||!table||!map.clientWidth||!map.clientHeight)return;
        const event={clientX:0,clientY:0,pointerId:1,preventDefault(){},stopPropagation(){}};
        global.hallPointerStart(event,button);
        global.hallPointerMove({...event,clientX:(payload.x-table.x)/100*map.clientWidth,clientY:(payload.y-table.y)/100*map.clientHeight},button);
        global.hallPointerEnd(event);
        // Native drag has no browser-generated click. Consume its suppression once.
        appState()._hallSuppressClick=false;
      }else button.click();submission=null;
      for(let i=0;i<job.promises.length;i++)await job.promises[i];
      if(current===snapshot&&attached(snapshot.root))post({action:'formResult',token:snapshot.token,ok:false,error:/^(Введите|Проверьте|Не удалось|Ошибка|Неверн|Требуется|Перезапустите|Выберите)/i.test(job.message),message:job.message,blocked:recoveryPending()});
    }catch(error){if(current===snapshot)post({action:'formResult',token:snapshot.token,ok:false,error:true,message:error?.message||'Не удалось выполнить операцию',blocked:recoveryPending()})}
    finally{submission=null;snapshot.busy=false;snapshot.job=null;if(activeProductJob===job)activeProductJob=null;if(snapshot.product&&current!==snapshot&&current?.product&&attached(current.root)&&(snapshot.warehouse?current.warehouse:snapshot.domain?current.domain:snapshot.receipt?current.receipt:snapshot.payment?job.cart===appState()?.cart:job.session===global._pmSession))post({action:'formResult',token:current.token,ok:false,error:/^(Введите|Проверьте|Не удалось|Ошибка|Неверн|Требуется|Перезапустите|Выберите)/i.test(job.message),message:job.message,blocked:recoveryPending()});schedule()}
  };
  global.MPosCore=global.MPosCore||{};
  global.MPosCore.ConnectionTestFeedback=Object.freeze({capture(){
    const snapshot=current,job=current?.job;
    return message=>{if(current===snapshot&&(!snapshot||snapshot.job===job&&attached(snapshot.root)))global.flash?.(message)};
  }});
  function observe(){
    const observer=new MutationObserver(()=>{dirty=true;schedule()});
    for(const id of ['app','modal-root','product-editor-root','warehouse-root','receiving-page-root']){const node=document.getElementById(id);if(node)observer.observe(node,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['hidden','disabled','aria-checked','aria-pressed']})}
    observer.observe(document.body,{childList:true});observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
    global.addEventListener('resize',()=>{if(current&&!current.form&&!current.product)hide();schedule()});document.addEventListener('visibilitychange',schedule);schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})(window);
