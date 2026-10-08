(function(global){
  'use strict';
  const bridge=global.webkit?.messageHandlers?.settingsScreen,core=global.MPosCore;
  if(!bridge||!core?.AdminAccess)return;
  const originalOpen=global.openLoyaltyAdjustment,originalSave=global.saveLoyaltyAdjustment,network=global.webkit?.messageHandlers?.network;
  const previousNetwork=global.__nativeNetworkResult;const verificationRequests=new Map();
  global.__nativeNetworkResult=result=>{
    const p=verificationRequests.get(result?.requestId);if(!p){previousNetwork?.(result);return;}
    verificationRequests.delete(result.requestId);clearTimeout(p.timer);
    if(result.ok&&result.authoritative&&result.program)p.resolve(result.program);else p.reject(Error(result.message||'Не удалось подтвердить актуальный баланс клиента'));
  };
  function verify(customerId,programId){return new Promise((resolve,reject)=>{
    const requestId='loyalty-balance-'+(++sequence),timer=setTimeout(()=>{verificationRequests.delete(requestId);network?.postMessage({action:'loyaltyProfileCancel',requestId});reject(Error('Не удалось подтвердить актуальный баланс клиента'));},6000);
    verificationRequests.set(requestId,{resolve,reject,timer});
    try{if(!network||network.postMessage({action:'loyaltyBalanceVerification',requestId,customerId:String(customerId),programId})===false)throw Error('Нативная проверка баланса недоступна');}
    catch(error){clearTimeout(timer);verificationRequests.delete(requestId);reject(error);}
  });}
  const enabled=()=>global.MPosNativeLoyaltyAuthorizationEnabled!==false;
  let busy=false,sequence=0;const pending=new Map();
  global.openLoyaltyAdjustment=async function(...args){
    let checked=null;
    if(enabled()){
      try{const status=await core.LoyaltyVerification.status({customerId:String(args[0]),programId:args[1]});
        if(status.required)checked=await verify(args[0],args[1]);}
      catch(error){flash(error?.message||'Не удалось проверить баланс');return false;}
    }
    const result=originalOpen.apply(this,args);
    if(checked){const note=document.createElement('div');note.className='settings-note';note.textContent='Актуальный баланс: прогресс '+String(checked.progress??0)+', подарков '+String(checked.rewards??0)+'. Укажите нужную корректировку заново.';document.getElementById('modal-root')?.querySelector('.modal-title')?.after(note);}
    if(enabled()){const field=document.getElementById('la-admin-password');if(field){field.value='';field.closest?.('.field')?.remove?.();}}
    return result;
  };
  global.__mposLoyaltyAuthorizationResult=result=>{
    const p=pending.get(result?.requestId);if(!p)return;
    if(result.action==='committing'||result.action==='retry')return;
    pending.delete(result.requestId);
    if(result.cancelled||result.ok)p.resolve(result);else p.reject(Error(result.message||'Не удалось сохранить корректировку'));
  };
  global.saveLoyaltyAdjustment=async function(customerId,programId){
    if(!enabled())return originalSave.apply(this,arguments);
    if(busy)return false;busy=true;
    try{
      const permission=await core.AdminAccess.check();if(!permission.allowed)throw Error('Требуются права администратора');
      await core.Storage.get('network',null);
      const status=await core.LoyaltyVerification.status({customerId:String(customerId),programId});
      if(status.required){await global.openLoyaltyAdjustment(customerId,programId,'Проверка баланса');return false;}
      const numeric=id=>{const value=Number(document.getElementById(id)?.value);return Number.isFinite(value)?value:null;};
      const command={version:1,operation:'adjust',customerId:String(customerId),programId,progressDelta:numeric('la-progress'),rewardDelta:numeric('la-rewards'),reason:document.getElementById('la-reason')?.value};
      const result=await new Promise((resolve,reject)=>{
        const requestId='loyalty-adjust-'+(++sequence);pending.set(requestId,{resolve,reject});
        try{if(bridge.postMessage({action:'loyaltyAdjustAuthorize',requestId,theme:state.theme||'light',command})===false)throw Error('Нативное окно подтверждения недоступно');}
        catch(error){pending.delete(requestId);reject(error);}
      });
      if(result.cancelled)return false;
      flash('Корректировка сохранена');openCustomerAdminCard(customerId);return true;
    }catch(error){flash(error?.message||'Не удалось сохранить корректировку');return false;}
    finally{busy=false;}
  };
})(window);
