// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.daycare.controllers

import fi.espoo.evaka.daycare.CareArea
import fi.espoo.evaka.daycare.CareType
import fi.espoo.evaka.daycare.Location
import fi.espoo.evaka.daycare.MailingAddress
import fi.espoo.evaka.daycare.UnitStub
import fi.espoo.evaka.daycare.VisitingAddress
import fi.espoo.evaka.daycare.domain.Language
import fi.espoo.evaka.daycare.domain.ProviderType
import fi.espoo.evaka.daycare.getApplicationUnits
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.db.bindNullable
import fi.espoo.evaka.shared.db.handle
import fi.espoo.evaka.shared.db.mapColumn
import fi.espoo.evaka.shared.db.transaction
import fi.espoo.evaka.shared.domain.BadRequest
import fi.espoo.evaka.shared.domain.Coordinate
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.mapTo
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
class LocationController(val jdbi: Jdbi) {
    @GetMapping("/public/units")
    fun getApplicationUnits(
        user: AuthenticatedUser,
        @RequestParam type: ApplicationUnitType,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<List<PublicUnit>> {
        val units = jdbi.handle { h -> h.getApplicationUnits(type, date, onlyApplicable = user.isEndUser()) }
        return ResponseEntity.ok(units)
    }

    // Units by areas, only including units that can be applied to
    @GetMapping("/enduser/areas")
    fun getEnduserUnitsByArea(): ResponseEntity<Collection<CareAreaResponseJSON>> {
        val areas = jdbi.transaction { h ->
            h.getAreas()
                .map { area: CareArea ->
                    CareArea(
                        area.id,
                        area.name,
                        area.shortName,
                        area.locations.filter {
                            it.type.contains(CareType.CLUB) || it.canApplyDaycare || it.canApplyPreschool
                        }
                    )
                }
                .toSet()
        }

        return ResponseEntity.ok(toCareAreaResponses(areas))
    }

    @GetMapping("/areas")
    fun getAreas(user: AuthenticatedUser): ResponseEntity<Collection<AreaJSON>> {
        return jdbi
            .handle {
                it.createQuery("SELECT id, name, short_name FROM care_area")
                    .mapTo<AreaJSON>()
                    .toList()
            }
            .let { ResponseEntity.ok(it) }
    }

    // TODO: fix this
    @GetMapping("/filters/units")
    fun getUnits(@RequestParam type: String, @RequestParam area: String?): ResponseEntity<List<UnitStub>> {
        val areas = area?.split(",") ?: listOf()
        val units = jdbi.transaction { h ->
            when (type.toLowerCase()) {
                "club" -> h.getClubs(areas)
                "daycare" -> h.getDaycares(areas)
                else -> throw BadRequest("Unsupported type $type")
            }
        }
        return ResponseEntity.ok(units)
    }

    companion object DomainMapping {
        private fun toCareAreaResponses(areas: Collection<CareArea>) = areas.map { area ->
            CareAreaResponseJSON(
                area.id,
                area.name,
                area.shortName,
                area.locations.map(::toLocationResponseJSON)
            )
        }

        private fun toLocationResponseJSON(location: Location) =
            LocationResponseJSON(
                id = location.id,
                name = location.name,
                address = location.visitingAddress.streetAddress,
                location = location.location,
                phone = location.phone,
                postalCode = location.visitingAddress.postalCode,
                POBox = location.mailingAddress.poBox,
                type = location.type,
                care_area_id = location.care_area_id,
                url = location.url,
                provider_type = location.provider_type,
                language = location.language,
                visitingAddress = location.visitingAddress,
                mailingAddress = location.mailingAddress,
                canApplyDaycare = location.canApplyDaycare,
                canApplyPreschool = location.canApplyPreschool,
                canApplyClub = location.canApplyClub
            )
    }
}

enum class ApplicationUnitType {
    CLUB, DAYCARE, PRESCHOOL, PREPARATORY;
}

data class PublicUnit(
    val id: UUID,
    val name: String,
    val type: Set<CareType>,
    val providerType: ProviderType,
    val language: Language,
    val streetAddress: String,
    val postalCode: String,
    val postOffice: String,
    val phone: String?,
    val email: String?,
    val url: String?,
    val location: Coordinate?
)

data class CareAreaResponseJSON(
    val id: UUID,
    val name: String,
    val shortName: String,
    val daycares: List<LocationResponseJSON>
)

data class LocationResponseJSON(
    val id: UUID,
    val name: String,

    @Deprecated("Use separate mailing/vising addresses that match data")
    val address: String,

    val location: Coordinate?, // @Todo remove nullability when data is complete
    val phone: String?, // @Todo remove nullability when data is complete

    @Deprecated("Use separate mailing/vising addresses that match the data")
    val postalCode: String?, // @Todo remove nullability when data is complete
    @Deprecated("Use separate mailing/vising addresses that match the data")
    val POBox: String?, // @Todo remove nullability when data is complete

    val type: Set<CareType>,
    val care_area_id: UUID,
    val url: String?,
    val provider_type: ProviderType?,
    val language: Language?,
    val visitingAddress: VisitingAddress,
    val mailingAddress: MailingAddress,
    val canApplyDaycare: Boolean,
    val canApplyPreschool: Boolean,
    val canApplyClub: Boolean
)

data class AreaJSON(
    val id: UUID,
    val name: String,
    val shortName: String
)

fun Handle.getAreas(): List<CareArea> = createQuery(
    // language=SQL
    """
SELECT
  ca.id AS care_area_id, ca.name AS care_area_name, ca.short_name AS care_area_short_name,
  u.id, u.name, u.street_address, u.location, u.phone, u.postal_code, u.post_office,
  u.mailing_street_address, u.mailing_po_box, u.mailing_postal_code, u.mailing_post_office,
  u.type, u.url, u.provider_type, u.language, u.can_apply_daycare, u.can_apply_preschool, u.can_apply_club
FROM care_area ca
LEFT JOIN daycare u ON ca.id = u.care_area_id
WHERE (u.can_apply_daycare OR u.can_apply_preschool OR u.can_apply_club)
AND (u.closing_date IS NULL OR u.closing_date >= current_date)
    """.trimIndent()
)
    .reduceRows(mutableMapOf<UUID, Pair<CareArea, MutableList<Location>>>()) { map, row ->
        val (_, locations) = map.computeIfAbsent(row.mapColumn("care_area_id")) { id ->
            Pair(
                CareArea(
                    id = id,
                    name = row.mapColumn("care_area_name"),
                    shortName = row.mapColumn("care_area_short_name"),
                    locations = listOf()
                ),
                mutableListOf()
            )
        }
        row.mapColumn<UUID?>("id")?.let {
            locations.add(row.getRow(Location::class.java))
        }

        map
    }
    .values
    .map { (area, locations) ->
        area.copy(locations = locations.toList())
    }

fun Handle.getDaycares(areaShortNames: Collection<String>): List<UnitStub> = createQuery(
    // language=SQL
    """
SELECT daycare.id, daycare.name
FROM daycare
JOIN care_area ON care_area_id = care_area.id
WHERE :areaShortNames::text[] IS NULL OR care_area.short_name = ANY(:areaShortNames)
ORDER BY name
    """.trimIndent()
).bindNullable("areaShortNames", areaShortNames.toTypedArray().takeIf { it.isNotEmpty() })
    .mapTo<UnitStub>()
    .list()

fun Handle.getClubs(areaShortNames: Collection<String>): List<UnitStub> = createQuery(
    // language=SQL
    """
SELECT club_group.id, club_group.name
FROM club_group
JOIN care_area ON care_area_id = care_area.id
WHERE :areaShortNames::text[] IS NULL OR care_area.short_name = ANY(:areaShortNames)
ORDER BY name
    """.trimIndent()
).bindNullable("areaShortNames", areaShortNames.toTypedArray().takeIf { it.isNotEmpty() })
    .mapTo<UnitStub>()
    .list()
