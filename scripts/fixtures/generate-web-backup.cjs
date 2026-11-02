// Developer-only fixture generator. Never included in Android assets/runtime.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),vm=require('vm');
const root=path.resolve(__dirname,'../..');
const reference=process.argv[2]||path.resolve(root,'../reference');
const name='app/src/main/assets/pos/Web/js/features/backup.js';
const source=fs.readFileSync(path.join(reference,name),'utf8');
const expected=JSON.parse(fs.readFileSync(path.join(root,'config/reference.json'),'utf8')).files[name];
if(crypto.createHash('sha256').update(source).digest('hex')!==expected) throw Error('Pinned backup source mismatch');
const state={products:[{id:'p',type:'simple',unit:'piece',name:'Синтетический товар',price:100,cost:20,stock:10}],employees:[{id:'cashier',name:'Синтетический кассир',role:'employee'}],shifts:[{id:'s',employeeId:'cashier',status:'open',openingCash:0,openedAt:1}],orders:[],parked:[],receivings:[],suppliers:[],purchaseOrders:[],discounts:[],hallTables:[],bookings:[],inventoryConfig:{enabled:false,frequency:'monthly',productIds:[],lastCompletedAt:null},inventoryHistory:[],inventoryDraft:null,company:{},deliveryRates:[],posNavigation:{version:1,categories:[]},printer:{},theme:'light',telegram:{},network:{},operationalOutbox:[],operationalRevision:0,webEvents:[]};
class FixedDate extends Date {static now(){return 1;}}
const context={state,window:{},Date:FixedDate,storageSnapshot:x=>JSON.parse(JSON.stringify(x)),loadKey:async(k,defaultValue)=>defaultValue,categoryLayoutSnapshot:()=>({categoryOrder:[],categoryColors:{},categorySymbols:{},tiles:[{type:'product',id:'p'}]}),currentOrderSessionSnapshot:()=>({items:[{productId:'p',name:'Синтетический товар',price:100,qty:1}],paymentDraft:{version:1,totalCents:10000,parts:[{method:'cash',amount:40,paid:true,cashGiven:50,change:10},{method:'card',amount:60,paid:false,cashGiven:null,change:null}],updatedAt:1}})};
vm.createContext(context);vm.runInContext(source.slice(0,source.indexOf('function backupFileName')),context);
context.completeBackupData().then(data=>fs.writeFileSync(path.join(root,'app/src/test/resources/backup/web-v13-source-export.json'),JSON.stringify(data,null,2)+'\n')).catch(()=>{process.exitCode=1;});
