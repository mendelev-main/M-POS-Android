(function(global){
  'use strict';
  const commands=global.MPosCore?.WorkspaceNavigation;if(!commands)return;
  const original=global.setTab,originalSearch=global.onSearch;let initialization=null,sequence=0,searchSequence=0;
  const enabled=()=>global.MPosNativeWorkspaceNavigationEnabled!==false;
  function initialize(){
    if(!initialization)initialization=commands.execute({version:1,operation:'initialize',tab:String(state.tab),search:String(state.search||'')}).catch(error=>{initialization=null;throw error;});
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
  if(typeof originalSearch==='function')global.onSearch=async function(value){
    if(!enabled())return originalSearch.apply(this,arguments);
    const request=++searchSequence,query=String(value||'');
    const context=JSON.stringify([state.tab,state.posPath,state.posFolder,state.editMode,global._posFolderModal]);
    const previousSearch=state.search,receiver=this;
    try{
      await initialize();
      const result=await commands.execute({version:1,operation:'selectSearch',search:query});
      if(request!==searchSequence||!enabled()||previousSearch!==state.search||context!==JSON.stringify([state.tab,state.posPath,state.posFolder,state.editMode,global._posFolderModal]))return false;
      if(typeof result?.snapshot?.search!=='string'||!Number.isInteger(result.snapshot.revision))throw Error('Некорректное состояние поиска');
      // Reviewed filtering remains a projection until native workspace read models replace it.
      originalSearch.call(receiver,result.snapshot.search);return true;
    }catch(error){if(request===searchSequence)flash(error?.message||'Не удалось изменить поиск');return false;}
  };
})(window);
