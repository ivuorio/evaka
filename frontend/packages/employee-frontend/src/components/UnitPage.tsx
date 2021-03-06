// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from 'react'
import { Redirect, Route, Switch, useParams } from 'react-router-dom'
import { useTranslation } from '~state/i18n'
import { RouteWithTitle } from '~components/RouteWithTitle'
import { Gap } from '~components/shared/layout/white-space'
import Tabs from '~components/shared/molecules/Tabs'
import { UUID } from '~types'
import TabUnitInformation from '~components/unit/TabUnitInformation'
import TabGroups from '~components/unit/TabGroups'
import { TitleContext, TitleState } from '~state/title'
import { isSuccess } from '~api'
import { getDaycare, getUnitData } from '~api/unit'
import { useRestApi } from '~utils/useRestApi'
import Container from '~components/shared/layout/Container'
import { UnitContext } from '~state/unit'
import TabPlacementProposals from '~components/unit/TabPlacementProposals'
import TabWaitingConfirmation from '~components/unit/TabWaitingConfirmation'
import TabApplications from '~components/unit/TabApplications'
import { requireRole } from '~utils/roles'
import { UserContext } from '~state/user'
import { useQuery } from '~utils/useQuery'
import LocalDate from '@evaka/lib-common/src/local-date'

export default React.memo(function UnitPage() {
  const { id } = useParams<{ id: UUID }>()
  const { i18n } = useTranslation()
  const { roles } = useContext(UserContext)
  const { setTitle } = useContext<TitleState>(TitleContext)
  const {
    unitInformation,
    setUnitInformation,
    unitData,
    setUnitData,
    filters,
    setFilters,
    savePosition,
    scrollToPosition
  } = useContext(UnitContext)

  const loadUnitInformation = useRestApi(getDaycare, setUnitInformation)

  const query = useQuery()
  useEffect(() => {
    if (query.has('start')) {
      const queryStart = LocalDate.parseIso(query.get('start') ?? '')
      setFilters(filters.withStartDate(queryStart))
    }
  }, [])

  useEffect(() => loadUnitInformation(id), [id])
  useEffect(() => {
    if (isSuccess(unitInformation)) {
      setTitle(unitInformation.data.daycare.name)
    }
  }, [unitInformation])

  const loadUnitData = useRestApi(getUnitData, setUnitData)
  const loadUnitDataWithFixedPosition = () => {
    savePosition()
    loadUnitData(id, filters.startDate, filters.endDate)
  }

  useEffect(() => {
    loadUnitDataWithFixedPosition()
  }, [filters])

  useEffect(() => {
    if (isSuccess(unitData)) {
      scrollToPosition()
    }
  }, [unitData])

  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({})

  const tabs = useMemo(
    () => [
      {
        id: 'unit-info',
        link: `/units/${id}/unit-info`,
        label: i18n.unit.tabs.unitInfo
      },
      {
        id: 'groups',
        link: `/units/${id}/groups`,
        label: i18n.unit.tabs.groups,
        counter:
          isSuccess(unitData) && unitData.data.missingGroupPlacements.length
            ? unitData.data.missingGroupPlacements.length
            : undefined
      },
      ...(requireRole(
        roles,
        'ADMIN',
        'SERVICE_WORKER',
        'FINANCE_ADMIN',
        'UNIT_SUPERVISOR'
      )
        ? [
            {
              id: 'waiting-confirmation',
              link: `/units/${id}/waiting-confirmation`,
              label: i18n.unit.tabs.waitingConfirmation
            },
            {
              id: 'placement-proposals',
              link: `/units/${id}/placement-proposals`,
              label: i18n.unit.tabs.placementProposals,
              counter:
                isSuccess(unitData) && unitData.data.placementProposals?.length
                  ? unitData.data.placementProposals?.length
                  : undefined
            },
            {
              id: 'applications',
              link: `/units/${id}/applications`,
              label: i18n.unit.tabs.applications
            }
          ]
        : [])
    ],
    [i18n, unitData]
  )

  const RedirectToUnitInfo = useCallback(
    () => <Redirect to={`/units/${id}/unit-info`} />,
    [id]
  )

  return (
    <>
      <Gap size="s" />
      <Tabs tabs={tabs} />
      <Gap size="s" />
      <Container>
        <Switch>
          <RouteWithTitle
            exact
            path="/units/:id/unit-info"
            component={TabUnitInformation}
          />
          <RouteWithTitle
            exact
            path="/units/:id/groups"
            component={() => (
              <TabGroups
                reloadUnitData={loadUnitDataWithFixedPosition}
                openGroups={openGroups}
                setOpenGroups={setOpenGroups}
              />
            )}
          />
          <RouteWithTitle
            exact
            path="/units/:id/waiting-confirmation"
            component={TabWaitingConfirmation}
          />
          <RouteWithTitle
            exact
            path="/units/:id/placement-proposals"
            component={() => (
              <TabPlacementProposals
                reloadUnitData={loadUnitDataWithFixedPosition}
              />
            )}
          />
          <RouteWithTitle
            exact
            path="/units/:id/applications"
            component={TabApplications}
          />
          <Route path="/" component={RedirectToUnitInfo} />
        </Switch>
      </Container>
    </>
  )
})
