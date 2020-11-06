# SAML authentication

- IdP `RelayState` only trusted if relative URL
  - Fallback: default app URL

## Flow

### Login

1. Users clicks login in evaka, frontend redirects to `/auth/saml/login`
1. GET `/auth/saml/login` (with optional `?RelayState`)
    1. apigw generates a LoginRequest which redirects to IdP
        - Audit event: `evaka.saml.${strategyName}.sign_in_started`
    1. User logs in within IdP
        - User might already be logged in via SSO (e.g. Azure AD)
    1. IdP generates SAML 2.0 response and redirects to `/auth/saml/login/callback` with LoginResponse
1. POST `/auth/saml/login/callback`
    1. LoginResponse parsed
        - Audit event: `evaka.saml.${strategyName}.sign_in` (or `.sign_in_failed`)
    1. apigw generates logout token and from token, a logout cookie (`${cookiePrefix(sessionType)}.logout`), and adds it to response
        - Also saves token to Redis
    1. apigw redirects to original `RelayState` URL (if exists)
        - logout cookie included in redirect response

### Logout

#### In-app (SP-initiated)

1. User click logout in evaka, frontend redirects to `/auth/saml/logout`
1. GET `/auth/saml/logout`
    1. apigw generates a LogoutRequest which redirects to IdP
        - Audit event: `evaka.saml.${strategyName}.sign_out_requested`
    1. IdP logs user out and also triggeres Single Logout (SLO) for all other SPs
    1. IdP generates SAML 2.0 response and redirects to `/auth/saml/logout/callback` with LogoutResponse
1. GET `/auth/saml/logout/callback`
    1. LogoutResponse parsed
        - Audit event: `evaka.saml.${strategyName}.sign_out` (or `.sign_out_failed`)
    1. apigw consumes logout token from logout cookie and sends a "clear cookie" response
        - Also clears it from Redis if it exists
    1. apigw redirects to original `RelayState` URL (if exists)
        - logout cookie included in redirect response

#### Single Logout (SLO, IdP-initiated)

1. IdP triggers Single Logout (e.g. user logs out via another app) and opens an iframe to `/auth/saml/logout/callback` with a LogoutRequest
1. POST `/auth/saml/logout/callback`
    1. LogoutRequest parsed
        - Audit event: `evaka.saml.${strategyName}.sign_out`
    1. apigw consumes logout token from logout cookie and sends a "clear cookie" response
        - Also clears it from Redis if it exists
