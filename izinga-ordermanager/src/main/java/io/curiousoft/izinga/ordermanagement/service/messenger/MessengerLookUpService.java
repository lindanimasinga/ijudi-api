package io.curiousoft.izinga.ordermanagement.service.messenger;

import io.curiousoft.izinga.commons.model.ProfileAvailabilityStatus;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessengerLookUpService {
    private static final Logger LOG = LoggerFactory.getLogger(MessengerLookUpService.class);
    private static final int PREFERRED_RESPONSE_TIME_MINUTES = 5;

    private final UserProfileRepo userProfileRepo;

    public MessengerLookUpService(UserProfileRepo userProfileRepo) {
        this.userProfileRepo = userProfileRepo;
    }

    public List<UserProfile> findNearbyMessengers(double fromLat, double fromLong, double radiusKm) {
        try {
            // Step 1: Calculate bounding box for radius and query messengers within it
            double latDelta = radiusKm / 111.0;
            double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(fromLat)));

            double minLat = fromLat - latDelta;
            double maxLat = fromLat + latDelta;
            double minLng = fromLong - lngDelta;
            double maxLng = fromLong + lngDelta;

            List<UserProfile> candidatesInRadius = userProfileRepo.findByRoleAndLatitudeBetweenAndLongitudeBetween(
                    ProfileRoles.MESSENGER,
                    minLat,
                    maxLat,
                    minLng,
                    maxLng);

            if (candidatesInRadius == null || candidatesInRadius.isEmpty()) {
                LOG.info("No messengers found within {}km of ({}, {})", radiusKm, fromLat, fromLong);
                return List.of();
            }

            LOG.debug("Found {} messenger candidates within radius", candidatesInRadius.size());

            // Decision tree filtering
            List<UserProfile> eligibleMessengers = candidatesInRadius.stream()
                    // Step 2: termsAccepted must be true
                    .filter(this::hasAcceptedTerms)
                    // Step 3: profileApproved must be true
                    .filter(this::isProfileApproved)
                    // Step 4: availabilityStatus must be ONLINE or AWAY
                    .filter(this::isAvailable)
                    .collect(Collectors.toList());

            if (eligibleMessengers.isEmpty()) {
                LOG.info("No eligible messengers after filtering for terms, approval, and availability");
                return List.of();
            }

            LOG.debug("{} messengers passed eligibility filters", eligibleMessengers.size());

            // Step 5: Sort by preference (imageUrl present, fast response time)
            eligibleMessengers.sort(messengerPreferenceComparator());

            LOG.info("Returning {} eligible messengers sorted by preference", eligibleMessengers.size());
            return eligibleMessengers;

        } catch (Exception e) {
            LOG.error("Error finding nearby messengers for lat={}, lon={}, radius={}", fromLat, fromLong, radiusKm, e);
            return List.of();
        }
    }

    private boolean hasAcceptedTerms(UserProfile messenger) {
        boolean accepted = Boolean.TRUE.equals(messenger.getTermsAccepted());
        if (!accepted) {
            LOG.trace("Messenger {} excluded: terms not accepted", messenger.getId());
        }
        return accepted;
    }

    private boolean isProfileApproved(UserProfile messenger) {
        boolean approved = messenger.getProfileApproved();
        if (!approved) {
            LOG.trace("Messenger {} excluded: profile not approved", messenger.getId());
        }
        return approved;
    }

    private boolean isAvailable(UserProfile messenger) {
        ProfileAvailabilityStatus status = messenger.getAvailabilityStatus();
        boolean available = status == ProfileAvailabilityStatus.ONLINE || status == ProfileAvailabilityStatus.AWAY;
        if (!available) {
            LOG.trace("Messenger {} excluded: availability status is {}", messenger.getId(), status);
        }
        return available;
    }

    private Comparator<UserProfile> messengerPreferenceComparator() {
        return (m1, m2) -> {
            int score1 = calculatePreferenceScore(m1);
            int score2 = calculatePreferenceScore(m2);
            // Higher score = more preferred, so sort descending
            return Integer.compare(score2, score1);
        };
    }

    private int calculatePreferenceScore(UserProfile messenger) {
        int score = 0;
        // Prefer messengers with an image (+2 points)
        if (hasImageUrl(messenger)) {
            score += 2;
        }
        // Prefer messengers with fast response time (+1 point)
        if (hasFastResponseTime(messenger)) {
            score += 1;
        }
        return score;
    }

    private boolean hasImageUrl(UserProfile messenger) {
        String imageUrl = messenger.getImageUrl();
        return imageUrl != null && !imageUrl.isBlank();
    }

    private boolean hasFastResponseTime(UserProfile messenger) {
        return messenger.getResponseTimeMinutes() < PREFERRED_RESPONSE_TIME_MINUTES;
    }
}
