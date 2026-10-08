const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const repo=path.resolve(__dirname,'..');
const sha=s=>require('node:crypto').createHash('sha256').update(s).digest('hex');

test('source refresh preserves Android adapters and refuses changed initialization before copying',()=>{
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'mpos-source-sync-'));
 try{
  const source=path.join(temp,'source'),ios=path.join(source,'PrilavokPOS');
  const work=path.join(temp,'work'),target=path.join(work,'app/src/main/assets/pos');
  fs.mkdirSync(ios,{recursive:true});fs.mkdirSync(target,{recursive:true});fs.mkdirSync(path.join(work,'scripts'));
  const run=(command,args)=>{const result=spawnSync(command,args,{encoding:'utf8'});assert.equal(result.status,0,result.stderr);return result;};
  run('git',['-C',source,'init','-q']);
  run('git',['-C',source,'-c','user.name=M POS test','-c','user.email=test@example.invalid','commit','-q','--allow-empty','-m','Fixture']);
  const asset=path.join(repo,'app/src/main/assets/pos');
  const html=fs.readFileSync(path.join(asset,'pos.html'),'utf8');
  const adapters=['android-bridge.js','native-storage-shadow.js','native-catalog-cutover.js','native-network-shadow.js','native-settings.js','native-platform-settings.js','native-settings-ui.js','native-connection-tests.js','native-workspace.js','native-workspace-header.js','native-layout-ui.js','native-overlay-lifecycle.js','native-print-jobs.js','native-availability.js','native-receipts-history.js','native-payment-command.js','native-configured-prices.js','native-payment-preflight.js','native-cart-totals.js','native-cart-preview.js','native-order-context-queue.js','native-delivery.js','native-order-settings.js','native-parked-command.js','native-catalog-edit.js','native-recipe-edit.js','native-navigation.js','native-workspace-navigation.js','native-system-back.js','native-employee-command.js','native-company-command.js','native-catalog-delete.js','native-editor-authorization.js','native-product-web.js','native-admin-settings.js','native-admin-access.js','native-loyalty-authorization.js','native-customer-command.js','native-loyalty-guard.js','native-loyalty-outbox.js','native-web-journal.js','native-web-sse.js','native-catalog-transport.js','native-supplier-command.js','native-purchase-command.js','native-receiving-command.js','native-receiving-draft.js','native-inventory-command.js','native-warehouse.js','native-analytics.js','native-hall-command.js','native-split-count.js','native-split-amount.js','native-split-recovery.js','native-shift-accounting.js','native-return-command.js','native-cash-movement-command.js','native-shift-lifecycle-command.js','native-shift-reports.js','native-shift-screen.js','native-storage-warning.js','native-cash-forms.js','native-close-form.js','native-open-form.js','native-card-confirmation.js','native-split-cash.js','native-cash-payment.js'];
  let reference=html;
  for(const name of [...adapters,'notification-native.js'])reference=reference.replace(`<script src="${name}"></script>\n`,'');
  reference=reference.replace('<script>loadAll().then(()=>startAvailabilityRecovery());</script>\n','').replace('<script src="network-printer.js"></script>','<script>loadAll().then(()=>startAvailabilityRecovery());</script>\n<script src="network-printer.js"></script>');
  fs.writeFileSync(path.join(ios,'pos.html'),reference.replaceAll('if(!await saveNetworkSettings()) return;','if(!saveNetworkSettings()) return;'));
  fs.cpSync(path.join(asset,'Web'),path.join(ios,'Web'),{recursive:true});
  const productSource=path.join(ios,'Web/js/features/product-persistence.js');
  fs.writeFileSync(productSource,fs.readFileSync(productSource,'utf8').replace("await (window.MPosCore?.ProductEditorCommands?.enabled() ? window.MPosCore.ProductEditorCommands.commit({version:1,editingId:editingId??null,expected:storageSnapshot(state.products),nextProducts,grants:window.MPosCore.EditorAuthorization?.grants()||{}}) : window.PrilavokCore.Storage.set('products',nextProducts));window.MPosCore?.EditorAuthorization?.clear();","await window.PrilavokCore.Storage.set('products',nextProducts);").replace("    if(String(e?.message).includes('commit status is uncertain')){criticalStorageRecoveryPending=true;markStorageBroken(e);}"+'\n',''));
  for(const name of ['network-printer.js','notification-native.js'])fs.copyFileSync(path.join(asset,name),path.join(ios,name));
  for(const name of adapters)fs.copyFileSync(path.join(asset,name),path.join(target,name));
  const script=path.join(work,'scripts/sync-pos-assets.py');fs.copyFileSync(path.join(repo,'scripts/sync-pos-assets.py'),script);
  for(let attempt=0;attempt<2;attempt++){
   run('python',[script,source]);
   assert.equal(sha(fs.readFileSync(path.join(target,'pos.html'),'utf8')),sha(html));
   assert.deepEqual(fs.readFileSync(path.join(target,'Web/js/features/product-persistence.js')),fs.readFileSync(path.join(asset,'Web/js/features/product-persistence.js')));
   for(const name of adapters)assert.deepEqual(fs.readFileSync(path.join(target,name)),fs.readFileSync(path.join(asset,name)));
  }
  const marker=path.join(target,'Web/keep-on-failure.txt');fs.writeFileSync(marker,'preserve');
  fs.writeFileSync(path.join(ios,'pos.html'),reference.replace('loadAll().then(()=>startAvailabilityRecovery())','loadAll()'));
  const rejected=spawnSync('python',[script,source],{encoding:'utf8'});
  assert.notEqual(rejected.status,0);
  assert.match(rejected.stderr,/initialization changed/);
  assert.equal(fs.readFileSync(marker,'utf8'),'preserve');
  assert.equal(sha(fs.readFileSync(path.join(target,'pos.html'),'utf8')),sha(html));
 }finally{fs.rmSync(temp,{recursive:true,force:true});}
});
