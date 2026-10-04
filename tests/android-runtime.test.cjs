const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const vm=require('node:vm');

const root=path.resolve(__dirname,'..');
const assets=path.join(root,'app/src/main/assets/pos');
const html=fs.readFileSync(path.join(assets,'pos.html'),'utf8');
const sha=value=>crypto.createHash('sha256').update(value).digest('hex');

test('all bundled JavaScript parses',()=>{
 const files=[];const walk=directory=>fs.readdirSync(directory,{withFileTypes:true}).forEach(entry=>{const file=path.join(directory,entry.name);if(entry.isDirectory())walk(file);else if(file.endsWith('.js'))files.push(file)});walk(assets);
 files.forEach(file=>assert.doesNotThrow(()=>new vm.Script(fs.readFileSync(file,'utf8'),{filename:file}),path.relative(root,file)));
});

test('every local script referenced by POS is bundled',()=>{
 const scripts=[...html.matchAll(/<script src="([^"]+)"/g)].map(match=>match[1]);
 assert.ok(scripts.includes('android-bridge.js'));assert.ok(scripts.includes('notification-native.js'));assert.ok(scripts.includes('native-settings.js'));
 scripts.forEach(script=>assert.ok(fs.existsSync(path.join(assets,script)),script));
});

test('Android bridge preserves all native iPad channels',()=>{
 const bridge=fs.readFileSync(path.join(assets,'android-bridge.js'),'utf8');
 for(const channel of ['printer','telegram','photoPicker','backup','settings'])assert.match(bridge,new RegExp(`${channel}:handler\\('${channel}'\\)`));
 assert.match(bridge,/window\.__MPOS_PLATFORM__|global\.__MPOS_PLATFORM__/);
});

test('Android POS differs from source HTML only by platform scripts',()=>{
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'web-source-manifest.json')));
 const restored=html.replace('<script src="android-bridge.js"></script>\n','').replace('\n<script src="native-settings.js"></script>','').replace('\n<script src="notification-native.js"></script>','');
 assert.equal(sha(restored),manifest.files['pos.html']);
 for(const [file,expected] of Object.entries(manifest.files)){
   if(file==='pos.html')continue;
   assert.equal(sha(fs.readFileSync(path.join(assets,file))),expected,file);
 }
});

test('shell uses trusted local origin and blocks file and cleartext WebView access',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 assert.match(activity,/appassets\.androidplatform\.net/);assert.match(activity,/allowFileAccess = false/);assert.match(activity,/MIXED_CONTENT_NEVER_ALLOW/);assert.match(activity,/WEB_MESSAGE_LISTENER/);
 const manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');assert.match(manifest,/usesCleartextTraffic="false"/);
});

test('native report routes preserve shift printing and monthly Telegram delivery',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 const reports=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/share/ReportShareManager.kt'),'utf8');
 const telegram=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/telegram/TelegramClient.kt'),'utf8');
 assert.match(activity,/"printShiftReport"[\s\S]{0,120}shares::printShiftReport/);
 assert.match(reports,/PrintManager/);
 assert.match(telegram,/"sendMonthlyWarehouseReport"/);
 assert.match(telegram,/sendDocument/);
 assert.match(activity,/onTelegramMonthlyWarehouseResult/);
});


test('native Android settings boundary is isolated from POS business storage',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 const router=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/bridge/NativeBridgeRouter.kt'),'utf8');
 const store=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/settings/NativeSettingsStore.kt'),'utf8');
 const adapter=fs.readFileSync(path.join(assets,'native-settings.js'),'utf8');
 assert.match(activity,/NativeSettingsStore/);
 assert.match(router,/"settings" -> settings\.handle/);
 assert.match(store,/PREFS_NAME\s*=\s*"mpos_native_settings"/);
 assert.match(store,/getSharedPreferences\(PREFS_NAME,\s*Context\.MODE_PRIVATE\)/);
 assert.match(store,/platform_settings_snapshot/);
 assert.match(adapter,/__printerSettingsSnapshot/);
 assert.doesNotMatch(store,/products|orders|shifts|receipts/);
});
