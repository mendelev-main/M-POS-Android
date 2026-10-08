(function(global){
  'use strict';
  const commands=global.MPosCore?.WorkspaceNavigation;if(!commands)return;
  const original=global.setTab;let initialization=null,sequence=0;
  const enabled=()=>global.MPosNativeWorkspaceNavigationEnabled!==false;
  function initialize(){
    if(!initialization)initialization=commands.execute({version:1,operation:'initialize',tab:String(state.tab)}).catch(error=>{initialization=null;throw error;});
    return initialization;
  }
  global.setTab=async function(tab){
    if(!enabled()||typeof tab!=='string')return original.apply(this,arguments);
    const request=++sequence;
    try{
      await initialize();
      const result=await commands.execute({version:1,operation:'selectTab',tab});
      if(request!==sequence||!enabled())return false;
      if(typeof result?.snapshot?.tab!=='string'||!Number.isInteger(result.snapshot.revision))throw Error('Некорректное состояние навигации');
      state.tab=result.snapshot.tab;render();return true;
    }catch(error){if(request===sequence)flash(error?.message||'Не удалось переключить раздел');return false;}
  };
})(window);
