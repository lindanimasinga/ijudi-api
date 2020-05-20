package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Profile;

public interface UserService {

    Profile create(Profile profile) throws Exception;

    Profile update(String profileId, Profile profile) throws Exception;

    void delete(String id);

    Profile find(String profileId);


}
