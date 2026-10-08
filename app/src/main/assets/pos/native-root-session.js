(function(global){
  'use strict';
  const core=global.MPosCore;
  if(!core?.RootStartup)return;
  const originalShift=global.currentShift,originalAdmin=global.currentShiftEmployeeIsAdmin,originalLoad=global.loadAll;
  let model=null,sequence=-1,tail=Promise.resolve();
  const enabled=()=>global.MPosNativeActiveSessionEnabled!==false;
  const copy=value=>value==null?null:JSON.parse(JSON.stringify(value));
  function accept(value){
    if(!value||typeof value.isAdmin!=='boolean'){
      model=null;return;
    }
    if(Array.isArray(value.shifts)&&Array.isArray(value.employees)){
      model=copy(value);
      model.currentShift=Number.isInteger(value.activeShiftIndex)?copy(value.shifts[value.activeShiftIndex]):null;
      model.selectedEmployee=Number.isInteger(value.activeEmployeeIndex)?copy(value.employees[value.activeEmployeeIndex]):null;
    }else if(Object.prototype.hasOwnProperty.call(value,'currentShift')&&Object.prototype.hasOwnProperty.call(value,'selectedEmployee')){
      model=copy(value);
    }else model=null;
  }
  core.RootSession=Object.freeze({
    receive(value,revision){
      if(!Number.isSafeInteger(revision)||revision<=sequence)return;
      sequence=revision;accept(value);
    },
    async begin(){
      if(!enabled())return null;
      model=null;
      return core.RootStartup.execute({operation:'begin'});
    },
    async recovered(ticket){
      const next=await core.RootStartup.execute({operation:'advance',generation:ticket.generation,completed:'recover'});
      if(next.step!=='hydrate')throw Error('Invalid native root startup phase');
      accept(next.rootSession);
      if(!model)throw Error('Native root session unavailable');
      return copy(model);
    },
    async hydrated(ticket){
      const next=await core.RootStartup.execute({operation:'advance',generation:ticket.generation,completed:'hydrate'});
      if(next.step!=='activate')throw Error('Invalid native root startup phase');
    },
    async activated(ticket){
      const next=await core.RootStartup.execute({operation:'advance',generation:ticket.generation,completed:'activate'});
      if(next.step!=='ready')throw Error('Invalid native root startup phase');
    },
    selectedEmployee(){return copy(model?.selectedEmployee);}
  });
  // Compatibility consumers receive Kotlin's decision; no JS search or role calculation on Android.
  global.currentShift=function(){return enabled()?copy(model?.currentShift):originalShift.apply(this,arguments)};
  global.currentShiftEmployeeIsAdmin=function(){return enabled()?model?.isAdmin===true:originalAdmin.apply(this,arguments)};
  // Import and restart use the same native sequence; a rejected load does not poison the next attempt.
  global.loadAll=function(...args){
    const run=tail.then(()=>originalLoad.apply(this,args));
    tail=run.catch(()=>{model=null;});
    return run;
  };
})(window);
