// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useContext } from 'react'
import * as _ from 'lodash'

import { useTranslation } from '~/state/i18n'
import { isFailure, isLoading } from '~/api'
import { ChildContext, ChildState } from '~/state/child'
import { faUser } from 'icon-set'
import { Table, Tbody, Td, Th, Thead, Tr } from 'components/shared/layout/Table'
import Loader from '~components/shared/atoms/Loader'
import { Parentship } from '~types/fridge'
import { Link } from 'react-router-dom'
import { getStatusLabelByDateRange } from '~utils/date'
import StatusLabel from '~components/common/StatusLabel'
import CollapsibleSection from 'components/shared/molecules/CollapsibleSection'

type Props = {
  open: boolean
}

const FridgeParents = React.memo(function FridgeParents({ open }: Props) {
  const { i18n } = useTranslation()
  const { parentships } = useContext<ChildState>(ChildContext)

  function renderFridgeParents() {
    if (isLoading(parentships)) {
      return <Loader />
    } else if (isFailure(parentships)) {
      return <div>{i18n.common.loadingFailed}</div>
    }

    return (
      <Table>
        <Thead>
          <Tr>
            <Th>{i18n.childInformation.fridgeParents.name}</Th>
            <Th>{i18n.childInformation.fridgeParents.ssn}</Th>
            <Th>{i18n.childInformation.fridgeParents.startDate}</Th>
            <Th>{i18n.childInformation.fridgeParents.endDate}</Th>
            <Th>{i18n.childInformation.fridgeParents.status}</Th>
          </Tr>
        </Thead>
        <Tbody>
          {_.orderBy(parentships.data, ['startDate'], ['desc']).map(
            (parentship: Parentship) => (
              <Tr key={parentship.id}>
                <Td>
                  <Link to={`/profile/${parentship.headOfChildId}`}>
                    {parentship.headOfChild.lastName}{' '}
                    {parentship.headOfChild.firstName}
                  </Link>
                </Td>
                <Td>{parentship.headOfChild.socialSecurityNumber}</Td>
                <Td>{parentship.startDate.format()}</Td>
                <Td>{parentship.endDate?.format()}</Td>
                <Td>
                  <StatusLabel
                    status={
                      parentship.conflict
                        ? 'conflict'
                        : getStatusLabelByDateRange(parentship)
                    }
                  />
                </Td>
              </Tr>
            )
          )}
        </Tbody>
      </Table>
    )
  }

  return (
    <div className="fridge-parents-section">
      <CollapsibleSection
        icon={faUser}
        title={i18n.childInformation.fridgeParents.title}
        startCollapsed={!open}
      >
        {renderFridgeParents()}
      </CollapsibleSection>
    </div>
  )
})

export default FridgeParents
