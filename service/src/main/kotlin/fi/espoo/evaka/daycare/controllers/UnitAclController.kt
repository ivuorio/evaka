// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.daycare.controllers

import fi.espoo.evaka.Audit
import fi.espoo.evaka.shared.auth.AccessControlList
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.DaycareAclRow
import fi.espoo.evaka.shared.auth.UserRole.ADMIN
import fi.espoo.evaka.shared.auth.UserRole.STAFF
import fi.espoo.evaka.shared.auth.UserRole.UNIT_SUPERVISOR
import fi.espoo.evaka.shared.auth.deleteDaycareAclRow
import fi.espoo.evaka.shared.auth.getDaycareAclRows
import fi.espoo.evaka.shared.auth.insertDaycareAclRow
import fi.espoo.evaka.shared.db.transaction
import org.jdbi.v3.core.Jdbi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class UnitAclController(private val acl: AccessControlList, private val jdbi: Jdbi) {
    @GetMapping("/daycares/{daycareId}/acl")
    fun getAcl(
        user: AuthenticatedUser,
        @PathVariable daycareId: UUID
    ): ResponseEntity<DaycareAclResponse> {
        Audit.UnitAclRead.log()
        acl.getRolesForUnit(user, daycareId)
            .requireOneOfRoles(ADMIN, UNIT_SUPERVISOR)
        val acls = jdbi.transaction { it.getDaycareAclRows(daycareId) }
        return ResponseEntity.ok(DaycareAclResponse(acls))
    }

    @PutMapping("/daycares/{daycareId}/supervisors/{employeeId}")
    fun insertUnitSupervisor(
        user: AuthenticatedUser,
        @PathVariable daycareId: UUID,
        @PathVariable employeeId: UUID
    ): ResponseEntity<Unit> {
        Audit.UnitAclCreate.log(targetId = daycareId, objectId = employeeId)
        acl.getRolesForUnit(user, daycareId)
            .requireOneOfRoles(ADMIN)
        jdbi.transaction { it.insertDaycareAclRow(daycareId, employeeId, UNIT_SUPERVISOR) }
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/daycares/{daycareId}/supervisors/{employeeId}")
    fun deleteUnitSupervisor(
        user: AuthenticatedUser,
        @PathVariable daycareId: UUID,
        @PathVariable employeeId: UUID
    ): ResponseEntity<Unit> {
        Audit.UnitAclDelete.log(targetId = daycareId, objectId = employeeId)
        acl.getRolesForUnit(user, daycareId)
            .requireOneOfRoles(ADMIN)

        jdbi.transaction { it.deleteDaycareAclRow(daycareId, employeeId, UNIT_SUPERVISOR) }
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/daycares/{daycareId}/staff/{employeeId}")
    fun insertStaff(
        user: AuthenticatedUser,
        @PathVariable daycareId: UUID,
        @PathVariable employeeId: UUID
    ): ResponseEntity<Unit> {
        Audit.UnitAclCreate.log(targetId = daycareId, objectId = employeeId)
        acl.getRolesForUnit(user, daycareId)
            .requireOneOfRoles(ADMIN, UNIT_SUPERVISOR)
        jdbi.transaction { it.insertDaycareAclRow(daycareId, employeeId, STAFF) }
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/daycares/{daycareId}/staff/{employeeId}")
    fun deleteStaff(
        user: AuthenticatedUser,
        @PathVariable daycareId: UUID,
        @PathVariable employeeId: UUID
    ): ResponseEntity<Unit> {
        Audit.UnitAclDelete.log(targetId = daycareId, objectId = employeeId)
        acl.getRolesForUnit(user, daycareId)
            .requireOneOfRoles(ADMIN, UNIT_SUPERVISOR)

        jdbi.transaction { it.deleteDaycareAclRow(daycareId, employeeId, STAFF) }
        return ResponseEntity.noContent().build()
    }
}

data class DaycareAclResponse(val rows: List<DaycareAclRow>)
