package com.redhat.mercury.operator.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public class DeploymentEventSource extends AbstractEventSource implements Watcher<Deployment> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentEventSource.class);
    public static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
    public static final String OPERATOR_NAME = "service-domain-cluster-operator";

    private final KubernetesClient client;

    public static DeploymentEventSource createAndRegisterWatch(KubernetesClient client) {
        DeploymentEventSource eventSource = new DeploymentEventSource(client);
        eventSource.registerWatch();
        return eventSource;
    }

    private DeploymentEventSource(KubernetesClient client) {
        this.client = client;
    }

    private void registerWatch() {
        client.resources(Deployment.class)
                .inAnyNamespace()
                .withLabel(MANAGED_BY_LABEL, OPERATOR_NAME)
                .watch(this);
    }

    @Override
    public void eventReceived(Action action, Deployment resource) {
        if (eventHandler == null) {
            LOGGER.warn("Ignoring action {} for resource {}. EventHandler has not yet been initialized.", action, resource);
            return;
        }
        LOGGER.info(
                "Event received for action: {}, Service: {}",
                action.name(),
                resource.getMetadata().getName());
        if (action == Action.ERROR) {
            LOGGER.warn(
                    "Skipping {} event for custom resource uid: {}, version: {}",
                    action,
                    getUID(resource),
                    getVersion(resource));
            return;
        }
        eventHandler.handleEvent(new DeploymentEvent(action, resource, this));
    }

    @Override
    public void onClose(WatcherException e) {
        if (e == null) {
            return;
        }
        if (e.isHttpGone()) {
            LOGGER.warn("Received error for watch, will try to reconnect.", e);
            registerWatch();
        } else {
            // Note that this should not happen normally, since fabric8 client handles reconnect.
            // In case it tries to reconnect this method is not called.
            LOGGER.error("Unexpected error happened with watch. Will exit.", e);
            System.exit(1);
        }
    }
}
