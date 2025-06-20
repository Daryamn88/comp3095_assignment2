package com.academicplanner.assignmentservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KeycloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, ?> resourceAccess = jwt.getClaim("realm_access");
        if (resourceAccess == null) return Collections.emptyList();
        List<?> roles = (List<?>) resourceAccess.get("roles");
        return roles
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_%s".formatted(role)))
                .collect(Collectors.toSet());
    }
}
