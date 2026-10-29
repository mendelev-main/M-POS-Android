package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.staff.*
import java.time.Clock
import java.util.UUID

/** Missing document means a new, empty directory; never materialize the legacy owner as an employee. */
data class EmployeeSnapshot(val ownerId: String,val employees: List<Employee>,val activeActorId: String?)
interface EmployeeAccess {
    suspend fun snapshot(): EmployeeSnapshot
    suspend fun commit(command: EmployeeCommand): String
    suspend fun outcome(command: EmployeeCommand): String?
}
class EmployeeRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC(),
    private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null): EmployeeAccess {
    internal suspend fun rows(): List<Employee> =db.workspace().document(EmployeeCodec.KEY)?.let {EmployeeCodec.decode(it.payload)}.orEmpty()
    override suspend fun snapshot(): EmployeeSnapshot {
        CatalogRepository(db).initialize()
        return db.withTransaction {
            val active=db.shifts().active();check(active.size<=1) {"Нарушен журнал смен"}
            EmployeeSnapshot(db.catalog().config()!!.ownerId,rows().filterNot {it.deleted}.sortedWith(compareBy({CatalogRules.key(it.name)},{it.id})),active.singleOrNull()?.actorId)
        }
    }
    override suspend fun outcome(command: EmployeeCommand): String? {
        val c=command.validated()
        return db.catalog().result(c.commandId)?.let {
            if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
            it.resultId
        }
    }
    override suspend fun commit(command: EmployeeCommand): String {
        val c=command.validated()
        val result=InstallationGate.write {db.adminTransaction {
            val config=db.catalog().config() ?: error("Хранилище не открыто")
            if(config.ownerId!=c.actorId) throw CatalogFailure("owner","Недопустимый автор изменения сотрудников")
            outcome(c)?.let {return@adminTransaction it}
            val doc=db.workspace().document(EmployeeCodec.KEY)
            val active=db.shifts().active();check(active.size<=1) {"Нарушен журнал смен"}
            val next=EmployeeMutations.apply(rows(),c,active.singleOrNull()?.actorId)
            val target=when(val a=c.action) {is EmployeeAction.Save->a.id;is EmployeeAction.Delete->a.id}
            val now=clock.millis();require(now>=0)
            db.workspace().put(WorkspaceDocument(EmployeeCodec.KEY,Math.addExact(doc?.revision ?: 0,1),EmployeeCodec.encode(next)));afterWrite?.invoke()
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),target));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,if(c.action is EmployeeAction.Save) "employee.save" else "employee.delete",target,now));afterWrite?.invoke()
            target
        }}
        afterCommit?.invoke();return result
    }
    /** Resolve at shift commit; UI identity and role are never trusted. */
    internal suspend fun cashier(id: String): Employee =rows().singleOrNull {it.id==id && it.selectable}
        ?: throw CatalogFailure("employee","Для новой смены создайте и выберите сотрудника с ролью кассира")
}
