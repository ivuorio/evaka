// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useEffect, useState } from 'react'
import ReactSelect from 'react-select'
import styled from 'styled-components'
import { Link } from 'react-router-dom'

import { Container, ContentArea } from '~components/shared/layout/Container'
import Loader from '~components/shared/atoms/Loader'
import Title from '~components/shared/atoms/Title'
import { Th, Tr, Td, Thead, Tbody } from '~components/shared/layout/Table'
import { reactSelectStyles } from '~components/shared/utils'
import { Translations, useTranslation } from '~state/i18n'
import { isFailure, isLoading, isSuccess, Loading, Result, Success } from '~api'
import { VoucherServiceProviderRow } from '~types/reports'
import {
  getVoucherServiceProvidersReport,
  VoucherServiceProvidersFilters
} from '~api/reports'
import ReturnButton from 'components/shared/atoms/buttons/ReturnButton'
import ReportDownload from '~components/reports/ReportDownload'
import { addDays, isWeekend, lastDayOfMonth } from 'date-fns'
import { formatDate } from '~utils/date'
import { SelectOptionProps } from '~components/common/Select'
import { fi } from 'date-fns/locale'
import { CareArea } from '~types/unit'
import { getAreas } from '~api/daycare'
import {
  FilterLabel,
  FilterRow,
  TableScrollable
} from '~components/reports/common'
import { FlexRow } from 'components/common/styled/containers'

const StyledTd = styled(Td)`
  white-space: nowrap;
`

const Wrapper = styled.div`
  width: 100%;
`

function monthOptions(): SelectOptionProps[] {
  const monthOptions = []
  for (let i = 1; i <= 12; i++) {
    monthOptions.push({
      value: i.toString(),
      label: String(fi.localize?.month(i - 1))
    })
  }
  return monthOptions
}

function yearOptions(): SelectOptionProps[] {
  const currentYear = new Date().getFullYear()
  const yearOptions = []
  for (let year = currentYear + 2; year > currentYear - 5; year--) {
    yearOptions.push({
      value: year.toString(),
      label: year.toString()
    })
  }
  return yearOptions
}

function getDisplayDates(year: number, month: number) {
  const fromDate = new Date(year, month - 1, 1)
  const toDate = lastDayOfMonth(fromDate)
  const dates: Date[] = []
  for (let date = fromDate; date <= toDate; date = addDays(date, 1)) {
    if (!isWeekend(date)) dates.push(date)
  }
  return dates
}

function getFilename(
  i18n: Translations,
  year: number,
  month: number,
  careAreaName: string
) {
  const time = formatDate(new Date(year, month - 1, 1), 'yyyy-MM')
  return `${time}-${careAreaName}.csv`.replace(/ /g, '_')
}

function getDisplayCells(
  reportRows: VoucherServiceProviderRow[],
  dates: Date[]
): string[][] {
  console.log(reportRows, dates)
  return ['TODO']['TODO'] as string[][]
  /*
  TODO
  return reportRows.map((row) => {

      const cells = [row.unitName, row.groupName].filter(
        (cell) => cell !== undefined
      ) as string[]
      for (const date of dates) {
        const occupancy = row.occupancies[formatDate(date, DATE_FORMAT_ISO)]
        cells.push(
          typeof occupancy?.sum === 'number'
            ? occupancy.sum.toFixed(2).replace('.', ',')
            : '0'
        )
        cells.push(
          typeof occupancy?.caretakers === 'number'
            ? occupancy.caretakers.toFixed(2).replace('.', ',')
            : '0'
        )
      }
      return cells
  })
  */
}

