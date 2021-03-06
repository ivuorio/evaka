// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

import { Strategy } from 'passport-saml'
import { Strategy as DummyStrategy } from 'passport-dummy'
import { SessionType } from '../../../session'

export interface SamlEndpointConfig {
  strategyName: string
  strategy: Strategy | DummyStrategy
  sessionType: SessionType
}

export interface PassportSamlError extends Error {
  statusXml?: string
}

export type PrimaryStatusCodeValue =
  | 'urn:oasis:names:tc:SAML:2.0:status:Success'
  | 'urn:oasis:names:tc:SAML:2.0:status:Requester'
  | 'urn:oasis:names:tc:SAML:2.0:status:Responder'
  | 'urn:oasis:names:tc:SAML:2.0:status:VersionMismatch'

export type SecondaryStatusCodeValue =
  | 'urn:oasis:names:tc:SAML:2.0:status:AuthnFailed'
  | 'urn:oasis:names:tc:SAML:2.0:status:RequestDenied'

export interface StatusObject {
  Status: {
    StatusCode: {
      '@_Value': PrimaryStatusCodeValue
      StatusCode?: {
        '@_Value': SecondaryStatusCodeValue
      }
    }
    StatusMessage: string
  }
}

export interface SamlUser {
  // eVaka id from person table
  id: string
  roles: string[]
  // fields used by passport-saml during logout flow
  nameID?: string
  nameIDFormat?: string
  nameQualifier?: string
  spNameQualifier?: string
  sessionIndex?: string
}
