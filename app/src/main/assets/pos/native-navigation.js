(function(global){
  'use strict';
  if(!global.MPosCore?.NavigationRead||!global.MPosCore?.Storage)return;
  const enabled=()=>global.MPosNativeNavigationEnabled!==false;
  const products=()=>state.products.map(p=>{const row={};for(const k of ['id','category','sortOrder'])if(Object.prototype.hasOwnProperty.call(p,k))row[k]=p[k];return row;});
  const dataStamp=()=>JSON.stringify([state.posNavigation,categoryLayoutSnapshot(),products()]);
  const viewContext=()=>JSON.stringify([state.posPath,state.posFolder,state.editMode,state.search,global._posFolderModal,global.MPosCore.OrderContext?.generation()]);
  const context=()=>JSON.stringify([viewContext(),document.getElementById('pos-folder-name')?.value,document.getElementById('pos-move-folder')?.value]);
  const busy=()=>(typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending)||state.busy||(typeof criticalOperationBusy!=='undefined'&&criticalOperationBusy)||global.MPosCore.OrderContext?.hasPending()||global.MPosCore.CartOperations?.hasPending()||global.MPosCore.SplitPayments?.hasPending();
  let tail=Promise.resolve(),pending=0;
  global.MPosCore.NativeNavigation=Object.freeze({hasPending:()=>pending>0});
  function enqueue(operation,args,folder){
    if(busy()||pending>=32||(folder&&global._posNavigationBusy))return Promise.resolve(false);
    const origin=context();if(folder)global._posNavigationBusy=true;pending++;
    let writeStarted=false;
    const work=tail.then(async()=>{
      if(!enabled()||busy()||origin!==context())return false;
      const before=dataStamp(),current=context(),view=viewContext(),category=state.posPath,folderContext=global._posFolderModal;
      const navigation=state.posNavigation,layout=categoryLayoutSnapshot();
      const request={version:1,operation,...args,...(folder?{category,navigation,products:products()}:{tiles:state.layoutTiles})};
      const result=await global.MPosCore.NavigationRead.calculate(JSON.parse(JSON.stringify(request,(_key,v)=>typeof v==='number'&&!Number.isFinite(v)?String(v):v)));
      if(!enabled()||busy()||before!==dataStamp()||current!==context())return false;
      if(typeof result?.allowed!=='boolean')throw Error('invalid navigation decision');
      if(!result.allowed){flash((result.formError?'':'Не удалось сохранить раскладку: ')+(result.message||'ошибка'));return false;}
      if(!folder&&result.changed===false)return true;
      if(folder&&(!result.navigation||result.navigation.version!==1||!Array.isArray(result.navigation.categories)))throw Error('invalid navigation document');
      if(!folder&&(!Array.isArray(result.tiles)||typeof result.changed!=='boolean'))throw Error('invalid tiles');
      writeStarted=true;
      await global.MPosCore.Storage.set(folder?'posNavigation':'layout',folder?result.navigation:{...layout,tiles:result.tiles});
      if(before!==dataStamp()){
        if(typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
        flash('Раскладка сохранена. Перезапустите M POS для обновления данных');return false;
      }
      if(folder)state.posNavigation=result.navigation;else state.layoutTiles=result.tiles;
      if(view!==viewContext()){render();return true;}
      const present=global.MPosCore.OrderContext?.present||((action)=>action());
      present(()=>{
        if(folder){closeModal();render();if(folderContext&&posCategoryItems(folderContext.category).some(i=>i.type==='folder'&&i.id===folderContext.id)){global._posFolderModal=folderContext;renderPosFolderModal();}}
        else {render();if(operation==='addTile')openLayoutEditor();else setTimeout(setupLayoutGridDrag,50);}
      });
      if(operation==='removeFolder'&&state.posFolder===args.id){state.posFolder='';render();}
      return true;
    }).catch(error=>{
      if(writeStarted&&String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
      if(writeStarted||origin===context())flash('Не удалось сохранить раскладку: '+(error?.message||'ошибка'));return false;
    }).finally(()=>{pending--;if(folder)global._posNavigationBusy=false;});
    tail=work;return work;
  }
  for(const name of ['savePosFolder','removePosFolder','movePosProduct','reorderPosCategoryTile']){
    const original=global[name];if(typeof original!=='function')continue;
    global[name]=function(id='',value,targetIndex){
      if(!enabled())return original.apply(this,arguments);
      if(!state.editMode||!state.posPath||(name==='reorderPosCategoryTile'&&state.search))return;
      if(name==='savePosFolder'){const node=document.getElementById('pos-folder-name'),text=node?.value||'';return enqueue('saveFolder',{id,name:text,...(!id?{newId:uid()}:{})},true);}
      if(name==='removePosFolder')return enqueue('removeFolder',{id},true).then(()=>{});
      if(name==='movePosProduct')return enqueue('moveProduct',{id,folderId:value||''},true);
      return enqueue('reorder',{type:id,id:value,targetIndex,parentId:global._posFolderModal?.id||state.posFolder||''},true);
    };
  }
  for(const [name,operation]of [['addLayoutTile','addTile'],['removeLayoutTile','removeTile']]){
    const original=global[name];global[name]=function(a,b){if(!enabled())return original.apply(this,arguments);return enqueue(operation,operation==='addTile'?{type:a,id:b}:{index:a},false);};
  }
  const pointerDown=global.onLayoutPointerDown;global.onLayoutPointerDown=function(e){if(enabled()&&pending)return;return pointerDown.apply(this,arguments);};
  const pointerUp=global.onLayoutPointerUp;
  global.onLayoutPointerUp=function(e){
    const d=typeof layoutDragState!=='undefined'?layoutDragState:null;
    if(!enabled()||!d||d.category||!d.dragging||d.pointerId!==e.pointerId||e.type==='pointercancel')return pointerUp.apply(this,arguments);
    const target=d.target||pointerToCell(d.grid,e.clientX,e.clientY),cols=gridMetrics(d.grid).cols;
    const args={index:d.index,col:target.col,row:target.row,cols};
    // Reviewed handler removes drag clone/capture; native command owns the root mutation.
    pointerUp.call(this,{pointerId:e.pointerId,clientX:e.clientX,clientY:e.clientY,type:'pointercancel',preventDefault:()=>e.preventDefault()});
    return enqueue('moveTile',args,false);
  };
})(window);
