// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.invoicing.service

import fi.espoo.evaka.invoicing.domain.FeeAlteration
import fi.espoo.evaka.invoicing.domain.FeeDecisionDetailed
import fi.espoo.evaka.invoicing.domain.FeeDecisionType
import fi.espoo.evaka.invoicing.domain.IncomeEffect
import fi.espoo.evaka.invoicing.domain.MailAddress
import fi.espoo.evaka.invoicing.domain.PlacementType
import fi.espoo.evaka.invoicing.domain.ServiceNeed
import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class Template(val value: String)
class Page(val template: Template, val context: Context)

data class FeeDecisionPdfData(
    val decision: FeeDecisionDetailed,
    val lang: String
)

fun BigDecimal.toDecimalString(): String = this.toString().replace('.', ',')

fun formatCents(amountInCents: Int?): String? =
    if (amountInCents != null) BigDecimal(amountInCents).divide(
        BigDecimal(100),
        2,
        RoundingMode.HALF_UP
    ).toDecimalString() else null

fun dateFmt(date: LocalDate?): String = date?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: ""

fun instantFmt(instant: Instant?): String =
    instant?.atZone(ZoneId.of("Europe/Helsinki"))?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: ""

@Component
class PDFService(
    private val templateEngine: ITemplateEngine
) {
    fun processPage(page: Page): String = templateEngine.process(page.template.value, page.context)

    fun render(pages: List<Page>): ByteArray {
        val os = ByteArrayOutputStream()
        renderHtmlPages(pages.map(this::processPage), os)
        return os.toByteArray()
    }

    fun renderHtml(pages: List<String>): ByteArray {
        val os = ByteArrayOutputStream()
        renderHtmlPages(pages, os)
        return os.toByteArray()
    }

    private fun renderHtmlPages(pages: List<String>, os: OutputStream) {
        val textRenderer = ITextRenderer()
        val head = pages.first()
        val tail = pages.drop(1)
        // First page
        textRenderer.setDocumentFromString(head)
        textRenderer.layout()
        textRenderer.createPDF(os, false)
        // Rest of the pages
        tail.forEach { page ->
            textRenderer.setDocumentFromString(page)
            textRenderer.layout()
            textRenderer.writeNextDocument()
        }
        textRenderer.finishPDF()
    }

    fun generateFeeDecisionPdf(data: FeeDecisionPdfData): ByteArray {
        val templates = listOf(
            "fee-decision/fee-decision-page1",
            "fee-decision/fee-decision-page2",
            "fee-decision/fee-decision-page3"
        )

        val pages = templates.mapIndexed { i, template ->
            Page(Template(template), createFeeDecisionPdfContext(data, i + 1))
        }

        return render(pages)
    }

    private fun createFeeDecisionPdfContext(
        data: FeeDecisionPdfData,
        pageNumber: Int
    ): Context {
        return Context().apply {
            locale = Locale.Builder().setLanguage(data.lang).build()
            setVariables(getFeeDecisionPdfVariables(data))
            setVariable("pageNumber", pageNumber)
        }
    }

    fun getFeeDecisionPdfVariables(data: FeeDecisionPdfData): Map<String, Any?> {
        data class FeeDecisionPdfPart(
            val childName: String,
            val placementType: PlacementType,
            val serviceNeed: ServiceNeed,
            val feeAlterations: List<Pair<FeeAlteration.Type, String>>,
            val finalFeeFormatted: String,
            val feeFormatted: String
        )

        val (decision, lang) = data

        val totalIncome = listOfNotNull(decision.headOfFamilyIncome?.total, decision.partnerIncome?.total).sum()

        val sendAddress = MailAddress.fromPerson(decision.headOfFamily, lang)

        val hideTotalIncome =
            (decision.headOfFamilyIncome == null || decision.headOfFamilyIncome.effect != IncomeEffect.INCOME) ||
                (decision.partnerIncome != null && decision.partnerIncome.effect != IncomeEffect.INCOME)

        return mapOf(
            "approvedAt" to instantFmt(decision.approvedAt),
            "decisionNumber" to decision.decisionNumber,
            "isReliefDecision" to (decision.decisionType !== FeeDecisionType.NORMAL),
            "decisionType" to decision.decisionType.toString(),
            "hasPartner" to (decision.partner != null),
            "hasPoBox" to (sendAddress.poBox != null),
            "headFullName" to with(decision.headOfFamily) { "$firstName $lastName" },
            "headIncomeEffect" to (decision.headOfFamilyIncome?.effect?.name ?: IncomeEffect.NOT_AVAILABLE.name),
            "headIncomeTotal" to formatCents(decision.headOfFamilyIncome?.total),
            "partnerFullName" to decision.partner?.let { "${it.firstName} ${it.lastName}" },
            "partnerIncomeEffect" to (decision.partnerIncome?.effect?.name ?: IncomeEffect.NOT_AVAILABLE.name),
            "partnerIncomeTotal" to formatCents(decision.partnerIncome?.total),
            "parts" to decision.parts.map {
                FeeDecisionPdfPart(
                    "${it.child.firstName} ${it.child.lastName}",
                    it.placement.type,
                    it.placement.serviceNeed,
                    it.feeAlterations.map { fa -> fa.type to formatCents(fa.effect)!! },
                    formatCents(it.finalFee())!!,
                    formatCents(it.fee)!!
                )
            },
            "sendAddress" to sendAddress,
            "totalFee" to formatCents(decision.totalFee()),
            "totalIncome" to formatCents(totalIncome),
            "showTotalIncome" to !hideTotalIncome,
            "validFor" to with(decision) { "${dateFmt(validFrom)} - ${dateFmt(validTo)}" },
            "validFrom" to dateFmt(decision.validFrom),
            "feePercent" to decision.feePercent().toDecimalString(),
            "pricingMinThreshold" to formatCents(-1 * decision.minThreshold()),
            "familySize" to decision.familySize,
            "showValidTo" to (decision.validTo?.isBefore(LocalDate.now()) ?: false),
            "approverFirstName" to decision.approvedBy?.firstName,
            "approverLastName" to decision.approvedBy?.lastName
        ).mapValues {
            it.value ?: ""
        }
    }
}
