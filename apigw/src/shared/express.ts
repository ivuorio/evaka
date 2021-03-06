// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import type express from 'express'
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import type { SamlUser } from './routes/auth/saml/types'

export interface LogoutToken {
  // milliseconds value of a Date. Not an actual Date because it will be JSONified
  expiresAt: number
  value: string
}

export type AsyncRequestHandler = (
  req: express.Request,
  res: express.Response
) => Promise<void>

// A middleware calls next() on success, and next(err) on failure
export function toMiddleware(f: AsyncRequestHandler): express.RequestHandler {
  return (req, res, next) =>
    f(req, res)
      .then(() => next())
      .catch(next)
}

// A request handler calls nothing on success, and next(err) on failure
export function toRequestHandler(
  f: AsyncRequestHandler
): express.RequestHandler {
  return (req, res, next) => f(req, res).catch(next)
}

// TS interface merging is used to add fields to the type of express
// req.user and req.session
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      traceId?: string
      spanId?: string
    }
    // eslint-disable-next-line @typescript-eslint/no-empty-interface
    interface User extends SamlUser {}
    interface SessionData {
      logoutToken?: LogoutToken
    }
  }
}
