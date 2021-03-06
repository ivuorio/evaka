// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import styled from 'styled-components'
import { useHistory } from 'react-router-dom'
import {
  Table,
  Tr,
  Th,
  Td,
  Thead,
  Tbody,
  SortableTh
} from '~components/shared/layout/Table'
import Title from '~components/shared/atoms/Title'
import Loader from '~components/shared/atoms/Loader'
import Checkbox from '~components/shared/atoms/form/Checkbox'
import NameWithSsn from '../common/NameWithSsn'
import ChildrenCell from '../common/ChildrenCell'
import { useTranslation } from '../../state/i18n'
import { FeeDecisionSummary } from '../../types/invoicing'
import { SearchOrder } from '~types'
import { isFailure, isLoading, isSuccess, Result } from '../../api'
import { formatDate } from '../../utils/date'
import { formatCents } from '../../utils/money'
import { SortByFeeDecisions } from '../../api/invoicing'
import Pagination from '~components/shared/Pagination'

const TitleRowContainer = styled.div`
  display: flex;
  justify-content: space-between;
  margin-bottom: 1.5rem;
`

const ResultsContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: flex-end;
`

const SectionTitle = styled(Title)`
  margin-bottom: 0 !important;
`

interface Props {
  decisions?: Result<FeeDecisionSummary[]>
  total?: number
  pages?: number
  currentPage: number
  setPage: (page: number) => void
  sortBy: SortByFeeDecisions
  setSortBy: (v: SortByFeeDecisions) => void
  sortDirection: SearchOrder
  setSortDirection: (v: SearchOrder) => void
  showCheckboxes: boolean
  checked: { [id: string]: boolean }
  toggleChecked: (id: string) => void
  checkAll: () => void
  clearChecked: () => void
}

const FeeDecisions = React.memo(function FeeDecisions({
  decisions,
  total,
  pages,
  currentPage,
  setPage,
  sortBy,
  setSortBy,
  sortDirection,
  setSortDirection,
  showCheckboxes,
  checked,
  toggleChecked,
  checkAll,
  clearChecked
}: Props) {
  const { i18n } = useTranslation()
  const history = useHistory()

  const allChecked =
    decisions && isSuccess(decisions)
      ? decisions.data.length > 0 &&
        decisions.data.every((it) => checked[it.id])
      : false

  const isSorted = (column: SortByFeeDecisions) =>
    sortBy === column ? sortDirection : undefined

  const toggleSort = (column: SortByFeeDecisions) => () => {
    if (sortBy === column) {
      setSortDirection(sortDirection === 'ASC' ? 'DESC' : 'ASC')
    } else {
      setSortBy(column)
      setSortDirection('ASC')
    }
  }

  const rows =
    decisions && isSuccess(decisions)
      ? decisions.data.map((item) => {
          return (
            <Tr
              key={item.id}
              onClick={() => history.push(`/finance/fee-decisions/${item.id}`)}
              data-qa="table-fee-decision-row"
            >
              <Td>
                <NameWithSsn {...item.headOfFamily} i18n={i18n} />
              </Td>
              <Td>
                <ChildrenCell people={item.parts.map(({ child }) => child)} />
              </Td>
              <Td>{`${item.validFrom.format()} - ${
                item.validTo?.format() ?? ''
              }`}</Td>
              <Td>{formatCents(item.finalPrice)}</Td>
              <Td>{item.decisionNumber}</Td>
              <Td>{formatDate(item.createdAt)}</Td>
              <Td>{formatDate(item.sentAt)}</Td>
              <Td>{i18n.feeDecision.status[item.status]}</Td>
              {showCheckboxes ? (
                <Td onClick={(e) => e.stopPropagation()}>
                  <Checkbox
                    label={item.id}
                    hiddenLabel
                    checked={!!checked[item.id]}
                    onChange={() => toggleChecked(item.id)}
                    dataQa="toggle-decision"
                  />
                </Td>
              ) : null}
            </Tr>
          )
        })
      : null

  return (
    <div className="fee-decisions">
      <TitleRowContainer>
        <SectionTitle size={1}>{i18n.feeDecisions.table.title}</SectionTitle>
        {decisions && isSuccess(decisions) && (
          <ResultsContainer>
            <div>{total ? i18n.feeDecisions.table.rowCount(total) : null}</div>
            <Pagination
              pages={pages}
              currentPage={currentPage}
              setPage={setPage}
            />
          </ResultsContainer>
        )}
      </TitleRowContainer>
      <Table data-qa="table-of-decisions">
        <Thead>
          <Tr>
            <SortableTh
              sorted={isSorted('HEAD_OF_FAMILY')}
              onClick={toggleSort('HEAD_OF_FAMILY')}
            >
              {i18n.feeDecisions.table.head}
            </SortableTh>
            <Th>{i18n.feeDecisions.table.children}</Th>
            <SortableTh
              sorted={isSorted('VALIDITY')}
              onClick={toggleSort('VALIDITY')}
            >
              {i18n.feeDecisions.table.validity}
            </SortableTh>
            <SortableTh
              sorted={isSorted('FINAL_PRICE')}
              onClick={toggleSort('FINAL_PRICE')}
            >
              {i18n.feeDecisions.table.price}
            </SortableTh>
            <SortableTh
              sorted={isSorted('NUMBER')}
              onClick={toggleSort('NUMBER')}
            >
              {i18n.feeDecisions.table.number}
            </SortableTh>
            <SortableTh
              sorted={isSorted('CREATED')}
              onClick={toggleSort('CREATED')}
            >
              {i18n.feeDecisions.table.createdAt}
            </SortableTh>
            <SortableTh sorted={isSorted('SENT')} onClick={toggleSort('SENT')}>
              {i18n.feeDecisions.table.sentAt}
            </SortableTh>
            <SortableTh
              sorted={isSorted('STATUS')}
              onClick={toggleSort('STATUS')}
            >
              {i18n.feeDecisions.table.status}
            </SortableTh>
            {showCheckboxes ? (
              <Td>
                <Checkbox
                  label="all"
                  hiddenLabel
                  checked={allChecked}
                  onChange={allChecked ? clearChecked : checkAll}
                  dataQa="toggle-all-decisions"
                />
              </Td>
            ) : null}
          </Tr>
        </Thead>
        <Tbody>{rows}</Tbody>
      </Table>
      {decisions && isSuccess(decisions) && (
        <ResultsContainer>
          <Pagination
            pages={pages}
            currentPage={currentPage}
            setPage={setPage}
          />
        </ResultsContainer>
      )}
      {decisions && isLoading(decisions) && <Loader />}
      {decisions && isFailure(decisions) && (
        <div>{i18n.common.error.unknown}</div>
      )}
    </div>
  )
})

export default FeeDecisions
