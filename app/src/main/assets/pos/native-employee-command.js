(function(global){
  'use strict';
  const storage=global.PrilavokCore?.Storage;
  if(!storage||!global.MPosCore?.EmployeeCommands)return;
  let gesture=null;
  const enabled=()=>global.MPosNativeEmployeeCommandsEnabled!==false;
  const facade=Object.freeze({...storage,async set(key,value){
    if(key!=='employees'||!gesture||!enabled())return storage.set(key,value);
    if(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)throw Error('Перезапустите M POS для восстановления данных');
    const command={version:1,...gesture,expected:state.employees,next:value,authorization:'reviewed-handler',shiftId:currentShift()?.id??null};
    try{return await global.MPosCore.EmployeeCommands.commit(JSON.parse(JSON.stringify(command)));}
    catch(error){if(String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;throw error;}
  }});
  global.PrilavokCore.Storage=facade;
  for(const [name,operation]of [['saveEmployee','save'],['confirmDeleteEmployee','delete']]){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(id=''){
      if(!enabled())return original.apply(this,arguments);
      // These reviewed async handlers call set before their first suspension.
      const previous=gesture;gesture={operation,id};
      try{return original.apply(this,arguments);}finally{gesture=previous;}
    };
  }
})(window);
