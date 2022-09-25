package com.operator.dojo.controllers;

import com.operator.dojo.models.Dojo;
import com.operator.dojo.models.DojoStatus;
import java.time.Duration;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
@Component
public class DojoController implements ResourceController<Dojo> {

    private final KubernetesClient client;

    @Autowired
    private RestTemplate restTemplate;

    private EventSourceManager eventSourceManager;

    private String pythonService;

    public static final long DELAY_TIME = Duration.ofMinutes(1).toMillis();
    public static final long PERIOD_TIME = Duration.ofMinutes(1).toMillis();

    public static final int PYTHON_SERVICE_INIT_RETRIES = 5;
    public static final long PYTHON_SERVICE_INIT_SLEEP = Duration.ofSeconds(15).toSeconds();

    public DojoController(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public DeleteControl deleteResource(Dojo resource, Context<Dojo> context) {
        String RESET_VALUE = "";
        setState(RESET_VALUE);

        return ResourceController.super.deleteResource(resource, context);
    }

    @Override
    public UpdateControl<Dojo> createOrUpdateResource(Dojo dojo, Context<Dojo> context) {

        Service service = client
                .services()
                .inNamespace(dojo.getMetadata().getNamespace())
                .withName(dojo.getMetadata().getName())
                .get();

        if (service == null) {
            Service newService = createPythonEndpointService(dojo);
            client.services().inNamespace(dojo.getMetadata().getNamespace()).createOrReplace(newService);
        }

        Deployment deployment = client.apps()
                .deployments()
                .inNamespace(dojo.getMetadata().getNamespace())
                .withName(dojo.getMetadata().getName())
                .get();

        if (deployment == null) {
            Deployment newDeployment = createPythonServiceDeployment(dojo);
            client.apps().deployments().inNamespace(dojo.getMetadata().getNamespace()).createOrReplace(newDeployment);
        }

        String dojoUid = dojo.getMetadata().getUid();
        String eventSourceName = String.format("dojo-%s", dojoUid);
        log.info("Starting createOrUpdateResource method with dojo {}", dojoUid);

        if (!eventSourceManager.getRegisteredEventSources().containsKey(eventSourceName)){
            log.info(String.format("Event Source not found - registering %s", eventSourceName));
            TimerEventSource timerEventSource = new TimerEventSource();
            timerEventSource.schedule(dojo, 1000 * 60, 1000 * 60);

            eventSourceManager.registerEventSource(eventSourceName, timerEventSource);
        }

        pythonService = getServiceName(dojo);

        if (!waitForServiceToStart(dojo)) {
            log.error("Python service at {} FAILED TO START", getServiceName(dojo));
        }

        String currentState = getCurrentState();
        String desiredState = dojo.getSpec().getContent();

        if(!currentState.equals(desiredState)){
            setState(desiredState);
        }

        dojo.setStatus(new DojoStatus("New Status: OK"));

        return UpdateControl.updateStatusSubResource(dojo);
    }

    @Override
    public void init(EventSourceManager eventSourceManager) {
        this.eventSourceManager = eventSourceManager;
        ResourceController.super.init(eventSourceManager);
    }

    private String getCurrentState(){
        return restTemplate.getForObject(pythonService, String.class);
    }

    private void setState(String desiredState){
        restTemplate.put(String.format("%s?content=%s", pythonService, desiredState), null);
    }

    private Map<String, String> labelsForPythonService() {
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "python-service");
        return labels;
    }

    private Deployment createPythonServiceDeployment(Dojo dojo) {
        return new DeploymentBuilder()
                .withKind("Deployment")
                .withApiVersion("apps/v1")
                .withMetadata(
                        new ObjectMetaBuilder()
                                .withName(dojo.getMetadata().getName())
                                .withNamespace(dojo.getMetadata().getNamespace())
                                .build())
                .withSpec(
                        new DeploymentSpecBuilder()
                                .withReplicas(1)
                                .withSelector(
                                        new LabelSelectorBuilder().withMatchLabels(labelsForPythonService()).build())
                                .withTemplate(
                                        new PodTemplateSpecBuilder()
                                                .withMetadata(
                                                        new ObjectMetaBuilder().withLabels(labelsForPythonService()).build())
                                                .withSpec(
                                                        new PodSpecBuilder()
                                                                .withContainers(
                                                                        new ContainerBuilder()
                                                                                .withImage("python-service:latest")
                                                                                .withImagePullPolicy("Never")
                                                                                .withName("python-service")
                                                                                .withPorts(
                                                                                        new ContainerPortBuilder()
                                                                                                .withContainerPort(5000)
                                                                                                .build())
                                                                                .build())
                                                                .build())
                                                .build())
                                .build())
                .build();
    }

    private Service createPythonEndpointService(Dojo dojo) {
        IntOrString targetPort = new IntOrString(5000);
        return new ServiceBuilder()
                .withKind("Service")
                .withApiVersion("v1")
                .withMetadata(
                        new ObjectMetaBuilder()
                                .withName(dojo.getMetadata().getName())
                                .withNamespace(dojo.getMetadata().getNamespace())
                                .build())
                .withSpec(
                        new ServiceSpecBuilder()
                                .withSelector(labelsForPythonService())
                                .withPorts(
                                        new ServicePortBuilder()
                                                .withProtocol("TCP")
                                                .withPort(80)
                                                .withTargetPort(targetPort)
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    private String getServiceName(Dojo dojo) {
        return "http://" + dojo.getMetadata().getName() + "." + dojo.getMetadata().getNamespace();
    }

    private boolean waitForServiceToStart(Dojo dojo) {
        for (int i = 0; i < PYTHON_SERVICE_INIT_RETRIES; i++) {
            try {
                getCurrentState();
                log.info("Python service successfully started at {}", getServiceName(dojo));
                return true;
            } catch (Exception e) {
                log.warn("Python service hasn't started for {}", getServiceName(dojo));
                try {
                    TimeUnit.SECONDS.sleep(PYTHON_SERVICE_INIT_SLEEP);
                } catch (InterruptedException ie) {
                    log.warn("Sleep was interrupted");
                }
            }
        }

        log.error("Python service at {} has failed to start", getServiceName(dojo));
        return false;
    }

}
