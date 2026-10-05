(function(global){
  'use strict';

  const core=global.MPosCore;
  if(!core?.Catalog)return;

  const MODES=Object.freeze({
    LEGACY:'legacy',
    COMPARE:'compare'
  });

  let mode=MODES.COMPARE;
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
    if(nextMode==='room'){
      throw new Error('M POS native catalog cutover is blocked until physical acceptance');
    }
    if(nextMode!==MODES.LEGACY&&nextMode!==MODES.COMPARE){
      throw new Error('Unknown M POS catalog mode');
    }
    mode=nextMode;
    return mode;
  }

  async function health(){
    if(mode===MODES.COMPARE){
      try{await compare()}catch(error){
        lastComparison={at:Date.now(),ok:false,matches:false,reason:error?.message||String(error)};
      }
    }
    return Object.freeze({
      mode,
      roomCutoverAllowed:false,
      activeSource:'legacy',
      comparison:lastComparison?{...lastComparison}:null
    });
  }

  core.CatalogCutover=Object.freeze({
    modes:MODES,
    get mode(){return mode},
    setMode,
    compare,
    health,
    activeSource(){return 'legacy'},
    roomCutoverAllowed:false
  });

  global.__mposCatalogCutoverHealth=()=>core.CatalogCutover.health();
})(window);
