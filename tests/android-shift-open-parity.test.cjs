const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const root='app/src/main/assets/pos/',source=fs.readFileSync(root+'Web/js/features/shifts.js','utf8');
const submit=source.slice(source.indexOf('async function submitOpenShift()'),source.indexOf('function openCashMovementModal('));
const accepted=submit.match(/password!==(['"])(.*?)\1/)[2];
const plain=v=>JSON.parse(JSON.stringify(v));
const history=[{id:'latest',status:'closed',closedAt:100,countedCash:80,custom:{keep:0}},{id:'older',status:'closed',closedAt:50,countedCash:40}];
const staff=[{id:'e1',name:'Кассир',phone:'phone',role:'employee',custom:false},{id:'a1',name:'Администратор',role:'admin'}];
async function reviewed(c){
 const state={shifts:structuredClone(c.input.expectedShifts),employees:structuredClone(c.input.expectedEmployees)},events=[],writes=[];
 const password=c.credential==='accepted'?accepted:c.credential==='spaced'?' '+accepted:c.credential==='caseChanged'?accepted.toLowerCase():c.credential==='wrong'?'synthetic-invalid':'';
 const ctx={state,criticalOperationBusy:false,currentShift:()=>state.shifts.find(s=>s.status==='open')||null,document:{getElementById:id=>({value:id==='sf-employee'?c.input.employeeId:password})},uid:()=>c.input.id,Date:{now:()=>c.input.openedAt},storageSnapshot:plain,commitCriticalStorage:async(_type,value)=>writes.push(plain(value)),closeModal:()=>events.push('close'),render:()=>events.push('render'),sendTelegramShiftOpened:()=>events.push('telegram'),maybeSendMonthlyWarehouseReport:()=>events.push('monthly'),flash:()=>{},console:{error:()=>{}}};vm.createContext(ctx);vm.runInContext(submit,ctx);const ok=await ctx.submitOpenShift();return {ok,shifts:plain(state.shifts),shift:ok?plain(state.shifts.at(-1)):null,events,writes:writes.length};
}
const input=(extra={})=>({version:1,id:'new-shift',openedAt:200,employeeId:'e1',expectedShifts:structuredClone(history),expectedEmployees:structuredClone(staff),...extra});
const cases=[
 {name:'ordinary employee opening uses last counted cash and retains extensions',credential:'wrong',input:input()},
 {name:'administrator exact credential accepted',credential:'accepted',input:input({employeeId:'a1'})},
 {name:'administrator wrong credential rejected',credential:'wrong',input:input({employeeId:'a1'})},
 {name:'administrator blank credential rejected',credential:'blank',input:input({employeeId:'a1'})},
 {name:'administrator whitespace is not trimmed',credential:'spaced',input:input({employeeId:'a1'})},
 {name:'administrator credential remains case sensitive',credential:'caseChanged',input:input({employeeId:'a1'})},
 {name:'first shift starts at zero',credential:'blank',input:input({expectedShifts:[]})},
 {name:'equal close times keep first history entry',credential:'blank',input:input({expectedShifts:[{id:'first',status:'closed',closedAt:100,countedCash:12},{id:'second',status:'closed',closedAt:100,countedCash:34}]})},
 {name:'missing prior counted cash uses zero',credential:'blank',input:input({expectedShifts:[{id:'old',status:'closed',closedAt:100}]})},
 {name:'negative carryover rejected',credential:'blank',input:input({expectedShifts:[{id:'old',status:'closed',closedAt:100,countedCash:-1}]})},
 {name:'existing open shift rejected',credential:'blank',input:input({expectedShifts:[{id:'open',status:'open',employeeId:'e1'}]})},
 {name:'missing selected employee rejected',credential:'blank',input:input({employeeId:'missing'})},
];
(async()=>{
 if(process.argv.includes('--write-fixtures')){const rows=[];for(const c of cases)rows.push({...c,expected:await reviewed(c)});fs.writeFileSync('tests/fixtures/shift-open-command.json',JSON.stringify(rows,null,2)+'\n');}
 else {const rows=JSON.parse(fs.readFileSync('tests/fixtures/shift-open-command.json','utf8'));test('opening fixtures match actual reviewed credential, carryover and post-commit ordering',async()=>{for(const c of rows)assert.deepEqual(await reviewed(c),c.expected,c.name)});test('fixture stores credential categories without credential bytes',()=>{assert.equal(fs.readFileSync('tests/fixtures/shift-open-command.json','utf8').includes(accepted),false)});}
})();
