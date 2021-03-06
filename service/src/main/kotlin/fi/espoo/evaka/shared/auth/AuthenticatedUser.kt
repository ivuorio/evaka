// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.auth

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import fi.espoo.evaka.shared.config.Roles
import java.util.UUID

@JsonSerialize(using = AuthenticatedUserJsonSerializer::class)
data class AuthenticatedUser(@JsonInclude val id: UUID, @JsonInclude override val roles: Set<UserRole>) : RoleContainer {
    fun isEndUser() = roles.contains(Roles.END_USER)
    fun isServiceWorker() = roles.contains(Roles.SERVICE_WORKER)
    fun isUnitSupervisor() = roles.contains(Roles.UNIT_SUPERVISOR)
    fun isFinanceAdmin() = roles.contains(Roles.FINANCE_ADMIN)
    fun isAdmin() = roles.contains(Roles.ADMIN)

    companion object {
        val anonymous = AuthenticatedUser(UUID.fromString("00000000-0000-0000-0000-000000000000"), setOf())
    }
}
