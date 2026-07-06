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

        if (phone != null && !phone.isBlank()) {
            UserProfile profile = userProfileService.findUserByPhone(phone);
            log.info("[jwt-converter] profile={} role={}", profile != null ? profile.getId() : "NOT_FOUND", profile != null ? profile.getRole() : "null");

            if (profile != null && profile.getRole() == ProfileRoles.ADMIN) {
                authorities = List.of(() -> "ROLE_ADMIN");
            } else {
                authorities = List.of(() -> "ROLE_USER");
            }
        } else {
            log.warn("[jwt-converter] no phone_number claim in JWT for uid={} — granting ROLE_USER", uid);
            authorities = List.of(() -> "ROLE_USER");
        }

        return new JwtAuthenticationToken(jwt, authorities, uid);
    }
}
