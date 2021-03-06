// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import React from 'react'
import { isFailure, isLoading, Result } from 'api'
import { SpinnerSegment } from 'components/shared/atoms/state/Spinner'
import ErrorSegment from 'components/shared/atoms/state/ErrorSegment'

export function renderResult<T>(
  result: Result<T>,
  renderer: (data: T) => React.ReactNode
) {
  if (isLoading(result)) return <SpinnerSegment />

  if (isFailure(result)) return <ErrorSegment />

  return <>{renderer(result.data)}</>
}
