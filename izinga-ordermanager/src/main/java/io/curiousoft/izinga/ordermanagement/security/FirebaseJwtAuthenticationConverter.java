package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.usermanagement.users.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class FirebaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(FirebaseJwtAuthenticationConverter.class);

    private final UserProfileService userProfileService;

    public FirebaseJwtAuthenticationConverter(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String phone = jwt.getClaimAsString("phone_number");
        String uid   = jwt.getSubject();
        log.info("[jwt-converter] uid={} phone_number={}", uid, phone);

        Collection<org.springframework.security.core.GrantedAuthority> authorities;

        UserProfile profile = null;
        if (phone != null && !phone.isBlank()) {
            profile = userProfileService.findUserByPhone(phone);
            log.info("[jwt-converter] profile={} role={}", profile != null ? profile.getId() : "NOT_FOUND", profile != null ? profile.getRole() : "null");
        } else {
            log.warn("[jwt-converter] no phone_number claim in JWT for uid={} — granting no authorities", uid);
        }

        ProfileRoles role = (profile != null) ? profile.getRole() : null;
        if (role != null) {
            authorities = List.of(() -> "ROLE_" + role.name());
        } else {
            authorities = List.of();
        }

        String principalName = (profile != null && profile.getId() != null) ? profile.getId() : uid;
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }
}
