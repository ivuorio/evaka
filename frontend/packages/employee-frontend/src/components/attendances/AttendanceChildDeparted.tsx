import React, { Fragment } from 'react'
import { useHistory } from 'react-router-dom'
import styled from 'styled-components'

import { deleteAttendance } from '~api/unit'
import AsyncButton from '~components/shared/atoms/buttons/AsyncButton'
import { DefaultMargins } from '~components/shared/layout/white-space'
import { useTranslation } from '~state/i18n'
import { UUID } from '~types'

export const WideAsyncButton = styled(AsyncButton)`
  @media screen and (max-width: 1023px) {
    margin-bottom: ${DefaultMargins.s};
    width: 100%;
    white-space: normal;
    height: 64px;
  }
`

interface Props {
  childAttendanceId: UUID
  id: UUID
  groupid: UUID | 'all'
}

export default React.memo(function AttendanceChildDeparted({
  childAttendanceId,
  id,
  groupid
}: Props) {
  const history = useHistory()
  const { i18n } = useTranslation()

  return (
    <Fragment>
      <WideAsyncButton
        primary
        text={i18n.attendances.actions.returnToComing}
        onClick={() => deleteAttendance(childAttendanceId)}
        onSuccess={() =>
          history.push(`/units/${id}/attendance/${groupid}/coming`)
        }
        data-qa="delete-attendance"
      />
    </Fragment>
  )
})