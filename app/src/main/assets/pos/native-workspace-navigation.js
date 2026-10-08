(function(global){
  'use strict';
  const commands=global.MPosCore?.WorkspaceNavigation;if(!commands)return;
  const original=global.setTab,originalSearch=global.onSearch;let initialization=null,sequence=0,searchSequence=0;
  const enabled=()=>global.MPosNativeWorkspaceNavigationEnabled!==false;
  const view=()=>({tab:state.tab,search:String(state.search||''),posPath:state.posPath??null,posFolder:state.posFolder||'',editMode:!!state.editMode});
  function initialize(){
    if(!initialization)initialization=commands.execute({version:1,operation:'initialize',...view()}).catch(error=>{initialization=null;throw error;});
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
  let routeTail=Promise.resolve(),routePending=0;
  const previousRoutes=global.MPosCore.WorkspaceRoutes;
  global.MPosCore.WorkspaceRoutes=Object.freeze({hasPending:()=>routePending>0||!!previousRoutes?.hasPending()});
  const routeEnabled=()=>enabled()&&global.MPosNativeWorkspaceRouteEnabled!==false;
  const modalNode=()=>global.document?.getElementById('modal-root')?.firstElementChild;
  const stamp=()=>JSON.stringify([view(),state.paymentPage,global._posFolderModal]);
  for(const [name,route]of [['openPosCategory','openCategory'],['closePosCategory','closeCategory'],['openPosFolder','openFolder'],['toggleEditMode','toggleEdit']]){
    const originalRoute=global[name];if(typeof originalRoute!=='function')continue;
    global[name]=function(...args){
      if(!routeEnabled())return originalRoute.apply(this,args);
      if(routePending>=32)return Promise.resolve(false);
      const originTab=state.tab,originPage=state.paymentPage;routePending++;
      const work=routeTail.then(async()=>{
        if(state.tab!==originTab||state.paymentPage!==originPage)return false;
        if(!routeEnabled())return originalRoute.apply(this,args);
        const before=stamp(),modal=modalNode();let proposal=null,accepted=false,acceptStarted=false,projected=false;
        const stale=()=>before!==stamp()||modal!==modalNode();
        try{
          await initialize();if(stale())return false;
          const prepared=await commands.execute({version:1,operation:'prepareRoute',route,expected:view(),
            value:route==='openCategory'?String(args[0]||''):args[0]??null,folderModal:global._posFolderModal??null});
          proposal=prepared.proposalToken;
          if(stale()||!routeEnabled())return false;
          if(prepared.allowed===false)return false;
          if(typeof proposal!=='string')throw Error('Не удалось подтвердить переход');
          acceptStarted=true;const result=await commands.execute({version:1,operation:'acceptRoute',proposalToken:proposal});accepted=true;
          if(stale()||!routeEnabled())return false;
          if(result.allowed!==true||!result.patch||Object.keys(result.patch).some(k=>!['posPath','posFolder','search','editMode'].includes(k))||!['render','renderFolder','closeModal'].includes(result.effect))throw Error('Некорректный переход');
          if(result.effect==='renderFolder'&&(!result.folderModal||result.folderModal.category!==state.posPath||typeof result.folderModal.id!=='string'))throw Error('Некорректная папка');
          Object.assign(state,result.patch);projected=true;
          if(result.effect==='closeModal')global.closeModal();
          else if(result.effect==='renderFolder'){global._posFolderModal=result.folderModal;global.renderPosFolderModal();}
          else {global.render();if(result.setupDrag===true)setTimeout(global.setupLayoutGridDrag,50);}
          return true;
        }catch(error){if(!stale())global.flash?.('Не удалось открыть раздел. Повторите действие или перезапустите приложение');return false;}
        finally{if(acceptStarted&&!projected)await commands.execute({version:1,operation:'discardRoute',proposalToken:proposal}).catch(()=>{});if(proposal&&!accepted)commands.execute({version:1,operation:'cancelRoute',proposalToken:proposal}).catch(()=>{});}
      }).finally(()=>{routePending--;});
      routeTail=work.catch(()=>false);return work;
    };
  }
})(window);
