package io.curiousoft.izinga.commons.model

import java.time.DayOfWeek
import java.util.*

data class BusinessHours(var day: DayOfWeek, var open: Date, var close: Date)