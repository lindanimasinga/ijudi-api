package io.curiousoft.ijudi.ordermanagent.service;

import io.curiousoft.ijudi.ordermanagent.model.Profile;

import java.util.List;

public interface ProfileService<U extends Profile> {

    U create(U profile) throws Exception;

    U update(String profileId, U profile) throws Exception;

    void delete(String id);

    U find(String profileId);

    List<U> findAll();


}
