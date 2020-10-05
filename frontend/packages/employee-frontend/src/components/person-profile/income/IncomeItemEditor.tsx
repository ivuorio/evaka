// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useState } from 'react'
import styled from 'styled-components'

import LocalDate from '@evaka/lib-common/src/local-date'
import AsyncButton from '~components/shared/atoms/buttons/AsyncButton'
import ListGrid from '~components/shared/layout/ListGrid'
import Button from '~components/shared/atoms/buttons/Button'
import Checkbox from '~components/shared/atoms/form/Checkbox'
import Radio from '~components/shared/atoms/form/Radio'
import Title from '~components/shared/atoms/Title'
import {
  FixedSpaceColumn,
  FixedSpaceRow
} from '~components/shared/layout/flex-helpers'
import { Label, LabelText } from '~components/common/styled/common'
import DateRangeInput from '../../common/DateRangeInput'
import IncomeTable from './IncomeTable'
import { useTranslation } from '~state/i18n'
import { incomeEffects, Income, PartialIncome } from '~types/income'
import { formatDate } from '~utils/date'
import { Gap } from '~components/shared/layout/white-space'

const ButtonsContainer = styled(FixedSpaceRow)`
  margin: 20px 0;
`

const emptyIncome: PartialIncome = {
  effect: 'INCOME',
  data: {} as Partial<Income['data']>,
  isEntrepreneur: false,
  worksAtECHA: false,
  notes: '',
  validFrom: LocalDate.today(),
  validTo: undefined
}

interface Props {
  baseIncome?: Income
  cancel: () => void
  update: (income: Income) => Promise<void>
  create: (income: PartialIncome) => Promise<void>
  onSuccess: () => void
}

const IncomeItemEditor = React.memo(function IncomeItemEditor({
  baseIncome,
  cancel,
  update,
  create,
  onSuccess
}: Props) {
  const { i18n } = useTranslation()

  const [editedIncome, setEditedIncome] = useState<PartialIncome>(
    baseIncome || emptyIncome
  )

  const [validationErrors, setValidationErrors] = useState<
    Partial<{ [K in keyof Income | 'dates']: boolean }>
  >({})

  return (
    <>
      <div data-qa="income-date-range">
        <Label>
          <LabelText>{i18n.personProfile.income.details.dateRange}</LabelText>
        </Label>
        <Gap size={'m'} />
        <DateRangeInput
          start={editedIncome.validFrom}
          end={editedIncome.validTo}
          onChange={(from: LocalDate, to?: LocalDate) =>
            setEditedIncome((prev) => ({
              ...prev,
              validFrom: from,
              validTo: to
            }))
          }
          onValidationResult={(hasErrors) =>
            setValidationErrors((prev) => ({ ...prev, dates: hasErrors }))
          }
          nullableEndDate
        />
      </div>
      <Gap size={'L'} />
      <Label>
        <LabelText>{i18n.personProfile.income.details.effect}</LabelText>
      </Label>
      <Gap size={'m'} />
      <FixedSpaceColumn data-qa="income-effect">
        {incomeEffects.map((effect) => (
          <Radio
            key={effect}
            label={i18n.personProfile.income.details.effectOptions[effect]}
            checked={editedIncome.effect === effect}
            onChange={() => setEditedIncome((prev) => ({ ...prev, effect }))}
            dataQa={`income-effect-${effect}`}
          />
        ))}
      </FixedSpaceColumn>
      <Gap size={'L'} />
      <Label>
        <LabelText>{i18n.personProfile.income.details.miscTitle}</LabelText>
      </Label>
      <Gap size={'m'} />
      <FixedSpaceColumn>
        <Checkbox
          label={i18n.personProfile.income.details.echa}
          checked={editedIncome.worksAtECHA}
          onChange={() =>
            setEditedIncome((prev) => ({
              ...prev,
              worksAtECHA: !prev.worksAtECHA
            }))
          }
        />
        <Checkbox
          label={i18n.personProfile.income.details.entrepreneur}
          checked={editedIncome.isEntrepreneur}
          onChange={() =>
            setEditedIncome((prev) => ({
              ...prev,
              isEntrepreneur: !prev.isEntrepreneur
            }))
          }
        />
      </FixedSpaceColumn>
      {baseIncome ? (
        <ListGrid labelWidth="fit-content(40%)" rowGap="xs" columnGap="L">
          <Label>{i18n.personProfile.income.details.updated}</Label>
          <span>{formatDate(baseIncome.updatedAt)}</span>
          <Label>{i18n.personProfile.income.details.handler}</Label>
          <span>{baseIncome.updatedBy}</span>
        </ListGrid>
      ) : null}
      {editedIncome.effect === 'INCOME' ? (
        <>
          <div className="separator" />
          <Title size={4}>
            {i18n.personProfile.income.details.incomeTitle}
          </Title>
          <IncomeTable
            data={editedIncome.data}
            editing
            setData={(data) =>
              setEditedIncome((prev) => ({
                ...prev,
                data: { ...prev.data, ...data }
              }))
            }
            setValidationError={(v) =>
              setValidationErrors((prev) => ({ ...prev, data: v }))
            }
            type="income"
          />
          <Title size={4}>
            {i18n.personProfile.income.details.expensesTitle}
          </Title>
          <IncomeTable
            data={editedIncome.data}
            editing
            setData={(data) =>
              setEditedIncome((prev) => ({
                ...prev,
                data: { ...prev.data, ...data }
              }))
            }
            setValidationError={(v) =>
              setValidationErrors((prev) => ({ ...prev, data: v }))
            }
            type="expenses"
          />
        </>
      ) : null}
      <ButtonsContainer>
        <Button
          onClick={cancel}
          text={i18n.personProfile.income.details.cancel}
        />
        <AsyncButton
          text={i18n.personProfile.income.details.save}
          disabled={Object.values(validationErrors).some(Boolean)}
          onClick={() =>
            !baseIncome
              ? create(editedIncome)
              : update({ ...baseIncome, ...editedIncome })
          }
          onSuccess={onSuccess}
          data-qa="save-income"
        />
      </ButtonsContainer>
    </>
  )
})

export default IncomeItemEditor
