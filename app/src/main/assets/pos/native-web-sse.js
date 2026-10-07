(function(global){
 'use strict';
 const BrowserSource=global.EventSource, sessions=new Map();let sequence=0;
 function send(payload){const bridge=global.webkit?.messageHandlers?.network;if(!bridge)throw new Error('Native WEB transport unavailable');if(bridge.postMessage(payload)===false)throw new Error('Native WEB transport unavailable')}
 function configFor(url){if(global.MPosNativeWebSseEnabled===false)return null;const config=typeof networkConfigFromState==='function'?networkConfigFromState():null;if(!config?.backendUrl||!config?.deviceKey)return null;const expected=String(config.backendUrl).replace(/\/+$/,'')+'/api/orders/events?deviceKey='+encodeURIComponent(config.deviceKey);return String(url)===expected?config:null}
 class NativeSource {
  constructor(url,config){this.url=String(url);this.withCredentials=false;this.readyState=0;this.onopen=null;this.onmessage=null;this.onerror=null;this.listeners=new Map();this.sessionId='web-sse-'+Date.now()+'-'+(++sequence);sessions.set(this.sessionId,this);try{send({action:'startWebSse',sessionId:this.sessionId,backendUrl:config.backendUrl,deviceKey:config.deviceKey})}catch(error){queueMicrotask(()=>this.receive({state:'closed'}))}}
  addEventListener(type,listener){if(!listener)return;const list=this.listeners.get(type)||new Set();list.add(listener);this.listeners.set(type,list)}
  removeEventListener(type,listener){this.listeners.get(type)?.delete(listener)}
  close(){if(this.readyState===2)return;this.readyState=2;sessions.delete(this.sessionId);send({action:'stopWebSse',sessionId:this.sessionId})}
  async dispatch(type,event){const handlers=[this['on'+type],...(this.listeners.get(type)||[])];for(const handler of handlers){if(!handler)continue;try{await(typeof handler==='function'?handler.call(this,event):handler.handleEvent(event))}catch(error){console.error('WEB event handler failed')}}}
  async receive(event){if(this.readyState===2)return;if(event.state==='open'){this.readyState=1;await this.dispatch('open',new Event('open'));return}if(event.state==='event'){try{const message=new MessageEvent(event.eventType||'message',{data:event.data,lastEventId:event.lastEventId||'',origin:new URL(this.url).origin});await this.dispatch(message.type,message)}finally{send({action:'webSseAck',sessionId:this.sessionId,sequence:event.sequence})}return}this.readyState=event.state==='closed'?2:0;if(this.readyState===2)sessions.delete(this.sessionId);await this.dispatch('error',new Event('error'))}
 }
 for(const name of ['CONNECTING','OPEN','CLOSED']){const value={CONNECTING:0,OPEN:1,CLOSED:2}[name];Object.defineProperty(NativeSource,name,{value});Object.defineProperty(NativeSource.prototype,name,{value})}
 function Source(url,options){const config=configFor(url);return config?new NativeSource(url,config):new BrowserSource(url,options)}
 Source.prototype=BrowserSource?.prototype||NativeSource.prototype;
 for(const name of ['CONNECTING','OPEN','CLOSED'])Object.defineProperty(Source,name,{value:NativeSource[name]});
 global.EventSource=Source;
 global.addEventListener('mpos-native-network-event',event=>{const detail=event.detail;if(detail?.type==='web-sse')sessions.get(detail.sessionId)?.receive(detail)});
 global.MPosCore=global.MPosCore||{};global.MPosCore.WebSse=Object.freeze({transport:'native',businessHandlers:'reviewed-source'});
})(window);
