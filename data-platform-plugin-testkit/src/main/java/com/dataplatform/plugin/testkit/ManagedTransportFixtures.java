package com.dataplatform.plugin.testkit;

import com.dataplatform.plugin.spi.ConnectorException;
import com.dataplatform.plugin.spi.ConnectorRawResponse;
import com.dataplatform.plugin.spi.ConnectorRequest;
import com.dataplatform.plugin.spi.ManagedHttpTransport;
import com.dataplatform.plugin.spi.StageExecutionContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Deterministic managed transport fakes for real runtime executor tests. */
public final class ManagedTransportFixtures {

    private ManagedTransportFixtures() { }

    public static ScriptedManagedHttpTransport scripted(Object... outcomes) {
        return new ScriptedManagedHttpTransport(List.of(outcomes));
    }

    public static final class ScriptedManagedHttpTransport implements ManagedHttpTransport {
        private final Deque<Object> outcomes;
        private final List<ConnectorRequest> requests = new ArrayList<>();

        private ScriptedManagedHttpTransport(List<Object> outcomes) {
            this.outcomes = new ArrayDeque<>(outcomes);
        }

        @Override
        public synchronized ConnectorRawResponse execute(
                ConnectorRequest request,
                StageExecutionContext context) throws ConnectorException {
            requests.add(Objects.requireNonNull(request, "request"));
            Object outcome = outcomes.pollFirst();
            if (outcome instanceof ConnectorException exception) throw exception;
            if (outcome instanceof RuntimeException exception) throw exception;
            return (ConnectorRawResponse) Objects.requireNonNull(outcome, "scripted outcome");
        }

        public synchronized List<ConnectorRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
