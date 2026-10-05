const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const bridge=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/pos/android-bridge.js'),'utf8');

function host({nativeThrows=false,settings=true}={}){
 const sent=[],flashes=[],buttons=[];
 let observer,ready;
 const screen={
  querySelector(selector){return selector==='[data-mpos-diagnostics]'?(buttons[0]||null):null;},
  insertBefore(button){buttons.push(button);}
 };
 const version={textContent:'Версия __MPOS_VERSION__',closest:()=>screen};
 const document={
  body:{},querySelector:()=>settings?version:null,
  addEventListener(name,callback){if(name==='DOMContentLoaded')ready=callback;},
  createElement(){return {setAttribute(){},addEventListener(name,callback){this[name]=callback;}};}
 };
 const window={MPosNative:{postMessage(raw){if(nativeThrows)throw new Error('unavailable');sent.push(JSON.parse(raw));}},flash:text=>flashes.push(text)};
 vm.runInNewContext(bridge,{window,document,MutationObserver:class {constructor(callback){observer=callback;}observe(){}}});
 return {window,sent,flashes,buttons,version,ready,mutate:()=>observer()};
}

test('diagnostic save action posts metadata-only command without affecting existing channels',()=>{
 const h=host();h.ready();h.mutate();h.mutate();
 assert.equal(h.buttons.length,1);
 assert.equal(h.version.textContent,'Версия 0.1.0');
 h.buttons[0].click();
 assert.deepEqual(h.sent[0],{channel:'diagnostics',payload:{action:'export'}});
 for(const channel of ['printer','telegram','photoPicker','backup','settings','storage','network']){
  h.window.webkit.messageHandlers[channel].postMessage({action:'existing',value:42});
  assert.deepEqual(h.sent.at(-1),{channel,payload:{action:'existing',value:42}});
 }
});

test('missing native diagnostics reports failure without throwing or changing business handlers',()=>{
 const h=host({nativeThrows:true});
 assert.equal(h.window.MPosCore.Diagnostics.exportReport(),false);
 assert.equal(h.flashes.length,1);
 assert.equal(typeof h.window.webkit.messageHandlers.printer.postMessage,'function');
});

test('diagnostic button is not added outside the settings screen',()=>{
 const h=host({settings:false});h.ready();h.mutate();
 assert.equal(h.buttons.length,0);
 assert.equal(h.sent.length,0);
});
