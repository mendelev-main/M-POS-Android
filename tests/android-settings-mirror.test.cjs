const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/pos/native-settings.js'), 'utf8');

function fixture(save) {
  const commands = [];
  const settings = {printers: [{name: 'fixture printer'}], posNotifications: {enabled: true}};
  const window = {
    webkit: {messageHandlers: {settings: {postMessage(command) {commands.push(command); return false;}}}},
    __printerSettingsSnapshot: () => settings,
    savePrinterFromPage: save,
  };
  vm.runInNewContext(source, {window, setTimeout() {}, console});
  return {window, commands, settings};
}

test('native persistence failure cannot fail a completed legacy settings save', () => {
  const {window, commands, settings} = fixture(() => 'saved locally');
  assert.equal(window.savePrinterFromPage(), 'saved locally');
  assert.equal(commands.length, 1);
  assert.equal(commands[0].action, 'replacePlatformSettings');
  const before = JSON.stringify(settings);
  window.__nativeSettingsResult({requestId: commands[0].requestId, ok: false, authoritative: false});
  assert.equal(JSON.stringify(settings), before);
  assert.equal(window.__lastNativeSettingsResult.ok, false);
});

test('failed local platform save is not mirrored', async () => {
  const sync = fixture(() => false);
  assert.equal(sync.window.savePrinterFromPage(), false);
  assert.equal(sync.commands.length, 0);
  const asyncSave = fixture(async () => false);
  assert.equal(await asyncSave.window.savePrinterFromPage(), false);
  assert.equal(asyncSave.commands.length, 0);
});

test('asynchronous local save retains result despite unavailable native bridge', async () => {
  const {window, commands} = fixture(async () => 'saved locally');
  assert.equal(await window.savePrinterFromPage(), 'saved locally');
  assert.equal(commands.length, 1);
  assert.equal(commands[0].settings.posNotifications.enabled, true);
});
