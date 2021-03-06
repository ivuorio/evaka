// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import _ from 'lodash'
import { Link } from 'react-router-dom'

import { faChild } from 'icon-set'
import { UUID } from '~types'
import { useTranslation } from '~state/i18n'
import { useEffect } from 'react'
import { isFailure, isLoading, isSuccess, Loading } from '~api'
import { useContext } from 'react'
import { PersonContext } from '~state/person'
import CollapsibleSection from 'components/shared/molecules/CollapsibleSection'
import { Table, Tbody, Td, Th, Thead, Tr } from 'components/shared/layout/Table'
import Loader from '~components/shared/atoms/Loader'
import { getPersonDependants } from '~api/person'
import { DependantAddress, PersonWithChildren } from '~/types/person'
import { formatName } from '~utils'
import { NameTd } from '~components/PersonProfile'
import { getAge } from '@evaka/lib-common/src/utils/local-date'

interface Props {
  id: UUID
  open: boolean
}

const PersonDependants = React.memo(function PersonDependants({
  id,
  open
}: Props) {
  const { i18n } = useTranslation()
  const { dependants, setDependants } = useContext(PersonContext)
  useEffect(() => {
    setDependants(Loading())
    void getPersonDependants(id).then((response) => {
      setDependants(response)
    })
  }, [id, setDependants])

  const printableAddresses = (addresses: DependantAddress[] | null) =>
    addresses && addresses.length > 0
      ? [
          addresses[0].streetAddress,
          addresses[0].postalCode,
          addresses[0].city
        ].join(', ')
      : ''

  const renderDependants = () =>
    isSuccess(dependants)
      ? _.orderBy(dependants.data, ['dateOfBirth'], ['asc']).map(
          (dependant: PersonWithChildren) => {
            return (
              <Tr key={`${dependant.id}`} data-qa="table-dependant-row">
                <NameTd data-qa="dependant-name">
                  <Link to={`/child-information/${dependant.id}`}>
                    {formatName(dependant.firstName, dependant.lastName, i18n)}
                  </Link>
                </NameTd>
                <Td data-qa="dependant-ssn">
                  {dependant.socialSecurityNumber}
                </Td>
                <Td data-qa="dependant-age">{getAge(dependant.dateOfBirth)}</Td>
                <Td data-qa="dependant-street-address">
                  {printableAddresses(dependant.addresses)}
                </Td>
              </Tr>
            )
          }
        )
      : null

  return (
    <div>
      <CollapsibleSection
        icon={faChild}
        title={i18n.personProfile.dependants}
        startCollapsed={!open}
        dataQa="person-dependants-collapsible"
      >
        <Table data-qa="table-of-dependants">
          <Thead>
            <Tr>
              <Th>{i18n.personProfile.name}</Th>
              <Th>{i18n.personProfile.ssn}</Th>
              <Th>{i18n.personProfile.age}</Th>
              <Th>{i18n.personProfile.streetAddress}</Th>
            </Tr>
          </Thead>
          <Tbody>{renderDependants()}</Tbody>
        </Table>
        {isLoading(dependants) && <Loader />}
        {isFailure(dependants) && <div>{i18n.common.loadingFailed}</div>}
      </CollapsibleSection>
    </div>
  )
})

export default PersonDependants
