// Isolated visual fixture: keep background sync from creating network notices or retry jobs.
// Screen renderers, CSS, icons, calculations and visible form controls stay from pinned Web.
window.queueOperationalSnapshot=()=>{};
window.flushOperationalOutbox=async()=>true;
window.startOperationalSchedule=()=>{};
window.startWebOrderEvents=()=>{};
