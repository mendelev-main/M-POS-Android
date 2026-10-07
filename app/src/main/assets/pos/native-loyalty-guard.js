(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.network,core=global.MPosCore;
  if(!bridge||!core?.LoyaltyEligibility)return;
  const originalApi=global.loyaltyApi,originalGuard=global.revalidateSelectedLoyaltyReward,previousResult=global.__nativeNetworkResult;
  let seq=0;const pending=new Map();
  const enabled=()=>global.MPosNativeLoyaltyGuardEnabled!==false;
  global.__nativeNetworkResult=function(result){
    const entry=pending.get(result?.requestId);
    if(!entry){if(typeof previousResult==='function')previousResult(result);return;}
    pending.delete(result.requestId);entry.cleanup();
    if(result.ok===true&&result.authoritative===true)entry.resolve(result.data);else entry.reject(Error(result.message||'Не удалось проверить программу лояльности'));
  };
  function profile(customerId,signal){
    return new Promise((resolve,reject)=>{
      const requestId='loyalty-profile-'+Date.now()+'-'+(++seq),config=networkConfigFromState();let timer;
      const cancel=(error)=>{if(!pending.delete(requestId))return;cleanup();try{bridge.postMessage({action:'loyaltyProfileCancel',requestId});}catch(_error){}reject(error);};
      const abort=()=>{const error=Error('Операция отменена');error.name='AbortError';cancel(error);};
      const cleanup=()=>{clearTimeout(timer);signal?.removeEventListener?.('abort',abort);};
      if(signal?.aborted){const error=Error('Операция отменена');error.name='AbortError';reject(error);return;}
      pending.set(requestId,{resolve,reject,cleanup});signal?.addEventListener?.('abort',abort,{once:true});
      timer=setTimeout(()=>cancel(Error('Сервер не ответил вовремя')),6000);
      try{if(bridge.postMessage({action:'loyaltyProfile',requestId,backendUrl:config.backendUrl,deviceKey:config.deviceKey,customerId})===false)throw Error('Нет связи с нативным серверным шлюзом');}
      catch(error){pending.delete(requestId);cleanup();reject(error);}
    });
  }
  global.loyaltyApi=function(path,options={}){
    const match=typeof path==='string'&&path.match(/^\/api\/customers\/([^/]+)\/loyalty$/);
    if(!enabled()||!match||Object.keys(options).some(k=>k!=='signal'))return originalApi.apply(this,arguments);
    return profile(decodeURIComponent(match[1]),options.signal);
  };
  global.revalidateSelectedLoyaltyReward=async function(){
    if(!enabled())return originalGuard.apply(this,arguments);
    if(!hasSelectedLoyaltyReward()||!state.customer?.id)return true;
    const customer=state.customer,id=String(customer.id),redemptions=JSON.stringify(state.loyaltyRedemptions||{}),programs=state.loyaltyPrograms;
    const stamp=core.OrderContext?.stamp(),generation=core.OrderContext?.generation();
    const stale=()=>!enabled()||state.customer!==customer||String(state.customer?.id)!==id||JSON.stringify(state.loyaltyRedemptions||{})!==redemptions||state.loyaltyPrograms!==programs||(stamp!==undefined&&core.OrderContext.stamp()!==stamp)||(generation!==undefined&&core.OrderContext.generation()!==generation);
    try{
      const data=await global.loyaltyApi('/api/customers/'+encodeURIComponent(id)+'/loyalty');
      if(stale())return false;
      const input=JSON.parse(JSON.stringify({version:1,redemptions:state.loyaltyRedemptions||{},programs:data.programs||[]},(_key,value)=>typeof value==='number'&&!Number.isFinite(value)?String(value):value));
      const result=await core.LoyaltyEligibility.calculate(input);
      if(stale())return false;
      if(typeof result?.valid!=='boolean')throw Error('invalid loyalty eligibility');
      if(!result.valid)return false;
      state.loyaltyPrograms=data.programs||[];return true;
    }catch(_error){return stale()?false:null;}
  };
})(window);
