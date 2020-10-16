// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.dvv

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@Profile("enable_mock_dvv_api")
@RestController
@RequestMapping("/mock-integration/dvv/api")
class MockDvvModificationsService(private val mapper: ObjectMapper) {

    @GetMapping("/v1/kirjausavain/{date}")
    fun getApiKey(@PathVariable("date") date: String?): ResponseEntity<String> {
        logger.info { "Mock dvv GET /kirjausavain/$date called" }
        return ResponseEntity.ok("{\"viimeisinKirjausavain\":100000021}")
    }

    @PostMapping("/v1/muutokset")
    fun getModifications(
        @RequestBody body: ModificationsRequest
    ): ResponseEntity<String> {
        logger.info { "Mock dvv POST /muutokset called, body: $body" }
        return ResponseEntity.ok(
            """
            {
              "viimeisinKirjausavain": ${body.viimeisinKirjausavain.toInt() + 1},
              "muutokset": [${getModifications(body.hetulista)}],
              "ajanTasalla": true
            }
        """
        )
    }
}

fun getModifications(ssns: List<String>): String {
    return ssns.map { ssn -> if (modifications.containsKey(ssn)) modifications[ssn] else null }.filter { it != null }.joinToString(",")
}

val modifications = mapOf<String, String>(
    "nimenmuutos" to """
{
  "henkilotunnus": "010579-9999",
  "tietoryhmat": [
    {
      "tietoryhma": "HENKILON_NIMI",
      "muutosattribuutti": "MUUTETTU",
      "alkupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      },
      "etunimi": "Etunimi5_muutos",
      "sukunimi": "Sukunimi5"
    },
    {
      "tietoryhma": "NIMENMUUTOS",
      "muutosattribuutti": "LISATTY",
      "nimilaji": "NYKYINEN_ETUNIMI",
      "nimi": "Etunimi5_muutos",
      "alkupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      }
    },
    {
      "tietoryhma": "NIMENMUUTOS",
      "muutosattribuutti": "MUUTETTU",
      "nimilaji": "EDELLINEN_ETUNIMI",
      "nimi": "Etunimi5",
      "alkupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      },
      "loppupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      }
    },
    {
      "tietoryhma": "NIMENMUUTOS_LAAJA",
      "muutosattribuutti": "LISATTY",
      "nimilaji": "NYKYINEN_ETUNIMI",
      "nimi": "Etunimi5_muutos",
      "alkupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      }
    },
    {
      "tietoryhma": "NIMENMUUTOS_LAAJA",
      "muutosattribuutti": "MUUTETTU",
      "nimilaji": "EDELLINEN_ETUNIMI",
      "nimi": "Etunimi5",
      "alkupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      },
      "loppupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      }
    }
  ],
  "muutospv": "2019-09-24T21:00:00.000Z"
}
    """.trimIndent(),
    "010180-9999" to """
{
  "henkilotunnus": "010180-9999",
  "tietoryhmat": [
    {
      "tietoryhma": "HENKILON_NIMI",
      "muutosattribuutti": "MUUTETTU",
      "alkupv": {
        "arvo": "2019-09-25",
        "tarkkuus": "PAIVA"
      },
      "etunimi": "Etunimi5_muutos",
      "sukunimi": "Sukunimi5"
    }
  ],
  "muutospv": "2019-09-24T21:00:00.000Z"
}
    """.trimIndent(),
    "020180-999Y" to """
{
  "henkilotunnus": "020180-999Y",
  "tietoryhmat": [
    {
      "tietoryhma": "TURVAKIELTO",
      "muutosattribuutti": "LISATTY",
      "turvakieltoAktiivinen": true
    },
    {
      "tietoryhma": "VAKINAINEN_KOTIMAINEN_OSOITE",
      "turvakiellonAlaisetKentat": [
        "katunimi",
        "katunumero",
        "huoneistokirjain",
        "huoneistonumero",
        "jakokirjain",
        "postinumero",
        "postitoimipaikka",
        "rakennustunnus",
        "osoitenumero"
      ],
      "muutosattribuutti": "MUUTETTU"
    }
  ],
  "muutospv": "2019-09-24T21:00:00.000Z"
}
    """.trimIndent(),
    "030180-999L" to """
{
  "henkilotunnus": "030180-999L",
  "tietoryhmat": [
    {
      "tietoryhma": "TURVAKIELTO",
      "muutosattribuutti": "MUUTETTU",
      "turvaLoppuPv": {
        "arvo": "2030-01-01",
        "tarkkuus": "PAIVA"
      },
      "turvakieltoAktiivinen": false
    },
    {
      "tietoryhma": "VAKINAINEN_KOTIMAINEN_OSOITE",
      "katunimi": {
        "fi": "Vanhakatu",
        "sv": "Gamlagatan"
      },
      "katunumero": "10h5",
      "huoneistonumero": "003",
      "postinumero": "02230",
      "postitoimipaikka": {
        "fi": "Espoo",
        "sv": "Esbo"
      },
      "rakennustunnus": "1234567890",
      "osoitenumero": 1,
      "alkupv": {
        "arvo": "1986-06-02",
        "tarkkuus": "PAIVA"
      },
      "loppupv": {
        "arvo": "2999-02-28",
        "tarkkuus": "PAIVA"
      },
      "muutosattribuutti": "MUUTETTU"
    }
  ],
  "muutospv": "2019-09-24T21:00:00.000Z"
}
    """.trimIndent(),
    "010180-999A" to """
{
  "henkilotunnus": "010180-999A",
  "tietoryhmat": [
    {
      "tietoryhma": "KUOLINPAIVA",
      "muutosattribuutti": "LISATTY",
      "kuollut": true,
      "kuolinpv": {
        "arvo": "2019-07-30",
        "tarkkuus": "PAIVA"
      }
    }
  ],
  "muutospv": "2019-09-24T21:00:00.000Z"
}
    """.trimIndent(),
    "yksinhuoltaja-muutos" to """
{
  "henkilotunnus": "010579-9999",
  "tietoryhmat": [
    {
      "tietoryhma": "HUOLLETTAVA_SUPPEA",
      "huollettava": {
        "henkilotunnus": "010118-9999",
        "etunimet": "Etu",
        "sukunimi": "Suku"
      },
      "huoltajanLaji": "MAARATTY_HUOLTAJA",
      "huoltajanRooli": "AITI",
      "huoltosuhteenAlkupv": {
        "arvo": "2020-09-08",
        "tarkkuus": "PAIVA"
      },
      "huoltosuhteenLoppupv": {
        "arvo": "2036-11-16",
        "tarkkuus": "PAIVA"
      },
      "asuminen": "AIDIN_LUONA",
      "asumisenAlkupv": {
        "arvo": "2020-09-08",
        "tarkkuus": "PAIVA"
      },
      "asumisenLoppupv": {
        "arvo": "2036-11-16",
        "tarkkuus": "PAIVA"
      },
      "oikeudet": [],
      "muutosattribuutti": "LISATTY",
      "lisatytOikeudet": [],
      "poistetutOikeudet": [],
      "muutetutOikeudet": []
    },
    {
      "tietoryhma": "HUOLLETTAVA_SUPPEA",
      "huollettava": {
        "henkilotunnus": "010118-9999",
        "etunimet": "Etu",
        "sukunimi": "Suku"
      },
      "huoltajanLaji": "LAKISAATEINEN_HUOLTAJA",
      "huoltajanRooli": "AITI",
      "huoltosuhteenAlkupv": {
        "arvo": "2018-11-16",
        "tarkkuus": "PAIVA"
      },
      "huoltosuhteenLoppupv": {
        "arvo": "2020-09-08",
        "tarkkuus": "PAIVA"
      },
      "asuminen": "AIDIN_LUONA",
      "asumisenAlkupv": {
        "arvo": "2020-09-08",
        "tarkkuus": "PAIVA"
      },
      "asumisenLoppupv": {
        "arvo": "2036-11-16",
        "tarkkuus": "PAIVA"
      },
      "oikeudet": [],
      "muutosattribuutti": "MUUTETTU",
      "lisatytOikeudet": [],
      "poistetutOikeudet": [],
      "muutetutOikeudet": []
    }
  ],
  "muutospv": "2020-10-01T04:38:04.394Z"
}
    """.trimIndent(),
    "huoltaja" to """
{
  "henkilotunnus": "010118-9999",
  "tietoryhmat": [
    {
      "tietoryhma": "HUOLTAJA_SUPPEA",
      "huoltaja": {
        "henkilotunnus": "010579-9999",
        "etunimet": "Etu",
        "sukunimi": "Suku"
      },
      "huoltajanLaji": "MAARATTY_HUOLTAJA",
      "huoltajanRooli": "AITI",
      "huoltosuhteenAlkupv": {
        "arvo": "2020-09-08",
        "tarkkuus": "PAIVA"
      },
      "huoltosuhteenLoppupv": {
        "arvo": "2036-11-16",
        "tarkkuus": "PAIVA"
      },
      "asuminen": "AIDIN_LUONA",
      "asumisenAlkupv": {
        "arvo": "2020-09-08",
        "tarkkuus": "PAIVA"
      },
      "asumisenLoppupv": {
        "arvo": "2036-11-16",
        "tarkkuus": "PAIVA"
      },
      "oikeudet": [],
      "muutosattribuutti": "LISATTY",
      "lisatytOikeudet": [],
      "poistetutOikeudet": [],
      "muutetutOikeudet": []
    }
  ],
  "muutospv": "2020-10-01T04:38:04.394Z"
}            
    """.trimIndent(),
    "010181-999K" to """
{
      "henkilotunnus": "010181-999K",
      "tietoryhmat": [
        {
          "tietoryhma": "HENKILOTUNNUS_KORJAUS",
          "voimassaolo": "AKTIIVI",
          "muutosattribuutti": "LISATTY",
          "muutettuHenkilotunnus": "010281-999C",
          "aktiivinenHenkilotunnus": "010281-999C",
          "edellisetHenkilotunnukset": [
            "010181-999K"
          ]
        },
        {
          "tietoryhma": "HENKILOTUNNUS_KORJAUS",
          "voimassaolo": "PASSIIVI",
          "muutosattribuutti": "MUUTETTU",
          "muutettuHenkilotunnus": "010118-9999",
          "aktiivinenHenkilotunnus": "010281-999C",
          "edellisetHenkilotunnukset": [
            "010118-9999"
          ]
        },
        {
          "tietoryhma": "HENKILON_NIMI",
          "etunimi": "Etunimi12",
          "sukunimi": "Sukunimi12",
          "alkupv": {
            "tarkkuus": "PAIVA",
            "arvo": "2019-04-23"
          },
          "muutosattribuutti": "LISATIETO"
        }
      ],
      "muutospv": "2019-09-24T21:00:00.000Z"
    }
    """.trimIndent(),
    "040180-9998" to """
{
  "henkilotunnus": "040180-9998",
  "tietoryhmat": [
    {
      "tietoryhma": "VAKINAINEN_KOTIMAINEN_OSOITE",
      "muutosattribuutti": "LISATTY",
      "alkupv": {
        "arvo": "2020-10-01",
        "tarkkuus": "PAIVA"
      },
      "rakennustunnus": "1234567890",
      "katunumero": "17",
      "osoitenumero": 1,
      "huoneistokirjain": "A",
      "huoneistonumero": "002",
      "postinumero": "02940",
      "katunimi": {
        "fi": "Uusitie",
        "sv": "Nyvägen"
      },
      "postitoimipaikka": {
        "fi": "ESPOO",
        "sv": "ESBO"
      }
    },
    {
      "tietoryhma": "VAKINAINEN_KOTIMAINEN_OSOITE",
      "muutosattribuutti": "MUUTETTU",
      "alkupv": {
        "arvo": "2018-03-01",
        "tarkkuus": "PAIVA"
      },
      "loppupv": {
        "arvo": "2020-09-30",
        "tarkkuus": "PAIVA"
      },
      "rakennustunnus": "1234567880",
      "katunumero": "2",
      "osoitenumero": 1,
      "huoneistokirjain": "A",
      "huoneistonumero": "031",
      "postinumero": "02600",
      "katunimi": {
        "fi": "Samatie",
        "sv": "Sammavägen"
      },
      "postitoimipaikka": {
        "fi": "ESPOO",
        "sv": "ESBO"
      }
    }
  ],
  "muutospv": "2020-10-01T04:38:04.394Z"
}
    """.trimIndent(),
    "" to """
{
  "henkilotunnus": "050180-999W",
  "tietoryhmat": [
    {
      "tietoryhma": "HUOLLETTAVA_SUPPEA",
      "huollettava": {
        "henkilotunnus": "050118-999W"
      },
      "huoltajanLaji": "LAKISAATEINEN_HUOLTAJA",
      "huoltajanRooli": "AITI",
      "huoltosuhteenAlkupv": {
        "arvo": "2020-10-01",
        "tarkkuus": "PAIVA"
      },
      "huoltosuhteenLoppupv": {
        "arvo": "2038-10-01",
        "tarkkuus": "PAIVA"
      },
      "oikeudet": [],
      "muutosattribuutti": "LISATTY",
      "lisatytOikeudet": [],
      "poistetutOikeudet": [],
      "muutetutOikeudet": []
    }
  ],
  "muutospv": "2020-09-30T22:01:04.568Z"
}            
    """.trimIndent()
)

data class ModificationsRequest(
    val viimeisinKirjausavain: String,
    val tuotekoodi: String,
    val hetulista: List<String>
)
