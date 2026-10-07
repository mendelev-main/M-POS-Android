/* Native presentation of reviewed settings. Mounted DOM handlers retain business/auth ownership. */
(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.settingsScreen;
  if(!bridge)return;
  if(global.MPosNativeSettingsUiEnabled===undefined)global.MPosNativeSettingsUiEnabled=true;
  let sequence=0,current=null,queued=false,scope=0,submission=null,dirty=true;
  const text=node=>String(node?.getAttribute?.('title')||node?.textContent||'').replace(/\s+/g,' ').trim().replace(/iPad/g,'Android').replace(/Safari/gi,'браузере');
  const enabled=()=>global.MPosNativeSettingsUiEnabled!==false;
  const recoveryPending=()=>global.MPosCore?.PlatformSettings?.blocked===true||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending);
  const theme=()=>document.documentElement.getAttribute('data-theme')==='dark'?'dark':'light';
  const post=value=>bridge.postMessage(value);
  const modal=()=>document.querySelector('#modal-root .modal');
  const attached=node=>node?.isConnected!==false;
  function visible(node,root){for(let p=node;p&&p!==root;p=p.parentElement)if(p.hidden||p.style?.display==='none')return false;return node.type!=='hidden'}
  function restore(){if(current?.focus)for(const [node,focus]of current.focus)node.focus=focus;if(current?.root){current.root.style.opacity=current.opacity;current.root.style.pointerEvents=current.pointerEvents}current=null}
  function hide(){if(current)post({action:current.form?'formHide':'hide',token:current.token});restore()}
  function field(node,root,index){
    const parent=node.closest('.field'),row=node.closest('.setting-row'),label=node.closest('label');
    const name=text(parent?.querySelector('label')||row?.querySelector('.setting-title'))||label?.getAttribute('aria-label')||text(label)||node.getAttribute('aria-label')||node.id||'Поле';
    return{key:String(index),label:name,type:node.tagName==='SELECT'?'select':node.tagName==='TEXTAREA'?'multiline':node.type==='checkbox'?'checkbox':node.type==='password'?'password':node.type==='number'?'number':node.type==='tel'?'phone':'text',value:node.type==='checkbox'?!!node.checked:String(node.value??''),hint:node.placeholder||'',visible:visible(node,root),options:node.options?[...node.options].map(o=>({value:o.value,label:text(o)})):[],readOnly:node.readOnly||node.disabled};
  }
  function model(root,form){
    const actions=[],fields=[],items=[];let cancelButton=null;
    function walk(node,out){
      if(!node||node.nodeType!==1)return;
      if(node.matches('script,style,svg,input[type="hidden"]'))return;
      if(node.matches('.network-device-key')){out.push({kind:'text',text:text(node)});return}
      if(node.matches('input,select,textarea')){const i=fields.length;fields.push(node);out.push({kind:'field',...field(node,root,i)});return}
      if(node.tagName==='BUTTON'){
        if(form&&/^(Отмена|Закрыть|← Назад|Понятно)$/.test(text(node))){cancelButton=node;return}
        const i=actions.length;actions.push(node);
        out.push({kind:'button',key:String(i),label:node.getAttribute('aria-label')||text(node)||'Открыть',primary:node.classList.contains('btn-primary'),danger:node.classList.contains('danger')||/Удалить/.test(text(node)),disabled:node.disabled,visible:visible(node,root)});return;
      }
      if(node.matches('.settings-card,.employee-settings-row,.list-row')){const children=[];for(const child of node.children)walk(child,children);out.push({kind:'card',items:children});return}
      if(node.matches('.content-title,.settings-section-title,.settings-card-title,.network-card-title,.modal-title')){out.push({kind:'heading',text:text(node)});return}
      if(node.matches('.setting-sub,.settings-note,.list-row-name,.list-row-sub,.admin-badge,.center-note,.network-card-subtitle,.network-status,.settings-version,p')){out.push({kind:'text',text:text(node)});return}
      // Labels are represented by their field descriptor, so do not duplicate field text.
      if(node.tagName==='LABEL'&&!node.querySelector('input,select,textarea'))return;
      for(const child of node.children)walk(child,out);
    }
    for(const child of root.children)walk(child,items);
    return{items,actions,fields,cancelButton};
  }
  function show(root,form){
    if(!root||!enabled())return;
    if(current?.root===root){if(form)post({action:'formUpdate',token:current.token,fields:current.fields.map((f,i)=>field(f,root,i))});return}
    hide();const data=model(root,form),token='settings-ui-'+(++sequence);
    current={root,form,token,...data,focus:form?data.fields.map(node=>[node,node.focus]):[],opacity:root.style.opacity,pointerEvents:root.style.pointerEvents};
    const r=root.getBoundingClientRect();
    const payload={action:form?'formShow':'show',token,theme:theme(),title:text(root.querySelector('.modal-title,.content-title'))||'Настройки',items:data.items,cancelLabel:text(data.cancelButton)||(data.fields.length?'Отмена':'Закрыть'),rect:{left:r.left,top:r.top,width:r.width,height:r.height},viewportWidth:global.innerWidth,viewportHeight:global.innerHeight};
    if(post(payload)===false){restore();return}
    root.style.opacity='0';root.style.pointerEvents='none';
    if(form){document.activeElement?.blur?.();for(const [node]of current.focus)node.focus=()=>{}}
    dirty=false;
  }
  function update(){
    queued=false;
    if(!enabled()||!global.state?.loaded||document.hidden){hide();return}
    const dialog=modal();
    if(dialog){if(dialog.dataset.mposSettingsForm==='true')show(dialog,true);else hide();return}
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
  if(typeof showModal==='function')global.showModal=function(...args){const value=showModal.apply(this,args);{const node=modal();if(node&&(scope||node.querySelector('#backup-import-password,#employee-delete-password,#ef-name')))node.dataset.mposSettingsForm='true'}schedule();return value};
  for(const name of ['openEmployeeModal','deleteEmployee','showEmployeeAdminInfo','showAdminOnlyInfo','openCompanyDetailsModal','openCompanyEditModal','openDeliveryRateModal','openDiscountModal','openBackendSettings','openTelegramSettings','openNotificationSettings','openBackupManager','prepareBackupImport','printerInfo']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){scope++;try{return original.apply(this,args)}finally{scope--;schedule()}};
  }
  for(const name of ['saveEmployee','confirmDeleteEmployee','savePrinterFromPage','testPrinterFromPage','deletePrinter','saveNotificationSettings','saveTelegramSettings','testTelegramConnection','testBackendConnection','confirmBackupImport','exportBackup','importBackup','syncMenuToBackend','testWebOrder']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(...args){const result=original.apply(this,args);if(submission&&result&&typeof result.then==='function')submission.promises.push(result);return result};
  }
  const close=global.closeModal;
  if(typeof close==='function')global.closeModal=function(...args){const result=close.apply(this,args);if(current?.form&&!attached(current.root))hide();schedule();return result};
  const flash=global.flash;
  if(typeof flash==='function')global.flash=function(message,...args){if(submission)submission.message=String(message??'');if(current?.busy&&current.job)current.job.message=String(message??'');return flash.call(this,message,...args)};
  function apply(values,change){
    if(!values||!current)return;
    for(const [key,value]of Object.entries(values)){
      if(!/^\d+$/.test(key))continue;const node=current.fields[Number(key)];if(!node||!attached(node)||node.disabled||node.readOnly)continue;
      const prior=node.type==='checkbox'?node.checked:node.value;
      if(node.type==='checkbox'){if(typeof value!=='boolean')continue;node.checked=value}
      else{if(typeof value!=='string')continue;node.value=node.type==='number'?value.replace(',','.'):value}
      if(change&&prior!==(node.type==='checkbox'?node.checked:node.value))node.dispatchEvent(new Event('change',{bubbles:true}));
    }
  }
  global.__nativeSettingsAction=async payload=>{
    if(!enabled()||!current||payload?.token!==current.token||!attached(current.root)||current.busy)return;
    if(!current.form&&!['settings','network'].includes(global.state?.tab))return;
    if(payload.action==='cancel'){if(!current.form)return;const button=current.cancelButton;if(button&&attached(button)&&!button.disabled)button.click();else{const page=document.getElementById('printer-page');if(page)global.closePrinterPage();else global.closeModal()}hide();schedule();return}
    if(payload.action==='change'){apply(payload.fields,true);if(current?.form)post({action:'formUpdate',token:current.token,fields:current.fields.map((f,i)=>field(f,current.root,i))});schedule();return}
    if(payload.action!=='click'||!/^\d+$/.test(String(payload.key)))return;
    const button=current.actions[Number(payload.key)];if(!button||!attached(button)||button.disabled||!visible(button,current.root))return;
    const snapshot=current,job={promises:[],message:''};snapshot.busy=true;snapshot.job=job;
    try{
      apply(payload.fields,false);submission=job;
      // Only a button from this mounted reviewed form can act; no onclick source is evaluated.
      button.click();submission=null;
      for(let i=0;i<job.promises.length;i++)await job.promises[i];
      if(current===snapshot&&attached(snapshot.root))post({action:'formResult',token:snapshot.token,ok:false,error:/^(Введите|Проверьте|Не удалось|Ошибка|Неверн|Требуется|Перезапустите|Выберите)/i.test(job.message),message:job.message,blocked:recoveryPending()});
    }catch(error){if(current===snapshot)post({action:'formResult',token:snapshot.token,ok:false,error:true,message:error?.message||'Не удалось выполнить операцию',blocked:recoveryPending()})}
    finally{submission=null;snapshot.busy=false;snapshot.job=null;schedule()}
  };
  function observe(){
    const observer=new MutationObserver(()=>{dirty=true;schedule()});
    for(const id of ['app','modal-root']){const node=document.getElementById(id);if(node)observer.observe(node,{subtree:true,childList:true})}
    observer.observe(document.body,{childList:true});observer.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});
    global.addEventListener('resize',()=>{if(current&&!current.form)hide();schedule()});document.addEventListener('visibilitychange',schedule);schedule();
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',observe,{once:true});else observe();
})(window);
