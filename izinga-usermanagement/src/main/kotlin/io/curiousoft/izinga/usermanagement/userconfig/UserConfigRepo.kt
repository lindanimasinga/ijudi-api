package io.curiousoft.izinga.usermanagement.userconfig

import org.springframework.data.mongodb.repository.MongoRepository

interface UserConfigRepo : MongoRepository<UserConfig, String>

