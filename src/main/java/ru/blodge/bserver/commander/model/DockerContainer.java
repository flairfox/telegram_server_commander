package ru.blodge.bserver.commander.model;

import java.util.List;
import java.util.Set;

public record DockerContainer(
        String names,
        String id,

        Set<String> networks,

        DockerContainerStatus status
) {}
