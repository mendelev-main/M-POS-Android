(function(global){
 'use strict';
 const pending=new Map();let seq=0;const parity={legacyEvents:0,nativeEvents:0,legacyHash:'',nativeHash:'',matches:0,mismatches:0};
 function request(action,payload){return new Promise((resolve,reject)=>{const requestId='network-'+Date.now()+'-'+(++seq);pending.set(requestId,{resolve,reject});const ok=global.webkit?.messageHandlers?.network?.postMessage({action,requestId,...(payload||{})});if(ok===false){pending.delete(requestId);reject(new Error('Native network bridge unavailable'))}})}
 global.__nativeNetworkResult=function(result){const entry=pending.get(result?.requestId);if(!entry)return;pending.delete(result.requestId);entry.resolve(result)};
 global.__nativeNetworkEvent=function(event){if(event?.type==='shadow-observed'){parity.nativeEvents++;parity.nativeHash=event.hash||'';if(parity.legacyHash&&parity.nativeHash){if(parity.legacyHash===parity.nativeHash)parity.matches++;else parity.mismatches++;}}global.dispatchEvent(new CustomEvent('mpos-native-network-event',{detail:event||{}}))};
 async function sha256(value){if(!global.crypto?.subtle)return '';const bytes=new TextEncoder().encode(String(value||''));const digest=await global.crypto.subtle.digest('SHA-256',bytes);return Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('')}
 async function observeLegacySse(raw){parity.legacyEvents++;parity.legacyHash=await sha256(raw);return parity.legacyHash}
 function parityStatus(){return {...parity,authoritative:false}}
 global.MPosCore=global.MPosCore||{};
 global.MPosCore.Network=Object.freeze({authoritative:false,sseEnabled:false,describe:()=>request('describe'),probe:(backendUrl,deviceKey)=>request('probe',{backendUrl,deviceKey}),startShadowSse:(backendUrl,deviceKey)=>request('startShadowSse',{backendUrl,deviceKey}),stopShadowSse:()=>request('stopShadowSse'),shadowStatus:()=>request('shadowStatus'),observeLegacySse,parityStatus});
})(window);
