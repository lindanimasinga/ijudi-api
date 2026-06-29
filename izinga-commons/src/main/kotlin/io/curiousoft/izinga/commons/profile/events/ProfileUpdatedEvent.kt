package io.curiousoft.izinga.commons.profile.events

import io.curiousoft.izinga.commons.model.Profile

class ProfileUpdatedEvent(source: Any, profile: Profile) : ProfileEvent(source, profile)

