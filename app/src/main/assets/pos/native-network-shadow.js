(function(global){
 'use strict';
 const pending=new Map();let seq=0;
 function request(action,payload){return new Promise((resolve,reject)=>{const requestId='network-'+Date.now()+'-'+(++seq);pending.set(requestId,{resolve,reject});const ok=global.webkit?.messageHandlers?.network?.postMessage({action,requestId,...(payload||{})});if(ok===false){pending.delete(requestId);reject(new Error('Native network bridge unavailable'))}})}
 global.__nativeNetworkResult=function(result){const entry=pending.get(result?.requestId);if(!entry)return;pending.delete(result.requestId);entry.resolve(result)};
 global.__nativeNetworkEvent=function(event){global.dispatchEvent(new CustomEvent('mpos-native-network-event',{detail:event||{}}))};
 global.MPosCore=global.MPosCore||{};
 global.MPosCore.Network=Object.freeze({authoritative:false,sseEnabled:false,describe:()=>request('describe'),probe:(backendUrl,deviceKey)=>request('probe',{backendUrl,deviceKey})});
})(window);
