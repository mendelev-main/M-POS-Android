(function(global){
  'use strict';
  const core=global.MPosCore,bridge=global.webkit?.messageHandlers?.settingsScreen;
  if(!core?.EmployeeCommands)return;
  const originalSave=global.saveEmployee,originalDelete=global.confirmDeleteEmployee;
  const originalOpen=global.openEmployeeModal,originalToggle=global.toggleEmployeeAdminPassword,originalDeleteForm=global.deleteEmployee;
  const enabled=()=>global.MPosNativeEmployeeCommandsEnabled!==false;
  let sequence=0;
  const pending=new Map();
  function command(input,credentialRequired){
    if(!credentialRequired)return core.EmployeeCommands.commit(input);
    if(!bridge) return Promise.reject(Error('Нативное окно подтверждения недоступно'));
    return new Promise((resolve,reject)=>{
      const requestId='employee-native-'+(++sequence);
      pending.set(requestId,{resolve,reject,timer:null});
      try{if(bridge.postMessage({action:'employeeAuthorize',requestId,theme:global.state?.theme||'light',command:input})===false)throw Error('Нативное окно подтверждения недоступно')}
      catch(e){pending.delete(requestId);reject(e)}
    });
  }
  global.__mposEmployeeCommandResult=result=>{
    const p=pending.get(result?.requestId);if(!p)return;
    if(result.action==='committing'){
      clearTimeout(p.timer);
      p.timer=setTimeout(()=>{pending.delete(result.requestId);p.reject(Error('commit status is uncertain'));},30000);return;
    }
    if(result.action==='retry'){clearTimeout(p.timer);p.timer=null;return}
    pending.delete(result.requestId);clearTimeout(p.timer);
    if(result.uncertain)p.reject(Error('commit status is uncertain'));
    else if(result.cancelled)p.resolve({cancelled:true});else if(result.ok)p.resolve(result);else p.reject(Error(result.message||'Не удалось сохранить сотрудника'));
  };
  function removePasswordField(id,wrapper){
    const node=document.getElementById(id);if(!node)return;
    node.value='';
    (wrapper?document.getElementById(wrapper):node.closest?.('.field'))?.remove?.();
  }
  if(typeof originalOpen==='function')global.openEmployeeModal=function(...args){
    const result=originalOpen.apply(this,args);
    if(enabled())removePasswordField('ef-admin-password','ef-admin-password-wrap');return result;
  };
  if(typeof originalToggle==='function')global.toggleEmployeeAdminPassword=function(...args){if(!enabled())return originalToggle.apply(this,args)};
  if(typeof originalDeleteForm==='function')global.deleteEmployee=function(...args){
    const result=originalDeleteForm.apply(this,args);if(enabled())removePasswordField('employee-delete-password');return result;
  };
  async function execute(operation,id,before,next,credentialRequired,buttonId){
    if(criticalStorageRecoveryPending){flash('Перезапустите M POS для восстановления данных');return false}
    const busyKey=operation==='save'?'_employeeSaveBusy':'_employeeDeleteBusy';
    if(global[busyKey])return false;
    global[busyKey]=true;
    const button=document.getElementById(buttonId);if(button)button.disabled=true;
    try{
      const result=await command({version:1,operation,id,expected:before,next,shiftId:currentShift()?.id??null},credentialRequired);
      if(result?.cancelled)return false;
      state.employees=next;closeModal();render();flash(operation==='delete'?'Сотрудник удалён':id?'Сотрудник сохранён':'Сотрудник добавлен');return true;
    }catch(e){
      if(String(e?.message).includes('commit status is uncertain'))criticalStorageRecoveryPending=true;
      markStorageBroken(e);flash('Не удалось сохранить сотрудника: '+(e?.message||'ошибка сохранения'));return false;
    }finally{global[busyKey]=false;if(button)button.disabled=false}
  }
  global.saveEmployee=function(id=''){
    if(!enabled())return originalSave.apply(this,arguments);
    if(global._employeeSaveBusy)return Promise.resolve(false);
    const name=(document.getElementById('ef-name')?.value||'').trim(),phone=(document.getElementById('ef-phone')?.value||'').trim();
    if(!name){flash('Введите ФИО сотрудника');return Promise.resolve(false)}
    const wantsAdmin=!!document.getElementById('ef-admin')?.checked;
    const before=storageSnapshot(state.employees),index=id?before.findIndex(e=>e.id===id):-1;
    if(id&&index<0){flash('Сотрудник не найден');return Promise.resolve(false)}
    const wasAdmin=index>=0&&before[index].role==='admin',next=storageSnapshot(before);
    if(index>=0)Object.assign(next[index],{name,phone,role:wantsAdmin?'admin':'employee'});
    else next.push({id:uid(),name,phone,role:wantsAdmin?'admin':'employee'});
    return execute('save',id,before,next,wantsAdmin!==wasAdmin,'employee-save-confirm');
  };
  global.confirmDeleteEmployee=function(id){
    if(!enabled())return originalDelete.apply(this,arguments);
    if(global._employeeDeleteBusy||!employeeDeletionAllowed(id)||!state.employees.some(e=>e.id===id))return Promise.resolve(false);
    const before=storageSnapshot(state.employees),next=before.filter(e=>e.id!==id);
    return execute('delete',id,before,next,true,'employee-delete-confirm');
  };
})(window);
