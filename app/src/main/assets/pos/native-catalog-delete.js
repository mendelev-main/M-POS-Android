(function(global){
  'use strict';
  const core=global.MPosCore,bridge=global.webkit?.messageHandlers?.settingsScreen;
  if(!core?.Storage||!bridge)return;
  const original=global.requestDelete,originalConfirm=global.confirmDelete;
  const enabled=()=>global.MPosNativeCatalogDeleteEnabled!==false;
  let busy=false,blocked=false,sequence=0;
  const pending=new Map(),clone=x=>JSON.parse(JSON.stringify(x));
  const stamp=()=>JSON.stringify([state.products,categoryLayoutSnapshot(),state.posNavigation]);
  core.CatalogDelete=Object.freeze({hasPending:()=>busy});
  global.__mposCatalogDeleteResult=result=>{
    const p=pending.get(result?.requestId);if(!p)return;
    if(result.action==='committing'){clearTimeout(p.timer);p.timer=setTimeout(()=>{pending.delete(result.requestId);p.reject(Error('commit status is uncertain'));},30000);return;}
    if(result.action==='retry'){clearTimeout(p.timer);p.timer=null;return;}
    pending.delete(result.requestId);clearTimeout(p.timer);
    if(result.uncertain)p.reject(Error('commit status is uncertain'));
    else if(result.cancelled)p.resolve(result);
    else if(result.ok)p.resolve(result);
    else p.reject(Error(result.message||'Не удалось удалить элемент каталога'));
  };
  global.requestDelete=async function(type,id){
    if(!enabled())return original.apply(this,arguments);
    if(!['product','category'].includes(type))return false;
    if(busy||blocked||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)){flash(blocked?'Перезапустите M POS для восстановления данных':'Дождитесь завершения изменения данных');return false;}
    busy=true;const before=stamp();
    try{
      const entries=await Promise.all(['products','layout','posNavigation'].map(async key=>[key,await core.Storage.get(key,null)]));
      // Ensure the native root/order authorities exist even on a fresh installation.
      await Promise.all(['employees','shifts','criticalStorageJournal','orders'].map(key=>core.Storage.get(key,null)));
      if(before!==stamp())throw Error('Каталог изменился. Откройте подтверждение заново');
      const input={version:1,operation:'delete',type,id,expected:Object.fromEntries(entries),passwordRequired:type!=='product'||!currentShiftEmployeeIsAdmin()};
      const result=await new Promise((resolve,reject)=>{
        const requestId='catalog-delete-'+(++sequence);pending.set(requestId,{resolve,reject,timer:null});
        try{if(bridge.postMessage({action:'catalogDeleteAuthorize',requestId,theme:state.theme||'light',command:clone(input)})===false)throw Error('Нативное окно подтверждения недоступно');}
        catch(e){pending.delete(requestId);reject(e);}
      });
      if(result.cancelled)return false;
      if(before!==stamp()){blocked=true;criticalStorageRecoveryPending=true;throw Error('Каталог изменился во время удаления. Перезапустите приложение');}
      if(!Array.isArray(result.products)||!result.layout||!result.posNavigation)throw Error('Некорректное подтверждение удаления');
      const layout=result.layout;
      state.products=result.products;state.layoutTiles=layout.tiles||[];
      if(type==='category'){
        state.posNavigation=result.posNavigation;
        state.categoryOrder=layout.categoryOrder||[];
        for(const key of ['categoryColors','categorySymbols','categoryOnlineOrder','categoryOnlineMenu'])state[key]=layout[key]||{};
        state.categoryOnline=state.categoryOnlineOrder;
      }
      closeModal();render();flash(type==='category'?'Категория удалена':'Товар удалён');
      if(type==='product'&&global._pmEditingId===id&&document.getElementById('pe-save'))finishProductEditor();
      return true;
    }catch(error){
      if(String(error?.message).includes('commit status is uncertain')){blocked=true;criticalStorageRecoveryPending=true;markStorageBroken(error);}
      flash(blocked?'Статус удаления не подтверждён. Перезапустите приложение':error?.message||'Не удалось удалить элемент каталога');return false;
    }finally{busy=false;}
  };
  global.confirmDelete=function(type,id){return enabled()?global.requestDelete(type,id):originalConfirm.apply(this,arguments);};
})(window);
