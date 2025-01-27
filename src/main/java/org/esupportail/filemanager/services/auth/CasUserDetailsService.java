package org.esupportail.filemanager.services.auth;

import org.apereo.cas.client.validation.Assertion;
import org.esupportail.filemanager.beans.CasUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.cas.userdetails.AbstractCasAssertionUserDetailsService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CasUserDetailsService extends AbstractCasAssertionUserDetailsService {

    Logger log = LoggerFactory.getLogger(CasUserDetailsService.class);

    @Override
    protected UserDetails loadUserDetails(Assertion assertion) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList();
        Map<String, Object> attributes = assertion.getPrincipal().getAttributes();
        log.info("Loading user attributes for CAS user {} : {}", assertion.getPrincipal().getName(), attributes);
        return new CasUser(assertion.getPrincipal().getName(), "NO_PASSWORD", grantedAuthorities, attributes);
    }
}
