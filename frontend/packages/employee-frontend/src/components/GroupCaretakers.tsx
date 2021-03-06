// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useContext, useEffect, useState } from 'react'

import { Container, ContentArea } from '~components/shared/layout/Container'
import { Table, Tbody, Td, Th, Thead, Tr } from 'components/shared/layout/Table'
import Loader from '~components/shared/atoms/Loader'
import Title from '~components/shared/atoms/Title'
import Button from '~components/shared/atoms/buttons/Button'
import IconButton from '~components/shared/atoms/buttons/IconButton'
import { RouteComponentProps } from 'react-router'
import { UUID } from '~types'
import { isFailure, isLoading, isSuccess, Loading, Result } from '~api'
import { CaretakerAmount, CaretakersResponse } from '~types/caretakers'
import { deleteCaretakers, getCaretakers } from '~api/caretakers'
import { TitleContext, TitleState } from '~state/title'
import { capitalizeFirstLetter } from '~utils'
import { getStatusLabelByDateRange } from '~utils/date'
import StatusLabel from '~components/common/StatusLabel'
import styled from 'styled-components'
import { faPen, faQuestion, faTrash } from 'icon-set'
import GroupCaretakersModal from '~components/group-caretakers/GroupCaretakersModal'
import InfoModal from '~components/common/InfoModal'
import { useTranslation } from '~state/i18n'
import ReturnButton from 'components/shared/atoms/buttons/ReturnButton'
import { FixedSpaceRow } from './shared/layout/flex-helpers'

const NarrowContainer = styled.div`
  max-width: 900px;
`

const StyledTd = styled(Td)`
  vertical-align: middle !important;
  padding: 8px 16px !important;
`

const StatusTd = styled(StyledTd)`
  width: 30%;
  vertical-align: middle !important;
  padding: 8px 16px !important;

  > div {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
`

const FlexRow = styled.div`
  display: flex;
`

const FlexRowRightAlign = styled(FlexRow)`
  justify-content: flex-end;
`

type Props = RouteComponentProps<{ unitId: UUID; groupId: UUID }>

function GroupCaretakers({
  match: {
    params: { unitId, groupId }
  }
}: Props) {
  const { i18n } = useTranslation()
  const { setTitle } = useContext<TitleState>(TitleContext)
  const [caretakers, setCaretakers] = useState<Result<CaretakersResponse>>(
    Loading()
  )
  const [modalOpen, setModalOpen] = useState<boolean>(false)
  const [rowToDelete, setRowToDelete] = useState<CaretakerAmount | null>(null)
  const [rowToEdit, setRowToEdit] = useState<CaretakerAmount | null>(null)

  const loadData = () => {
    setCaretakers(Loading())
    void getCaretakers(unitId, groupId).then((response) => {
      setCaretakers(response)
      if (isSuccess(response)) setTitle(response.data.groupName)
    })
  }

  useEffect(() => {
    loadData()
  }, [unitId, groupId])

  const deleteRow = (id: UUID) => {
    void deleteCaretakers(unitId, groupId, id).then(loadData)
    setRowToDelete(null)
  }

  return (
    <Container>
      <ReturnButton />
      <ContentArea opaque>
        {isLoading(caretakers) && <Loader />}
        {isFailure(caretakers) && <span>{i18n.common.error.unknown}</span>}
        {isSuccess(caretakers) && (
          <NarrowContainer>
            <Title size={2}>{i18n.groupCaretakers.title}</Title>
            <Title size={3}>
              {caretakers.data.unitName} |{' '}
              {capitalizeFirstLetter(caretakers.data.groupName)}
            </Title>
            <p>{i18n.groupCaretakers.info}</p>
            <FlexRowRightAlign>
              <Button
                onClick={() => setModalOpen(true)}
                text={i18n.groupCaretakers.create}
              />
            </FlexRowRightAlign>
            <Table>
              <Thead>
                <Tr>
                  <Th>{i18n.groupCaretakers.startDate}</Th>
                  <Th>{i18n.groupCaretakers.endDate}</Th>
                  <Th>{i18n.groupCaretakers.amount}</Th>
                  <Th>{i18n.groupCaretakers.status}</Th>
                </Tr>
              </Thead>
              <Tbody>
                {caretakers.data.caretakers.map((row) => (
                  <Tr key={row.id}>
                    <StyledTd>{row.startDate.format()}</StyledTd>
                    <StyledTd>
                      {row.endDate ? row.endDate.format() : ''}
                    </StyledTd>
                    <StyledTd>
                      {row.amount.toLocaleString()}{' '}
                      {i18n.groupCaretakers.amountUnit}
                    </StyledTd>
                    <StatusTd>
                      <div>
                        <StatusLabel status={getStatusLabelByDateRange(row)} />
                        <FixedSpaceRow>
                          <IconButton
                            onClick={() => {
                              setRowToEdit(row)
                              setModalOpen(true)
                            }}
                            icon={faPen}
                          />
                          <IconButton
                            onClick={() => setRowToDelete(row)}
                            icon={faTrash}
                          />
                        </FixedSpaceRow>
                      </div>
                    </StatusTd>
                  </Tr>
                ))}
              </Tbody>
            </Table>

            {modalOpen && (
              <GroupCaretakersModal
                unitId={unitId}
                groupId={groupId}
                existing={rowToEdit}
                onSuccess={() => {
                  loadData()
                  setModalOpen(false)
                  setRowToEdit(null)
                }}
                onReject={() => {
                  setModalOpen(false)
                  setRowToEdit(null)
                }}
              />
            )}

            {rowToDelete && (
              <InfoModal
                iconColour={'orange'}
                title={i18n.groupCaretakers.confirmDelete}
                resolveLabel={i18n.common.remove}
                rejectLabel={i18n.common.cancel}
                icon={faQuestion}
                resolve={() => deleteRow(rowToDelete?.id)}
                reject={() => setRowToDelete(null)}
              />
            )}
          </NarrowContainer>
        )}
      </ContentArea>
    </Container>
  )
}

export default GroupCaretakers
