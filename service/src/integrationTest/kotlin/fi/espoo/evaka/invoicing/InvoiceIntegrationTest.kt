// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.invoicing

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.jsonBody
import fi.espoo.evaka.FullApplicationTest
import fi.espoo.evaka.insertGeneralTestFixtures
import fi.espoo.evaka.invoicing.controller.InvoiceSearchResult
import fi.espoo.evaka.invoicing.controller.InvoiceSortParam
import fi.espoo.evaka.invoicing.controller.SortDirection
import fi.espoo.evaka.invoicing.controller.Wrapper
import fi.espoo.evaka.invoicing.data.getInvoice
import fi.espoo.evaka.invoicing.data.getInvoicesByIds
import fi.espoo.evaka.invoicing.data.getMaxInvoiceNumber
import fi.espoo.evaka.invoicing.data.paginatedSearch
import fi.espoo.evaka.invoicing.data.searchInvoices
import fi.espoo.evaka.invoicing.data.upsertFeeDecisions
import fi.espoo.evaka.invoicing.data.upsertInvoices
import fi.espoo.evaka.invoicing.domain.FeeDecisionStatus
import fi.espoo.evaka.invoicing.domain.FeeDecisionType
import fi.espoo.evaka.invoicing.domain.InvoiceDetailed
import fi.espoo.evaka.invoicing.domain.InvoiceStatus
import fi.espoo.evaka.invoicing.domain.InvoiceSummary
import fi.espoo.evaka.invoicing.domain.Product
import fi.espoo.evaka.invoicing.integration.InvoiceIntegrationClient
import fi.espoo.evaka.invoicing.integration.fallbackPostOffice
import fi.espoo.evaka.invoicing.integration.fallbackPostalCode
import fi.espoo.evaka.invoicing.integration.fallbackStreetAddress
import fi.espoo.evaka.resetDatabase
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.asUser
import fi.espoo.evaka.shared.config.Roles
import fi.espoo.evaka.shared.db.handle
import fi.espoo.evaka.shared.dev.DevCareArea
import fi.espoo.evaka.shared.dev.DevDaycare
import fi.espoo.evaka.shared.dev.insertTestCareArea
import fi.espoo.evaka.shared.dev.insertTestDaycare
import fi.espoo.evaka.shared.domain.Period
import fi.espoo.evaka.svebiTestCode
import fi.espoo.evaka.svebiTestId
import fi.espoo.evaka.testAdult_1
import fi.espoo.evaka.testAdult_2
import fi.espoo.evaka.testAreaCode
import fi.espoo.evaka.testChild_1
import fi.espoo.evaka.testChild_2
import fi.espoo.evaka.testDaycare
import fi.espoo.evaka.testDecisionMaker_1
import fi.espoo.evaka.testSvebiDaycare
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

class InvoiceIntegrationTest : FullApplicationTest() {
    @Autowired
    lateinit var integrationClient: InvoiceIntegrationClient

    private fun assertEqualEnough(expected: List<InvoiceSummary>, actual: List<InvoiceSummary>) {
        assertEquals(
            expected.map { it.copy(sentAt = null) }.toSet(),
            actual.map { it.copy(sentAt = null) }.toSet()
        )
    }

    private fun deserializeListResult(json: String) = objectMapper.readValue<InvoiceSearchResult>(json)
    private fun deserializeResult(json: String) = objectMapper.readValue<Wrapper<InvoiceSummary>>(json)

