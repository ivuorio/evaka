// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useContext, useEffect, Fragment } from 'react'
import LocalDate from '@evaka/lib-common/src/local-date'
import {
  AreaFilter,
  Filters,
  InvoiceStatusFilter,
  UnitFilter,
  InvoiceDistinctionsFilter,
  InvoiceDateFilter
} from '../common/Filters'
import { InvoicingUiContext } from '../../state/invoicing-ui'
import { isSuccess } from '../../api'
import { getAreas, getUnits } from '../../api/daycare'
import { InvoiceStatus, InvoiceDistinctiveDetails } from '../../types/invoicing'
import { Gap } from '~components/shared/layout/white-space'
import { useTranslation } from '~state/i18n'

function InvoiceFilters() {
  const {
    invoices: {
      searchFilters,
      setSearchFilters,
      searchTerms,
      setSearchTerms,
      clearSearchFilters
    },
    shared: { units, setUnits, availableAreas, setAvailableAreas }
  } = useContext(InvoicingUiContext)

  const { i18n } = useTranslation()

  useEffect(() => {
    void getAreas().then(setAvailableAreas)
  }, [])

  useEffect(() => {
    void getUnits([]).then(setUnits)
  }, [])

  // remove selected unit filter if the unit is not included in the selected areas
  useEffect(() => {
    if (
      searchFilters.unit &&
      isSuccess(units) &&
      !units.data.map(({ id }) => id).includes(searchFilters.unit)
    ) {
      setSearchFilters({ ...searchFilters, unit: undefined })
    }
  }, [units])

  const toggleArea = (code: string) => () => {
    searchFilters.area.includes(code)
      ? setSearchFilters({
          ...searchFilters,
          area: searchFilters.area.filter((v) => v !== code)
        })
      : setSearchFilters({
          ...searchFilters,
          area: [...searchFilters.area, code]
        })
  }

  const selectUnit = (id: string) =>
    setSearchFilters((filters) => ({ ...filters, unit: id }))

  const toggleStatus = (id: InvoiceStatus) => () => {
    setSearchFilters({
      ...searchFilters,
      status: id
    })
  }

  const toggleServiceNeed = (id: InvoiceDistinctiveDetails) => () => {
    searchFilters.distinctiveDetails.includes(id)
      ? setSearchFilters({
          ...searchFilters,
          distinctiveDetails: searchFilters.distinctiveDetails.filter(
            (v) => v !== id
          )
        })
      : setSearchFilters({
          ...searchFilters,
          distinctiveDetails: [...searchFilters.distinctiveDetails, id]
        })
  }

  const setStartDate = (startDate: LocalDate | undefined) => {
    setSearchFilters({
      ...searchFilters,
      startDate: startDate
    })
  }

  const setEndDate = (endDate: LocalDate | undefined) => {
    setSearchFilters({
      ...searchFilters,
      endDate: endDate
    })
  }

  const setUseCustomDatesForInvoiceSending = (value: boolean) => {
    setSearchFilters({
      ...searchFilters,
      useCustomDatesForInvoiceSending: value
    })
  }

  return (
    <Filters
      searchPlaceholder={i18n.filters.freeTextPlaceholder}
      freeText={searchTerms}
      setFreeText={setSearchTerms}
      clearFilters={clearSearchFilters}
      column1={
        <>
          <Gap size="s" />
          <AreaFilter
            areas={isSuccess(availableAreas) ? availableAreas.data : []}
            toggled={searchFilters.area}
            toggle={toggleArea}
          />
          <Gap size="L" />
          <UnitFilter
            units={
              isSuccess(units)
                ? units.data.map(({ id, name }) => ({ id, label: name }))
                : []
            }
            selected={
              isSuccess(units)
                ? units.data
                    .map(({ id, name }) => ({ id, label: name }))
                    .filter((unit) => unit.id === searchFilters.unit)[0]
                : undefined
            }
            select={selectUnit}
          />
        </>
      }
      column2={
        <Fragment>
          <Gap size="s" />
          <InvoiceDistinctionsFilter
            toggled={searchFilters.distinctiveDetails}
            toggle={toggleServiceNeed}
          />
        </Fragment>
      }
      column3={
        <Fragment>
          <Gap size="s" />
          <InvoiceStatusFilter
            toggled={searchFilters.status}
            toggle={toggleStatus}
          />
          <Gap size="L" />
          <InvoiceDateFilter
            startDate={searchFilters.startDate}
            setStartDate={setStartDate}
            endDate={searchFilters.endDate}
            setEndDate={setEndDate}
            searchByStartDate={searchFilters.useCustomDatesForInvoiceSending}
            setUseCustomDatesForInvoiceSending={
              setUseCustomDatesForInvoiceSending
            }
          />
        </Fragment>
      }
    />
  )
}

export default InvoiceFilters
