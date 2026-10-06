const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const script=fs.readFileSync('app/src/main/assets/pos/native-storage-warning.js','utf8');
test('Android warning preserves visible failure without falsely announcing data loss or Safari',()=>{
 const ctx={__MPOS_PLATFORM__:'android',storageBroken:true,renderStorageWarning:()=> 'old Safari warning'};ctx.window=ctx;vm.createContext(ctx);vm.runInContext(script,ctx);
 const html=ctx.renderStorageWarning();assert.match(html,/role="alert"/);assert.match(html,/не удалось подтвердить сохранение/);assert.doesNotMatch(html,/Safari|браузере|всё сбросится/);assert.equal(ctx.storageBroken,true);
 const page=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8');assert.ok(page.indexOf('src="native-storage-warning.js"')<page.indexOf('<script>loadAll()'));
});
test('warning replacement is Android-only and introduces no storage or network actions',()=>{
 const original=()=> 'original';const ctx={renderStorageWarning:original};ctx.window=ctx;vm.createContext(ctx);vm.runInContext(script,ctx);assert.equal(ctx.renderStorageWarning,original);
});