    private val testInvoices = listOf(
        createInvoiceFixture(
            status = InvoiceStatus.DRAFT,
            headOfFamilyId = testAdult_1.id,
            agreementType = testAreaCode,
            rows = listOf(createInvoiceRowFixture(childId = testChild_1.id))
        ),
        createInvoiceFixture(
            status = InvoiceStatus.SENT,
            headOfFamilyId = testAdult_1.id,
            agreementType = testAreaCode,
            number = 5000000001L,
            period = Period(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31)),
            rows = listOf(createInvoiceRowFixture(childId = testChild_1.id))
        ),
        createInvoiceFixture(
            status = InvoiceStatus.DRAFT,
            headOfFamilyId = testAdult_2.id,
            agreementType = testAreaCode,
            period = Period(LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 31)),
            rows = listOf(createInvoiceRowFixture(childId = testChild_2.id))
        )
    )

    private val testDecisions = listOf(
        createFeeDecisionFixture(
            status = FeeDecisionStatus.DRAFT,
            decisionType = FeeDecisionType.NORMAL,
            headOfFamilyId = testAdult_1.id,
            period = Period(LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6)),
            parts = listOf(
                createFeeDecisionPartFixture(
                    childId = testChild_1.id,
                    dateOfBirth = testChild_1.dateOfBirth,
                    daycareId = testDaycare.id
                ),
                createFeeDecisionPartFixture(
                    childId = testChild_2.id,
                    dateOfBirth = testChild_2.dateOfBirth,
                    daycareId = testDaycare.id,
                    siblingDiscount = 50,
                    fee = 14500
                )
            )
        ),
        createFeeDecisionFixture(
            status = FeeDecisionStatus.SENT,
            decisionType = FeeDecisionType.NORMAL,
            headOfFamilyId = testAdult_1.id,
            period = Period(LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6)),
            parts = listOf(
                createFeeDecisionPartFixture(
                    childId = testChild_2.id,
                    dateOfBirth = testChild_2.dateOfBirth,
                    daycareId = testDaycare.id
                )
            )
        ),
        createFeeDecisionFixture(
            status = FeeDecisionStatus.SENT,
            decisionType = FeeDecisionType.NORMAL,
            headOfFamilyId = testAdult_2.id,
            period = Period(LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6)),
            parts = listOf(
                createFeeDecisionPartFixture(
                    childId = testChild_1.id,
                    dateOfBirth = testChild_1.dateOfBirth,
                    daycareId = testDaycare.id
                )
            )
        )
    )

    private val testUser = AuthenticatedUser(testDecisionMaker_1.id, setOf(Roles.FINANCE_ADMIN))

    @BeforeEach()
    fun beforeEach() {
        jdbi.handle(::insertGeneralTestFixtures)
        jdbi.handle { h ->
            h.insertTestCareArea(
                DevCareArea(
                    id = svebiTestId,
                    name = testSvebiDaycare.areaName,
                    shortName = "svenska-bildningstjanster",
                    areaCode = svebiTestCode
                )
            )
            h.insertTestDaycare(
                DevDaycare(
                    areaId = svebiTestId,
                    id = testSvebiDaycare.id,
                    name = testSvebiDaycare.name
                )
            )
        }
    }

    @AfterEach
    fun afterEach() {
        jdbi.handle(::resetDatabase)
        (integrationClient as InvoiceIntegrationClient.MockClient).sentBatches.clear()
    }

    @Test
    fun `search works with draft status parameter`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val drafts = testInvoices.filter { it.status === InvoiceStatus.DRAFT }.sortedBy { it.dueDate }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&status=DRAFT")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            drafts.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works with sent status parameter`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sent = testInvoices.filter { it.status === InvoiceStatus.SENT }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&status=SENT")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            sent.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works with canceled status parameter`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val canceled = testInvoices.filter { it.status === InvoiceStatus.CANCELED }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&status=CANCELED")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            canceled.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works with multiple status parameters`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sentAndCanceled =
            testInvoices.filter { it.status == InvoiceStatus.SENT || it.status == InvoiceStatus.CANCELED }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&status=SENT,CANCELED")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            sentAndCanceled.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works with all status parameters`() {
        val testInvoiceSubset = testInvoices.take(2)
        jdbi.handle { h -> upsertInvoices(h, testInvoiceSubset) }
        val invoices = testInvoiceSubset.sortedBy { it.status }.reversed()

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&status=DRAFT,SENT,CANCELED")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            invoices.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with existing area param`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val invoices = testInvoices.sortedBy { it.status }.reversed()

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&area=test_area")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            invoices.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with area and status params`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val invoices = testInvoices.sortedBy { it.status }.reversed()

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&area=test_area&status=DRAFT")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            invoices.filter { it.status == InvoiceStatus.DRAFT && it.agreementType == testAreaCode }.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with non existant area param`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&area=non_existent")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            listOf(),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with multiple partial search terms`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, result) = http.get(
            "/invoices/search?page=1&pageSize=200&searchTerms=${testAdult_1.streetAddress} ${testAdult_1.firstName.substring(
                0,
                2
            )}"
        )
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            testInvoices.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with multiple more specific search terms`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, result) = http.get(
            "/invoices/search?page=1&pageSize=200&searchTerms=${testAdult_1.lastName.substring(
                0,
                2
            )} ${testAdult_1.firstName}"
        )
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            testInvoices.take(2).map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with multiple search terms where one does not match anything`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&searchTerms=${testAdult_1.lastName} ${testAdult_1.streetAddress} nomatch")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            listOf(),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with child name as search term`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&searchTerms=${testChild_2.firstName}")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            testInvoices.takeLast(1).map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with ssn as search term`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=200&searchTerms=${testAdult_1.ssn}")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            testInvoices.take(2).map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search works as expected with date of birth as search term`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, result) = http.get(
            "/invoices/search?page=1&pageSize=200&searchTerms=${testAdult_1.ssn!!.substring(
                0,
                6
            )}"
        )
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            testInvoices.take(2).map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search with pageSize 1 will find only one result`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sent = listOf(testInvoices.sortedWith(compareBy({ it.periodStart }, { it.id })).first())

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=1&sortBy=START&sortDirection=ASC")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            sent.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search with pageSize 1 and pageNumber 2 will find the second result`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sent = listOf(testInvoices.sortedWith(compareBy({ it.periodStart }, { it.id }))[1])

        val (_, response, result) = http.get("/invoices/search?page=2&pageSize=1&sortBy=START&sortDirection=ASC")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            sent.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search with pageSize 2 and pageNumber 1 will find first two results`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sent = testInvoices.sortedWith(compareBy({ it.periodStart }, { it.id })).subList(0, 2)

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=2&sortBy=START&sortDirection=ASC")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            sent.map(::toSummary),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `search gives correct total and page composition when using filters`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sent = testInvoices
            .filter { it.status == InvoiceStatus.DRAFT }
            .sortedBy { it.periodStart }
            .reversed()

        val (_, response, result) = http.get("/invoices/search?page=1&pageSize=2&status=DRAFT&sortBy=START&sortDirection=DESC")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEquals(2, deserializeListResult(result.get()).total)
        assertEqualEnough(
            sent.map(::toSummary).take(2),
            deserializeListResult(result.get()).data
        )
    }

    @Test
    fun `getInvoice works with existing invoice`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val invoice = testInvoices[0]

        val (_, response, result) = http.get("/invoices/${invoice.id}")
            .asUser(testUser)
            .responseString()
        assertEquals(200, response.statusCode)

        assertEqualEnough(
            listOf(invoice.let(::toSummary)),
            listOf(deserializeResult(result.get()).data)
        )
    }

    @Test
    fun `getInvoice returns not found with non-existant invoice`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }

        val (_, response, _) = http.get("/invoices/00000000-0000-0000-0000-000000000000")
            .asUser(testUser)
            .responseString()
        assertEquals(404, response.statusCode)
    }

    @Test
    fun `send works with draft invoice`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val draft = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(listOf(draft.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)
    }

    @Test
    fun `send returns bad request for sent status invoice`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sent = testInvoices.find { it.status == InvoiceStatus.SENT }!!

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(listOf(sent.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(400, response.statusCode)
    }

    @Test
    fun `send updates invoice status and number and sent fields`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val draft = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(listOf(draft.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val (_, _, result) = http.get("/invoices/${draft.id}")
            .asUser(testUser)
            .responseString()

        val updated = draft.copy(status = InvoiceStatus.SENT, number = 5000000002L, sentBy = testDecisionMaker_1.id)

        assertEqualEnough(
            listOf(updated.let(::toSummary)),
            listOf(deserializeResult(result.get()).data)
        )

        objectMapper.readValue<Wrapper<InvoiceDetailed>>(result.get()).let {
            assertEquals(InvoiceStatus.SENT, it.data.status)
            assertEquals(testDecisionMaker_1.id, it.data.sentBy)
            assertNotNull(it.data.sentAt)
        }
    }

    @Test
    fun `send sets distinct numbers`() {
        val drafts = (1..5).map { _ ->
            createInvoiceFixture(
                status = InvoiceStatus.DRAFT,
                headOfFamilyId = testAdult_1.id,
                agreementType = testAreaCode,
                rows = listOf(createInvoiceRowFixture(childId = testChild_1.id))
            )
        }

        jdbi.handle { h -> upsertInvoices(h, drafts) }

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(drafts.map { it.id }))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val sentInvoices = jdbi.handle { h -> getInvoicesByIds(h, drafts.map { it.id }) }

        assertThat(sentInvoices.all { it.status == InvoiceStatus.SENT }).isTrue()

        val maxInvoiceNumber = jdbi.handle { h -> getMaxInvoiceNumber(h) }
        assertEquals(4999999999L + drafts.size, maxInvoiceNumber)
    }

    @Test
    fun `send sets numbers correctly when earlier rows exist`() {
        val sentInvoice = testInvoices[1]
        val drafts = (1..2).map {
            testInvoices[0].let {
                it.copy(
                    id = UUID.randomUUID(),
                    number = null,
                    rows = it.rows.map { it.copy(id = UUID.randomUUID()) }
                )
            }
        }

        jdbi.handle { h -> upsertInvoices(h, drafts + sentInvoice) }

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(drafts.map { it.id }))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val maxInvoiceNumber = jdbi.handle { h -> getMaxInvoiceNumber(h) }
        assertEquals(sentInvoice.number!! + drafts.size, maxInvoiceNumber)
    }

    @Test
    fun `mark as sent updates invoice status and sent fields`() {
        val invoice = testInvoices.first().copy(status = InvoiceStatus.WAITING_FOR_SENDING)
        jdbi.handle { h -> upsertInvoices(h, listOf(invoice)) }

        val (_, response, _) = http.post("/invoices/mark-sent")
            .jsonBody(objectMapper.writeValueAsString(listOf(invoice.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val (_, _, result) = http.get("/invoices/${invoice.id}")
            .asUser(testUser)
            .responseString()

        val updated = invoice.copy(status = InvoiceStatus.SENT, sentBy = testDecisionMaker_1.id)

        assertEqualEnough(
            listOf(updated.let(::toSummary)),
            listOf(deserializeResult(result.get()).data)
        )

        objectMapper.readValue<Wrapper<InvoiceDetailed>>(result.get()).let {
            assertEquals(InvoiceStatus.SENT, it.data.status)
            assertEquals(testDecisionMaker_1.id, it.data.sentBy)
            assertNotNull(it.data.sentAt)
        }
    }

    @Test
    fun `mark as sent returns bad request if invoice status is wrong`() {
        val invoice = testInvoices.first().copy(status = InvoiceStatus.DRAFT)
        jdbi.handle { h -> upsertInvoices(h, listOf(invoice)) }

        val (_, response, _) = http.post("/invoices/mark-sent")
            .jsonBody(objectMapper.writeValueAsString(listOf(invoice.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(400, response.statusCode)
    }

    @Test
    fun `mark as sent returns bad request if one of the ids is incorrect`() {
        val invoice = testInvoices.first().copy(status = InvoiceStatus.DRAFT)
        jdbi.handle { h -> upsertInvoices(h, listOf(invoice)) }

        val (_, response, _) = http.post("/invoices/mark-sent")
            .jsonBody(objectMapper.writeValueAsString(listOf(invoice.id, UUID.randomUUID())))
            .asUser(testUser)
            .responseString()
        assertEquals(400, response.statusCode)
    }

    @Test
    fun `updateInvoice works on drafts without updates`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val draft = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!

        val (_, response, _) = http.put("/invoices/${draft.id}")
            .jsonBody(objectMapper.writeValueAsString(draft))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)
    }

    @Test
    fun `updateInvoice returns bad request on sent invoices`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val sent = testInvoices.find { it.status == InvoiceStatus.SENT }!!

        val (_, response, _) = http.put("/invoices/${sent.id}")
            .jsonBody(objectMapper.writeValueAsString(sent))
            .asUser(testUser)
            .responseString()
        assertEquals(400, response.statusCode)
    }

    @Test
    fun `updateInvoice updates invoice row areaCode, costCenter, subCostCenter and adds a new row`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val original = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!
        val updated = original.copy(
            rows = original.rows.map {
                it.copy(
                    description = "UPDATED",
                    costCenter = "UPDATED",
                    subCostCenter = "UPDATED"
                )
            } + createInvoiceRowFixture(testChild_1.id).copy(
                product = Product.PRESCHOOL_WITH_DAYCARE,
                amount = 100,
                unitPrice = 100000
            )
        )

        val (_, response, _) = http.put("/invoices/${updated.id}")
            .jsonBody(objectMapper.writeValueAsString(updated))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val (_, _, result) = http.get("/invoices/${updated.id}")
            .asUser(testUser)
            .responseString()

        assertEqualEnough(
            listOf(updated.let(::toSummary)),
            listOf(deserializeResult(result.get()).data)
        )
    }

    @Test
    fun `updateInvoice does not update invoice status, periods, invoiceDate, dueDate or headOfFamily`() {
        jdbi.handle { h -> upsertInvoices(h, testInvoices) }
        val original = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!
        val updated = original.copy(
            status = InvoiceStatus.SENT,
            periodStart = LocalDate.MIN,
            periodEnd = LocalDate.MAX,
            invoiceDate = LocalDate.MIN,
            dueDate = LocalDate.MAX
        )

        val (_, response, _) = http.put("/invoices/${updated.id}")
            .jsonBody(objectMapper.writeValueAsString(updated))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val (_, _, result) = http.get("/invoices/${updated.id}")
            .asUser(testUser)
            .responseString()

        JSONAssert.assertNotEquals(
            objectMapper.writeValueAsString(Wrapper(updated.let(::toDetailed))),
            result.get(),
            false
        )

        assertEqualEnough(
            listOf(original.let(::toSummary)),
            listOf(deserializeResult(result.get()).data)
        )
    }

    @Test
    fun `createAllDraftInvoices works with one decision`() {
        val decision = testDecisions.find { it.status == FeeDecisionStatus.SENT }!!
        jdbi.handle { h -> upsertFeeDecisions(h, objectMapper, listOf(decision)) }

        val (_, response, _) = http.post("/invoices/create-drafts")
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val drafts = jdbi.handle { h ->
            paginatedSearch(
                h,
                1,
                50,
                InvoiceSortParam.STATUS,
                SortDirection.DESC,
                listOf(InvoiceStatus.DRAFT),
                listOf(),
                null,
                listOf()
            )
        }

        assertEquals(1, drafts.second.size)
    }

    @Test
    fun `createAllDraftInvoices works with two decisions`() {
        val testDecisions2 = testDecisions.filter { it.status == FeeDecisionStatus.SENT }.take(2)
        jdbi.handle { h -> upsertFeeDecisions(h, objectMapper, testDecisions2) }

        val (_, response, _) = http.post("/invoices/create-drafts")
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val drafts = jdbi.handle { h ->
            paginatedSearch(
                h,
                1,
                50,
                InvoiceSortParam.STATUS,
                SortDirection.DESC,
                listOf(InvoiceStatus.DRAFT),
                listOf(),
                null,
                listOf()
            )
        }

        assertEquals(2, drafts.second.size)
    }

    @Test
    fun `createAllDraftInvoices is idempotent`() {
        val decisions = listOf(testDecisions.find { it.status == FeeDecisionStatus.SENT }!!)
        jdbi.handle { h -> upsertFeeDecisions(h, objectMapper, decisions) }

        for (i in 1..4) {
            val (_, response, _) = http.post("/invoices/create-drafts")
                .asUser(testUser)
                .responseString()
            assertEquals(204, response.statusCode)
        }

        val drafts = jdbi.handle { h ->
            paginatedSearch(
                h,
                1,
                50,
                InvoiceSortParam.STATUS,
                SortDirection.DESC,
                listOf(InvoiceStatus.DRAFT),
                listOf(),
                null,
                listOf()
            )
        }

        assertEquals(1, drafts.second.size)
    }

    @Test
    fun `createAllDraftInvoices generates no drafts from already invoiced decisions`() {
        val decisions = listOf(testDecisions.find { it.status == FeeDecisionStatus.SENT }!!)
        jdbi.handle { h -> upsertFeeDecisions(h, objectMapper, decisions) }

        val (_, response1, _) = http.post("/invoices/create-drafts")
            .asUser(testUser)
            .responseString()
        assertEquals(204, response1.statusCode)

        val draftIds = jdbi.handle { h ->
            searchInvoices(h, listOf(InvoiceStatus.DRAFT), listOf(), null, listOf()).map { it.id }
        }
        assertThat(draftIds).isNotEmpty

        val (_, response2, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(draftIds))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response2.statusCode)

        val sents = jdbi.handle { h ->
            searchInvoices(h, listOf(InvoiceStatus.SENT), listOf(), null, listOf())
        }
        assertThat(sents).isNotEmpty

        val (_, response3, _) = http.post("/invoices/create-drafts")
            .asUser(testUser)
            .responseString()
        assertEquals(204, response3.statusCode)

        val drafts = jdbi.handle { h ->
            searchInvoices(h, listOf(InvoiceStatus.DRAFT), listOf(), null, listOf())
        }
        assertThat(drafts).isEmpty()
    }

    @Test
    fun `createAllDraftInvoices overrides drafts`() {
        val decisions = listOf(testDecisions.find { it.status == FeeDecisionStatus.SENT }!!).take(1)
        jdbi.handle { h -> upsertFeeDecisions(h, objectMapper, decisions) }

        val (_, response1, _) = http.post("/invoices/create-drafts")
            .asUser(testUser)
            .responseString()
        assertEquals(204, response1.statusCode)

        val originalDrafts = jdbi.handle { h ->
            searchInvoices(h, listOf(InvoiceStatus.DRAFT), listOf(), null, listOf())
        }
        assertEquals(1, originalDrafts.size)

        val (_, response3, _) = http.post("/invoices/create-drafts")
            .asUser(testUser)
            .responseString()
        assertEquals(204, response3.statusCode)

        val originalDraft = jdbi.handle { h -> getInvoice(h, originalDrafts.first().id) }
        assertEquals(null, originalDraft)

        val newDrafts = jdbi.handle { h ->
            searchInvoices(h, listOf(InvoiceStatus.DRAFT), listOf(), null, listOf())
        }
        assertEquals(1, newDrafts.size)
    }

    @Test
    fun `sending an invoice uses the recipient's actual address if it's valid`() {
        val draft = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!
        jdbi.handle { h -> upsertInvoices(h, listOf(draft)) }

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(listOf(draft.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val sentBatch = (integrationClient as InvoiceIntegrationClient.MockClient).sentBatches[0]

        assertEquals(1, sentBatch.invoices.size)
        sentBatch.invoices.first().let { invoice ->
            assertEquals(testAdult_1.streetAddress, invoice.client.street)
            assertEquals(testAdult_1.postalCode, invoice.client.postalCode)
            assertEquals(testAdult_1.postOffice, invoice.client.post)
            assertEquals(testAdult_1.streetAddress, invoice.recipient.street)
            assertEquals(testAdult_1.postalCode, invoice.recipient.postalCode)
            assertEquals(testAdult_1.postOffice, invoice.recipient.post)
        }
    }

    @Test
    fun `sending an invoice uses a fallback address when the recipient's actual address is partially incomplete`() {
        val draft = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!
        jdbi.handle { h -> upsertInvoices(h, listOf(draft)) }

        jdbi.handle { h ->
            h
                .createUpdate("UPDATE person SET street_address = :emptyStreetAddress WHERE id = :id")
                .bind("emptyStreetAddress", "")
                .bind("id", testAdult_1.id)
                .execute()
        }

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(listOf(draft.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val sentBatch = (integrationClient as InvoiceIntegrationClient.MockClient).sentBatches[0]

        assertEquals(1, sentBatch.invoices.size)
        sentBatch.invoices.first().let { invoice ->
            assertEquals(null, invoice.client.street)
            assertEquals(null, invoice.client.postalCode)
            assertEquals(null, invoice.client.post)
            assertEquals(fallbackStreetAddress, invoice.recipient.street)
            assertEquals(fallbackPostalCode, invoice.recipient.postalCode)
            assertEquals(fallbackPostOffice, invoice.recipient.post)
        }
    }

    @Test
    fun `sending an invoice uses the recipient's invoicing address when it's complete`() {
        val draft = testInvoices.find { it.status == InvoiceStatus.DRAFT }!!
        jdbi.handle { h -> upsertInvoices(h, listOf(draft)) }

        val streetAddress = "Testikatu 1"
        val postalCode = "00100"
        val postOffice = "Helsinki"
        jdbi.handle { h ->
            h
                .createUpdate(
                    """
                UPDATE person SET
                    invoicing_street_address = :streetAddress,
                    invoicing_postal_code = :postalCode,
                    invoicing_post_office = :postOffice
                WHERE id = :id
                    """.trimIndent()
                )
                .bind("streetAddress", streetAddress)
                .bind("postalCode", postalCode)
                .bind("postOffice", postOffice)
                .bind("id", testAdult_1.id)
                .execute()
        }

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(listOf(draft.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val sentBatch = (integrationClient as InvoiceIntegrationClient.MockClient).sentBatches[0]

        assertEquals(1, sentBatch.invoices.size)
        sentBatch.invoices.first().let { invoice ->
            assertEquals(testAdult_1.streetAddress, invoice.client.street)
            assertEquals(testAdult_1.postalCode, invoice.client.postalCode)
            assertEquals(testAdult_1.postOffice, invoice.client.post)
            assertEquals(streetAddress, invoice.recipient.street)
            assertEquals(postalCode, invoice.recipient.postalCode)
            assertEquals(postOffice, invoice.recipient.post)
        }
    }

    @Test
    fun `invoice sent to Community has the rows grouped by child`() {
        val draft = createInvoiceFixture(
            status = InvoiceStatus.DRAFT,
            headOfFamilyId = testAdult_1.id,
            agreementType = testAreaCode,
            rows = listOf(
                createInvoiceRowFixture(childId = testChild_2.id),
                createInvoiceRowFixture(childId = testChild_1.id),
                createInvoiceRowFixture(childId = testChild_1.id)
            )
        )
        jdbi.handle { h -> upsertInvoices(h, listOf(draft)) }

        val (_, response, _) = http.post("/invoices/send")
            .jsonBody(objectMapper.writeValueAsString(listOf(draft.id)))
            .asUser(testUser)
            .responseString()
        assertEquals(204, response.statusCode)

        val sentBatch = (integrationClient as InvoiceIntegrationClient.MockClient).sentBatches[0]

        assertEquals(1, sentBatch.invoices.size)
        sentBatch.invoices.first().let { invoice ->
            assertEquals(7, invoice.rows.size)
            assertEquals("${testChild_1.lastName} ${testChild_1.firstName}", invoice.rows[0].description)
            assertEquals("Varhaiskasvatus", invoice.rows[1].description)
            assertEquals("Varhaiskasvatus", invoice.rows[2].description)
            assertEquals("", invoice.rows[3].description)
            assertEquals("${testChild_2.lastName} ${testChild_2.firstName}", invoice.rows[4].description)
            assertEquals("Varhaiskasvatus", invoice.rows[5].description)
            assertEquals("", invoice.rows[6].description)
        }
    }
}
