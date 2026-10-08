(function(global){
  'use strict';
  const commands=global.MPosCore?.ProductWebCommands;if(!commands)return;
  const original=global.toggleProductOnline;let busy=false,blocked=false;
  global.toggleProductOnline=async function(id){
    if(global.MPosNativeProductWebEnabled===false)return original.apply(this,arguments);
    if(busy||blocked||(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending))return false;
    if(!canEditProductWebSetting()){flash('Настройку WEB может изменять только администратор при открытой им смене');return false;}
    if(!getProduct(id))return false;
    busy=true;const before=JSON.stringify(state.products);
    try{
      const result=await commands.commit({version:1,id,expected:JSON.parse(before)});
      if(!result.changed)return false;
      if(before!==JSON.stringify(state.products)){blocked=true;criticalStorageRecoveryPending=true;throw Error('Каталог изменился. Перезапустите приложение');}
      if(!Array.isArray(result.products)||typeof result.enabled!=='boolean')throw Error('Не удалось подтвердить настройку WEB');
      state.products=result.products;render();flash(result.enabled?'Товар доступен для онлайн-заказа':'Товар недоступен для онлайн-заказа');return true;
    }catch(error){
      if(String(error?.message).includes('commit status is uncertain')){blocked=true;criticalStorageRecoveryPending=true;markStorageBroken(error);}
      flash(blocked?'Перезапустите приложение перед повторным изменением':error?.message||'Не удалось сохранить настройку WEB');return false;
    }finally{busy=false;}
  };
})(window);
