// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.pis.dao

import fi.espoo.evaka.identity.ExternalIdentifier
import fi.espoo.evaka.pis.AbstractIntegrationTest
import fi.espoo.evaka.pis.service.PersonDTO
import fi.espoo.evaka.pis.service.PersonIdentityRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class PartnershipDAOIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    lateinit var partnershipDAO: PartnershipDAO

    @Autowired
    lateinit var personDAO: PersonDAO

    @Test
    fun `test creating partnership`() {
        val person1 = testPerson1()
        val person2 = testPerson2()
        val startDate = LocalDate.now()
        val endDate = startDate.plusDays(100)
        val partnership = partnershipDAO.createPartnership(person1.id, person2.id, startDate, endDate)
        assertNotNull(partnership.id)
        assertEquals(2, partnership.partners.size)
        assertEquals(startDate, partnership.startDate)
        assertEquals(endDate, partnership.endDate)
    }

    @Test
    fun `test fetching partnerships by person`() {
        val person1 = testPerson1()
        val person2 = testPerson2()
        val person3 = testPerson3()

        val partnership1 = partnershipDAO.createPartnership(person1.id, person2.id, LocalDate.now(), LocalDate.now().plusDays(200))
        val partnership2 = partnershipDAO.createPartnership(person2.id, person3.id, LocalDate.now().plusDays(300), LocalDate.now().plusDays(400))

        val person1Partnerships = partnershipDAO.getPartnershipsForPerson(person1.id)
        assertEquals(setOf(partnership1), person1Partnerships)

        val person2Partnerships = partnershipDAO.getPartnershipsForPerson(person2.id)
        assertEquals(setOf(partnership1, partnership2), person2Partnerships)

        val person3Partnerships = partnershipDAO.getPartnershipsForPerson(person3.id)
        assertEquals(setOf(partnership2), person3Partnerships)
    }

    @Test
    fun `test partnership without endDate`() {
        val person1 = testPerson1()
        val person2 = testPerson2()
        val startDate = LocalDate.now()
        val partnership = partnershipDAO.createPartnership(person1.id, person2.id, startDate, endDate = null)
        assertNotNull(partnership.id)
        assertEquals(2, partnership.partners.size)
        assertEquals(startDate, partnership.startDate)
        assertEquals(null, partnership.endDate)

        val fetched = partnershipDAO.getPartnershipsForPerson(person1.id).first()
        assertEquals(partnership.id, fetched.id)
        assertEquals(2, fetched.partners.size)
        assertEquals(startDate, fetched.startDate)
        assertEquals(null, fetched.endDate)
    }

    private fun createPerson(ssn: String, firstName: String): PersonDTO {
        return personDAO.getOrCreatePersonIdentity(
            PersonIdentityRequest(
                identity = ExternalIdentifier.SSN.getInstance(ssn),
                firstName = firstName,
                lastName = "Meikäläinen",
                email = "${firstName.toLowerCase()}.meikalainen@example.com",
                language = "fi"
            )
        )
    }

    private fun testPerson1() = createPerson("140881-172X", "Aku")
    private fun testPerson2() = createPerson("150786-1766", "Iines")
    private fun testPerson3() = createPerson("170679-601K", "Hannu")
}
