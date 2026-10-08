(function(global){
  'use strict';
  const access=global.MPosCore?.AdminAccess;if(!access)return;
  for(const name of ['openAdminPanel','openCompanyDetailsModal','testTelegramConnection','testWebOrder','testBackendConnection']){
    const original=global[name];if(typeof original!=='function')continue;
    let busy=false;
    global[name]=async function(...args){
      if(global.MPosNativeAdminAccessEnabled===false)return original.apply(this,args);
      if(busy)return false;
      busy=true;
      try{
        const result=await access.check();
        if(result.allowed!==true){
          if(name==='openAdminPanel'||name==='openCompanyDetailsModal')showAdminOnlyInfo(name==='openAdminPanel'?'Панель администратора':'Реквизиты организации');
          else flash('Сетевые конфигурации доступны только администратору');
          return false;
        }
        return await original.apply(this,args);
      }catch(error){flash(error?.message||'Не удалось проверить права администратора');return false;}
      finally{busy=false;}
    };
  }
})(window);
