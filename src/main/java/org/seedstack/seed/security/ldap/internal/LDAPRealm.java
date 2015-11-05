/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.seed.security.ldap.internal;

import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import org.seedstack.seed.security.AuthenticationInfo;
import org.seedstack.seed.security.AuthenticationToken;
import org.seedstack.seed.security.Realm;
import org.seedstack.seed.security.RoleMapping;
import org.seedstack.seed.security.RolePermissionResolver;
import org.seedstack.seed.security.UsernamePasswordToken;
import org.seedstack.seed.security.AuthenticationException;
import org.seedstack.seed.security.IncorrectCredentialsException;
import org.seedstack.seed.security.UnsupportedTokenException;
import org.seedstack.seed.security.principals.PrincipalProvider;
import org.seedstack.seed.security.principals.Principals;
import org.seedstack.seed.security.principals.SimplePrincipalProvider;
import org.seedstack.seed.security.ldap.api.LDAPSupport;
import org.seedstack.seed.security.ldap.api.LDAPUserContext;
import org.seedstack.seed.security.ldap.api.LDAPUserContextPrincipalProvider;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Set;

class LDAPRealm implements Realm {

    private RoleMapping roleMapping;

    private RolePermissionResolver rolePermissionResolver;

    @Inject
    private LDAPSupport ldapSupport;

    @Override
    public Set<String> getRealmRoles(PrincipalProvider<?> identityPrincipal, Collection<PrincipalProvider<?>> otherPrincipals) {
        SimplePrincipalProvider dnPrincipalProvider = Principals.getSimplePrincipalByName(otherPrincipals, "dn");
        try {
            LDAPUserContext userContext;
            if (dnPrincipalProvider != null) {
                userContext = ldapSupport.createUserContext(dnPrincipalProvider.getValue());
            } else {
                String identity = identityPrincipal.getPrincipal().toString();
                userContext = ldapSupport.findUser(identity);
            }
            return ldapSupport.retrieveUserGroups(userContext);
        } catch (org.seedstack.seed.security.ldap.api.LDAPException e) {
            throw new AuthenticationException(e.getCause().getMessage());
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (!(token instanceof UsernamePasswordToken)) {
            throw new UnsupportedTokenException("LDAPRealm only supports UsernamePasswordToken");
        }
        UsernamePasswordToken userNamePasswordToken = (UsernamePasswordToken) token;
        try {
            LDAPUserContext userContext = ldapSupport.findUser(userNamePasswordToken.getUsername());
            ldapSupport.authenticate(userContext, new String(userNamePasswordToken.getPassword()));

            AuthenticationInfo authcInfo = new AuthenticationInfo(userNamePasswordToken.getUsername(), userNamePasswordToken.getPassword());
            authcInfo.getOtherPrincipals().add(new SimplePrincipalProvider("dn", userContext.getDn()));
            authcInfo.getOtherPrincipals().add(Principals.fullNamePrincipal(ldapSupport.getAttributeValue(userContext, "cn")));
            authcInfo.getOtherPrincipals().add(new LDAPUserContextPrincipalProvider(userContext));
            return authcInfo;
        } catch (org.seedstack.seed.security.ldap.api.LDAPException ex) {
            LDAPException e = (LDAPException) ex.getCause();
            switch (e.getResultCode().intValue()) {
                case ResultCode.INVALID_CREDENTIALS_INT_VALUE:
                    throw new IncorrectCredentialsException(e.getMessage());
                default:
                    throw new AuthenticationException(e.getMessage());
            }
        }
    }

    @Override
    public RoleMapping getRoleMapping() {
        return this.roleMapping;
    }

    @Override
    public RolePermissionResolver getRolePermissionResolver() {
        return this.rolePermissionResolver;
    }

    /**
     * Setter roleMapping
     *
     * @param roleMapping the role mapping
     */
    @Inject
    public void setRoleMapping(@Named("LDAPRealm-role-mapping") RoleMapping roleMapping) {
        this.roleMapping = roleMapping;
    }

    /**
     * Setter rolePermissionResolver
     *
     * @param rolePermissionResolver the rolePermissionResolver
     */
    @Inject
    public void setRolePermissionResolver(@Named("LDAPRealm-role-permission-resolver") RolePermissionResolver rolePermissionResolver) {
        this.rolePermissionResolver = rolePermissionResolver;
    }

    @Override
    public Class<? extends AuthenticationToken> supportedToken() {
        return UsernamePasswordToken.class;
    }

}
