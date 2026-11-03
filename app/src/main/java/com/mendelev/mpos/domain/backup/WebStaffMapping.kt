package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.staff.Employee
import com.mendelev.mpos.domain.staff.EmployeeRules

/** Historical snapshot fields stay nullable; a current directory name must not impersonate history. */
data class WebShiftFacts(val id: String,val sourceId: String,val actorId: String?,val actorName: String?,
    val status: String,val openedAt: Long?,val closedAt: Long?,val openingCash: Long?,val countedCash: Long?,
    val expectedCash: Long?,val difference: Long?,val sourcePreviousId: String?,val movements: List<WebCashFacts>,val blockers: Set<String>)
data class WebCashFacts(val sourceId: String?,val position: Int,val type: String,val subtype: String,
    val amount: Long,val timestamp: Long?,val note: String)
data class WebStaffFacts(val employees: List<Employee>,val shifts: List<WebShiftFacts>)

object WebStaffMapping {
    fun map(document: WebBackupDocument): WebStaffFacts {
        val root=document.root.fields
        val employees=WebValues.rows(root["employees"]).map {value->val p=WebValues.obj(value)
            val source=WebIdentity.source(p["id"])
            val employee=Employee(WebIdentity.mapped("employee",source),WebValues.text(p["name"]),WebValues.text(p["phone"]),WebValues.text(p["role"]),1)
            try {EmployeeRules.validate(employee)} catch(_:IllegalArgumentException) {backupBad("employee")}
            employee
        }
        if(employees.map {it.id}.distinct().size!=employees.size) backupBad("identity")
        val employeesById=employees.associateBy {it.id}
        val shifts=WebValues.rows(root["shifts"]).map {value->val p=WebValues.obj(value);val blockers=linkedSetOf<String>()
            val source=WebIdentity.source(p["id"])
            val actor=p["employeeId"]?.takeUnless {it===BackupJson.Nil || it is BackupJson.Str && it.value.isEmpty()}?.let {WebIdentity.mapped("employee",WebIdentity.source(it))}
            val name=WebValues.text(p["employeeName"]).takeIf {it.isNotBlank()}
            val status=WebValues.text(p["status"]);if(status !in setOf("open","closed")) backupBad("shift")
            val opened=WebValues.time(p["openedAt"]);val closed=WebValues.time(p["closedAt"])
            if(opened==null) blockers.add("openedAtUnknown")
            if(status=="open" && closed!=null) backupBad("shift")
            if(status=="closed" && closed==null) blockers.add("closedAtUnknown")
            if(opened!=null && closed!=null && closed<opened) backupBad("shift")
            val opening=WebValues.minor(p["openingCash"]);val counted=WebValues.minor(p["countedCash"])
            val expected=WebValues.minor(p["expectedCash"],true);val difference=WebValues.minor(p["difference"],true)
            if(opening==null) blockers.add("openingCashUnknown")
            if(status=="closed" && counted==null) blockers.add("countedCashUnknown")
            // Pinned Web closes with countedCash only; derive expected later from historical payments/movements.
            if(status=="closed" && expected==null) blockers.add("expectedCashNeedsHistory")
            if(counted!=null && expected!=null && difference!=null && counted.toBigInteger()-expected.toBigInteger()!=difference.toBigInteger()) backupBad("shift")
            if(name==null) blockers.add("historicalActorNameUnknown")
            val employee=employeesById[actor]
            if(employee==null) blockers.add("actorMissing")
            if(status=="open" && employee?.selectable!=true) blockers.add("activeCashierUnavailable")
            val movements=WebValues.rows(p["cashMovements"]).mapIndexed {i,v->val m=WebValues.obj(v)
                val type=WebValues.text(m["type"]);if(type !in setOf("deposit","withdrawal")) backupBad("cashMovement")
                WebCashFacts(m["id"]?.takeUnless {it===BackupJson.Nil}?.let(WebIdentity::source),i,type,WebValues.text(m["subtype"]),
                    WebValues.minor(m["amount"]) ?: backupBad("cashMovement"),WebValues.time(m["timestamp"]),WebValues.text(m["note"]))
            }
            val ids=movements.mapNotNull {it.sourceId};if(ids.distinct().size!=ids.size) backupBad("identity")
            if(movements.any {it.sourceId==null || it.timestamp==null}) blockers.add("movementIdentityOrTimeUnknown")
            WebShiftFacts(WebIdentity.mapped("shift",source),source,actor,name,status,opened,closed,opening,counted,expected,difference,
                p["openingSourceShiftId"]?.takeUnless {it===BackupJson.Nil || it is BackupJson.Str && it.value.isEmpty()}?.let(WebIdentity::source),movements,blockers)
        }
        if(shifts.map {it.id}.distinct().size!=shifts.size || shifts.count {it.status=="open"}>1) backupBad("shift")
        return WebStaffFacts(employees,shifts)
    }
}
