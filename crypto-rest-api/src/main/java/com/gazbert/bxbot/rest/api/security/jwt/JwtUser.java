
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gazbert.crypto.rest.api.security.model.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Encapsulates the User details stored in the JWT.
 *
 * @author gazbert
 */
public class JwtUser implements UserDetails {

  private static final long serialVersionUID = -7857515944595149222L;

  private final Long id;
  private final String username;
  private final String firstname;
  private final String lastname;
  private final String password;
  private final String email;
  private final Collection<? extends GrantedAuthority> authorities;
  private final boolean enabled;
  private final long lastPasswordResetDate;
  private final List<String> roles;

  /**
   * Creates a JWT User.
   *
   * @param id the user's id.
   * @param username the user's name.
   * @param firstname the user's first name.
   * @param lastname the user's last name.
   * @param password the use's password.
   * @param email the user's email.
   * @param enabled is the user enabled or disabled?
   * @param lastPasswordResetDate the date the user's password was reset.
   * @param authorities the user's authorities.
   * @param roles the user's roles.
   */
  public JwtUser(
      Long id,
      String username,
      String firstname,
      String lastname,
      String password,
      String email,
      boolean enabled,
      long lastPasswordResetDate,
      Collection<? extends GrantedAuthority> authorities,
      List<Role> roles) {

    this.id = id;
    this.username = username;
    this.firstname = firstname;
    this.lastname = lastname;
    this.password = password;
    this.email = email;
    this.enabled = enabled;
    this.lastPasswordResetDate = lastPasswordResetDate;
    this.authorities = authorities;
    this.roles = new ArrayList<>();
    for (final Role role : roles) {
      this.roles.add(role.getName().name());
    }
  }

  @JsonIgnore
  public Long getId() {
    return id;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @JsonIgnore
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @JsonIgnore
  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @JsonIgnore
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  public String getFirstname() {
    return firstname;
  }

  public String getLastname() {
    return lastname;
  }

  public String getEmail() {
    return email;
  }

  @JsonIgnore
  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public long getLastPasswordResetDate() {
    return lastPasswordResetDate;
  }

  public List<String> getRoles() {
    return roles;
  }
}