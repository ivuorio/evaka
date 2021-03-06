// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useContext, useState } from 'react'
import _ from 'lodash'

import {
  Table,
  Td,
  Th,
  Tr,
  Thead,
  Tbody
} from '~components/shared/layout/Table'
import Title from '~components/shared/atoms/Title'
import InlineButton from '~components/shared/atoms/buttons/InlineButton'
import { faArrowRight } from 'icon-set'
import { useTranslation } from '~state/i18n'
import { DaycareGroup } from '~types/unit'
import GroupPlacementModal from '~components/unit/tab-groups/missing-group-placements/GroupPlacementModal'
import { UIContext } from '~state/ui'
import { Translations } from '~assets/i18n'
import { Link } from 'react-router-dom'
import CareTypeLabel, {
  careTypesFromPlacementType
} from '~components/common/CareTypeLabel'
import { UnitBackupCare } from '~types/child'
import BackupCareGroupModal from './missing-group-placements/BackupCareGroupModal'
import { formatName } from '~utils'
import PlacementCircle from '~components/shared/atoms/PlacementCircle'
import { MissingGroupPlacement } from '~api/unit'

function renderMissingGroupPlacementRow(
  missingPlacement: MissingGroupPlacement,
  onAddToGroup: () => void,
  i18n: Translations,
  savePosition: () => void,
  canManageChildren: boolean
) {
  const {
    childId,
    firstName,
    lastName,
    dateOfBirth,
    placementPeriod,
    gap,
    placementType
  } = missingPlacement

  return (
    <Tr
      key={`${childId}:${gap.start.formatIso()}`}
      data-qa="missing-placement-row"
    >
      <Td data-qa="child-name">
        <Link to={`/child-information/${childId}`} onClick={savePosition}>
          {formatName(firstName, lastName, i18n, true)}
        </Link>
      </Td>
      <Td data-qa="child-dob">{dateOfBirth.format()}</Td>
      <Td data-qa="placement-type">
        {placementType ? (
          careTypesFromPlacementType(placementType)
        ) : (
          <CareTypeLabel type="backup-care" />
        )}
      </Td>
      <Td data-qa="placement-subtype">
        {placementType && <PlacementCircle type={placementType} />}
      </Td>
      <Td data-qa="placement-duration">
        {`${placementPeriod.start.format()} - ${placementPeriod.end.format()}`}
      </Td>
      {canManageChildren ? (
        <Td data-qa="group-missing-duration">
          {`${gap.start.format()} - ${gap.end.format()}`}
        </Td>
      ) : null}
      {canManageChildren ? (
        <Td>
          <InlineButton
            onClick={() => onAddToGroup()}
            icon={faArrowRight}
            dataQa="add-to-group-btn"
            text={i18n.unit.placements.addToGroup}
          />
        </Td>
      ) : null}
    </Tr>
  )
}

type Props = {
  canManageChildren: boolean
  groups: DaycareGroup[]
  missingGroupPlacements: MissingGroupPlacement[]
  backupCares: UnitBackupCare[]
  savePosition: () => void
  reloadUnitData: () => void
}

export default React.memo(function MissingGroupPlacements({
  canManageChildren,
  groups,
  missingGroupPlacements,
  backupCares,
  savePosition,
  reloadUnitData
}: Props) {
  const { i18n } = useTranslation()
  const { uiMode, toggleUiMode } = useContext(UIContext)
  const [
    activeMissingPlacement,
    setActiveMissingPlacement
  ] = useState<MissingGroupPlacement | null>(null)

  const addPlacementToGroup = (missingPlacement: MissingGroupPlacement) => {
    setActiveMissingPlacement(missingPlacement)
    if (missingPlacement.backup) {
      toggleUiMode('backup-care-group')
    } else {
      toggleUiMode('group-placement')
    }
  }

  const sortedRows = _.sortBy(missingGroupPlacements, [
    (p: MissingGroupPlacement) => p.lastName,
    (p: MissingGroupPlacement) => p.firstName,
    (p: MissingGroupPlacement) => p.placementPeriod.start,
    (p: MissingGroupPlacement) => p.gap.start
  ])

  const activeBackupCare =
    activeMissingPlacement &&
    backupCares.find((bc) => bc.id === activeMissingPlacement.placementId)

  return (
    <>
      <Title size={2}>{i18n.unit.placements.title}</Title>
      <div
        className="table-of-missing-groups"
        data-qa="table-of-missing-groups"
      >
        <Table data-qa="table-of-missing-groups">
          <Thead>
            <Tr>
              <Th>{i18n.unit.placements.name}</Th>
              <Th>{i18n.unit.placements.birthday}</Th>
              <Th>{i18n.unit.placements.type}</Th>
              <Th>{i18n.unit.placements.subtype}</Th>
              <Th>{i18n.unit.placements.placementDuration}</Th>
              {canManageChildren ? (
                <Th>{i18n.unit.placements.missingGroup}</Th>
              ) : null}
              {canManageChildren ? <Th /> : null}
            </Tr>
          </Thead>
          <Tbody>
            {sortedRows.map((row) =>
              renderMissingGroupPlacementRow(
                row,
                () => addPlacementToGroup(row),
                i18n,
                savePosition,
                canManageChildren
              )
            )}
          </Tbody>
        </Table>
      </div>
      {uiMode == 'group-placement' && activeMissingPlacement && (
        <GroupPlacementModal
          groups={groups}
          missingPlacement={activeMissingPlacement}
          reload={reloadUnitData}
        />
      )}
      {uiMode == 'backup-care-group' && activeBackupCare && (
        <BackupCareGroupModal
          backupCare={activeBackupCare}
          groups={groups}
          reload={reloadUnitData}
        />
      )}
    </>
  )
})