function VoucherServiceProviders() {
  const { i18n } = useTranslation()
  const [rows, setRows] = useState<Result<VoucherServiceProviderRow[]>>(
    Success([])
  )
  const [areas, setAreas] = useState<CareArea[]>([])
  const [filters, setFilters] = useState<VoucherServiceProvidersFilters>({
    year: new Date().getFullYear(),
    month: new Date().getMonth() + 1,
    careAreaId: ''
  })

  useEffect(() => {
    void getAreas().then((res) => isSuccess(res) && setAreas(res.data))
  }, [])

  useEffect(() => {
    if (filters.careAreaId == '') return

    setRows(Loading())
    void getVoucherServiceProvidersReport(filters).then(setRows)
  }, [filters])

  const dates = getDisplayDates(filters.year, filters.month)
  const displayCells: string[][] = isSuccess(rows)
    ? getDisplayCells(rows.data, dates)
    : []
  const dateCols = dates.reduce((cols, date) => {
    cols.push(date)
    // TODO
    //if (usedValues === 'raw') cols.push(date)
    return cols
  }, [] as Date[])

  const months = monthOptions()
  const years = yearOptions()

  return (
    <Container>
      <ReturnButton />
      <ContentArea opaque>
        <Title size={1}>{i18n.reports.voucherServiceProviders.title}</Title>
        <FilterRow>
          <FilterLabel>{i18n.reports.common.period}</FilterLabel>
          <FlexRow>
            <Wrapper>
              <ReactSelect
                options={months}
                value={months.find(
                  (opt) => opt.value === filters.month.toString()
                )}
                onChange={(value) => {
                  if (value && 'value' in value) {
                    const month = parseInt(value.value)
                    setFilters({ ...filters, month })
                  }
                }}
                styles={reactSelectStyles}
              />
            </Wrapper>
            <Wrapper>
              <ReactSelect
                options={years}
                value={years.find(
                  (opt) => opt.value === filters.year.toString()
                )}
                onChange={(value) => {
                  if (value && 'value' in value) {
                    const year = parseInt(value.value)
                    setFilters({ ...filters, year })
                  }
                }}
                styles={reactSelectStyles}
              />
            </Wrapper>
          </FlexRow>
        </FilterRow>
        <FilterRow>
          <FilterLabel>{i18n.reports.common.careAreaName}</FilterLabel>
          <Wrapper>
            <ReactSelect
              options={[
                ...areas.map((area) => ({ value: area.id, label: area.name }))
              ]}
              onChange={(value) => {
                if (value && 'value' in value) {
                  setFilters({ ...filters, careAreaId: value.value })
                }
              }}
              styles={reactSelectStyles}
            />
          </Wrapper>
        </FilterRow>
        {isLoading(rows) && <Loader />}
        {isFailure(rows) && <span>{i18n.common.loadingFailed}</span>}
        {isSuccess(rows) && filters.careAreaId != '' && (
          <>
            <ReportDownload
              data={[
                [
                  i18n.reports.common.unitName,
                  i18n.reports.voucherServiceProviders.average,
                  ...dateCols.map((date) => formatDate(date, 'dd.MM.'))
                ].filter((label) => label !== undefined),
                ...displayCells
              ]}
              filename={getFilename(
                i18n,
                filters.year,
                filters.month,
                areas.find((area) => area.id == filters.careAreaId)?.name ?? ''
              )}
            />
            <TableScrollable>
              <Thead>
                <Tr>
                  <Th>{i18n.reports.common.careAreaName}</Th>
                  <Th>{i18n.reports.common.unitName}</Th>
                  <Th>{i18n.reports.voucherServiceProviders.childCount}</Th>
                  <Th>{i18n.reports.voucherServiceProviders.unitVoucherSum}</Th>
                  <Th>{i18n.reports.voucherServiceProviders.detailsLink}</Th>
                </Tr>
              </Thead>
              <Tbody>
                {rows.data.map((row) => (
                  <Tr key={row.unitId}>
                    <StyledTd>{row.careAreaName}</StyledTd>
                    <StyledTd>
                      <Link to={`/units/${row.unitId}`}>{row.unitName}</Link>
                    </StyledTd>
                    <StyledTd>{row.voucherChildCount}</StyledTd>
                    <StyledTd>{row.voucherSum}</StyledTd>
                    <StyledTd>{row.voucherChildCount}</StyledTd>
                    <StyledTd>{row.unitVoucherReportUri}</StyledTd>
                  </Tr>
                ))}
              </Tbody>
            </TableScrollable>
          </>
        )}
      </ContentArea>
    </Container>
  )
}

export default VoucherServiceProviders
