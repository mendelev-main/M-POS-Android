(function(global){
  'use strict';
  const core=global.MPosCore,queue=core?.OrderContext;
  if(!queue||!core.CustomerContext||!core.Storage)return;
  const originals={selectCustomer:global.selectCustomer,removeOrderCustomer:global.removeOrderCustomer,loadCustomerLoyalty:global.loadCustomerLoyalty};
  const enabled=()=>global.MPosNativeCustomerCommandsEnabled!==false;
  const blocked=()=>typeof criticalStorageRecoveryPending!=='undefined'&&criticalStorageRecoveryPending;
  function apply(operation,details,guard=()=>true){
    const generation=queue.generation();
    return queue.enqueue(async()=>{
      const before=queue.stamp();let writing=false;
      const stale=()=>!enabled()||blocked()||generation!==queue.generation()||before!==queue.stamp()||!guard();
      if(stale())return false;
      try{
        const result=await core.CustomerContext.calculate({version:1,operation,session:currentOrderSessionSnapshot(),...details});
        if(stale())return false;
        const session=result?.session;if(!session||!session.customer)throw Error('invalid customer context');
        writing=true;await core.Storage.set('currentOrderSession',session);
        if(before!==queue.stamp()){
          if(typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
          throw Error('Заказ изменился во время сохранения');
        }
        queue.commit(()=>{state.customer=session.customer;state.loyaltyPrograms=session.loyaltyPrograms;state.loyaltyRedemptions=session.loyaltyRedemptions;state.loyaltyCustomerId=session.loyaltyCustomerId;state.loyaltyLoadingCustomerId=operation==='select'?String(details.customer.id):'';state.loyaltyLoadError='';});
        queue.present(()=>{if(generation===queue.generation()&&operation!=='profile')closeModal();render();if(operation==='profile')refreshOrderCustomerModal();});
        return true;
      }catch(error){
        if(writing&&String(error?.message).includes('commit status is uncertain')&&typeof criticalStorageRecoveryPending!=='undefined')criticalStorageRecoveryPending=true;
        if(writing&&typeof markStorageBroken==='function')markStorageBroken(error);
        flash('Не удалось сохранить клиента заказа: '+(error?.message||'ошибка'));return false;
      }
    });
  }
  global.selectCustomer=async function(id,name,phone){
    if(!enabled())return originals.selectCustomer.apply(this,arguments);
    if(await apply('select',{customer:{id,name,phone}})){
      const selected=state.customer;
      await global.loadCustomerLoyalty(id).catch(()=>{if(state.customer===selected){flash('Клиент выбран, но loyalty недоступна');render();}});
    }
  };
  global.removeOrderCustomer=function(){
    if(!enabled())return originals.removeOrderCustomer.apply(this,arguments);
    ++customerSearchSeq;return apply('remove',{});
  };
  global.loadCustomerLoyalty=async function(customerId){
    if(!enabled()||!customerId)return originals.loadCustomerLoyalty.apply(this,arguments);
    const selected=state.customer,id=String(customerId);
    const matches=()=>state.customer===selected&&String(state.customer?.id)===id;
    state.loyaltyLoadingCustomerId=id;state.loyaltyLoadError='';refreshOrderCustomerModal();
    try{
      const data=await loyaltyApi('/api/customers/'+encodeURIComponent(customerId)+'/loyalty');
      if(!matches())return;
      const saved=await apply('profile',{data,customerId:id},matches);
      if(!saved&&matches()){state.loyaltyLoadingCustomerId='';state.loyaltyLoadError='Не удалось сохранить данные клиента';refreshOrderCustomerModal();}
    }catch(error){
      if(matches()){state.loyaltyLoadingCustomerId='';state.loyaltyLoadError=String(error?.message||error);refreshOrderCustomerModal();}
      throw error;
    }
  };
})(window);
