package io.curiousoft.izinga.commons.profile.events

import io.curiousoft.izinga.commons.model.Profile

class ProfileCreatedEvent(source: Any, profile: Profile) : ProfileEvent(source, profile)

