// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.assistanceaction

import fi.espoo.evaka.Audit
import fi.espoo.evaka.daycare.controllers.utils.created
import fi.espoo.evaka.daycare.controllers.utils.noContent
import fi.espoo.evaka.daycare.controllers.utils.ok
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.config.Roles
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class AssistanceActionController(
    private val assistanceActionService: AssistanceActionService
) {
    @PostMapping("/children/{childId}/assistance-actions")
    fun createAssistanceAction(
        user: AuthenticatedUser,
        @PathVariable childId: UUID,
        @RequestBody body: AssistanceActionRequest
    ): ResponseEntity<AssistanceAction> {
        Audit.ChildAssistanceActionCreate.log(targetId = childId)
        user.requireOneOfRoles(Roles.SERVICE_WORKER, Roles.UNIT_SUPERVISOR)
        return assistanceActionService.createAssistanceAction(
            user = user,
            childId = childId,
            data = body
        ).let { created(it, URI.create("/children/$childId/assistance-actions/${it.id}")) }
    }

    @GetMapping("/children/{childId}/assistance-actions")
    fun getAssistanceActions(
        user: AuthenticatedUser,
        @PathVariable childId: UUID
    ): ResponseEntity<List<AssistanceAction>> {
        Audit.ChildAssistanceActionRead.log(targetId = childId)
        user.requireOneOfRoles(Roles.SERVICE_WORKER, Roles.UNIT_SUPERVISOR, Roles.FINANCE_ADMIN)
        return assistanceActionService.getAssistanceActionsByChildId(childId).let(::ok)
    }

    @PutMapping("/assistance-actions/{id}")
    fun updateAssistanceAction(
        user: AuthenticatedUser,
        @PathVariable id: UUID,
        @RequestBody body: AssistanceActionRequest
    ): ResponseEntity<AssistanceAction> {
        Audit.ChildAssistanceActionUpdate.log(targetId = id)
        user.requireOneOfRoles(Roles.SERVICE_WORKER, Roles.UNIT_SUPERVISOR)
        return assistanceActionService.updateAssistanceAction(
            user = user,
            id = id,
            data = body
        ).let(::ok)
    }

    @DeleteMapping("/assistance-actions/{id}")
    fun deleteAssistanceAction(
        user: AuthenticatedUser,
        @PathVariable id: UUID
    ): ResponseEntity<Unit> {
        Audit.ChildAssistanceActionDelete.log(targetId = id)
        user.requireOneOfRoles(Roles.SERVICE_WORKER, Roles.UNIT_SUPERVISOR)
        assistanceActionService.deleteAssistanceAction(id)
        return noContent()
    }
}
