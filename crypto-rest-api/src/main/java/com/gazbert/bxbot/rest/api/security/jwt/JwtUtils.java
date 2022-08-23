/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Stephan Zerhusen
 * Copyright (c) 2019 gazbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.gazbert.crypto.rest.api.security.jwt;

import com.gazbert.crypto.rest.api.security.authentication.JwtAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Util class for validating and accessing JSON Web Tokens.
 *
 * <p>Properties are loaded from the config/application.properties file.
 *
 * <p>Code originated from the excellent JWT and Spring Boot example by Stephan Zerhusen:
 * https://github.com/szerhusenBC/jwt-spring-security-demo
 *
 * @author gazbert
 */
@Component
public class JwtUtils {

  private static final Logger LOG = LogManager.getLogger();
  private static final String CUSTOM_CLAIM_NAMESPACE = "https://gazbert.com/crypto/";

  static final String CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE =
      CUSTOM_CLAIM_NAMESPACE + "lastPasswordChangeDate";
  static final String CLAIM_KEY_ROLES = CUSTOM_CLAIM_NAMESPACE + "roles";

  private static final String CLAIM_KEY_USERNAME = "sub";
  private static final String CLAIM_KEY_ISSUER = "iss";
  private static final String CLAIM_KEY_ISSUED_AT = "iat";
  private static final String CLAIM_KEY_AUDIENCE = "aud";

  @NotNull
  @Value("${crypto.restapi.jwt.secret}")
  private String secret;

  @NotNull
  @Value("${crypto.restapi.jwt.expiration}")
  @Min(1)
  private long expirationInSecs;

  @NotNull
  @Value("${crypto.restapi.jwt.allowed_clock_skew}")
  @Min(1)
  private long allowedClockSkewInSecs;

  @NotNull
  @Value("${crypto.restapi.jwt.issuer}")
  private String issuer;

  @NotNull
  @Value("${crypto.restapi.jwt.audience}")
  private String audience;

  /**
   * For simple validation, it is sufficient to check the token integrity by just decrypting it with
   * the signing key and making sure it has not expired. We don't have to call the database for an
   * additional User lookup/check for every request.
   *
   * @param token the JWT in String format.
   * @return the token claims if the JWT was valid.
   * @throws JwtAuthenticationException if the JWT was invalid.
   */
  public Claims validateTokenAndGetClaims(String token) {
    try {
      final Claims claims = getClaimsFromToken(token);
      final Date created = getIssuedAtDateFromTokenClaims(claims);
      final Date lastPasswordResetDate = getLastPasswordResetDateFromTokenClaims(claims);
      if (!isCreatedAfterLastPasswordReset(created, lastPasswordResetDate)) {
        final String errorMsg =
            "Invalid token! Created date claim is before last password reset date."
                + " Created date: "
                + created
                + " Password reset date: "
                + lastPasswordResetDate;
        LOG.error(errorMsg);
        throw new JwtAuthenticationException(errorMsg);
      }
      return claims;
    } catch (Exception e) {
      final String errorMsg = "Invalid token! Details: " + e.getMessage();
      LOG.error(errorMsg, e);
      throw new JwtAuthenticationException(errorMsg, e);
    }
  }

  /**
   * Creates a JWT in String format.
   *
   * @param userDetails the JWT User details.
   * @return the JWT as a String.
   */
  public String createToken(JwtUser userDetails) {
    final Map<String, Object> claims = new HashMap<>();
    claims.put(CLAIM_KEY_ISSUER, issuer);
    claims.put(CLAIM_KEY_ISSUED_AT, new Date());
    claims.put(CLAIM_KEY_AUDIENCE, audience);
    claims.put(CLAIM_KEY_USERNAME, userDetails.getUsername());
    claims.put(CLAIM_KEY_ROLES, mapRolesFromGrantedAuthorities(userDetails.getAuthorities()));
    claims.put(CLAIM_KEY_LAST_PASSWORD_CHANGE_DATE, userDetails.getLastPasswordResetDate());
    return buildToken(claims);
  }

  /**
   * Checks if a JWT can be refreshed.
   *
   * <p>The creation time of the current JWT must be AFTER than the last password reset date.
   * Earlier tokens are deemed to be invalid and potentially compromised.
   *
   * @param claims the JWT claims.
   * @param lastPasswordReset the last password reset date.
   * @return true if the token can be refreshed, false otherwise.
 