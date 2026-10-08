(function(global){
  'use strict';
  const commands=global.MPosCore?.CompanyCommands;
  if(!commands)return;
  const original=global.saveCompanySettings,originalCanEdit=global.canEditCompanySettings,originalOpen=global.openCompanyEditModal;
  const enabled=()=>global.MPosNativeCompanyCommandsEnabled!==false;
  let busy=false,uncertain=false,form=null;
  global.openCompanyEditModal=function(...args){
    if(enabled())form={expected:JSON.parse(JSON.stringify(state.company)),shiftId:currentShift()?.id??null};
    return originalOpen.apply(this,args);
  };
  global.canEditCompanySettings=function(){return enabled()?currentShiftEmployeeIsAdmin():originalCanEdit.apply(this,arguments)};
  global.saveCompanySettings=async function(...args){
    if(!enabled())return original.apply(this,args);
    if(busy)return false;
    if(uncertain){flash("Статус сохранения неизвестен. Перезапустите приложение перед повторной записью");return false;}
    if(!global.canEditCompanySettings()){closeModal();flash('Изменять реквизиты может только администратор при открытой им смене');return false}
    const expected=form?.expected??JSON.parse(JSON.stringify(state.company));
    const fields={};
    for(const key of ['establishmentName','legalName','address','deliveryAddress']){
      const id='company-'+key.replace(/[A-Z]/g,c=>'-'+c.toLowerCase());
      fields[key]=(document.getElementById(id)?.value||'').trim();
    }
    const shiftId=form?form.shiftId:currentShift()?.id??null;
    busy=true;
    try{
      const result=await commands.commit({version:1,expected,fields,shiftId});
      if(!result?.company)throw Error('Не удалось получить сохранённые реквизиты');
      state.company=result.company;form=null;closeModal();render();flash('Реквизиты сохранены');return true;
    }catch(error){
      if(String(error?.message).includes('commit status is uncertain')){uncertain=true;}
      if(uncertain||!['Реквизиты изменились. Откройте форму заново','Изменять реквизиты может только администратор при открытой им смене'].includes(error?.message))markStorageBroken(error);
      flash(uncertain?'Статус сохранения неизвестен. Перезапустите приложение перед повторной записью':error?.message||'Не удалось сохранить реквизиты');return false;
    }finally{busy=false}
  };
})(window);
