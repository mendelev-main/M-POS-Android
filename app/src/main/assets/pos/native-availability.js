(function(global){
 'use strict';
 const core=global.MPosCore=global.MPosCore||{},bridge=global.webkit?.messageHandlers?.network,previous=global.__nativeNetworkResult,pendingReplies=new Map();let sequence=0,running=null,next=null;
 const retryKey='mpos_availability_retry_blocked';
 function conservativeGate(){try{global.localStorage.setItem(retryKey,'1')}catch(_){}}
 global.__nativeNetworkResult=function(result){const item=pendingReplies.get(result?.requestId);if(!item){if(typeof previous==='function')previous(result);return}item.finish();item.resolve(result.ok===true&&result.authoritative===true&&result.sent===true)};
 function publishNative(token,body,signal){return new Promise(resolve=>{
  if(!bridge||signal.aborted){resolve(false);return}
  const requestId='availability-'+Date.now()+'-'+(++sequence);let timer;
  const finish=()=>{pendingReplies.delete(requestId);clearTimeout(timer);signal.removeEventListener('abort',cancel)};
  const cancel=()=>{if(!pendingReplies.has(requestId))return;finish();try{bridge.postMessage({action:'availabilityCancel',requestId})}catch(_){}resolve(false)};
  pendingReplies.set(requestId,{resolve,finish});signal.addEventListener('abort',cancel,{once:true});timer=setTimeout(cancel,31000);
  try{if(bridge.postMessage({action:'availabilityPublish',requestId,token,body})===false)throw Error('Native availability unavailable')}catch(_){finish();resolve(false)}
 })}
 async function send(payment){
  if(document.hidden||global._availabilityAppActive===false||!state.loaded||storageBroken||!core.AvailabilityGate)return false;
  conservativeGate();let timeout;
  try{
   let failed=false;const onError=()=>{failed=true},storage=global.PrilavokCore.Storage;
   const [network,previousRevision]=await Promise.all([storage.get('network',null,onError),storage.get('webAvailabilityRevision',0,onError)]);
   if(failed||!network?.backendUrl||!network.deviceKey)return false;
   const prepared=await core.AvailabilityGate.prepare({version:1,id:payment.id,at:Date.now(),network,previous:previousRevision,ids:[...availabilitySettlements]});
   if(!prepared.send)return false;
   const body=prepared.body;body.items.sort((a,b)=>a.externalId.localeCompare(b.externalId));
   await storage.set('webAvailabilityRevision',body.revision);
   if(document.hidden||global._availabilityAppActive===false)return false;
   availabilityController=new AbortController();let ok=false;
   if(global.MPosNativeAvailabilityTransportEnabled===false){
    const ticket=await core.AvailabilityGate.consume({token:prepared.token,body});
    if(availabilityController.signal.aborted)return false;
    timeout=setTimeout(()=>availabilityController?.abort(),30000);
    const response=await global.fetch(ticket.network.backendUrl.replace(/\/+$/,'')+'/api/availability/snapshot',{method:'POST',headers:{'Content-Type':'application/json','X-Device-Key':ticket.network.deviceKey},body:JSON.stringify(ticket.body),cache:'no-store',signal:availabilityController.signal});ok=response.ok;
   }else ok=await publishNative(prepared.token,body,availabilityController.signal);
   if(ok)body.settledWebOrderIds.forEach(id=>availabilitySettlements.delete(id));return ok;
  }catch(_){return false}finally{clearTimeout(timeout);availabilityController=null}
 }
 function paymentCommitted(ids){
  const receipt=state.orders?.[state.orders.length-1];if(!receipt?.id)return Promise.resolve(false);
  (Array.isArray(ids)?ids:[]).map(String).map(id=>id.trim()).filter(Boolean).forEach(id=>availabilitySettlements.add(id));
  next={id:receipt.id};if(running)return running;
  running=(async()=>{let sent=false;while(next){const payment=next;next=null;const ok=await send(payment);if(!ok){next=null;break}sent=true}return sent})().finally(()=>{running=null});return running;
 }
 core.Availability=Object.freeze({paymentCommitted});
 global.publishAvailability=function(ids){return arguments.length>0&&Array.isArray(ids)?paymentCommitted(ids):Promise.resolve(false)};
 global.onAvailabilityAppState=function(active){global._availabilityAppActive=active;if(!active)availabilityController?.abort()};
 global.startAvailabilityRecovery=function(){if(global._availabilityRecoveryStarted)return;global._availabilityRecoveryStarted=true;document.addEventListener('visibilitychange',()=>global.onAvailabilityAppState(!document.hidden))};
})(window);
