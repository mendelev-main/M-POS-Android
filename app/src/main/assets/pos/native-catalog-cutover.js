(function(global){
  'use strict';

  const core=global.MPosCore;
  if(!core?.Catalog)return;

  const MODES=Object.freeze({
    ROOM:'room',
    COMPARE:'compare'
  });

  let mode=MODES.ROOM;
  let lastComparison=null;

  async function compare(){
    const parity=await core.Catalog.parity();
    lastComparison={
      at:Date.now(),
      ok:!!parity?.ok&&parity?.shadowCaughtUp!==false,
      matches:!!parity?.matches&&parity?.shadowCaughtUp!==false,
      legacyProductCount:parity?.legacyProductCount??null,
      nativeProductCount:parity?.nativeProductCount??null,
      legacyCategoryCount:parity?.legacyCategoryCount??null,
      nativeCategoryCount:parity?.nativeCategoryCount??null,
      reason:parity?.reason||parity?.message||null
    };
    return Object.freeze({...lastComparison});
  }

  function setMode(nextMode){
    if(nextMode!==MODES.ROOM&&nextMode!==MODES.COMPARE){
      throw new Error('Legacy catalog authority requires an explicit code rollback');
    }
    mode=nextMode;
    return mode;
  }

  async function health(){
    if(mode===MODES.COMPARE||mode===MODES.ROOM){
      try{await compare()}catch(error){
        lastComparison={at:Date.now(),ok:false,matches:false,reason:error?.message||String(error)};
      }
    }
    return Object.freeze({
      mode,
      roomCutoverAllowed:true,
      activeSource:'room',
      comparison:lastComparison?{...lastComparison}:null
    });
  }

  core.CatalogCutover=Object.freeze({
    modes:MODES,
    get mode(){return mode},
    setMode,
    compare,
    health,
    activeSource(){return 'room'},
    roomCutoverAllowed:true
  });

  global.__mposCatalogCutoverHealth=()=>core.CatalogCutover.health();
})(window);
