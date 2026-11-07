// Only the isolated reference server injects this file. No production state or endpoints.
(() => {
  const RealDate = Date, fixed = RealDate.parse('2026-11-07T10:00:00+03:00');
  window.Date = class extends RealDate { constructor(...args) {super(...(args.length ? args : [fixed]));} static now(){return fixed;} };
  window.MPosNative = {postMessage(){}};
  // The reference uses its real Android presentation adapters with a no-op native bridge.
})();
