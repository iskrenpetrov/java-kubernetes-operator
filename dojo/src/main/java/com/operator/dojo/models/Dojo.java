package com.operator.dojo.models;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("sample.operator.com")
@Version("v1")
@Plural("dojos")
public class Dojo extends CustomResource<DojoSpec, DojoStatus> implements Namespaced {

    @Override
    public DojoStatus initStatus(){
        return new DojoStatus("Initial Status");
    }
}
