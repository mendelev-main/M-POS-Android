(function(global){
  'use strict';
  const core=global.MPosCore,bridge=global.webkit?.messageHandlers?.settingsScreen;
  if(!core?.Storage||!bridge)return;
  const names=['requestStockUnlock','confirmStockUnlock','requestNoStockUnlock','confirmNoStockUnlock'];
  const originals=Object.fromEntries(names.map(k=>[k,global[k]]));
  const enabled=()=>global.MPosNativeEditorAuthorizationEnabled!==false;
  let busy=false,blocked=false,sequence=0;
  let editorContext=null;const grants={};
  const open=global.openProductModal;
  if(typeof open==='function')global.openProductModal=function(...args){for(const key of Object.keys(grants))delete grants[key];editorContext=null;return open.apply(this,args);};
  core.EditorAuthorization=Object.freeze({grants(){return editorContext===documentContext()?{...grants}:{}},clear(){for(const key of Object.keys(grants))delete grants[key];editorContext=null;}});const pending=new Map();
  const documentContext=()=>JSON.stringify([global._pmSession,global._pmEditingId,getProduct(global._pmEditingId)??null]);
  const context=()=>JSON.stringify([global._pmSession,global._pmEditingId,global._pmType,getProduct(global._pmEditingId)??null]);
  global.__mposEditorAuthorizationResult=result=>{
    const p=pending.get(result?.requestId);if(!p)return;
    if(result.action==='committing'){clearTimeout(p.timer);p.timer=setTimeout(()=>{pending.delete(result.requestId);p.reject(Error('commit status is uncertain'));},30000);return;}
    if(result.action==='retry'){clearTimeout(p.timer);p.timer=null;return;}
    pending.delete(result.requestId);clearTimeout(p.timer);
    if(result.uncertain)p.reject(Error('commit status is uncertain'));
    else if(result.cancelled||result.ok)p.resolve(result);
    else p.reject(Error(result.message||'Не удалось проверить разрешение редактора'));
  };
  async function authorize(operation){
    if(busy||blocked||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)){flash('Дождитесь завершения действия или перезапустите приложение');return false;}
    if(operation==='stock'&&!global._pmEditingId)return false;
    busy=true;const before=context(),productId=global._pmEditingId??null,expected=JSON.parse(JSON.stringify(getProduct(productId)??null));
    try{
      await Promise.all(['products','employees','shifts','criticalStorageJournal'].map(key=>core.Storage.get(key,null)));
      if(before!==context())throw Error('Товар изменился. Откройте карточку заново');
      const result=await new Promise((resolve,reject)=>{
        const requestId='editor-authorize-'+(++sequence);pending.set(requestId,{resolve,reject,timer:null});
        try{if(bridge.postMessage({action:'editorAuthorize',requestId,theme:state.theme||'light',command:{version:1,operation,productId,expected}})===false)throw Error('Нативное окно подтверждения недоступно');}
        catch(error){pending.delete(requestId);reject(error);}
      });
      if(before!==context())throw Error('Товар изменился. Откройте карточку заново');
      if(!result.cancelled){
        if(result.granted!==true||typeof result.grant!=='string'||result.operation!==operation||result.productId!==productId)throw Error('Не удалось подтвердить разрешение редактора');
        editorContext=documentContext();grants[operation]=result.grant;
        if(operation==='stock')global._pmStockUnlocked=true;else global._pmNoStockUnlocked=true;
      }
      closeModal();renderProductModal(getProduct(global._pmEditingId),global._pmType,global._pmSimpleProducts,state.categoryOrder.slice());
      if(!result.cancelled)flash(operation==='stock'?'Изменение остатка разрешено':'Доступ администратора разрешен');
      return !result.cancelled;
    }catch(error){
      if(String(error?.message).includes('commit status is uncertain'))blocked=true;
      flash(blocked?'Перезапустите приложение перед повторным подтверждением':error?.message||'Не удалось проверить разрешение редактора');return false;
    }finally{busy=false;}
  }
  for(const name of names)global[name]=function(...args){
    if(!enabled())return originals[name].apply(this,args);
    return authorize(name.includes('NoStock')?'no-stock':'stock');
  };
})(window);
