package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Profile;

import java.util.List;

public interface ProfileService<U extends Profile> {

    U create(U profile) throws Exception;

    U update(String profileId, U profile) throws Exception;

    void delete(String id);

    U find(String profileId);

    List<U> findAll();


}
