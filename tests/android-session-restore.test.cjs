const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const page=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8');
const block=page.slice(page.indexOf('  const safeCurrentOrder=currentOrderSession'),page.indexOf('  state.deliveryFee = Number(state.deliveryFee || 0);',page.indexOf('  const safeCurrentOrder=currentOrderSession')));
const plain=v=>JSON.parse(JSON.stringify(v));
async function restore(session,projection=null){
 const warnings=[],effects=[],drafts=[];
 const ctx={currentOrderSession:structuredClone(session),state:{untouched:'keep'},MPosCore:{SessionRestore:{prepare:async()=>projection}},markStorageBroken:x=>warnings.push(x),storageSnapshot:plain,storageRecord:(v,k,f={})=>{if(v&&typeof v==='object'&&!Array.isArray(v))return v;if(v!=null)warnings.push('Некорректный формат '+k);return plain(f)},cartTotal:()=>12,validateSplitPaymentDraft:d=>{drafts.push(d);return d?.valid?d:null},print:()=>effects.push('print')};ctx.window=ctx;vm.createContext(ctx);await vm.runInContext('(async()=>{'+block+'})()',ctx);
 const state=plain(ctx.state);delete state.untouched;
 return {state,kitchenPrinted:ctx.__currentOrderKitchenPrinted??false,printedItems:plain(ctx.__currentOrderPrintedItems??[]),warnings,drafts,effects};
}
const cases=[
 {name:'empty cart defaults',session:{items:[]}},
 {name:'complete order and extensions',session:{items:[{productId:'p',qty:2,comment:'Без сахара',extension:{zero:0}},null,3,[]],orderLabel:'Стол 3',orderType:'Доставка',customer:{name:'Анна',phone:'+375',address:'Минск',extension:false},deliveryFee:'2.5',deliveryTariffSelected:true,orderComment:'Позвонить',source:'web',webOrderId:'w1',webOrderStatus:'accepted',kitchenPrinted:true,printedItems:[{id:'p',qty:2,extension:'kept'}],loyaltyPrograms:[{id:'l',custom:0}],loyaltyRedemptions:{l:1},loyaltyCustomerId:'c1'}},
 {name:'falsy fields and strict flag',session:{items:[{qty:0}],orderLabel:0,orderType:false,customer:null,deliveryFee:false,deliveryTariffSelected:1,kitchenPrinted:[],printedItems:{},loyaltyPrograms:null,loyaltyRedemptions:[],loyaltyCustomerId:0}},
 {name:'malformed customer retains warning',session:{items:[],customer:['wrong'],deliveryFee:'0x10'}},
 {name:'empty strings in customer retained',session:{items:[],customer:{name:null,phone:0,address:false},deliveryFee:-2}},
 {name:'missing items leaves state',session:{orderLabel:'Ignored'}},
];
if(process.argv.includes('--write-fixtures'))(async()=>{
 const rows=[];for(const c of cases){const {state,kitchenPrinted,printedItems,warnings}=await restore(c.session);rows.push({...c,expected:{state,kitchenPrinted,printedItems,warnings}})}fs.writeFileSync('tests/fixtures/session-restore.json',JSON.stringify(rows,null,2)+'\n');
})();else{
 const fixtures=JSON.parse(fs.readFileSync('tests/fixtures/session-restore.json','utf8'));
 test('fixtures match actual reviewed session restore block',async()=>{for(const c of fixtures){const {state,kitchenPrinted,printedItems,warnings}=await restore(c.session);assert.deepEqual({state,kitchenPrinted,printedItems,warnings},c.expected,c.name)}});
 test('native projection applies without extra effects and retains the split validator',async()=>{for(const c of fixtures.filter(c=>Array.isArray(c.session.items))){const original=await restore(c.session);const native=await restore(c.session,{...c.expected,restore:true});assert.deepEqual(native,original,c.name);assert.deepEqual(native.effects,[])}});
 test('paid split parts are restored unchanged after native session projection',async()=>{const session={...fixtures[1].session,paymentDraft:{valid:true,parts:[{paid:true,amount:5,method:'card'},{paid:false,amount:7}],totalCents:1200}};const projection={...fixtures[1].expected,restore:true};const actual=await restore(session,projection);assert.deepEqual(actual.state._splitPayments,session.paymentDraft.parts);assert.equal(actual.state._splitPaymentTotalCents,1200);assert.deepEqual(actual.effects,[])});
 test('invalid split draft still warns and keeps loyalty metadata',async()=>{const c=fixtures[1];const actual=await restore({...c.session,paymentDraft:{broken:true}},{...c.expected,restore:true});assert.equal(actual.warnings.at(-1),'Некорректный черновик раздельной оплаты');assert.deepEqual(actual.state.loyaltyPrograms,c.session.loyaltyPrograms)});
 test('native read failure and explicit rollback return to reviewed restore',async()=>{
  const script=fs.readFileSync('app/src/main/assets/pos/native-storage-shadow.js','utf8');const adapter=script.slice(script.indexOf('  mposCore.SessionRestore='),script.indexOf('  mposCore.SplitRecoveryRead='));
  for(const mode of ['failure','rollback','invalid']){let calls=0;const global={MPosNativeSessionRestoreEnabled:mode==='rollback'?false:true};const mposCore={};const context={global,mposCore,request:async()=>{calls++;if(mode==='failure')throw Error('offline bridge');return {ok:true,authoritative:true,restore:true}},requireNative:r=>r};vm.createContext(context);vm.runInContext(adapter,context);assert.equal(await mposCore.SessionRestore.prepare(fixtures[1].session),null);assert.equal(calls,mode==='rollback'?0:1)}
 });
}
