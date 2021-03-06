// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.invoicing.controller

import com.fasterxml.jackson.databind.ObjectMapper
import fi.espoo.evaka.Audit
import fi.espoo.evaka.invoicing.data.deleteDraftInvoices
import fi.espoo.evaka.invoicing.data.getDetailedInvoice
import fi.espoo.evaka.invoicing.data.getHeadOfFamilyInvoices
import fi.espoo.evaka.invoicing.data.paginatedSearch
import fi.espoo.evaka.invoicing.domain.Invoice
import fi.espoo.evaka.invoicing.domain.InvoiceDetailed
import fi.espoo.evaka.invoicing.domain.InvoiceStatus
import fi.espoo.evaka.invoicing.domain.InvoiceSummary
import fi.espoo.evaka.invoicing.service.InvoiceCodes
import fi.espoo.evaka.invoicing.service.InvoiceService
import fi.espoo.evaka.invoicing.service.createAllDraftInvoices
import fi.espoo.evaka.invoicing.service.getInvoiceCodes
import fi.espoo.evaka.invoicing.service.markManuallySent
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.config.Roles
import fi.espoo.evaka.shared.db.transaction
import fi.espoo.evaka.shared.domain.BadRequest
import fi.espoo.evaka.shared.domain.NotFound
import fi.espoo.evaka.shared.utils.parseEnum
import org.jdbi.v3.core.Jdbi
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class InvoiceDistinctiveParams {
    MISSING_ADDRESS
}

enum class InvoiceSortParam {
    HEAD_OF_FAMILY,
    CHILDREN,
    START,
    END,
    SUM,
    STATUS
}

data class InvoiceSearchResult(
    val data: List<InvoiceSummary>,
    val total: Int,
    val pages: Int
)

@RestController
@RequestMapping("/invoices")
class InvoiceController(
    private val service: InvoiceService,
    private val jdbi: Jdbi,
    private val objectMapper: ObjectMapper
) {
    @GetMapping("/search")
    fun searchInvoices(
        user: AuthenticatedUser,
        @RequestParam(required = true) page: Int,
        @RequestParam(required = true) pageSize: Int,
        @RequestParam(required = false) sortBy: InvoiceSortParam?,
        @RequestParam(required = false) sortDirection: SortDirection?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) area: String?,
        @RequestParam(required = false) unit: String?,
        @RequestParam(required = false) distinctions: String?,
        @RequestParam(required = false) searchTerms: String?,
        @RequestParam(required = false) periodStart: String?,
        @RequestParam(required = false) periodEnd: String?
    ): ResponseEntity<InvoiceSearchResult> {
        Audit.InvoicesSearch.log()
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        val maxPageSize = 5000
        if (pageSize > maxPageSize) throw BadRequest("Maximum page size is $maxPageSize")
        val (total, invoices) = jdbi.transaction { h ->
            paginatedSearch(
                h,
                page,
                pageSize,
                sortBy ?: InvoiceSortParam.STATUS,
                sortDirection ?: SortDirection.DESC,
                status?.split(",")?.mapNotNull { parseEnum<InvoiceStatus>(it) } ?: listOf(),
                area?.split(",") ?: listOf(),
                unit?.let { parseUUID(it) },
                distinctions?.split(",")?.mapNotNull { parseEnum<InvoiceDistinctiveParams>(it) } ?: listOf(),
                searchTerms ?: "",
                periodStart?.let { LocalDate.parse(periodStart, DateTimeFormatter.ISO_DATE) },
                periodEnd?.let { LocalDate.parse(periodEnd, DateTimeFormatter.ISO_DATE) }
            )
        }
        val pages =
            if (total % pageSize == 0) total / pageSize
            else (total / pageSize) + 1
        return ResponseEntity.ok(InvoiceSearchResult(invoices, total, pages))
    }

    @PostMapping("/create-drafts")
    fun createDraftInvoices(user: AuthenticatedUser): ResponseEntity<Unit> {
        Audit.InvoicesCreate.log()
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        jdbi.transaction(createAllDraftInvoices(objectMapper))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/delete-drafts")
    fun deleteDraftInvoices(user: AuthenticatedUser, @RequestBody invoiceIds: List<UUID>): ResponseEntity<Unit> {
        Audit.InvoicesDeleteDrafts.log(targetId = invoiceIds)
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        jdbi.transaction { h -> deleteDraftInvoices(h, invoiceIds) }
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/send")
    fun sendInvoices(
        user: AuthenticatedUser,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @RequestParam(required = false) invoiceDate: LocalDate?,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @RequestParam(required = false) dueDate: LocalDate?,
        @RequestBody invoiceIds: List<UUID>
    ): ResponseEntity<Unit> {
        Audit.InvoicesSend.log(targetId = invoiceIds)
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        service.sendInvoices(
            user,
            invoiceIds,
            invoiceDate,
            dueDate
        )
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/send/by-date")
    fun sendInvoicesByDate(
        user: AuthenticatedUser,
        @RequestBody payload: InvoicePayload
    ): ResponseEntity<Unit> {
        Audit.InvoicesSendByDate.log()
        val invoiceIds = service.getInvoiceIds(payload.from, payload.to, payload.areas)
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        service.sendInvoices(
            user,
            invoiceIds,
            payload.invoiceDate,
            payload.dueDate
        )
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/mark-sent")
    fun markInvoicesSent(user: AuthenticatedUser, @RequestBody invoiceIds: List<UUID>): ResponseEntity<Unit> {
        Audit.InvoicesMarkSent.log(targetId = invoiceIds)
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        jdbi.transaction(markManuallySent(user, invoiceIds))
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{uuid}")
    fun getInvoice(user: AuthenticatedUser, @PathVariable uuid: String): ResponseEntity<Wrapper<InvoiceDetailed>> {
        Audit.InvoicesRead.log(targetId = uuid)
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        val parsedUuid = parseUUID(uuid)
        val res = jdbi.transaction { h -> getDetailedInvoice(h, parsedUuid) }
            ?: throw NotFound("No invoice found with given ID ($uuid)")
        return ResponseEntity.ok(Wrapper(res))
    }

    @GetMapping("/head-of-family/{uuid}")
    fun getHeadOfFamilyInvoices(
        user: AuthenticatedUser,
        @PathVariable uuid: String
    ): ResponseEntity<Wrapper<List<Invoice>>> {
        Audit.InvoicesRead.log(targetId = uuid)
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        val parsedUuid = parseUUID(uuid)
        val res = jdbi.transaction { h -> getHeadOfFamilyInvoices(h, parsedUuid) }
        return ResponseEntity.ok(Wrapper(res))
    }

    @PutMapping("/{uuid}")
    fun putInvoice(
        user: AuthenticatedUser,
        @PathVariable uuid: String,
        @RequestBody invoice: Invoice
    ): ResponseEntity<Unit> {
        Audit.InvoicesUpdate.log(targetId = uuid)
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        val parsedUuid = parseUUID(uuid)
        service.updateInvoice(parsedUuid, invoice)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/codes")
    fun getInvoiceCodes(user: AuthenticatedUser): ResponseEntity<Wrapper<InvoiceCodes>> {
        user.requireOneOfRoles(Roles.FINANCE_ADMIN)
        val codes = jdbi.transaction { h -> getInvoiceCodes(h) }
        return ResponseEntity.ok(Wrapper(codes))
    }
}

data class InvoicePayload(val from: LocalDate, val to: LocalDate, val areas: List<String>, val invoiceDate: LocalDate?, val dueDate: LocalDate?)
