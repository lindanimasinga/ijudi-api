package io.curiousoft.izinga.commons.profile.events

import io.curiousoft.izinga.commons.model.Profile
import org.springframework.context.ApplicationEvent

abstract class ProfileEvent(source: Any, val profile: Profile) : ApplicationEvent(source)

