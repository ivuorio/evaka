// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React, { useContext, useEffect, useState } from 'react'
import { useTranslation } from '~state/i18n'
import { FixedSpaceColumn } from 'components/shared/layout/flex-helpers'
import { UUID } from 'types'
import AddButton from 'components/shared/atoms/buttons/AddButton'
import { isFailure, isLoading, isSuccess, Loading, Result } from 'api'
import { ApplicationNote } from 'types/application'
import { useRestApi } from 'utils/useRestApi'
import { getApplicationNotes } from 'api/applications'
import { SpinnerSegment } from 'components/shared/atoms/state/Spinner'
import ErrorSegment from 'components/shared/atoms/state/ErrorSegment'
import ApplicationNoteBox from 'components/application-page/ApplicationNoteBox'
import { UserContext } from 'state/user'
import { requireRole } from 'utils/roles'
import styled from 'styled-components'
import { DefaultMargins, Gap } from 'components/shared/layout/white-space'

const Sticky = styled.div`
  position: sticky;
  top: ${DefaultMargins.s};
`

type Props = {
  applicationId: UUID
}

export default React.memo(function ApplicationNotes({ applicationId }: Props) {
  const { i18n } = useTranslation()
  const { roles, user } = useContext(UserContext)

  const [notes, setNotes] = useState<Result<ApplicationNote[]>>(Loading())
  const [editing, setEditing] = useState<UUID | null>(null)
  const [creating, setCreating] = useState<boolean>(false)

  const loadNotes = useRestApi(
    () => getApplicationNotes(applicationId),
    setNotes
  )
  useEffect(loadNotes, [loadNotes, applicationId])

  const editAllowed = (note: ApplicationNote): boolean => {
    return (
      requireRole(roles, 'ADMIN', 'SERVICE_WORKER') ||
      !!(
        requireRole(roles, 'UNIT_SUPERVISOR') &&
        user &&
        user.id &&
        user.id === note.createdBy
      )
    )
  }

  return (
    <>
      {isLoading(notes) && <SpinnerSegment />}
      {isFailure(notes) && <ErrorSegment />}
      {isSuccess(notes) && (
        <>
          <FixedSpaceColumn>
            {notes.data.map((note) =>
              editing === note.id ? (
                <ApplicationNoteBox
                  key={note.id}
                  note={note}
                  onSave={() => {
                    setEditing(null)
                    loadNotes()
                  }}
                  onCancel={() => setEditing(null)}
                />
              ) : (
                <ApplicationNoteBox
                  key={note.id}
                  note={note}
                  editable={!creating && editing === null && editAllowed(note)}
                  onStartEdit={() => setEditing(note.id)}
                  onDelete={() => loadNotes()}
                />
              )
            )}
          </FixedSpaceColumn>

          {notes.data.length > 0 && <Gap size="s" />}

          <Sticky>
            {creating ? (
              <ApplicationNoteBox
                applicationId={applicationId}
                onSave={() => {
                  setCreating(false)
                  loadNotes()
                }}
                onCancel={() => setCreating(false)}
              />
            ) : editing ? null : (
              <AddButton
                onClick={() => setCreating(true)}
                text={i18n.application.notes.add}
                dataQa="add-note"
              />
            )}
          </Sticky>
        </>
      )}
    </>
  )
})
