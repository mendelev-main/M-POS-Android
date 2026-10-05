(function(global){
  'use strict';
  // Android policy: a failed/interrupted attempt can only be retried by a new durable payment.
  // In the reviewed runtime only payment.js supplies an explicit array argument, even for a non-WEB sale.
  // All other stock/manual-sync triggers call publishAvailability() with no arguments.
  const retryKey='mpos_availability_retry_blocked';
  let blocked=true;
  try{blocked=global.localStorage.getItem(retryKey)==='1';}catch(_){}
  let pending=false,running=null;

  function setBlocked(value){
    blocked=value;
    try{global.localStorage.setItem(retryKey,value?'1':'0');}catch(_){}
  }

  function request(ids,paymentCommitted){
    (Array.isArray(ids)?ids:[]).map(String).map(id=>id.trim()).filter(Boolean)
      .forEach(id=>availabilitySettlements.add(id));
    if(paymentCommitted)setBlocked(false);
    if(blocked&&!running)return Promise.resolve(false);
    pending=true;
    if(running)return running;
    running=(async()=>{
      let sent=false;
      while(pending){
        pending=false;
        // Persist before attempting, so a process exit cannot turn an interrupted send into an automatic retry.
        setBlocked(true);
        let ok=false;
        try{ok=await sendAvailabilitySnapshot();}catch(_){}
        if(!ok){pending=false;setBlocked(true);break;}
        sent=true;
        setBlocked(false);
      }
      return sent;
    })().finally(()=>{running=null;});
    return running;
  }

  global.MPosCore=global.MPosCore||{};
  global.MPosCore.Availability=Object.freeze({paymentCommitted:ids=>request(ids,true)});
  global.publishAvailability=function(ids){
    return arguments.length>0&&Array.isArray(ids)
      ?global.MPosCore.Availability.paymentCommitted(ids)
      :request([],false);
  };
  global.onAvailabilityAppState=function(active){
    global._availabilityAppActive=active;
    if(!active)availabilityController?.abort();
  };
  global.startAvailabilityRecovery=function(){
    if(global._availabilityRecoveryStarted)return;
    global._availabilityRecoveryStarted=true;
    document.addEventListener('visibilitychange',()=>global.onAvailabilityAppState(!document.hidden));
  };
})(window);
